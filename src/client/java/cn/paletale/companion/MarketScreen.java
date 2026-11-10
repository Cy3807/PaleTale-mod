package cn.paletale.companion;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import static cn.paletale.companion.MarketData.*;
import static cn.paletale.companion.MarketTheme.*;

public final class MarketScreen extends WindowScreen {
    private static final Category[] FILTERS = { Category.ALL, Category.WHOLESALE, Category.RETAIL, Category.WINE, Category.DISH,
            Category.MAP_ART, Category.MATERIALS, Category.BUYING, Category.MODELING, Category.SERVICE, Category.EVENT, Category.RECRUITMENT, Category.OTHER };
    private final Screen parent;
    private final MarketApi api;
    private String query = "";
    private final EnumSet<Category> categories = EnumSet.noneOf(Category.class);
    private AdState state = AdState.ACTIVE;
    private Page result;
    private String error = "";
    private boolean loading, started;
    private volatile int requestId;
    private int requestedPage = 1, rows, columns, cardWidth, cardHeight;
    private int left, panelWidth, sidebar, contentX, contentWidth, filterScroll, filterRows;
    private CompletableFuture<Page> pending;
    private long nextRefresh;
    private Action account;
    private boolean mineOnly, favoritesOnly;
    private String sort = "random";
    private EditBox searchBox;
    /** One random order for the whole client session; 随机刷新 reshuffles. */
    private static int seed = 1 + java.util.concurrent.ThreadLocalRandom.current().nextInt(999_999_999);
    static void clearSession() { seed = 1 + java.util.concurrent.ThreadLocalRandom.current().nextInt(999_999_999); }

    public MarketScreen(Screen parent, MarketApi api) {
        super(Component.literal("PaleTale · 广场")); this.parent = parent; this.api = api;
    }
    static MarketScreen favorites(Screen parent,MarketApi api){var screen=new MarketScreen(parent,api);screen.favoritesOnly=true;return screen;}
    public MarketScreen(Screen parent, MarketApi api, String query) { this(parent, api); this.query = query; }
    public MarketScreen(Screen parent, MarketApi api, String query, AdState state) { this(parent, api, query); this.state = state; }
    @Override protected void init() {
        int oldRows=rows;
        fitWindow();
        panelWidth = Math.min(1120, width - 16); left = (width - panelWidth) / 2;
        sidebar = ArchiveLayout.of(width).sidebar();
        contentX = left + sidebar + 14; contentWidth = panelWidth - sidebar - 14;
        // Cards in a grid (at most 24 per screen: two of the site's batches): columns follow the width, rows the height.
        int area = height - 108;
        columns = Math.max(2, Math.min(6, contentWidth / 92));
        cardWidth = (contentWidth - (columns - 1) * 6) / columns;
        // A card is its cover (16:10, but it may give up to half of that height) plus 60 of text; as many rows as fit, never more than 24 cards a screen.
        int coverHeight = Math.round((cardWidth - 2) * 10f / 16);
        int smallestCard = Math.round(coverHeight * 0.5f) + 60;
        int gridRows = Math.max(1, Math.min(Math.max(1, MarketPaging.MAX_ROWS / columns), (area + 6) / (smallestCard + 6)));
        cardHeight = Math.min(coverHeight + 64, (area - (gridRows - 1) * 6) / gridRows);
        rows = columns * gridRows;
        if(oldRows>0&&oldRows!=rows&&started){int first=(requestedPage-1)*oldRows;submit(first/rows+1);return;}
        ArchiveNavigation.tabs(this::addRenderableWidget, parent, api, ArchiveNavigation.Tab.MARKET, left, panelWidth);
        account = addRenderableWidget(new Action(PaleTaleClient.ACCOUNT.label(), left + panelWidth - 96, 13, 96, 18, false, "person", b -> {
            PaleTaleClient.ACCOUNT.checkStatus(minecraft);
        }));
        account.setTooltip(Tooltip.create(Component.literal("验证 SIMMC 登录状态\n需先使用正版账号进入 play.simmc.cn")));
        EditBox input = addRenderableWidget(new EditBox(font, left + 4, 67, sidebar - 26, 10, Component.literal("搜索关键词")));
        input.setMaxLength(100); searchBox = input; input.setValue(query);
        input.setBordered(false); input.setTextShadow(false); input.setTextColor(INK); input.setTextColorUneditable(MUTED); input.setResponder(value -> query = value);
        addRenderableWidget(new Action("搜", left + sidebar - 20, 62, 20, 20, false, null, b -> submit(1)));
        addRenderableWidget(new Action("状态：" + state.label, left, 89, sidebar, 18, true, null, b -> { state = state.next(); submit(1); }));
        Action mineButton = addRenderableWidget(new Action("我的/协助", left, 111, sidebar, 18, mineOnly, "person", b -> {
            if (!PaleTaleClient.ACCOUNT.loggedIn()) { error = "请先登录正版账号（进入 SIMMC 后会自动登录）。"; rebuildWidgets(); return; }
            mineOnly = !mineOnly; favoritesOnly = false; submit(1);
        }));
        mineButton.setTooltip(Tooltip.create(Component.literal("只看你作为广告主或协助者的广告；再点一次显示全部")));
        Action favoriteButton = addRenderableWidget(new Action("★ 收藏", left, 131, sidebar, 18, favoritesOnly, null, b -> {
            if (!PaleTaleClient.ACCOUNT.loggedIn()) { error = "请先登录正版账号（进入 SIMMC 后会自动登录）。"; rebuildWidgets(); return; }
            favoritesOnly = !favoritesOnly; mineOnly = false; submit(1);
        }));
        favoriteButton.setTooltip(Tooltip.create(Component.literal("只看你收藏的广告；再点一次显示全部")));
        if (!mineOnly && !favoritesOnly) {
            Action sortButton = addRenderableWidget(new Action(sort.equals("views") ? "排序：浏览量" : sort.equals("favorites") ? "排序：收藏量" : sort.equals("updated") ? "排序：最近更新" : "排序：随机",
                    contentX + contentWidth - 112, 44, 112, 18, true, null, b -> {
                sort = switch (sort) { case "random" -> "views"; case "views" -> "favorites"; case "favorites" -> "updated"; default -> "random"; };
                submit(1);
            }));
            sortButton.setTooltip(Tooltip.create(Component.literal("随机 → 浏览量 → 收藏量 → 最近更新")));
        }
        // Categories are small icon buttons, three or four to a row (the name is in the tooltip).
        int cols = Math.max(3, Math.min(4, (sidebar - 4) / 18));
        filterRows = Math.max(1, (height - 234) / 18);
        int totalRows = (FILTERS.length + cols - 1) / cols;
        filterScroll = Math.max(0, Math.min(filterScroll, Math.max(0, totalRows - filterRows)));
        int cell = (sidebar - 3 * (cols - 1)) / cols;
        for (int slot = 0; slot < filterRows * cols; slot++) {
            int index = filterScroll * cols + slot;
            if (index >= FILTERS.length) break;
            Category category = FILTERS[index];
            boolean selected = category == Category.ALL ? categories.isEmpty() : categories.contains(category);
            Action button = addRenderableWidget(new Action("", left + slot % cols * (cell + 3), 172 + slot / cols * 18,
                    cell, 17, selected, category.id.isEmpty() ? "all" : category.id, b -> {
                if (category == Category.ALL) categories.clear();
                else if (!categories.add(category)) categories.remove(category);
                submit(1);
            }));
            button.setTooltip(Tooltip.create(Component.literal(category.label + " · 可多选")));
        }
        if (totalRows > filterRows) {
            Action up = addRenderableWidget(new Action("↑", left + sidebar - 36, 154, 18, 13, false, null, b -> { filterScroll--; rebuildWidgets(); }));
            Action down = addRenderableWidget(new Action("↓", left + sidebar - 18, 154, 18, 13, false, null, b -> { filterScroll++; rebuildWidgets(); }));
            up.active = filterScroll > 0; down.active = filterScroll + filterRows < totalRows;
        }
        boolean random=sort.equals("random")&&!mineOnly&&!favoritesOnly;
        var refresh=addRenderableWidget(new Action("手动刷新",left,height-56,random?sidebar/2-1:sidebar,19,false,null,b->refresh()));boolean roomy=sidebar/2-1>=42;refresh.setDisplayLabel(random&&!roomy?"⟳":"⟳ 刷新");refresh.active=!loading;
        refresh.setTooltip(Tooltip.create(Component.literal("手动刷新广告，默认每小时更新")));
        if(random)addRenderableWidget(new Action("随机刷新",left+sidebar/2+1,height-56,sidebar-sidebar/2-1,19,true,null,b->{seed=1+java.util.concurrent.ThreadLocalRandom.current().nextInt(999_999_999);submit(1);})).setDisplayLabel(roomy?"⇄ 随机":"⇄");
        addRenderableWidget(new Action("清除筛选", left, height - 32, sidebar, 17, false, null, b -> {
            query = ""; categories.clear(); state = AdState.ACTIVE; filterScroll = 0; mineOnly = false; favoritesOnly = false; sort = "random"; submit(1);
        }));
        if (result != null) {
            for (int slot = 0; slot < rows; slot++) {
                int index = slot;
                if (index >= result.ads().size()) break;
                Ad ad = result.ads().get(index);
                var card = addRenderableWidget(new MarketCardButton(ad, api, contentX + slot % columns * (cardWidth + 6), 70 + slot / columns * (cardHeight + 6), cardWidth, cardHeight,
                        b -> minecraft.setScreen(new MarketDetailScreen(this, this, api, ad))));
                var place = card.place();
                card.setTips(null,
                        place == null ? null : Tooltip.create(Component.literal("导航到广告主设置的地址\n" + place.x() + ", " + place.z() + (place.y() == null ? "" : ", " + place.y()))));
            }
        }
        Button previous = addRenderableWidget(new Action("上一页", contentX, height - 32, 52, 17, false, null, b -> {
            submit(requestedPage - 1);
        }));
        Button next = addRenderableWidget(new Action("下一页", contentX + contentWidth - 52, height - 32, 52, 17, false, null, b -> {
            submit(requestedPage + 1);
        }));
        previous.active = !loading && requestedPage > 1;
        next.active = !loading && result != null && result.hasMore();
        if (!started) { started = true; PaleTaleClient.ACCOUNT.autoLogin(minecraft); submit(1); }
    }
    /** Re-reads the current page, e.g. after a chest-shop binding changed. */
    void refresh() { var previous=result;api.invalidatePublic();submit(requestedPage);if(loading&&result==null&&previous!=null){result=previous;rebuildWidgets();} }
    private void submit(int page) {
        int id = ++requestId; if (pending != null) pending.cancel(true);
        requestedPage = page; result = null; loading = true; error = ""; nextRefresh=System.currentTimeMillis()+WorldCatalogCache.REFRESH_MS;
        int visibleRows=rows;
        int batch=MarketPaging.firstBatch(page,visibleRows);
        String q=query,order=sort,token=PaleTaleClient.ACCOUNT.token();var tags=EnumSet.copyOf(categories);var selectedState=state;int orderSeed=seed;boolean mine=mineOnly,favorites=favoritesOnly;
        var source=api.snapshot();
        java.util.function.IntFunction<CompletableFuture<Page>> fetch=number->mine?source.mine(token,number):favorites?source.favorites(token,number):source.search(q,tags,selectedState,number,orderSeed,order);
        try{if(!mine&&!favorites){var loaded=new java.util.ArrayList<Page>();var peeked=source.peekSearch(q,tags,selectedState,batch,orderSeed,order);while(peeked!=null){loaded.add(peeked);if(!MarketPaging.needsNext(page,visibleRows,peeked))break;peeked=source.peekSearch(q,tags,selectedState,batch+loaded.size(),orderSeed,order);}if(!loaded.isEmpty()&&!MarketPaging.needsNext(page,visibleRows,loaded.getLast()))result=MarketPaging.window(page,visibleRows,loaded);}}catch(IllegalStateException changed){source.invalidatePublic();}
        rebuildWidgets();
        pending=gather(fetch,page,visibleRows,batch,new java.util.ArrayList<>(),id);
        pending.whenComplete((value, failure) -> minecraft.execute(() -> {
            if (id != requestId || minecraft.screen != this) return;
            loading = false;
            if (failure == null) {result = value;if(!mine&&!favorites){long remaining=source.refreshIn(q,tags,selectedState,batch,orderSeed,order);if(value.ads().size()>0){int lastBatch=((page-1)*visibleRows+value.ads().size()-1)/MarketPaging.BATCH+1;for(int more=batch+1;more<=lastBatch;more++)remaining=Math.min(remaining,source.refreshIn(q,tags,selectedState,more,orderSeed,order));}nextRefresh=System.currentTimeMillis()+Math.max(1000,remaining);}}
            else {
                Throwable cause = failure instanceof CompletionException ? failure.getCause() : failure;
                error = cause instanceof IllegalStateException ? cause.getMessage() : "网络连接失败或超时，请搜索重试。";
            }
            rebuildWidgets();
        }));
    }
    /** Reads the batches a screen page spans (one to three of them, each only once the previous one says there are more). */
    private CompletableFuture<Page> gather(java.util.function.IntFunction<CompletableFuture<Page>> fetch,int page,int visibleRows,int batch,java.util.List<Page> loaded,int id){
        return fetch.apply(batch+loaded.size()).thenCompose(next->{
            if(id!=requestId)return CompletableFuture.failedFuture(new java.util.concurrent.CancellationException());
            loaded.add(next);
            return MarketPaging.needsNext(page,visibleRows,next)?gather(fetch,page,visibleRows,batch,loaded,id):CompletableFuture.completedFuture(MarketPaging.window(page,visibleRows,loaded));
        });
    }
    @Override public void tick() {
        super.tick();
        if(started&&!loading&&!mineOnly&&!favoritesOnly&&System.currentTimeMillis()>=nextRefresh)refresh();
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (getFocused() instanceof EditBox && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) { submit(1); return true; }
        return super.keyPressed(event);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if ((x - winX)/windowScale < contentX && (y - winY)/windowScale >= 110 && (y - winY)/windowScale < height - 40) {
            filterScroll += vertical < 0 ? 1 : -1; rebuildWidgets(); return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override protected void windowBackground(GuiGraphics g, int x, int y, float delta) {
        g.fill(0, 0, width, height, PAPER);
        g.fill(left, 42, left + sidebar, height - 38, MarketTheme.c(0xFFF0F1EA));
        line(g, left, 38, panelWidth); g.vLine(contentX - 7, 44, height - 37, LINE);
        g.renderOutline(left, 62, sidebar - 22, 20, LINE);
    }
    @Override protected void renderWindow(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (ModUpdate.consumeChange()) { rebuildWidgets(); return; }
        String label = PaleTaleClient.ACCOUNT.label();
        if (!account.getMessage().getString().equals(label)) {
            account.setMessage(Component.literal(label));
            var citizen = PaleTaleClient.ACCOUNT.citizen();
            account.setTooltip(Tooltip.create(Component.literal(citizen == null ? "验证 SIMMC 登录状态\n需先使用正版账号进入 play.simmc.cn"
                    : "验证 SIMMC 登录状态\n网站账号：" + citizen.displayName() + "\n玩家编号：" + citizen.playerId())));
        }
        account.setAvatar(api, ArchiveNavigation.avatarOf(PaleTaleClient.ACCOUNT.citizen()));
        account.active = !PaleTaleClient.ACCOUNT.busy() && (PaleTaleClient.ACCOUNT.loggedIn() || PaleTaleClient.ACCOUNT.canLogin(minecraft));
        super.renderWindow(g, mouseX, mouseY, delta);
        if (searchBox != null) hint(g, searchBox, "搜索");
        ArchiveNavigation.brand(g, left, panelWidth);
        text(g, "广场", left + 4, 47, sidebar - 8, INK);
        text(g, "标签筛选", left + 3, 157, sidebar - 32, MUTED);
        String status = loading && result==null ? "正在查询…" : !error.isEmpty() ? error : result == null ? "" : result.total() == 0
                ? "未找到广告，试试其他条件。" : "找到 " + result.total() + " 条广告" + (mineOnly ? " · 我的 / 协助广告" : favoritesOnly ? " · 我的收藏" : "");
        text(g, status, contentX + 2, 49, contentWidth - 2 - (mineOnly || favoritesOnly ? 0 : 118), error.isEmpty() ? MUTED : MarketTheme.c(0xFF9E4942));
        if (result != null && !result.ads().isEmpty()) {
            int start = (result.page() - 1) * result.pageSize() + 1;
            int end = Math.min((result.page() - 1) * result.pageSize() + result.ads().size(), start + rows - 1);
            String count = start + "–" + end + " / " + result.total();
            g.drawString(font, count, contentX + contentWidth / 2 - font.width(count) / 2, height - 27, MUTED, false);
        }
        String message = PaleTaleClient.ACCOUNT.message();
        if (!message.isEmpty() && !PaleTaleClient.ACCOUNT.busy()) text(g, message, left + 2, height - 10, panelWidth - 4, MarketTheme.c(0xFF9E4942));
    }
    /** Clicking the place of a card starts the navigation to the first address the advertiser set. */
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double mx = (event.x() - winX)/windowScale, my = (event.y() - winY)/windowScale;
            for (var child : children()) if (child instanceof MarketCardButton card && card.onPlace(mx, my)) {
                var t = card.place();
                Navigation.start(new Navigation.Target(t.name(), t.x(), t.y(), t.z(), t.kind(), t.marks()));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public void removed() {
        requestId++; if (pending != null && !pending.isDone()) pending.cancel(true);
        if (loading) { started = false; loading = false; }
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    public boolean isLoading() { return loading; }
    public Page result() { return result; }
    public String error() { return error; }
}
