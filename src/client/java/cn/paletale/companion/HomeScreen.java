package cn.paletale.companion;

import java.time.LocalDate;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import static cn.paletale.companion.MarketTheme.*;

/**
 * The start page: the first thing H shows after the game starts. A welcome, the ledger in three spans (yesterday, today, the last seven days),
 * and a few ads from the plaza. The tabs at the top are the doors to every other window.
 */
final class HomeScreen extends WindowScreen {
    private static int RED() { return MarketTheme.c(0xFFB3392F); }
    private record Span(String title, LedgerDaily.Summary summary) {}
    private final Screen parent;
    private final MarketApi api;
    private List<Span> spans = List.of();
    private int refreshTicks;
    /** A few plaza ads for the bottom of the page, fetched once per ten minutes (random order, first page). */
    private static java.util.List<MarketData.Ad> featured = java.util.List.of();
    private static long featuredAt;
    private static boolean featuredFailed;
    private boolean featuredLoading;

    HomeScreen(Screen parent, MarketApi api) { super(Component.literal("PaleTale 开始")); this.parent = parent; this.api = api; }

    @Override protected void init() {
        fitWindow();
        var layout = ArchiveLayout.of(width);
        ArchiveNavigation.tabs(this::addRenderableWidget, parent, api, ArchiveNavigation.Tab.HOME, layout.left(), layout.panelWidth());
        int accountWidth = layout.panelWidth() < 600 ? 88 : 96;
        addRenderableWidget(new Action(PaleTaleClient.ACCOUNT.label(), layout.left() + layout.panelWidth() - accountWidth, 13, accountWidth, 18, false, "person", b -> PaleTaleClient.ACCOUNT.checkStatus(minecraft)));
        addRenderableWidget(new Action("打开官网", layout.left() + layout.panelWidth() - 70, 46, 70, 16, false, null,
                b -> net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLink(minecraft.screen, SiteConfig.current()).onPress(b)));
        refresh();
        // A card is its 16:10 cover plus the text rows. The window's size decides how many fit across: when the room below is short (a big UI size)
        // more, narrower cards bring the whole cover back into view; when it is plenty (a small UI size) the cards stop at their natural height instead of stretching.
        int gap = 6, cardTop = ADS_TOP + 17, room = Math.max(70, height - 30 - cardTop);
        int columns = Math.max(2, Math.min(6, layout.panelWidth() / 104)), cardWidth = (layout.panelWidth() - (columns - 1) * gap) / columns;
        for (int more = columns; more <= 6; more++) {
            columns = more; cardWidth = (layout.panelWidth() - (columns - 1) * gap) / columns;
            if (cardWidth < 84 || (cardWidth - 2) * 10 / 16 + CARD_TEXT <= room) break;
        }
        final int shown = columns;
        int cardHeight = Math.min(room, (cardWidth - 2) * 10 / 16 + CARD_TEXT);
        for (int slot = 0; slot < Math.min(columns, featured.size()); slot++) {
            var ad = featured.get(slot);
            var card = addRenderableWidget(new MarketCardButton(ad, api, layout.left() + slot * (cardWidth + gap), cardTop, cardWidth, cardHeight, b -> minecraft.setScreen(new MarketDetailScreen(this, null, api, ad))));
            var place = card.place();
            card.setTips(null, place == null ? null : net.minecraft.client.gui.components.Tooltip.create(Component.literal("导航到广告主设置的地址\n" + place.x() + ", " + place.z() + (place.y() == null ? "" : ", " + place.y()))));
        }
        addRenderableWidget(new Action("随机刷新", layout.left() + layout.panelWidth() - 64, ADS_TOP - 3, 64, 15, false, null, b -> loadFeatured(shown))).setDisplayLabel("⇄ 随机");
        if (featured.isEmpty() || System.currentTimeMillis() - featuredAt > 600_000) loadFeatured(shown);
    }

    private void loadFeatured(int columns) {
        if (featuredLoading) return;
        featuredLoading = true;
        var source = api.snapshot();
        source.search("", java.util.EnumSet.noneOf(MarketData.Category.class), MarketData.AdState.ACTIVE, 1, 1 + java.util.concurrent.ThreadLocalRandom.current().nextInt(999_999_999), "random").whenComplete((page, failure) -> minecraft.execute(() -> {
            featuredLoading = false;
            featuredAt = System.currentTimeMillis();
            featuredFailed = failure != null;
            if (failure == null) featured = java.util.List.copyOf(page.ads().subList(0, Math.min(12, page.ads().size())));
            if (minecraft.screen == this) rebuildWidgets();
        }));
    }

    /** The ledger numbers; cheap, but the ledger loads in the background and keeps changing while the page is open. */
    private void refresh() {
        var zone = LedgerStore.BEIJING;
        LocalDate today = LocalDate.now(zone);
        var entries = LedgerStore.enabled() ? LedgerStore.visible() : List.<LedgerEntry>of();
        var yesterday = LedgerDaily.summarize(entries, today.minusDays(1), zone);
        double income = 0, spent = 0; int count = 0;
        for (int back = 0; back < 7; back++) {
            var day = LedgerDaily.summarize(entries, today.minusDays(back), zone);
            if (day != null) { income += day.income(); spent += day.spent(); count += day.count(); }
        }
        spans = List.of(new Span("昨日", yesterday), new Span("今日", LedgerDaily.summarize(entries, today, zone)), new Span("近 7 日", count == 0 ? null : new LedgerDaily.Summary(income, spent, count, "", 0)));
    }

    @Override public void tick() { if (++refreshTicks % 40 == 0) refresh(); }

    /** Where the ad section starts: below the ledger cards. */
    private static final int ADS_TOP = 152;
    /** Height of the text rows under a card's cover: title, summary, status and place, advertiser. */
    private static final int CARD_TEXT = 62;

    private static String money(double value) { return String.format("%,.2f", value); }

    @Override protected void windowBackground(GuiGraphics g, int mouseX, int mouseY, float delta) { g.fill(0, 0, width, height, PAPER); }

    @Override protected void renderWindow(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderWindow(g, mouseX, mouseY, delta);
        var layout = ArchiveLayout.of(width);
        int left = layout.left(), w = layout.panelWidth();
        ArchiveNavigation.brand(g, left, w);
        line(g, left, 38, w);
        String name = LedgerStore.accountName();
        int y = 48;
        // The player's head to the left of the greeting and the date line.
        var user = minecraft.getUser();
        String headPath = user == null ? null : "/api/players/" + user.getProfileId().toString().replace("-", "") + "/avatar";
        int head = 26, textX = left + head + 6;
        if (!MarketImages.INSTANCE.draw(g, api, headPath, left, y - 1, head)) g.fill(left, y - 1, left + head, y - 1 + head, MarketTheme.c(0xFFE8EBE2));
        g.pose().pushMatrix(); g.pose().translate(textX, y); g.pose().scale(1.5f, 1.5f);
        g.drawString(font, "欢迎回来" + (name.isEmpty() ? "" : "，" + name), 0, 0, INK, false);
        g.pose().popMatrix();
        y += 17;
        g.drawString(font, LocalDate.now(LedgerStore.BEIJING) + (PaleTaleClient.ACCOUNT.loggedIn() ? " · 已登录官网" : " · 未登录官网，点击右上角登录"), textX, y, MUTED, false);
        y += 16;
        text(g, "记账总结", left, y, w, ACCENT);
        line(g, left, y + 11, w);
        y += 17;
        int gap = 8, cardWidth = (w - gap * 2) / 3, cardHeight = 47;
        for (int i = 0; i < spans.size(); i++) {
            int x = left + i * (cardWidth + gap);
            g.renderOutline(x, y, cardWidth, cardHeight, LINE);
            var span = spans.get(i);
            g.drawString(font, span.title(), x + 6, y + 4, INK, false);
            if (span.summary() == null) { g.drawString(font, "暂无记录", x + 6, y + 18, MUTED, false); continue; }
            var s = span.summary();
            text(g, "收入 " + money(s.income()), x + 6, y + 15, cardWidth - 12, INK);
            text(g, "支出 " + money(s.spent()), x + 6, y + 25, cardWidth - 12, INK);
            text(g, "净 " + (s.net() >= 0 ? "+" : "-") + money(Math.abs(s.net())) + " · " + s.count() + " 笔", x + 6, y + 35, cardWidth - 12, s.net() >= 0 ? ACCENT : RED());
        }
        text(g, "广场", left, ADS_TOP, w, ACCENT);
        line(g, left, ADS_TOP + 11, w);
        if (featured.isEmpty()) text(g, featuredFailed ? "暂时读不到广场里的广告" : "正在读取广场…", left, ADS_TOP + 22, w, MUTED);
    }

    /** Clicking the place of a card starts the navigation to the first address the advertiser set. */
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double mx = (event.x() - winX) / windowScale, my = (event.y() - winY) / windowScale;
            for (var child : children()) if (child instanceof MarketCardButton card && card.onPlace(mx, my)) {
                var t = card.place();
                Navigation.start(new Navigation.Target(t.name(), t.x(), t.y(), t.z(), t.kind(), t.marks()));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
