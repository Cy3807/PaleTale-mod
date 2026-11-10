package cn.paletale.companion;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static cn.paletale.companion.MarketData.*;
import static cn.paletale.companion.MarketTheme.*;

public final class MarketDetailScreen extends WindowScreen {
    /** A vertical slice of the scrolling column; draw receives the top-left of the slice. */
    private record Block(int height, BiConsumer<GuiGraphics, int[]> draw) {}
    private final Screen parent;
    private final MarketScreen list;
    private final List<int[]> linkHits = new ArrayList<>();
    private final MarketApi api;
    private Ad ad;
    private boolean bindingsChanged, reloading;
    private String reloadError = "";
    private final List<Block> blocks = new ArrayList<>();
    private final long openedAt = System.nanoTime();
    private int contentHeight, scroll, viewTop, viewHeight;
    private int left, panelWidth, side, bodyX, bodyWidth;
    private boolean copied, favoriteBusy, favoriteAsked, viewed, detailAsked;
    private int navY;
    private boolean navShown;
    private String navTitle = "", navDetail = "";
    private FavoriteApi.State favorite;
    private final FavoriteApi favorites;
    private String favoriteError = "";
    private final List<int[]> bindHits = new ArrayList<>();
    private final List<int[]> navHits = new ArrayList<>();

    /** {@code list} is the ad list to refresh after a chest binding; null when opened from an archive page. */
    MarketDetailScreen(Screen parent, MarketScreen list, MarketApi api, Ad ad) {
        super(Component.literal(ad.title()));
        this.parent = parent; this.list = list;
        this.api = api; this.favorites = PaleTaleClient.favoritesFor(api);
        this.ad = ad;
    }

    @Override
    protected void init() {
        fitWindow();
        panelWidth = Math.min(820, width - 20);
        left = (width - panelWidth) / 2;
        side = Math.max(112, Math.min(190, panelWidth / 3));
        bodyX = left + side + 16;
        bodyWidth = panelWidth - side - 16 - 8;
        viewTop = 44;
        viewHeight = height - 44 - 34;
        addRenderableWidget(new Action("返回列表", left, 12, 62, 19, false, null, b -> onClose()));
        String starLabel = favorite == null ? "☆ 收藏" : (favorite.favorited() ? "★ 已收藏 " : "☆ 收藏 ") + favorite.favorites();
        Button star = addRenderableWidget(new Action(starLabel, left + panelWidth - 84, 12, 84, 19, favorite != null && favorite.favorited(), null, b -> toggleFavorite()));
        star.active = !favoriteBusy;
        star.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(!favoriteError.isEmpty() ? favoriteError
                : PaleTaleClient.ACCOUNT.loggedIn() ? "收藏后，价格、状态变动会在游戏内提醒" : "登录 PaleTale 后可收藏")));
        if (!viewed) { viewed = true; favorites.viewAd(PaleTaleClient.ACCOUNT.token(), ad.code()); }
        // Lists carry no full text or contact: fetch the detail once and show it when it arrives.
        if (!detailAsked && ad.body().isEmpty() && ad.contactQq().isEmpty() && ad.contactNote().isEmpty()) {
            detailAsked = true;
            api.detail(ad.code()).whenComplete((fresh, failure) -> minecraft.execute(() -> {
                if (minecraft.screen == this && failure == null && fresh.code().equals(ad.code())) { ad = fresh; rebuildWidgets(); }
            }));
        }
        // The ad's own categories as small icons (their names on hover), under the identity column.
        int tagX = left + 52; // beside the state badge
        for (String tag : ad.tags()) {
            var category = java.util.Arrays.stream(MarketData.Category.values()).filter(c -> c.id.equals(tag) && !c.id.isEmpty()).findFirst().orElse(null);
            if (category == null || tagX + 14 > left + side - 4) continue;
            var icon = addRenderableWidget(new Action("", tagX, tagIconsY(), 14, 14, false, category.id, b -> {}));
            icon.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(category.label)));
            tagX += 16;
        }
        if (!favoriteAsked && PaleTaleClient.ACCOUNT.token() != null) {
            favoriteAsked = true;
            favorites.adState(PaleTaleClient.ACCOUNT.token(), ad.code()).whenComplete((state, failure) -> minecraft.execute(() -> {
                if (minecraft.screen == this && failure == null) { favorite = state; rebuildWidgets(); }
            }));
        }
        var firstPlace = NavTargets.ofAd(ad);
        // Two lines on the button: what it does, and exactly where it goes (drawn over the button in renderWindow).
        // A short window has no room for the second line: the button stays one line and the details are in its tooltip.
        boolean roomy = height >= 250;
        navY = roomy ? height - 92 : height - 80;
        navTitle = Navigation.target() == null ? "导航" : "导航中…";
        navDetail = firstPlace == null ? "这则广告没有可导航的地址" : (firstPlace.name().isBlank() ? "广告地点" : firstPlace.name()) + " · " + firstPlace.x() + ", " + (firstPlace.y() == null ? "~" : firstPlace.y()) + ", " + firstPlace.z();
        var navButton = addRenderableWidget(new Action(navTitle, left, navY, side, roomy ? 31 : 19, Navigation.target() != null, "compass", b -> {
            // The first address the advertiser set (chest shops have their own button on each product).
            Navigation.start(new Navigation.Target(firstPlace.name(), firstPlace.x(), firstPlace.y(), firstPlace.z(), firstPlace.kind(), firstPlace.marks())); rebuildWidgets();
        }));
        navButton.active = firstPlace != null;
        navButton.setDisplayLabel(roomy ? "" : null);
        navShown = roomy;
        if (firstPlace != null) navButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("导航到：" + navDetail)));
        Button copy = addRenderableWidget(new Action(copied ? "已复制地址" : "复制地址", left, height - 56, side, 19, false, null, b -> {
            minecraft.keyboardHandler.setClipboard(ad.locations().stream().map(location ->
                    location.name() + "：" + location.coordinates() + (location.directions().isBlank() ? "" : "\n" + location.directions()))
                    .collect(Collectors.joining("\n")));
            copied = true; rebuildWidgets();
        }));
        copy.active = ad.locations().stream().anyMatch(location -> !location.coordinates().isBlank());
        addRenderableWidget(new Action("网站详情", left, height - 32, side, 19, true, null, ConfirmLinkScreen.confirmLink(this, api.adUri(ad))));
        if (!reloadError.isEmpty()) addRenderableWidget(new Action("重试刷新", bodyX, height - 32, 76, 19, false, null, b -> refreshAfterBinding()));
        build();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - viewHeight)));
    }

    /** Top of the category icons in the left column (below 编号 and 更新). */
    private int tagIconsY() {
        int icon = Math.round(Math.min(side - 16, 102) * 10f / 16), y = 60 + icon; // the cover, 16:10 like the site (kept small: the column also holds the advertiser, tags and the buttons)
        if (!ad.author().mcName().equals(ad.author().name())) y += 14;
        return y + 20;
    }

    void refreshAfterBinding() {
        WorldPages.CACHE.invalidate(api.site());api.invalidatePublic();
        bindingsChanged = true;
        if (reloading) return;
        reloading = true; reloadError = "";
        api.detail(ad.code()).whenComplete((fresh, failure) -> minecraft.execute(() -> {
            reloading = false;
            if (minecraft.screen != this) return;
            if (failure == null && fresh.code().equals(ad.code())) { ad = fresh; reloadError = ""; }
            else reloadError = "广告暂时无法刷新，请稍后重试。";
            rebuildWidgets();
        }));
    }

    private void toggleFavorite() {
        String token = PaleTaleClient.ACCOUNT.token();
        if (token == null) { favoriteError = "请先在列表页右上角登录 PaleTale 账号。"; PaleTaleClient.ACCOUNT.start(minecraft); rebuildWidgets(); return; }
        favoriteBusy = true; favoriteError = ""; rebuildWidgets();
        favorites.setAd(token, ad.code(), favorite == null || !favorite.favorited()).whenComplete((state, failure) -> minecraft.execute(() -> {
            favoriteBusy = false;
            if (failure == null) {DiskPages.clear(DiskPages.owner(api.site()));FavoritesHub.clearAdCache();}
            if (minecraft.screen != this) return;
            if (failure == null) favorite = state;
            else favoriteError = failure.getCause() instanceof IllegalStateException cause ? cause.getMessage() : "收藏暂时失败，请稍后重试。";
            rebuildWidgets();
        }));
    }
    private void build() {
        blocks.clear();
        // The full text sits on top (as 广告内容 on the website); the short summary moved to the left column.
        wrapped(ad.body().isBlank() ? "广告主暂未补充正文。" : ad.body(), ad.body().isBlank() ? MUTED : INK);
        heading("商品与服务 · " + ad.products().size());
        if (ad.products().isEmpty()) wrapped("尚未单独列出商品，请查看网站详情。", MUTED);
        // Products grouped by the owner's category (as on the website): a coloured band per group that folds and unfolds on click.
        boolean grouped = ad.products().stream().anyMatch(product -> !product.categories().isEmpty());
        if (!grouped) for (int i = 0; i < ad.products().size(); i++) product(ad.products().get(i), i, MarketTheme.c(0xFFFAFAF7), true, 0);
        else {
            var root = new CategoryNode("");
            var uncategorized = new CategoryNode("未分类");
            for (int i = 0; i < ad.products().size(); i++) {
                var path = ad.products().get(i).categories();
                var node = path.isEmpty() ? uncategorized : root;
                if (!path.isEmpty()) node.total++;
                if (path.isEmpty()) { node.products.add(i); node.total++; continue; }
                for (int level = 0; level < path.size(); level++) { node = node.child(path.get(level)); node.total++; }
                node.products.add(i);
            }
            if (!uncategorized.products.isEmpty()) root.children.put("\u0000未分类", uncategorized);
            int shade = 0;
            for (var child : root.children.values()) categoryNode(child, 0, BAND_COLORS[shade++ % BAND_COLORS.length], child.name);
        }
        contentHeight = blocks.stream().mapToInt(Block::height).sum();
    }

    private void wrapped(String text, int color) {
        List<FormattedCharSequence> rows = font.split(Component.literal(text), bodyWidth - 8);
        blocks.add(new Block(rows.size() * 12 + 6, (g, at) -> {
            for (int i = 0; i < rows.size(); i++) g.drawString(font, rows.get(i), at[0] + 2, at[1] + 2 + i * 12, color, false);
        }));
    }

    private void heading(String title) {
        blocks.add(new Block(24, (g, at) -> {
            g.drawString(font, title, at[0] + 2, at[1] + 10, INK, false);
            line(g, at[0], at[1] + 22, bodyWidth);
        }));
    }

    private static String shopSummary(Product product) {
        var shops = product.shops();
        if (shops.isEmpty()) return "";
        var sell = shops.stream().map(s -> s.reading().sellPrice()).filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue).toArray();
        var buy = shops.stream().map(s -> s.reading().buyPrice()).filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue).toArray();
        int sellQuantity = shops.stream().filter(s -> s.reading().mode() != ShopSigns.Mode.BUY).mapToInt(s -> s.reading().quantity()).sum();
        int buyQuantity = shops.stream().filter(s -> s.reading().mode() != ShopSigns.Mode.SELL).mapToInt(s -> s.reading().quantity()).sum();
        StringBuilder text = new StringBuilder("箱子商店 " + shops.size() + " 个");
        if (sell.length > 0) text.append(" · 出售合计×").append(sellQuantity).append(" ").append(range(sell));
        if (buy.length > 0) text.append(" · 收购合计×").append(buyQuantity).append(" ").append(range(buy));
        String latest = shops.stream().map(Shop::observedAt).max(String::compareTo).orElse("");
        return text + (latest.isBlank() ? "" : " · 更新 " + ago(latest));
    }
    private static String range(double[] values) {
        double min = java.util.Arrays.stream(values).min().orElse(0), max = java.util.Arrays.stream(values).max().orElse(0);
        return min == max ? num(min) : num(min) + "~" + num(max);
    }
    private static String num(double value) { return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value); }
    private static String ago(String iso) {
        try {
            long minutes = java.time.Duration.between(java.time.Instant.parse(iso), java.time.Instant.now()).toMinutes();
            return minutes < 1 ? "刚刚" : minutes < 60 ? minutes + "分钟前" : minutes < 2880 ? minutes / 60 + "小时前" : minutes / 1440 + "天前";
        } catch (RuntimeException e) { return iso.substring(0, Math.min(10, iso.length())); }
    }
    /** Group band colour (header) and the lighter tint of the product cards under it. */
    private static final int[][] BAND_COLORS = { { 0xFFDDE8DC, 0xFFF4F8F2 }, { 0xFFE8E0D2, 0xFFFAF6EE }, { 0xFFD9E3EA, 0xFFF2F6F9 }, { 0xFFE6DAE6, 0xFFF8F2F8 } };
    private final java.util.Set<String> collapsed = new java.util.HashSet<>();
    /** One category in the owner's tree (up to three levels): its direct products, its sub-categories and how many products it holds in all. */
    private static final class CategoryNode {
        final String name; final java.util.Map<String, CategoryNode> children = new java.util.LinkedHashMap<>(); final List<Integer> products = new ArrayList<>(); int total;
        CategoryNode(String name) { this.name = name; }
        CategoryNode child(String key) { return children.computeIfAbsent(key, CategoryNode::new); }
    }
    private static int lighter(int color, double amount) {
        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        r += (int) ((255 - r) * amount); g += (int) ((255 - g) * amount); b += (int) ((255 - b) * amount);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
    /** A band for the category (sub-categories nest under it, indented and lighter), then its products. */
    private void categoryNode(CategoryNode node, int level, int[] colors, String key) {
        boolean open = !collapsed.contains(key);
        groupHeader(node.name, node.total, open, lighter(colors[0], level * 0.4), level, key);
        if (!open) return;
        for (int index : node.products) product(ad.products().get(index), index, level == 0 ? colors[1] : lighter(colors[1], 0.5), false, level * 14);
        for (var child : node.children.values()) categoryNode(child, level + 1, colors, key + " / " + child.name);
    }
    private void groupHeader(String name, int count, boolean open, int color, int level, String key) {
        int indent = level * 14;
        blocks.add(new Block(26, (g, at) -> {
            int x = at[0] + indent, w = bodyWidth - indent;
            boolean hot = hovering(x, at[1] + 2, w, 22);
            g.fill(x, at[1] + 2, x + w, at[1] + 24, hot ? lighter(color, .35) : color);
            g.renderOutline(x, at[1] + 2, w, 22, hot ? ACCENT : LINE);
            g.drawString(font, (open ? "▼ " : "▶ ") + name, x + 8, at[1] + 9, hot ? ACCENT : INK, false);
            String size = count + " 件" + (open ? "" : " · 点击展开");
            g.drawString(font, size, x + w - 8 - font.width(size), at[1] + 9, MUTED, false);
            if (at[1] + 24 > viewTop && at[1] + 2 < viewTop + viewHeight) groupHits.add(new Object[]{ x, at[1] + 2, w, 22, key });
        }));
    }
    /** The mouse (window coordinates, set on every frame) is over this area and inside the scrolling view. */
    private int hoverX = -1, hoverY = -1;
    private boolean hovering(int x, int y, int w, int h) {
        return hoverX >= x && hoverX < x + w && hoverY >= y && hoverY < y + h && hoverY >= viewTop && hoverY < viewTop + viewHeight;
    }
    private final List<Object[]> groupHits = new ArrayList<>();

    private void product(Product product, int index, int tint, boolean showCategories, int indent) {
        List<FormattedCharSequence> description = product.description().isBlank() ? List.of() : font.split(Component.literal(product.description()), bodyWidth - indent - 56);
        int shown = Math.min(description.size(), 4);
        String shopLine = shopSummary(product);
        List<FormattedCharSequence> shopRows = shopLine.isEmpty() ? List.of() : font.split(Component.literal(shopLine), bodyWidth - indent - 56);
        String categories = !showCategories || product.categories().isEmpty() ? "" : "分类：" + String.join(" / ", product.categories());
        int extra = categories.isEmpty() ? 0 : 12;
        int height = Math.max(44, 36 + extra + shown * 12 + shopRows.size() * 12 + 6);
        blocks.add(new Block(height, (g, at) -> {
            int x = at[0] + indent, y = at[1], bw = bodyWidth - indent;
            g.fill(x, y + 2, x + bw, y + height - 2, tint);
            asset(g, "paper-frame", x, y + 1, bw, height - 2, 512, 112);
            // Linked item model first (rotating when the loaded pack has it), then item/product art, then initial.
            int icon = 32, iconX = x + 8, iconY = y + 7;
            float angle = .6f + (float) ((System.nanoTime() - openedAt) / 1_000_000 % 8000) / 8000f * (float) Math.PI * 2;
            var hint = product.nativeAppearance();
            boolean drawn = hint != null && (product.itemHasModel() ? ArchiveModels.INSTANCE.drawModel(g, hint, iconX, iconY, icon, angle) : ArchiveModels.INSTANCE.draw(g, hint, iconX, iconY, icon))
                    || !product.vanillaItem().isEmpty() && ArchiveModels.INSTANCE.drawVanilla(g, product.vanillaItem(), iconX, iconY, icon)
                    || MarketImages.INSTANCE.draw(g, api, product.imagePath(), iconX, iconY, icon);
            if (!drawn) {
                g.fill(iconX, iconY, iconX + icon, iconY + icon, MarketTheme.c(0xFFE8EBE2));
                String initial = product.name().isBlank() ? "品" : new String(Character.toChars(product.name().codePointAt(0)));
                text(g, initial, iconX + icon / 2 - 4, iconY + icon / 2 - 4, 12, MUTED);
            }
            int textX = x + 50, statusWidth = 40;
            int nameWidth = bw - 50 - statusWidth - 12;
            text(g, product.itemId() > 0 ? product.name() + " →" : product.name(), textX, y + 8, nameWidth, product.itemId() > 0 ? ACCENT : INK);
            if (product.itemId() > 0 && y + 19 > viewTop && y + 6 < viewTop + viewHeight) {
                linkHits.add(new int[]{ textX, y + 6, nameWidth, 13, 1, product.itemId() });
                int linkWidth = Math.min(nameWidth, font.width(product.name() + " →") + 2);
                if (hovering(textX, y + 6, linkWidth, 13)) hoverMark(g, textX, y + 6, linkWidth, 11);
            }
            g.renderOutline(x + bw - statusWidth - 8, y + 6, statusWidth, 13, LINE);
            text(g, stateLabel(product.state()), x + bw - statusWidth - 5, y + 8, statusWidth - 4, MUTED);
            String price = product.price().isBlank() ? "价格未填写" : product.price();
            text(g, price, textX, y + 22, bw - 58, ACCENT);
            // The website's labels after the price: 已认证 / 原版 / 未关联档案, then the item kind (地图画, 2D 酒 …).
            int tagX = textX + Math.min(font.width(price), bw / 3) + 8;
            for (String tag : product.tags()) {
                int w = font.width(tag) + 6;
                if (tagX + w > x + bw - 120) break;
                g.renderOutline(tagX, y + 20, w, 12, LINE); g.drawString(font, tag, tagX + 3, y + 22, tag.equals("已认证") ? ACCENT : MUTED, false);
                tagX += w + 3;
            }
            if (!categories.isEmpty()) text(g, categories, textX, y + 36, bw - 58 - 120, MUTED);
            for (int i = 0; i < shown; i++) g.drawString(font, description.get(i), textX, y + 36 + extra + i * 12, MUTED, false);
            for (int i = 0; i < shopRows.size(); i++) g.drawString(font, shopRows.get(i), textX, y + 36 + extra + shown * 12 + i * 12, ACCENT, false);
            var shopTargets = NavTargets.ofProduct(ad, product);
            if (!shopTargets.isEmpty()) {
                int navWidth = font.width("导航") + 24, nx = x + bw - navWidth - 8, ny = y + height - 24;
                if (hovering(nx, ny, navWidth, 15)) g.fill(nx, ny, nx + navWidth, ny + 15, MarketTheme.c(0xFFE6EBE2));
                g.renderOutline(nx, ny, navWidth, 15, hovering(nx, ny, navWidth, 15) ? ACCENT : LINE); compass(g, nx + 4, ny + 2, 11); g.drawString(font, "导航", nx + 18, ny + 4, INK, false);
                if (ny + 15 > viewTop && ny < viewTop + viewHeight) navHits.add(new int[]{ nx, ny, navWidth, 15, index });
            }
            if (PaleTaleClient.ACCOUNT.loggedIn()) {
                String label = "绑定箱子商店"; int labelWidth = font.width(label) + 8, lx = x + bw - labelWidth - 8, ly = y + 20;
                if (hovering(lx, ly, labelWidth, 13)) g.fill(lx, ly, lx + labelWidth, ly + 13, MarketTheme.c(0xFFE6EBE2));
                g.renderOutline(lx, ly, labelWidth, 13, hovering(lx, ly, labelWidth, 13) ? ACCENT : LINE); g.drawString(font, label, lx + 4, ly + 3, INK, false);
                if (ly + 13 > viewTop && ly < viewTop + viewHeight) bindHits.add(new int[]{ lx, ly, labelWidth, 13, index });
            }
        }));
    }

    private void scrollBy(int amount) {
        scroll = Math.max(0, Math.min(Math.max(0, contentHeight - viewHeight), scroll + amount));
    }

    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) for (Object[] hit : groupHits) {
            double mx = (event.x() - winX) / windowScale, my = (event.y() - winY) / windowScale;
            if (mx >= (int) hit[0] && mx < (int) hit[0] + (int) hit[2] && my >= (int) hit[1] && my < (int) hit[1] + (int) hit[3]) {
                String name = (String) hit[4];
                if (!collapsed.remove(name)) collapsed.add(name);
                build(); scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - viewHeight))); return true;
            }
        }
        if (event.button() == 0) for (int[] hit : linkHits)
            if (((event.x() - winX)/windowScale) >= hit[0] && ((event.x() - winX)/windowScale) < hit[0] + hit[2] && ((event.y() - winY)/windowScale) >= hit[1] && ((event.y() - winY)/windowScale) < hit[1] + hit[3]) {
                minecraft.setScreen(new ArchiveDetailScreen(this, api, hit[4] == 1 ? ArchiveData.Kind.ITEMS : ArchiveData.Kind.PLAYERS, hit[5])); return true;
            }
        if (event.button() == 0) for (int[] hit : navHits)
            if (((event.x() - winX)/windowScale) >= hit[0] && ((event.x() - winX)/windowScale) < hit[0] + hit[2] && ((event.y() - winY)/windowScale) >= hit[1] && ((event.y() - winY)/windowScale) < hit[1] + hit[3]) {
                var shops = NavTargets.ofProduct(ad, ad.products().get(hit[4]));
                if (shops.size() == 1) { var t = shops.get(0); Navigation.start(new Navigation.Target(t.name(), t.x(), t.y(), t.z(), t.kind(), t.marks())); rebuildWidgets(); }
                else minecraft.setScreen(new NavigateScreen(this, ad.products().get(hit[4]).name(), shops));
                return true;
            }
        if (event.button() == 0) for (int[] hit : bindHits)
            if (((event.x() - winX)/windowScale) >= hit[0] && ((event.x() - winX)/windowScale) < hit[0] + hit[2] && ((event.y() - winY)/windowScale) >= hit[1] && ((event.y() - winY)/windowScale) < hit[1] + hit[3]) {
                if (!reloading && reloadError.isEmpty()) minecraft.setScreen(new ShopBindScreen(this, list, ad, hit[4])); return true;
            }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        scrollBy(vertical > 0 ? -28 : vertical < 0 ? 28 : 0);
        return true;
    }

    @Override protected void renderWindow(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderWindow(g, mouseX, mouseY, delta);
        hoverX = mouseX; hoverY = mouseY;
        if (navShown) {
            text(g, navTitle, left + 17, navY + 5, side - 21, INK);
            text(g, navDetail, left + 17, navY + 17, side - 21, MUTED);
        }
        // Left identity column.
        int coverWidth = Math.min(side - 16, 102), icon = Math.round(coverWidth * 10f / 16);
        int iconX = left + (side - coverWidth) / 2;
        if (!MarketImages.INSTANCE.drawFill(g, api, ad.coverPath(), iconX, 52, coverWidth, icon)) {
            g.fill(iconX, 52, iconX + coverWidth, 52 + icon, MarketTheme.c(0xFFE8EBE2));
            String initial = ad.title().isBlank() ? "广" : new String(Character.toChars(ad.title().codePointAt(0)));
            text(g, initial, iconX + coverWidth / 2 - 4, 52 + icon / 2 - 4, 12, MUTED);
        }
        int y = 60 + icon;
        boolean avatar = MarketImages.INSTANCE.draw(g, api, ad.author().avatarPath(), left + 4, y - 1, 12);
        linkHits.clear();
        boolean authorLink = ad.author().playerId() > 0;
        text(g, ad.author().name(), left + 4 + (avatar ? 16 : 0), y + 1, side - 8 - (avatar ? 16 : 0), authorLink ? ACCENT : INK);
        if (authorLink) {
            linkHits.add(new int[]{ left + 4, y - 1, side - 8, 13, 2, ad.author().playerId() });
            int shown = Math.min(side - 8, 16 + font.width(ad.author().name()));
            if (over(mouseX, mouseY, left + 4, y - 1, shown, 13)) hoverMark(g, left + 4, y - 1, shown, 12);
        }
        if (!ad.author().mcName().equals(ad.author().name())) { text(g, ad.author().mcName(), left + 4, y + 15, side - 8, MUTED); y += 14; }
        g.renderOutline(left + 4, y + 20, 44, 14, LINE);
        text(g, stateLabel(ad.state()), left + 8, y + 23, 38, MUTED);
        text(g, "编号 " + ad.code(), left + 4, y + 42, side - 8, MUTED);
        text(g, "更新 " + (ad.updatedAt().isBlank() ? "未记录" : ad.updatedAt().substring(0, Math.min(10, ad.updatedAt().length()))), left + 4, y + 55, side - 8, MUTED);
        // Under the identity: the ad's category icons (buttons, placed in init), the short summary and how to reach the author.
        int infoY = y + 70, addressBottom = height - 84;
        String contact = !ad.contactQq().isEmpty() ? "QQ：" + ad.contactQq() : ad.contactNote().isEmpty() ? "联系方式：未公开" : "";
        if (!contact.isEmpty() && infoY + 10 <= addressBottom) { text(g, contact, left + 4, infoY, side - 8, ad.contactQq().isEmpty() ? MUTED : ACCENT); infoY += 12; }
        if (!ad.contactNote().isEmpty()) for (var row : font.split(Component.literal(ad.contactNote()), side - 8).stream().limit(2).toList()) {
            if (infoY + 10 > addressBottom) break;
            g.drawString(font, row, left + 4, infoY, MUTED, false); infoY += 11;
        }
        infoY += 3;
        if (!ad.summary().isBlank()) for (var row : font.split(Component.literal(ad.summary()), side - 8).stream().limit(3).toList()) {
            if (infoY + 10 > addressBottom) break;
            g.drawString(font, row, left + 4, infoY, INK, false); infoY += 11;
        }
        // Addresses also sit in the left column, above the buttons, so they stay in view while the body scrolls.
        int addressY = infoY + 4;
        for (Location location : ad.locations()) {
            String line = location.name() + "：" + (location.coordinates().isBlank() ? "坐标未填写" : location.coordinates());
            for (var row : font.split(Component.literal(line), side - 8)) {
                if (addressY + 10 > addressBottom) break;
                g.drawString(font, row, left + 4, addressY, INK, false); addressY += 11;
            }
            if (!location.directions().isBlank()) for (var row : font.split(Component.literal(location.directions()), side - 8)) {
                if (addressY + 10 > addressBottom) break;
                g.drawString(font, row, left + 4, addressY, MUTED, false); addressY += 11;
            }
            addressY += 3;
        }
        String views = ad.views() >= 0 ? "浏览 " + ad.views() : "";
        int viewsWidth = views.isEmpty() ? 0 : font.width(views);
        if (viewsWidth > 0) g.drawString(font, views, left + panelWidth - 84 - 10 - viewsWidth, 18, MUTED, false);
        String title = font.plainSubstrByWidth(ad.title(), panelWidth - 80 - 96 - viewsWidth);
        g.drawString(font, title, left + 74, 17, INK, false);
        bindHits.clear(); navHits.clear(); groupHits.clear();
        g.enableScissor(bodyX, viewTop, bodyX + bodyWidth, viewTop + viewHeight);
        int top = viewTop - scroll;
        for (Block block : blocks) {
            if (top + block.height() > viewTop - 4 && top < viewTop + viewHeight) block.draw().accept(g, new int[]{ bodyX, top });
            top += block.height();
        }
        g.disableScissor();
        if (contentHeight > viewHeight) {
            int thumb = Math.max(16, viewHeight * viewHeight / contentHeight);
            int thumbY = viewTop + (viewHeight - thumb) * scroll / Math.max(1, contentHeight - viewHeight);
            g.fill(bodyX + bodyWidth + 3, viewTop, bodyX + bodyWidth + 5, viewTop + viewHeight, LINE);
            g.fill(bodyX + bodyWidth + 3, thumbY, bodyX + bodyWidth + 5, thumbY + thumb, MUTED);
        }
        text(g, reloading ? "正在刷新箱子商店…" : !reloadError.isEmpty() ? reloadError : "滚轮翻阅 · ESC 返回", bodyX + (reloadError.isEmpty() ? 0 : 82), height - 22, bodyWidth - (reloadError.isEmpty() ? 0 : 82), MUTED);
    }

    @Override protected void windowBackground(GuiGraphics g, int x, int y, float delta) {
        g.fill(0, 0, width, height, PAPER);
        g.fill(left, 40, left + side, height - 62, MarketTheme.c(0xFFF0F1EA));
        line(g, left, 38, panelWidth); g.vLine(bodyX - 8, 44, height - 36, LINE);
    }

    @Override public void onClose() { minecraft.setScreen(parent); if (bindingsChanged && list != null) list.refresh(); }
    @Override public boolean isPauseScreen() { return false; }
}
