package cn.paletale.companion;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static cn.paletale.companion.ArchiveData.*;
import static cn.paletale.companion.MarketTheme.*;

public final class ArchiveScreen extends WindowScreen {
    private final Screen parent;
    private final MarketApi media;
    private final ArchiveApi api;
    private final Kind kind;
    private String query = "", type = "", model = "", sort = "id", error = "";
    /** Wine effect filter key ("" = all, "unknown" = no recorded effect); independent of the 2D/3D filter. */
    private String effect = "";
    /** Fish tag filter (all selected tags must match); only used while the fish category is open. */
    private final java.util.LinkedHashSet<String> fishTags = new java.util.LinkedHashSet<>();
    
    /** One random order and the pages already read live for the whole client session: reopening the archive shows them as they were. */
    private static int itemSeed = newSeed();
    private record Saved(String query, String type, String model, String sort, int page, int localPage, boolean favorites, String effect) {}
    private static final java.util.Map<Kind, Saved> SAVED = new java.util.EnumMap<>(Kind.class);
    private static final java.util.LinkedHashMap<String, Page> PAGES = new java.util.LinkedHashMap<>(64, .75f, true);
    static void clearSession() { SAVED.clear(); PAGES.clear(); itemSeed = newSeed(); MarketScreen.clearSession(); }
    private int seed = itemSeed;
    private int ownerId;
    private Page result;
    private int requestedPage = 1, localPage, requestId, left, panelWidth, sidebar, contentX, contentWidth, columns, rows, cardWidth, cardHeight;
    private boolean loading, started, restore, favorites, online;
    private int requestedSize;
    private CompletableFuture<Page> pending;
    private Action account;
    private EditBox searchBox;
    /** Login can finish after the screen was built: the login-only filters are rebuilt (enabled) as soon as it changes. */
    private boolean seenLoggedIn;
    private String searchHint = "";
    private Button ownProfile;
    private Button uploadButton;
    private boolean uploadOpen;

    public ArchiveScreen(Screen parent, MarketApi media, Kind kind, int ownerId) {
        super(Component.literal(kind.label)); this.parent = parent; this.media = media; this.api = new ArchiveApi(media); this.kind = kind; this.ownerId = ownerId;
        if (kind == Kind.ITEMS) sort = "random";
        var saved = ownerId == 0 ? SAVED.get(kind) : null;
        if (saved != null) { query = saved.query(); type = saved.type(); model = saved.model(); sort = saved.sort(); favorites = saved.favorites(); effect = saved.effect(); requestedPage = saved.page(); localPage = saved.localPage(); restore = true; }
    }
    private static int newSeed() { return 1 + java.util.concurrent.ThreadLocalRandom.current().nextInt(999_999_999); }
    @Override protected void init() {
        fitWindow();
        var layout=ArchiveLayout.of(width);panelWidth=layout.panelWidth();left=layout.left();sidebar=layout.sidebar();contentX=layout.contentX();contentWidth=layout.contentWidth();
        ArchiveNavigation.tabs(this::addRenderableWidget, parent, media, kind == Kind.ITEMS ? ArchiveNavigation.Tab.ITEMS : kind == Kind.ORGS ? ArchiveNavigation.Tab.ORGS : ArchiveNavigation.Tab.PLAYERS, left, panelWidth);
        if (kind == Kind.ITEMS && ArchiveLocks.locked(type)) type = "";
        seenLoggedIn = PaleTaleClient.ACCOUNT.loggedIn();
        int accountWidth = panelWidth < 600 ? 88 : 96;
        account = addRenderableWidget(new Action(PaleTaleClient.ACCOUNT.label(), left + panelWidth - accountWidth, 13, accountWidth, 18, false, "person", b -> {
            PaleTaleClient.ACCOUNT.checkStatus(minecraft);
        }));
        account.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("验证 SIMMC 登录状态\n需先使用正版账号进入 play.simmc.cn")));
        EditBox input = addRenderableWidget(new EditBox(font, left + 4, 67, sidebar - 26, 10, Component.literal("搜索档案")));
        input.setMaxLength(100); input.setValue(query); input.setBordered(false); input.setTextShadow(false); input.setTextColor(INK);
        searchHint = "搜索"; searchBox = input; input.setResponder(value -> query = value);
        addRenderableWidget(new Action("搜", left + sidebar - 20, 62, 20, 20, false, null, b -> submit(1, false, true)));
        if (kind == Kind.ITEMS) {
            String[] names = { "全部", "酒", "菜", "地图画", "鱼类" }, values = { "", "wine", "dish", "map_art", "fish" };
            // All five categories start above the window's middle line, so SidebarLayout keeps them in one stack (a lower one would drop to the bottom group, above 我的收藏).
            for (int i = 0; i < names.length; i++) {
                String value = values[i];
                boolean locked = ArchiveLocks.locked(value);
                String label = names[i] + (locked ? " " + ArchiveLocks.LOCK_ICON : "");
                var button = addRenderableWidget(new Action(label, left, 86 + i * 9, sidebar, 17, type.equals(value), null, b -> { type = value; model = ""; effect = ""; fishTags.clear(); submit(1); }));
                if (locked) { button.active = false; button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("酒、菜档案整改中，暂不可查看"))); }
            }
            if (type.equals("fish")) addRenderableWidget(new Action("标签：" + (fishTags.isEmpty() ? "全部" : fishTags.size() == 1 ? fishTags.iterator().next() : fishTags.iterator().next() + " 等" + fishTags.size() + "项"), left, 202, sidebar, 19, !fishTags.isEmpty(), null,
                    b -> minecraft.setScreen(new FishTagPickerScreen(this, fishTags, () -> submit(1)))));
            if (type.equals("wine")) addRenderableWidget(new Action(model.isEmpty() ? "展示：全部" : "展示：" + model.toUpperCase(), left, 202, sidebar, 19, true, null, b -> {
                model = model.isEmpty() ? "2d" : model.equals("2d") ? "3d" : ""; submit(1);
            }));
            if (type.equals("wine")) addRenderableWidget(new Action((effect.isEmpty() ? "" : "   ") + "效果：" + effectLabel(effect), left, 222, sidebar, 19, true, null, b ->
                    minecraft.setScreen(new EffectPickerScreen(this, effect, chosen -> { effect = chosen; submit(1); }))));
            filter(type.equals("wine") ? 242 : type.equals("fish") ? 222 : 222, "我的收藏", favorites, PaleTaleClient.ACCOUNT.loggedIn(), "登录 PaleTale 后可筛选收藏的物品", () -> favorites = !favorites);
            // Map art can also be sorted by size (largest first); the other categories have no size.
            boolean maps = type.equals("map_art") || (ArchiveLocks.ACTIVE && type.isBlank());
            if (!maps && sort.equals("size")) sort = "random";
            addRenderableWidget(new Action(sort.equals("views") ? "排序：浏览量" : sort.equals("favorites") ? "排序：收藏量" : sort.equals("size") ? "排序：大小" : "排序：随机", contentX + contentWidth - 112, 44, 112, 18, true, null, b -> {
                sort = sort.equals("random") ? "views" : sort.equals("views") ? "favorites" : sort.equals("favorites") && maps ? "size" : "random"; submit(1);
            })).setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(maps ? "随机 → 浏览量 → 收藏量 → 大小" : "随机 → 浏览量 → 收藏量")));
        } else {
            addRenderableWidget(new Action(sort.equals("id") ? "排序：编号" : "排序：名称", left, 111, sidebar, 20, true, null, b -> { sort = sort.equals("id") ? "name" : "id"; submit(1); }));
            if (kind == Kind.PLAYERS) ownProfile = addRenderableWidget(new Action("我的档案", left, 137, sidebar, 20, false, "person", b -> {
                var citizen = PaleTaleClient.ACCOUNT.citizen();
                if (citizen != null) minecraft.setScreen(new ArchiveDetailScreen(this, media, Kind.PLAYERS, (int) citizen.playerId()));
            })); if (ownProfile != null) ownProfile.active = PaleTaleClient.ACCOUNT.loggedIn();
            if (kind == Kind.PLAYERS) {
                addRenderableWidget(new Action("修改密码", left, 163, sidebar, 20, false, null, b -> minecraft.setScreen(new ChangePasswordScreen(this, PaleTaleClient.favoritesFor(media))))).active = PaleTaleClient.ACCOUNT.loggedIn();
                addRenderableWidget(new Action("我的收藏",left,185,sidebar,20,false,null,b->minecraft.setScreen(new WorldFavoritesScreen(this,media,false)))).active=PaleTaleClient.ACCOUNT.loggedIn();
                filter(207, "在线玩家", online, OnlinePlayers.uuids(minecraft) != null, "进入服务器后，可按你看到的在线列表筛选", () -> online = !online);
            }
        }
        if (kind == Kind.ITEMS) uploadButton = addRenderableWidget(new Action("上传", left, height - 54, sidebar, 17, uploadOpen, null, b -> uploadOpen = !uploadOpen));
        if (kind == Kind.ITEMS && sort.equals("random")) addRenderableWidget(new Action("随机刷新", left, height - 76, sidebar, 19, true, null, b -> { seed = itemSeed = newSeed(); submit(1); })).setDisplayLabel("⇄ 随机");
        addRenderableWidget(new Action("清除筛选", left, height - 32, sidebar, 17, false, null, b -> { query = ""; type = ""; model = ""; effect = ""; fishTags.clear(); ownerId = 0; favorites = false; online = false; sort = kind == Kind.ITEMS ? "random" : "id"; submit(1); }));
        // Dense grid: more entries per screen; cards shrink to a compact layout before the page gets sparse.
        // Fixed rows (items 3, players 4); columns follow the width, so every full page holds columns × rows entries.
        columns = Math.max(2, contentWidth / 84); cardWidth = (contentWidth - (columns - 1) * 6) / columns;
        int areaHeight = height - 112;
        // Fewer rows on short windows, so the cards never run under the page controls.
        rows = kind == Kind.ITEMS ? 3 : 4;
        while (rows > 1 && (areaHeight - (rows - 1) * 6) / rows < (kind == Kind.ITEMS ? 68 : 48)) rows--;
        cardHeight = Math.min(150, Math.max(40, (areaHeight - (rows - 1) * 6) / rows));
        int visible = columns * rows;
        // A resized window changes how many cards fit: reload so every page is exactly one screen.
        if (started && !loading && result != null && requestedSize != Math.max(4, Math.min(48, visible))) { submit(1); return; }
        if (result != null && !loading) {
            List<Entry> entries = visibleEntries();
            localPage = Math.min(localPage, Math.max(0, (entries.size() - 1) / visible));
            for (int slot = 0; slot < visible; slot++) {
                int index = localPage * visible + slot; if (index >= entries.size()) break;
                Entry entry = entries.get(index);
                addRenderableWidget(new ArchiveCardButton(entry, media, contentX + slot % columns * (cardWidth + 6), 70 + slot / columns * (cardHeight + 6), cardWidth, cardHeight,
                        b -> minecraft.setScreen(new ArchiveDetailScreen(this, media, kind, entry.id()))));
            }
        }
        Button previous = addRenderableWidget(new Action("上一页", contentX, height - 32, 52, 17, false, null, b -> {
            if (localPage > 0) { localPage--; rebuildWidgets(); } else submit(requestedPage - 1, true);
        }));
        Button next = addRenderableWidget(new Action("下一页", contentX + contentWidth - 52, height - 32, 52, 17, false, null, b -> {
            if (result != null && (localPage + 1) * visible < visibleEntries().size()) { localPage++; rebuildWidgets(); } else submit(requestedPage + 1);
        }));
        previous.active = !loading && (localPage > 0 || requestedPage > 1);
        next.active = !loading && result != null && ((localPage + 1) * visible < visibleEntries().size() || result.hasMore());
        if (!started) { started = true; PaleTaleClient.ACCOUNT.autoLogin(minecraft); if (restore) { restore = false; submit(requestedPage, false, false, localPage); } else submit(1); }
    }
    /** Cards for the current page, without the temporarily locked wine and dish entries. */
    private String fishTagParam() { return kind == Kind.ITEMS && type.equals("fish") ? String.join(",", fishTags) : ""; }
    private List<Entry> visibleEntries() { return result == null ? List.of() : ArchiveLocks.visible(kind, result.entries()); }
    private void submit(int page) {
        submit(page, false);
    }
    /** A toggle filter in the sidebar; unavailable (login / not on a server) shows why on hover and turns itself off. */
    private void filter(int y, String label, boolean on, boolean available, String why, Runnable toggle) {
        Button button = addRenderableWidget(new Action(label, left, y, sidebar, 20, on && available, null, b -> { toggle.run(); submit(1); }));
        button.active = available;
        if (!available) button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(why)));
    }
    private static java.util.Set<String> registeredCache = java.util.Set.of();
    private static long registeredAt;
    /** Registered players' UUIDs, refreshed at most once a minute. */
    private CompletableFuture<java.util.Set<String>> registeredPlayers() {
        if (System.currentTimeMillis() - registeredAt < 60_000 && !registeredCache.isEmpty()) return CompletableFuture.completedFuture(registeredCache);
        return api.registeredPlayers().thenApply(set -> { registeredCache = set; registeredAt = System.currentTimeMillis(); return set; });
    }
    private void submit(int page, boolean lastViewport) { submit(page, lastViewport, false, 0); }
    private void submit(int page, boolean lastViewport, boolean force) { submit(page, lastViewport, force, 0); }
    private void submit(int page, boolean lastViewport, boolean force, int keepLocal) {
        int run = ++requestId; if (pending != null) pending.cancel(true);
        requestedSize = Math.max(4, Math.min(48, columns * rows));
        int usedSeed = sort.equals("random") ? seed : 0;
        String token = PaleTaleClient.ACCOUNT.token();
        boolean onlyFavorites = favorites && token != null;
        java.util.Set<String> seen = online && kind == Kind.PLAYERS ? OnlinePlayers.uuids(minecraft) : null;
        boolean onlineFilter = seen != null;
        String requestType = kind == Kind.ITEMS && ArchiveLocks.ACTIVE && type.isBlank() ? ArchiveLocks.OPEN_TYPES : type;
        String key = media.site()+"|"+kind + "|" + query + "|" + requestType + "|" + model + "|" + ownerId + "|" + sort + "|" + usedSeed + "|" + requestedSize + "|" + page + "|" + effect + "|" + fishTagParam();
        boolean cacheable = !onlyFavorites && !onlineFilter;
        // Favourites and who is online change while playing: those lists are always read fresh.
        Page cached = force || onlyFavorites || onlineFilter ? null : PAGES.get(key);
        if (cached != null) {
            // Pages read before come back instantly, with no empty list in between.
            requestedPage = page; error = ""; loading = false; result = cached;
            localPage = lastViewport ? Math.max(0, (ArchiveLocks.visible(kind, cached.entries()).size() - 1) / (columns * rows)) : keepLocal;
            rebuildWidgets();
        }
        requestedPage = page; localPage = 0; loading = cached==null; error = ""; result = cached; rebuildWidgets();
        boolean[] fresh={false};String diskScope="public:"+media.site(),diskKey="archive:"+key;
        if(cacheable&&!force)DiskPages.read(diskScope,diskKey,Page.class).thenAccept(snapshot->minecraft.execute(()->{if(snapshot==null||fresh[0]||run!=requestId||minecraft.screen!=this)return;try{if(snapshot.entries()==null||snapshot.entries().size()>48||snapshot.page()!=page)return;result=snapshot;loading=false;rebuildWidgets();}catch(RuntimeException ignored){}}));
        // A SIMMC tab list holds far more names than registered players, so it is matched against the registered UUIDs here,
        // and only the (few) registered players that are online go into the request.
        pending = !onlineFilter ? api.search(kind, query, requestType, model, ownerId, sort, page, usedSeed, requestedSize, onlyFavorites, null, onlyFavorites ? token : null, kind == Kind.ITEMS ? effect : "", fishTagParam())
                : registeredPlayers().thenCompose(registered -> api.search(kind, query, requestType, model, ownerId, sort, page, usedSeed, requestedSize, onlyFavorites,
                        seen.stream().filter(registered::contains).sorted().limit(300).toList(), onlyFavorites ? token : null, ""));
        pending.whenComplete((data, failure) -> minecraft.execute(() -> {
            if (run != requestId || minecraft.screen != this) return;
            boolean unchanged=failure==null&&java.util.Objects.equals(result,data)&&!loading;fresh[0]=failure==null;loading = false;
            if (failure == null) { if(cacheable)DiskPages.write(diskScope,diskKey,data); if (cacheable) PAGES.put(key, data); while (PAGES.size() > 48) PAGES.remove(PAGES.keySet().iterator().next()); result = data; requestedPage = data.page(); if (lastViewport) localPage = Math.max(0, (ArchiveLocks.visible(kind, data.entries()).size() - 1) / (columns * rows)); }
            else { Throwable cause = failure.getCause(); error = cause instanceof IllegalStateException ? cause.getMessage() : "公开档案读取失败，请重试。"; }
            if(!unchanged)rebuildWidgets();
        }));
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (getFocused() instanceof EditBox && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) { submit(1); return true; }
        return super.keyPressed(event);
    }
    @Override protected void windowBackground(GuiGraphics g, int x, int y, float delta) {
        g.fill(0,0,width,height,PAPER);ArchiveLayout.of(width).draw(g,height); g.renderOutline(left, 62, sidebar - 22, 20, LINE);
    }
    @Override protected void renderWindow(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (ModUpdate.consumeChange() || seenLoggedIn != PaleTaleClient.ACCOUNT.loggedIn()) { rebuildWidgets(); return; }
        account.setMessage(Component.literal(PaleTaleClient.ACCOUNT.label()));
        if (ownProfile != null) ownProfile.active = PaleTaleClient.ACCOUNT.loggedIn();
        account.setAvatar(media, ArchiveNavigation.avatarOf(PaleTaleClient.ACCOUNT.citizen()));
        account.active = !PaleTaleClient.ACCOUNT.busy() && (PaleTaleClient.ACCOUNT.loggedIn() || PaleTaleClient.ACCOUNT.canLogin(minecraft));
        super.renderWindow(g, mouseX, mouseY, delta); ArchiveNavigation.brand(g, left, panelWidth);
        if (searchBox != null) hint(g, searchBox, searchHint);
        if (kind == Kind.ITEMS && type.equals("wine") && !effect.isEmpty()) EffectIcons.draw(g, effect, left + 4, 226, 12);
        text(g, kind.label, left + 4, 47, sidebar - 8, INK);
        String status = loading ? "正在读取公开档案…" : !error.isEmpty() ? error : result == null ? "" : "共 " + result.total() + (kind == Kind.ITEMS ? " 件公开物品" : kind == Kind.ORGS ? " 个公开组织" : " 位玩家") + (ownerId > 0 ? " · 此玩家的物品" : "") + (effect.isEmpty() ? "" : " · " + effectLabel(effect)) + (favorites ? " · 我的收藏" : "") + (sort.equals("views") ? " · 按浏览量" : sort.equals("favorites") ? " · 按收藏量" : "") + (online && kind == Kind.PLAYERS ? " · 当前在线" : "") + (kind == Kind.ITEMS && ArchiveLocks.ACTIVE ? " · 酒、菜整改中，暂不展示" : "");
        text(g, status, contentX + 2, 49, contentWidth - 4 - (kind == Kind.ITEMS ? 118 : 0), error.isEmpty() ? MUTED : MarketTheme.c(0xFF9E4942));
        if (result != null && !loading && visibleEntries().isEmpty()) {
            text(g, result.entries().isEmpty() ? "未找到对应档案，请更换条件。" : "本页酒、菜档案整改中，暂不展示；请翻页或选择其他分类。", contentX + 8, 92, contentWidth - 16, MUTED);
        }
        if (result != null && !visibleEntries().isEmpty()) {
            int start = (result.page() - 1) * result.pageSize() + localPage * columns * rows + 1;
            String count = start + "–" + Math.min((result.page() - 1) * result.pageSize() + visibleEntries().size(), start + columns * rows - 1) + " / " + result.total();
            g.drawString(font, count, contentX + contentWidth / 2 - font.width(count) / 2, height - 27, MUTED, false);
        }
        if (!PaleTaleClient.ACCOUNT.message().isEmpty() && !PaleTaleClient.ACCOUNT.busy()) text(g, PaleTaleClient.ACCOUNT.message(), left + 2, height - 10, panelWidth - 4, MarketTheme.c(0xFF9E4942));
        for (var child : children()) if (child instanceof ArchiveCardButton card) card.preview(g, mouseX, mouseY, width, height);
        if (uploadOpen && kind == Kind.ITEMS) drawUploadMenu(g, mouseX, mouseY);
    }

    /** The upload choices: what to add and what happens next (wine and dish go through the official group, the rest through the website). */
    private static final String[][] UPLOADS = { { "酒 / 菜", "加入官方群咨询" }, { "地图画", "打开网页上传" }, { "酒效果", "打开网页提交" } };
    private int uploadX() { return left; }
    private int uploadY() { return height - 58 - UPLOADS.length * 26 - 6; }
    private int uploadWidth() { return 150; }
    private int uploadRow(double mx, double my) {
        if (mx < uploadX() || mx >= uploadX() + uploadWidth() || my < uploadY() + 3) return -1;
        int row = (int) ((my - uploadY() - 3) / 26);
        return row >= 0 && row < UPLOADS.length ? row : -1;
    }
    private void drawUploadMenu(GuiGraphics g, int mouseX, int mouseY) {
        int x = uploadX(), y = uploadY(), w = uploadWidth(), h = UPLOADS.length * 26 + 6;
        g.fill(x, y, x + w, y + h, PAPER); g.renderOutline(x, y, w, h, LINE);
        int hover = uploadRow(mouseX, mouseY);
        for (int i = 0; i < UPLOADS.length; i++) {
            int ry = y + 3 + i * 26;
            if (i == hover) g.fill(x + 2, ry, x + w - 2, ry + 25, MarketTheme.c(0xFFE4E7DE));
            g.drawString(font, UPLOADS[i][0], x + 8, ry + 3, INK, false);
            g.drawString(font, UPLOADS[i][1], x + 8, ry + 14, MUTED, false);
        }
    }
    private void openUpload(int row) {
        java.net.URI target = row == 0 ? ArchiveNavigation.QQ_GROUP : SiteConfig.current().resolve(row == 1 ? "/items?type=map_art" : "/items?type=wine");
        net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLink(this, target).onPress(uploadButton);
    }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (uploadOpen && event.button() == 0) {
            double mx = (event.x() - winX)/windowScale, my = (event.y() - winY)/windowScale;
            int row = uploadRow(mx, my);
            if (row >= 0) { uploadOpen = false; openUpload(row); return true; }
            boolean onButton = mx >= left && mx < left + sidebar && my >= height - 54 && my < height - 37;
            if (!onButton) uploadOpen = false;
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public void removed() { // Filters are remembered even while a page is still loading (it then reopens on page 1).
        if (ownerId == 0) SAVED.put(kind, new Saved(query, type, model, sort, result != null ? requestedPage : 1, result != null ? localPage : 0, favorites, effect)); requestId++; if (pending != null && !pending.isDone()) pending.cancel(true); if (loading) { loading = false; started = false; } }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    public boolean isLoading() { return loading; }
    public Page result() { return result; }
    public String error() { return error; }
}
