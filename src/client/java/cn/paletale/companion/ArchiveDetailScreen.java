package cn.paletale.companion;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static cn.paletale.companion.ArchiveData.*;
import static cn.paletale.companion.MarketTheme.*;

public final class ArchiveDetailScreen extends WindowScreen {
    private final Screen parent;
    private final MarketApi media;
    private final ArchiveApi api;
    private final Kind kind;
    private final int id;
    private Detail detail;
    private String error = "";
    private boolean loading = true, started;
    private final long openedAt = System.nanoTime();
    private int requestId, left, panelWidth, side, textX, textWidth, offset, visible;
    private final List<FormattedCharSequence> lines = new ArrayList<>();
    private final List<Entry> lineIcons = new ArrayList<>();
    private final java.util.Map<Integer, List<String>> fishIcons = new java.util.HashMap<>();
    private final java.util.Set<Integer> fishNav = new java.util.HashSet<>();
    private final List<String> lineAvatars = new ArrayList<>();
    private final List<Runnable> lineActions = new ArrayList<>();
    /** Rows of the brand text sit on a dark panel (its light colours would vanish on the pale page). */
    private final List<Boolean> lineDark = new ArrayList<>();
    private boolean qqCopied;
    /** The organization's members are laid out three to a row (two text lines tall) from this line on, like the website's grid. */
    private List<Member> gridMembers = List.of();
    private int gridStart = -1;
    private static final int GRID_COLUMNS = 3;
    private String notice = "";
    /** Wine/dish content is temporarily locked; the page shows only the lock notice. */
    private boolean locked;
    private CompletableFuture<Detail> pending;
    private FavoriteApi.State favorite;
    private int effectRow = -1;
    private String effectKey = "unknown";
    /** Favourites and views go to the same site this page reads from. */
    private final FavoriteApi favorites;
    private boolean favoriteBusy, viewed;

    public ArchiveDetailScreen(Screen parent, MarketApi media, Kind kind, int id) {
        super(Component.literal(kind.label)); this.parent = parent; this.media = media; this.favorites = PaleTaleClient.favoritesFor(media); this.api = new ArchiveApi(media); this.kind = kind; this.id = id;
    }
    @Override protected void init() {
        fitWindow();
        panelWidth = Math.min(920, width - 20); left = (width - panelWidth) / 2;
        side = Math.min(190, Math.max(106, panelWidth / 3)); textX = left + side + 18; textWidth = panelWidth - side - 18;
        visible = Math.max(1, (height - 121) / 12);
        addRenderableWidget(new Action("返回列表", left, 13, 60, 19, false, null, b -> onClose()));
        if (!started) { started = true; fetch(); return; }
        if (loading) return;
        if (!error.isEmpty()) { addRenderableWidget(new Action("重试", left + 70, 13, 46, 19, false, null, b -> fetch())); return; }
        if (detail == null) return;
        Entry entry = detail.entry();
        if (kind == Kind.ITEMS && ArchiveLocks.locked(entry)) { locked = true; return; }
        if(kind==Kind.ITEMS||kind==Kind.PLAYERS&&entry.name().matches("[A-Za-z0-9_]{1,16}")){var world=addRenderableWidget(new Action("SMC世界",left+panelWidth-120-(entry.views()>=0?font.width("浏览 "+entry.views()):0),13,20,19,false,"compass",b->minecraft.setScreen(kind==Kind.ITEMS?WorldScreen.searchItem(this,media,entry.name()):new WorldEntityScreen(this,media,"player",entry.name()))));world.setDisplayLabel("");world.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(kind==Kind.ITEMS?"在SMC世界搜索此物品":"查看玩家SMC世界信息")));}

        if (kind != Kind.ORGS && !fish()) {
            String label = favorite == null ? "☆ 收藏" : (favorite.favorited() ? "★ 已收藏 " : "☆ 收藏 ") + favorite.favorites();
            Button star = addRenderableWidget(new Action(label, left + panelWidth - 84, 13, 84, 19, favorite != null && favorite.favorited(), null, b -> toggleFavorite()));
            star.active = !favoriteBusy;
            star.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(PaleTaleClient.ACCOUNT.loggedIn() ? "收藏后可在列表「我的收藏」中筛选" : "登录 PaleTale 后可收藏")));
        }
        // 鱼类档案在官网保持锁定，没有可打开的网页。
        if (!fish()) addRenderableWidget(new Action(entry.hasModel() ? "网站3D档案" : "网站完整档案", left, height - 32, side, 18, true, null, ConfirmLinkScreen.confirmLink(this, api.website(entry))));
        if (kind == Kind.ITEMS && entry.owner() != null && entry.owner().playerId() != null && entry.owner().verified()) {
            addRenderableWidget(new Action("查看拥有者", left, height - 56, side, 18, false, "person", b ->
                    minecraft.setScreen(new ArchiveDetailScreen(this, media, Kind.PLAYERS, entry.owner().playerId()))));
        }
        if (kind != Kind.ITEMS && !detail.qq().isEmpty()) {
            String what = kind == Kind.ORGS ? "群号" : " QQ";
            addRenderableWidget(new Action((qqCopied ? "已复制" : "复制") + what, left, height - (kind == Kind.ORGS ? 56 : 104), side, 18, false, null, b -> {
                minecraft.keyboardHandler.setClipboard(detail.qq()); qqCopied = true; rebuildWidgets();
            }));
        }
        if (kind == Kind.ORGS && !detail.joinUrl().isEmpty()) {
            addRenderableWidget(new Action("加入QQ群", left, height - 80, side, 18, true, null,
                    net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLink(this, java.net.URI.create(detail.joinUrl()))));
        }
        if (kind == Kind.PLAYERS) {
            addRenderableWidget(new Action("查看拥有物品", left, height - 80, side, 18, false, null, b -> minecraft.setScreen(ArchiveNavigation.owned(this, media, id))));
            addRenderableWidget(new Action("查看玩家广告", left, height - 56, side, 18, false, null, b -> minecraft.setScreen(ArchiveNavigation.ads(this, media, id))));
        }
        gridMembers = List.of(); gridStart = -1;
        lines.clear(); fishIcons.clear(); fishNav.clear(); lineIcons.clear(); lineAvatars.clear(); lineActions.clear(); lineDark.clear();
        append(fish() && !detail.fishCode().isEmpty() ? "鱼类 · 编号 " + detail.fishCode() : entry.badge() + " · " + (kind == Kind.PLAYERS ? "玩家编号 " : kind == Kind.ORGS ? "组织编号 " : "物品编号 ") + id);
        if (kind != Kind.ITEMS) append(entry.subtitle());
        if (kind == Kind.ITEMS && !fish() && !entry.facts().isBlank()) append(entry.category().equals("map_art") ? MapSizeColor.styled(entry.facts(), INK) : Component.literal(entry.facts()), null, null, null);
        effectRow = -1;
        if (kind == Kind.ITEMS && !entry.effectText().isEmpty()) {
            // Four leading spaces leave room for the effect icon drawn in render().
            effectRow = lines.size(); effectKey = entry.effect().isEmpty() ? "unknown" : entry.effect();
            append("    " + entry.effectText());
            appendBrand(entry.brand());
        }
        if (kind == Kind.PLAYERS) {
            var online = OnlinePlayers.uuids(minecraft);
            String uuid = OnlinePlayers.uuidOf(entry.imagePath());
            append(online == null || uuid == null ? "在线状态：进入服务器后可查看" : online.contains(uuid) ? "在线状态：● 当前在线" : "在线状态：○ 不在线");
        }
        if (kind == Kind.PLAYERS) append(!detail.qq().isEmpty() ? "QQ：" + detail.qq() : detail.qqHidden() ? "QQ：登录后可见" : "QQ：暂未公开");
        if (kind == Kind.ORGS) append(detail.qq().isEmpty() ? "QQ群：暂未公开" : "QQ群：" + detail.qq());
        if (entry.owner() != null) {
            var owner = entry.owner();
            append("拥有者：" + owner.name() + (owner.verified() ? " · 已认证玩家" : " · 暂未认证来源"), null, owner.avatarPath(),
                    owner.verified() && owner.playerId() != null ? () -> minecraft.setScreen(new ArchiveDetailScreen(this, media, Kind.PLAYERS, owner.playerId())) : null);
        }
        append(entry.description().isBlank() ? "暂未填写简介。" : entry.description());
        if (fish()) {
            append("渔获资料：");
            for (var row : detail.fishLines()) appendFish(row.text(), row.icons(), row.command());
        }
        if (kind == Kind.ORGS) {
            append("成员 · " + detail.members().size() + " 人");
            gridMembers = detail.members(); gridStart = lines.size();
            for (int row = 0; row < (gridMembers.size() + GRID_COLUMNS - 1) / GRID_COLUMNS * 2; row++) {
                lines.add(FormattedCharSequence.EMPTY); lineActions.add(null); lineIcons.add(null); lineAvatars.add(null); lineDark.add(false);
            }
            lines.add(FormattedCharSequence.EMPTY); lineActions.add(null); lineIcons.add(null); lineAvatars.add(null); lineDark.add(false);
        }
        if (kind == Kind.ITEMS && !fish()) {
            append("关联广告 · " + detail.adsTotal() + " 条");
            for (Ad ad : detail.ads()) append("• " + ad.title() + " · " + MarketData.stateLabel(ad.state()), null, null, () -> openAd(ad.code()));
        }
        if (kind == Kind.PLAYERS) {
            append("公开拥有物品 · " + detail.ownedTotal() + " 件");
            boolean hidden = false;
            for (Entry item : detail.ownedItems()) {
                if (ArchiveLocks.locked(item)) { hidden = true; continue; }
                append(item.name() + " · " + item.badge(), item, null, () -> minecraft.setScreen(new ArchiveDetailScreen(this, media, Kind.ITEMS, item.id())));
            }
            if (hidden) append("酒、菜档案整改中，暂不展示。");
            if (detail.ownedTotal() > detail.ownedItems().size()) append("点击左侧「查看拥有物品」浏览全部公开藏品。");
            append("公开广告 · " + detail.adsTotal() + " 条");
            for (Ad ad : detail.ads()) append("• " + ad.title() + " · " + MarketData.stateLabel(ad.state()), null, null, () -> openAd(ad.code()));
        }
        offset = Math.min(offset, Math.max(0, lines.size() - visible));
        if (effectRow >= offset && effectRow < offset + visible) {
            String label = "提交效果";
            addRenderableWidget(new Action(label, textX + font.width(lines.get(effectRow)) + 10, 49 + (effectRow - offset) * 12, font.width(label) + 12, 16, false, null, ConfirmLinkScreen.confirmLink(this, effectSubmitUri())))
                    .setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("在网站提交你认为这款酒的效果；管理员审核通过后才会生效")));
        }
        // Long pages scroll with the mouse wheel; there are no page buttons.
    }
    /** Opens the ad directly on top of this page, so Esc comes back here. */
    private void openAd(String code) {
        notice = "正在打开广告…";
        media.search(code, java.util.EnumSet.noneOf(MarketData.Category.class), MarketData.AdState.ALL, 1).whenComplete((page, failure) -> minecraft.execute(() -> {
            if (minecraft.screen != this) return;
            var found = failure == null ? page.ads().stream().filter(ad -> ad.code().equals(code)).findFirst() : java.util.Optional.<MarketData.Ad>empty();
            if (found.isPresent()) { notice = ""; minecraft.setScreen(new MarketDetailScreen(this, null, media, found.get())); }
            else notice = "暂时无法打开这则广告，请稍后重试。";
        }));
    }
    private void fetch() {
        int run = ++requestId; if (pending != null) pending.cancel(true);
        loading = true; error = ""; detail = null; locked = false; offset = 0; rebuildWidgets();
        pending = api.detail(kind, id, PaleTaleClient.ACCOUNT.token());
        pending.whenComplete((data, failure) -> minecraft.execute(() -> {
            if (run != requestId || minecraft.screen != this) return;
            loading = false;
            if (failure == null) {
                detail = data; loadFavorite();
                // Opening the page counts as a view (orgs have no counter).
                if (kind != Kind.ORGS && !fish() && !viewed) { viewed = true; favorites.view(PaleTaleClient.ACCOUNT.token(), favoriteType(), id); }
            }
            else { Throwable cause = failure.getCause(); error = cause instanceof IllegalStateException ? cause.getMessage() : "档案暂时无法读取，请重试。"; }
            rebuildWidgets();
        }));
    }
    /** The wine's own page on the website, with the effect submission panel already open. */
    private java.net.URI effectSubmitUri() {
        String site = api.website(detail.entry()).toString();
        return java.net.URI.create(site + (site.contains("?") ? "&" : "?") + "submit=effect");
    }
    private String favoriteType() { return kind == Kind.ITEMS ? "item" : "player"; }
    private boolean fish() { return kind == Kind.ITEMS && detail != null && detail.entry().category().equals("fish"); }
    private void loadFavorite() {
        String token = PaleTaleClient.ACCOUNT.token();
        if (kind == Kind.ORGS || token == null || fish()) return;
        favorites.state(token, favoriteType(), id).whenComplete((state, failure) -> minecraft.execute(() -> {
            if (minecraft.screen != this || failure != null) return;
            favorite = state; rebuildWidgets();
        }));
    }
    private void toggleFavorite() {
        String token = PaleTaleClient.ACCOUNT.token();
        if (token == null) { notice = "请先在列表页右上角登录 PaleTale 账号。"; PaleTaleClient.ACCOUNT.start(minecraft); return; }
        favoriteBusy = true; notice = ""; rebuildWidgets();
        boolean on = favorite == null || !favorite.favorited();
        var requestSite = media.site(); long requestAccount = FavoritePresenceWatch.currentAccountId();
        favorites.set(token, favoriteType(), id, on).whenComplete((state, failure) -> minecraft.execute(() -> {
            if (failure == null && kind == Kind.PLAYERS) { DiskPages.clear(DiskPages.owner(requestSite)); WorldEntityScreen.clearFavoriteCache(); PaleTaleClient.FAVORITE_PRESENCE.invalidate(token, requestSite, requestAccount); }
            favoriteBusy = false;
            if (minecraft.screen != this) return;
            if (failure == null) favorite = state;
            else notice = failure.getCause() instanceof IllegalStateException cause ? cause.getMessage() : "收藏暂时失败，请稍后重试。";
            rebuildWidgets();
        }));
    }
    /** A fishing fact row: the website card's vanilla item icons, then the text. */
    private void appendFish(String text, List<String> icons, String command) {
        int shift = icons.isEmpty() ? 0 : icons.size() * 14 + 4;
        boolean nav = !command.isEmpty();
        // The navigation button sits right after the text, so the text leaves room for it.
        var rows = font.split(Component.literal(text), textWidth - 6 - shift - (nav ? 46 : 0));
        Runnable go = nav ? () -> { var player = minecraft.player; if (player != null) { minecraft.setScreen(null); player.connection.sendCommand(command.substring(1)); } } : null;
        for (int i = 0; i < rows.size(); i++) {
            if (i == 0 && !icons.isEmpty()) fishIcons.put(lines.size(), icons);
            if (i == 0 && nav) fishNav.add(lines.size());
            lines.add(rows.get(i)); lineActions.add(go); lineIcons.add(null); lineAvatars.add(null); lineDark.add(false);
        }
        lines.add(FormattedCharSequence.EMPTY); lineActions.add(null); lineIcons.add(null); lineAvatars.add(null); lineDark.add(false);
    }
    private void append(String text) { append(text, null, null, null); }
    private void append(String text, Entry icon) { append(text, icon, null, null); }
    /** A row keeps its thumbnail (item) or avatar (player) beside the first wrapped line. */
    private void append(String text, Entry icon, String avatar, Runnable action) { append(Component.literal(text), icon, avatar, action); }
    private void append(Component text, Entry icon, String avatar, Runnable action) {
        boolean marked = icon != null || avatar != null && !avatar.isBlank();
        var rows = font.split(text, textWidth - 6 - (marked ? 18 : 0));
        for (int i = 0; i < rows.size(); i++) { lines.add(rows.get(i)); lineActions.add(action); lineIcons.add(i == 0 ? icon : null); lineAvatars.add(i == 0 && marked && icon == null ? avatar : null); lineDark.add(false); }
        lines.add(FormattedCharSequence.EMPTY); lineActions.add(null); lineIcons.add(null); lineAvatars.add(null); lineDark.add(false);
    }
    /** The brand text: each line wrapped and coloured like on the website, on a dark panel. */
    private void appendBrand(String raw) {
        var brand = BrandText.lines(raw);
        if (brand.isEmpty()) return;
        for (var line : brand) for (var row : font.split(line, textWidth - 20)) { lines.add(row); lineActions.add(null); lineIcons.add(null); lineAvatars.add(null); lineDark.add(true); }
        lines.add(FormattedCharSequence.EMPTY); lineActions.add(null); lineIcons.add(null); lineAvatars.add(null); lineDark.add(false);
    }
    private void scroll(int amount) { offset = Math.max(0, Math.min(Math.max(0, lines.size() - visible), offset + amount)); rebuildWidgets(); }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) { scroll(vertical > 0 ? -3 : vertical < 0 ? 3 : 0); return true; }
    @Override protected void windowBackground(GuiGraphics g, int x, int y, float delta) {
        g.fill(0, 0, width, height, PAPER); line(g, left, 40, panelWidth); g.vLine(textX - 10, 50, height - 42, LINE);
    }
    @Override protected void renderWindow(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderWindow(g, mouseX, mouseY, delta);
        boolean titleIcon = detail != null && (ArchiveModels.INSTANCE.draw(g, detail.entry(), left + 70, 17, 14) || MarketImages.INSTANCE.draw(g, media, detail.entry().imagePath(), left + 70, 17, 14));
        // Top right: the view counter sits beside the favourite button.
        String views = detail != null && detail.entry().views() >= 0 ? "浏览 " + detail.entry().views() : "";
        int viewsWidth = views.isEmpty() ? 0 : font.width(views);
        if (viewsWidth > 0) g.drawString(font, views, left + panelWidth - 84 - 10 - viewsWidth, 19, MUTED, false);
        text(g, detail == null ? kind.label : detail.entry().name(), left + 70 + (titleIcon ? 19 : 0), 19, panelWidth - 70 - (titleIcon ? 19 : 0) - (kind == Kind.ORGS ? 0 : 122 + viewsWidth), INK);
        if (loading || !error.isEmpty()) { text(g, loading ? "正在读取公开档案…" : error, textX, 60, textWidth - 6, loading ? MUTED : MarketTheme.c(0xFF9E4942)); return; }
        if (detail == null) return;
        if (locked) {
            text(g, ArchiveLocks.TITLE, textX, 64, textWidth - 6, MarketTheme.c(0xFF9E4942));
            text(g, ArchiveLocks.MESSAGE, textX, 80, textWidth - 6, MUTED);
            return;
        }
        Entry entry = detail.entry();
        int size = Math.min(kind != Kind.ITEMS ? 80 : 128, Math.min(side - 12, height - (kind == Kind.PLAYERS ? (detail.qq().isEmpty() ? 161 : 185) : kind == Kind.ORGS ? 161 : 157)));
        size = Math.max(32, size);
        int imageX = left + (side - size) / 2;
        boolean nativeModel = entry.hasModel() && ArchiveModels.INSTANCE.drawModel(g, entry, imageX, 56, size, .6f + (float) ((System.nanoTime() - openedAt) / 1_000_000 % 20000) / 20000 * (float) Math.PI * 2);
        if (!nativeModel && !ArchiveModels.INSTANCE.draw(g, entry, imageX, 56, size) && !MarketImages.INSTANCE.draw(g, media, entry.imagePath(), imageX, 56, size)) {
            g.fill(imageX, 56, imageX + size, 56 + size, MarketTheme.c(0xFFF0F1EA));
            String initial = entry.name().isBlank() ? "档" : new String(Character.toChars(entry.name().codePointAt(0)));
            text(g, initial, imageX + size / 2 - 4, 56 + size / 2 - 4, 12, MUTED);
        }
        int infoY = 63 + size;
        if (entry.hasModel() && !nativeModel) {
            text(g, "完整3D可在网站打开", left + 6, infoY, side - 12, MUTED); infoY += 13;
            var hint = entry.nativeAppearance();
            if (hint != null && !hint.itemModel().isBlank()) { text(g, "资源包未匹配：" + hint.itemModel(), left + 6, infoY, side - 12, MUTED); infoY += 13; }
        }

        g.enableScissor(textX, 50, textX + textWidth, height - 50);
        Runnable hovered = null;
        if (mouseX >= textX && mouseX < textX + textWidth && mouseY >= 52 && mouseY < height - 50) {
            int at = offset + (mouseY - 52) / 12;
            if (at >= 0 && at < lineActions.size()) hovered = lineActions.get(at);
        }
        for (int i = 0; i < visible && offset + i < lines.size(); i++) {
            // Rows that can be clicked (owner, linked ads) get the same light highlight as the SMC lists.
            if (hovered != null && lineActions.get(offset + i) == hovered) g.fill(textX - 2, 51 + i * 12, textX + textWidth - 8, 63 + i * 12, MarketTheme.c(0xFFEAECE5));
            boolean dark = lineDark.get(offset + i);
            if (dark) g.fill(textX, 51 + i * 12, textX + textWidth - 8, 63 + i * 12, MarketTheme.c(0xFF1B1722));
            Entry icon = lineIcons.get(offset + i);
            var fishRow = fishIcons.get(offset + i);
            boolean fishText = fishRow != null || fishNav.contains(offset + i);
            if (fishText) {
                int shift = fishRow == null ? 0 : fishRow.size() * 14 + 4;
                if (fishRow != null) for (int k = 0; k < fishRow.size(); k++) if (!ArchiveModels.INSTANCE.drawVanilla(g, fishRow.get(k).replaceFirst("^minecraft:", ""), textX + k * 14, 52 + i * 12, 12)) g.fill(textX + k * 14, 52 + i * 12, textX + k * 14 + 12, 64 + i * 12, MarketTheme.c(0xFFF0F1EA));
                g.drawString(font, lines.get(offset + i), textX + shift, 54 + i * 12, INK, false);
                if (fishNav.contains(offset + i)) {
                    // A small button right after the text; the whole row is clickable.
                    int bx = textX + shift + font.width(lines.get(offset + i)) + 8;
                    boolean over = mouseX >= textX && mouseX < textX + textWidth && mouseY >= 52 + i * 12 && mouseY < 64 + i * 12;
                    g.fill(bx, 52 + i * 12, bx + 36, 64 + i * 12, over ? MarketTheme.c(0xFFD8DDCF) : MarketTheme.c(0xFFE4E7DE));
                    g.renderOutline(bx, 52 + i * 12, 36, 12, MarketTheme.LINE);
                    g.drawString(font, "导航", bx + 6, 54 + i * 12, ACCENT, false);
                }
            } else if (icon != null) {
                if (!ArchiveModels.INSTANCE.draw(g, icon, textX, 52 + i * 12, 12) && !MarketImages.INSTANCE.draw(g, media, icon.imagePath(), textX, 52 + i * 12, 12))
                    g.fill(textX, 52 + i * 12, textX + 12, 64 + i * 12, MarketTheme.c(0xFFF0F1EA));
                g.drawString(font, lines.get(offset + i), textX + 18, 54 + i * 12, lineActions.get(offset + i) != null ? ACCENT : INK, false);
            } else if (lineAvatars.get(offset + i) != null) {
                if (!MarketImages.INSTANCE.draw(g, media, lineAvatars.get(offset + i), textX, 52 + i * 12, 12)) g.fill(textX, 52 + i * 12, textX + 12, 64 + i * 12, MarketTheme.c(0xFFF0F1EA));
                g.drawString(font, lines.get(offset + i), textX + 18, 54 + i * 12, lineActions.get(offset + i) != null ? ACCENT : INK, false);
            } else g.drawString(font, lines.get(offset + i), textX + (dark ? 6 : 0), 54 + i * 12, lineActions.get(offset + i) != null ? ACCENT : dark ? MarketTheme.c(0xFFF1EEF5) : INK, false);
        }
        if (gridStart >= 0) {
            int cellWidth = (textWidth - 8) / GRID_COLUMNS;
            for (int row = 0; row < (gridMembers.size() + GRID_COLUMNS - 1) / GRID_COLUMNS; row++) {
                int index = gridStart + row * 2;
                if (index + 2 <= offset || index >= offset + visible) continue;
                int y = 51 + (index - offset) * 12;
                for (int column = 0; column < GRID_COLUMNS && row * GRID_COLUMNS + column < gridMembers.size(); column++) {
                    var member = gridMembers.get(row * GRID_COLUMNS + column); int x = textX + column * cellWidth;
                    boolean over = mouseX >= x && mouseX < x + cellWidth - 4 && mouseY >= Math.max(52, y) && mouseY < Math.min(height - 50, y + 24);
                    if (over) g.fill(x - 2, y, x + cellWidth - 4, y + 24, MarketTheme.c(0xFFEAECE5));
                    if (member.avatarPath().isBlank() || !MarketImages.INSTANCE.draw(g, media, member.avatarPath(), x, y + 2, 20)) g.fill(x, y + 2, x + 20, y + 22, MarketTheme.c(0xFFF0F1EA));
                    text(g, (member.leader() ? "★ " : "") + member.mcName(), x + 25, y + 3, cellWidth - 31, ACCENT);
                    if (!member.name().equals(member.mcName())) text(g, member.name(), x + 25, y + 14, cellWidth - 31, MUTED);
                }
            }
        }
        if (effectRow >= offset && effectRow < offset + visible) EffectIcons.draw(g, effectKey, textX, 51 + (effectRow - offset) * 12, 12);
        g.disableScissor();
        if (!notice.isEmpty()) text(g, notice, textX, height - 44, textWidth - 6, MUTED);
    }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && gridStart >= 0) {
            double mx = (event.x() - winX) / windowScale, my = (event.y() - winY) / windowScale;
            int cellWidth = (textWidth - 8) / GRID_COLUMNS;
            if (mx >= textX && mx < textX + textWidth && my >= 52 && my < height - 50) {
                int line = offset + (int) ((my - 52) / 12) - gridStart, column = (int) ((mx - textX) / cellWidth), at = line < 0 ? -1 : line / 2 * GRID_COLUMNS + column;
                if (line >= 0 && column < GRID_COLUMNS && at < gridMembers.size()) { int member = gridMembers.get(at).playerId(); minecraft.setScreen(new ArchiveDetailScreen(this, media, Kind.PLAYERS, member)); return true; }
            }
        }
        if (event.button() == 0 && detail != null && ((event.x() - winX)/windowScale) >= textX && ((event.x() - winX)/windowScale) < textX + textWidth && ((event.y() - winY)/windowScale) >= 52 && ((event.y() - winY)/windowScale) < height - 50) {
            int index = offset + (int) ((((event.y() - winY)/windowScale) - 52) / 12);
            // A wrapped entry keeps one action on every line it spans.
            if (index >= 0 && index < lineActions.size() && lineActions.get(index) != null) { lineActions.get(index).run(); return true; }
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public void removed() { requestId++; if (pending != null && !pending.isDone()) { pending.cancel(true); started = false; loading = false; } }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    public boolean isLoading() { return loading; }
    public Detail detail() { return detail; }
    public String error() { return error; }
}
