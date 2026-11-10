package cn.paletale.companion;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static cn.paletale.companion.MarketTheme.*;

/**
 * 账本: shop sales, transfers and tips seen in this player's game, kept only on this computer. Sales of an ad's product link to that ad.
 * Newest first; the mouse wheel scrolls. Search, a rolling time window (24 h / 3 days / 1 week / 1 month) and a selection mode for deleting entries.
 */
public final class LedgerScreen extends WindowScreen {
    private static final int ROW = 13;
    private static int GREEN() { return MarketTheme.c(0xFF2F6B44); } private static int RED() { return MarketTheme.c(0xFF9E4942); }
    private static final String[] RANGE_LABELS = { "全部时间", "近 24 小时", "近 3 天", "近 1 周", "近 1 个月" };
    private static final String[] RANGE_SHORT = { "全部", "24 小时", "3 天", "1 周", "1 个月" };
    private static final long[] RANGE_MS = { 0L, 86_400_000L, 3 * 86_400_000L, 7 * 86_400_000L, 30 * 86_400_000L };
    private final Screen parent;
    private final MarketApi media;
    private String group = "", query = "";
    private int officialFilter, flow;
    private static final String[] OFFICIAL_LABELS = { "全部", "农作物", "矿物", "钓鱼", "其他" };
    private static final LedgerEntry.Kind[] OFFICIAL_KINDS = { null, LedgerEntry.Kind.OFFICIAL_CROP, LedgerEntry.Kind.OFFICIAL_MINERAL, LedgerEntry.Kind.OFFICIAL_FISH, LedgerEntry.Kind.OFFICIAL_OTHER };
    private int range; // index into RANGE_LABELS
    private int offset, left, panelWidth, sidebar, contentX, contentWidth, visible;
    private boolean selecting, stats, editFilters;
    private final OfficialQuotaPanel quotaPanel = new OfficialQuotaPanel();
    private boolean quotaVisible() { return group.equals("official") && !stats; }
    private int quotaHeight() { return Math.min(128, Math.max(88, (height - 120) / 2)); }
    private int quotaTop() { return height - 38 - quotaHeight(); }
    private int listRows() { return Math.max(1, ((quotaVisible() ? quotaTop() - 4 : height - 40) - 98) / ROW); }
    private final LedgerStatsPanel statsPanel = new LedgerStatsPanel(() -> this.filtered);
    private Action account, prevPage, nextPage;
    private final Set<String> selected = new HashSet<>();
    private final Map<String, Set<String>> selectedMembers = new LinkedHashMap<>();
    private String notice = "";
    private EditBox searchBox, fromBox, toBox, minBox, maxBox;
    private int sideY;
    private String minText = "", maxText = "";
    private Double amountMin, amountMax;
    private java.time.LocalDate dateFrom, dateTo;
    private final List<int[]> linkHits = new ArrayList<>();   // x, y, w, h, kind (0 open ad, 1 cycle), row index
    private List<LedgerEntry> shown = List.of(), filtered = List.of();
    private long revision = -1;
    private Map<String, List<LedgerEntry>> dailyMembers = Map.of();

    public LedgerScreen(Screen parent, MarketApi media) { super(Component.literal("账本")); this.parent = parent; this.media = media; restoreView(); }
    private static final java.util.Set<String> GROUPS = java.util.Set.of("", "official", "shop", "ad", "player", "bank");
    private void restoreView() {
        String[] saved = ModSettings.ledgerView().split("\\|", -1);
        if (saved.length != 5) return;
        try {
            int savedFlow = Integer.parseInt(saved[1]), savedRange = Integer.parseInt(saved[2]), savedOfficial = Integer.parseInt(saved[3]);
            if (!GROUPS.contains(saved[0]) || savedFlow < 0 || savedFlow > 2 || savedRange < 0 || savedRange >= RANGE_LABELS.length || savedOfficial < 0 || savedOfficial >= OFFICIAL_LABELS.length) return;
            group = saved[0]; flow = savedFlow; range = savedRange; officialFilter = savedOfficial; stats = Boolean.parseBoolean(saved[4]);
            quotaPanel.category(officialFilter);
        } catch (NumberFormatException ignored) {}
    }
    private void saveView() { ModSettings.setLedgerView(group + "|" + flow + "|" + range + "|" + officialFilter + "|" + stats); }

    @Override protected void init() {
        fitWindow();
        panelWidth = Math.min(1120, width - 16); left = (width - panelWidth) / 2;
        sidebar = ArchiveLayout.of(width).sidebar(); contentX = left + sidebar + 14; contentWidth = panelWidth - sidebar - 14;
        ArchiveNavigation.tabs(this::addRenderableWidget, parent, media, ArchiveNavigation.Tab.LEDGER, left, panelWidth);
        int accountWidth = panelWidth < 600 ? 88 : 96;
        account = addRenderableWidget(new Action(PaleTaleClient.ACCOUNT.label(), left + panelWidth - accountWidth, 13, accountWidth, 18, false, "person", b -> PaleTaleClient.ACCOUNT.checkStatus(minecraft)));
        account.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("验证 SIMMC 登录状态\n需先使用正版账号进入 play.simmc.cn")));
        searchBox = addRenderableWidget(new EditBox(font, left + 4, 67, sidebar - 8, 10, Component.literal("搜索账本")));
        searchBox.setMaxLength(60); searchBox.setValue(query); searchBox.setBordered(false); searchBox.setTextShadow(false); searchBox.setTextColor(INK);
        searchBox.setResponder(value -> { query = value; offset = 0; compute(); });
        fromBox = toBox = minBox = maxBox = null;
        boolean compact = height < 320; int groupStep = compact ? 11 : 15;
        String[][] groups = { { "", "全部" }, { "official", "官方" }, { "shop", "商店交易" }, { "ad", "广告交易" }, { "player", "玩家" }, { "bank", "领地存取" } };
        for (int i = 0; i < groups.length && (!compact || !editFilters); i++) {
            String value = groups[i][0];
            addRenderableWidget(new Action(groups[i][1], left, 86 + i * groupStep, sidebar, compact ? 11 : 14, group.equals(value), null, b -> { group = value; offset = 0; rebuildWidgets(); }));
        }
        addRenderableWidget(new Action("导入日志", left, height - 52, sidebar, 17, false, null, b -> minecraft.setScreen(new LedgerImportScreen(this))));
        addRenderableWidget(new Action("清空账本", left, height - 34, sidebar, 17, false, null, b -> minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes -> {
            if (yes) { LedgerStore.clear(); selected.clear(); selectedMembers.clear(); offset = 0; notice = "账本已清空。"; }
            minecraft.setScreen(this);
        }, Component.literal("清空账本"), Component.literal("本机上的全部账本记录都会被删除，无法恢复。确定清空吗？")))));
        // Top right of the list: selection mode.
        int x = left + panelWidth;
        if (selecting) {
            int doneWidth = font.width("完成") + 14, deleteWidth = font.width("删除所选（" + selected.size() + "）") + 14, allWidth = font.width("全选") + 14;
            x -= doneWidth; addRenderableWidget(new Action("完成", x, 44, doneWidth, 17, false, null, b -> { selecting = false; selected.clear(); selectedMembers.clear(); rebuildWidgets(); }));
            x -= deleteWidth + 4;
            var delete = addRenderableWidget(new Action("删除所选（" + selected.size() + "）", x, 44, deleteWidth, 17, !selected.isEmpty(), null, b -> confirmDelete()));
            delete.active = !selected.isEmpty();
            x -= allWidth + 4; addRenderableWidget(new Action("全选", x, 44, allWidth, 17, false, null, b -> { shown.forEach(this::selectEntry); rebuildWidgets(); }));
        } else {
            int selectWidth = font.width("选择") + 14, settingsWidth = font.width("设置") + 14;
            if (!stats) { x -= selectWidth; addRenderableWidget(new Action("选择", x, 44, selectWidth, 17, false, null, b -> { selecting = true; rebuildWidgets(); })); x -= 4; }
            x -= settingsWidth; addRenderableWidget(new Action("设置", x, 44, settingsWidth, 17, false, null, b -> minecraft.setScreen(new LedgerSettingsScreen(this))));
        }
        // The time window: five buttons above the list.
        int rangeX = contentX;
        for (int i = 0; i < RANGE_LABELS.length; i++) {
            int index = i; String label = RANGE_SHORT[i];
            int w = font.width(label) + 14;
            addRenderableWidget(new Action(label, rangeX, 62, w, 16, range == index && dateFrom == null && dateTo == null, null, b -> { range = index; dateFrom = null; dateTo = null; offset = 0; fromText = ""; toText = ""; rebuildWidgets(); }));
            rangeX += w + 3;
        }
        if (group.equals("official")) addRenderableWidget(new Action(OFFICIAL_LABELS[officialFilter], rangeX, 62, 70, 16, true, null, b -> { officialFilter = (officialFilter + 1) % OFFICIAL_LABELS.length; quotaPanel.category(officialFilter); offset = 0; rebuildWidgets(); }));
        // Date range and amount range live in the sidebar (yyyy-MM-dd, either end may stay empty; a filled date replaces the rolling window).
        if (!compact || !editFilters) addRenderableWidget(new Action("统计", left, 86 + groups.length * groupStep, sidebar, compact ? 11 : 14, stats, null, b -> { stats = !stats; selecting = false; selected.clear(); selectedMembers.clear(); offset = 0; rebuildWidgets(); }));
        // In a short window the date/amount button must end above the bottom pair (导入日志, 清空账本), which starts at height - 52.
        int flowY = compact && editFilters ? 173 : 86 + (groups.length + 1) * groupStep + (compact ? 0 : 4);
        String[] flows = { "收支", "收入", "支出" };
        int flowWidth = sidebar / 3;
        for (int i = 0; i < flows.length; i++) {
            int choice = i;
            var button = addRenderableWidget(new FlowButton(flows[i], left + i * flowWidth, flowY, i == 2 ? sidebar - 2 * flowWidth : flowWidth, flow == i, b -> { flow = choice; offset = 0; rebuildWidgets(); }));
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(i == 0 ? "全部收支" : i == 1 ? "仅显示收入" : "仅显示支出")));
        }
        if (compact) addRenderableWidget(new Action(editFilters ? "返回分类" : "日期/金额", left, editFilters ? 86 : flowY + 13, sidebar, 12, editFilters, null, b -> { editFilters = !editFilters; rebuildWidgets(); }));
        sideY = compact ? 106 : flowY + 17;
        if (!compact || editFilters) {
        int boxWidth = sidebar - 8, half = (sidebar - 12) / 2;
        fromBox = addRenderableWidget(new EditBox(font, left + 4, sideY + 4, boxWidth, 10, Component.literal("开始日期")));
        fromBox.setMaxLength(10); fromBox.setBordered(false); fromBox.setTextShadow(false); fromBox.setValue(fromText);
        fromBox.setResponder(value -> { fromText = value; dateFrom = parseDate(value); fromBox.setTextColor(value.isBlank() || dateFrom != null ? INK : RED()); offset = 0; compute(); });
        toBox = addRenderableWidget(new EditBox(font, left + 4, sideY + 24, boxWidth, 10, Component.literal("结束日期")));
        toBox.setMaxLength(10); toBox.setBordered(false); toBox.setTextShadow(false); toBox.setValue(toText);
        toBox.setResponder(value -> { toText = value; dateTo = parseDate(value); toBox.setTextColor(value.isBlank() || dateTo != null ? INK : RED()); offset = 0; compute(); });
        fromBox.setTextColor(fromText.isBlank() || dateFrom != null ? INK : RED()); toBox.setTextColor(toText.isBlank() || dateTo != null ? INK : RED());
        minBox = addRenderableWidget(new EditBox(font, left + 4, sideY + 44, half, 10, Component.literal("最小金额")));
        minBox.setMaxLength(12); minBox.setBordered(false); minBox.setTextShadow(false); minBox.setValue(minText);
        minBox.setResponder(value -> { minText = value; amountMin = parseAmount(value); minBox.setTextColor(value.isBlank() || amountMin != null ? INK : RED()); offset = 0; compute(); });
        maxBox = addRenderableWidget(new EditBox(font, left + 8 + half + 4, sideY + 44, half, 10, Component.literal("最大金额")));
        maxBox.setMaxLength(12); maxBox.setBordered(false); maxBox.setTextShadow(false); maxBox.setValue(maxText);
        maxBox.setResponder(value -> { maxText = value; amountMax = parseAmount(value); maxBox.setTextColor(value.isBlank() || amountMax != null ? INK : RED()); offset = 0; compute(); });
        minBox.setTextColor(minText.isBlank() || amountMin != null ? INK : RED()); maxBox.setTextColor(maxText.isBlank() || amountMax != null ? INK : RED());
        }
        if (stats) statsPanel.buttons(this::addRenderableWidget, contentX, 85, this::rebuildWidgets);
        if (quotaVisible()) quotaPanel.buttons(this::addRenderableWidget, contentX, quotaTop(), contentWidth, quotaHeight(), this::rebuildWidgets);
        visible = listRows();
        compute();
        saveView();
        offset = Math.min(offset / visible * visible, Math.max(0, (shown.size() - 1) / visible * visible));
        // The list is paged, never scrolled: a ledger is long and scrolling it by wheel loses your place.
        prevPage = nextPage = null;
        if (!stats) {
            prevPage = addRenderableWidget(new Action("上一页", contentX, height - 22, 52, 16, false, null, b -> offset = Math.max(0, offset - visible)));
            nextPage = addRenderableWidget(new Action("下一页", contentX + contentWidth - 52, height - 22, 52, 16, false, null, b -> { if (offset + visible < shown.size()) offset += visible; }));
        }
    }

    private void selectEntry(LedgerEntry entry) {
        selected.add(entry.id());
        selectedMembers.put(entry.id(), dailyMembers.getOrDefault(entry.id(), List.of(entry)).stream().map(LedgerEntry::id).collect(java.util.stream.Collectors.toSet()));
    }
    private void confirmDelete() {
        int count = selected.size();
        var ids = new HashSet<String>(); for (String id : selected) ids.addAll(selectedMembers.getOrDefault(id, Set.of()));
        minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes -> {
            if (yes) { LedgerStore.remove(ids); selected.clear(); selectedMembers.clear(); notice = "已删除 " + count + " 条记录。"; }
            minecraft.setScreen(this);
        }, Component.literal("删除记录"), Component.literal("将删除选中的 " + count + " 条账本记录，包含 " + ids.size() + " 笔原始交易，无法恢复。确定吗？")));
    }

    private String fromText = "", toText = "";
    private static Double parseAmount(String text) {
        String value = text.strip().replace(",", "");
        if (value.isEmpty()) return null;
        try { double number = Double.parseDouble(value); return number >= 0 ? number : null; } catch (NumberFormatException invalid) { return null; }
    }
    private static java.time.LocalDate parseDate(String text) {
        String value = text.strip().replace('/', '-').replace('.', '-');
        if (value.isEmpty()) return null;
        try {
            String[] parts = value.split("-");
            if (parts.length == 3) return java.time.LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            if (parts.length == 2) return java.time.LocalDate.of(java.time.LocalDate.now(LedgerStore.BEIJING).getYear(), Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (RuntimeException invalid) { /* shown in red */ }
        return null;
    }
    private boolean customDates() { return dateFrom != null || dateTo != null; }
    private String periodLabel() {
        if (!customDates()) return RANGE_LABELS[range];
        return (dateFrom == null ? "起" : dateFrom.toString()) + " 至 " + (dateTo == null ? "今" : dateTo.toString());
    }

    private void compute() {
        long since = RANGE_MS[range] == 0 ? 0L : System.currentTimeMillis() - RANGE_MS[range], until = Long.MAX_VALUE;
        if (customDates()) {
            since = dateFrom == null ? 0L : dateFrom.atStartOfDay(LedgerStore.BEIJING).toInstant().toEpochMilli();
            until = dateTo == null ? Long.MAX_VALUE : dateTo.plusDays(1).atStartOfDay(LedgerStore.BEIJING).toInstant().toEpochMilli() - 1;
        }
        String needle = query.strip().toLowerCase();
        List<LedgerEntry> list = new ArrayList<>();
        var all = LedgerStore.visible();
        for (int i = all.size() - 1; i >= 0; i--) {
            LedgerEntry entry = all.get(i);
            if (!entry.matchesFlow(flow)) continue;
            if (entry.time() < since || entry.time() > until) continue;
            if ((amountMin != null || amountMax != null) && (entry.kind() == LedgerEntry.Kind.SOLD_OUT || amountMin != null && entry.amount() < amountMin || amountMax != null && entry.amount() > amountMax)) continue;
            if (group.equals("ad") ? entry.adCode().isEmpty() : !group.isEmpty() && !entry.kind().group().equals(group)) continue;
            if (group.equals("official") && officialFilter > 0 && entry.kind() != OFFICIAL_KINDS[officialFilter]) continue;
            if (!needle.isEmpty() && !searchable(entry).contains(needle)) continue;
            list.add(entry);
        }
        filtered = List.copyOf(list); revision = LedgerStore.revision(); var daily = LedgerOfficialDaily.group(list); shown = daily.rows(); dailyMembers = daily.members();
    }
    private static String searchable(LedgerEntry e) {
        return (e.kind().label + " " + e.counterparty() + " " + e.item() + " " + e.adTitle() + " " + e.adCode() + " " + e.productName() + " " + String.format("%.2f", e.amount())).toLowerCase();
    }
    private static String money(double value) { return String.format("%,.2f", value) + " 云币"; }

    @Override public void tick() { if (revision != LedgerStore.revision()) compute(); }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (stats) statsPanel.scroll(vertical);
        return true;
    }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mx = (event.x() - winX)/windowScale, my = (event.y() - winY)/windowScale;
        if (stats) return super.mouseClicked(event, doubleClick);
        if (event.button() == 0 && selecting && mx >= contentX && mx < contentX + contentWidth && my >= rowTop && my < rowTop + visible * ROW) {
            int row = offset + (int) ((my - rowTop) / ROW);
            if (row < shown.size()) { String id = shown.get(row).id(); if (!selected.remove(id)) selectEntry(shown.get(row)); else selectedMembers.remove(id); rebuildWidgets(); return true; }
        }
        if (event.button() == 0 && !selecting) for (int[] hit : linkHits) {
            if (mx >= hit[0] && mx < hit[0] + hit[2] && my >= hit[1] && my < hit[1] + hit[3]) {
                LedgerEntry entry = shown.get(hit[5]);
                if (hit[4] == 0) openAd(entry.adCode()); else { LedgerStore.cycleAd(entry.id()); compute(); }
                return true;
            }
        }
        // Any other click on a row opens its details.
        if (event.button() == 0 && !selecting && mx >= contentX && mx < contentX + contentWidth && my >= rowTop && my < rowTop + visible * ROW) {
            int row = offset + (int) ((my - rowTop) / ROW);
            if (row < shown.size()) { var entry = shown.get(row); minecraft.setScreen(LedgerOfficialDaily.summarized(entry) ? new LedgerDailyScreen(this, entry, dailyMembers.get(entry.id())) : new LedgerDetailScreen(this, entry)); return true; }
        }
        return super.mouseClicked(event, doubleClick);
    }
    /** Opens the ad on top of this page, so Esc comes back here. */
    private void openAd(String code) {
        notice = "正在打开广告…";
        media.search(code, java.util.EnumSet.noneOf(MarketData.Category.class), MarketData.AdState.ALL, 1).whenComplete((page, failure) -> minecraft.execute(() -> {
            if (minecraft.screen != this) return;
            var found = failure == null ? page.ads().stream().filter(ad -> ad.code().equals(code)).findFirst() : java.util.Optional.<MarketData.Ad>empty();
            if (found.isPresent()) { notice = ""; minecraft.setScreen(new MarketDetailScreen(this, null, media, found.get())); }
            else notice = "暂时无法打开这则广告，请稍后重试。";
        }));
    }

    @Override protected void windowBackground(GuiGraphics g, int x, int y, float delta) {
        g.fill(0, 0, width, height, PAPER); g.fill(left, 42, left + sidebar, height - 38, MarketTheme.c(0xFFF0F1EA));
        line(g, left, 38, panelWidth); g.vLine(contentX - 7, 44, height - 37, LINE); g.renderOutline(left, 62, sidebar, 20, LINE);
    }
    private int pointerX = -1, pointerY = -1;
    @Override protected void renderWindow(GuiGraphics g, int mouseX, int mouseY, float delta) {
        pointerX = mouseX; pointerY = mouseY;
        account.setMessage(Component.literal(PaleTaleClient.ACCOUNT.label()));
        account.setAvatar(media, ArchiveNavigation.avatarOf(PaleTaleClient.ACCOUNT.citizen()));
        account.active = !PaleTaleClient.ACCOUNT.busy() && (PaleTaleClient.ACCOUNT.loggedIn() || PaleTaleClient.ACCOUNT.canLogin(minecraft));
        super.renderWindow(g, mouseX, mouseY, delta);
        ArchiveNavigation.brand(g, left, panelWidth);
        text(g, "账本（本地）", left + 4, 47, sidebar - 8, INK);
        if (searchBox != null) hint(g, searchBox, "搜索账本");
        if (!notice.isEmpty()) text(g, notice, contentX + 2, height - 36, contentWidth - 4, MUTED);
        // Summary of what is shown: one line per currency (excluding out-of-stock notices).
        Map<String, double[]> totals = new LinkedHashMap<>();
        for (LedgerEntry entry : shown) {
            if (entry.kind() == LedgerEntry.Kind.SOLD_OUT) continue;
            double[] sums = totals.computeIfAbsent(LedgerParser.MONEY_NAME, key -> new double[2]);
            if (entry.signed() >= 0) sums[0] += entry.signed(); else sums[1] += -entry.signed();
        }
        String title = periodLabel() + " · 共 " + shown.size() + " 条";
        g.drawString(font, title, contentX + 2, 47, INK, false);
        if (fromBox != null) {
        for (EditBox box : new EditBox[]{ fromBox, toBox, minBox, maxBox }) g.renderOutline(box.getX() - 2, box.getY() - 4, box.getWidth() + 4, 16, LINE);
        if (fromBox.getValue().isEmpty()) g.drawString(font, "开始日期", fromBox.getX() + 1, fromBox.getY(), MarketTheme.c(0xFFB5B8AE), false);
        if (toBox.getValue().isEmpty()) g.drawString(font, "结束日期", toBox.getX() + 1, toBox.getY(), MarketTheme.c(0xFFB5B8AE), false);
        if (minBox.getValue().isEmpty()) g.drawString(font, "最小", minBox.getX() + 1, minBox.getY(), MarketTheme.c(0xFFB5B8AE), false);
        if (maxBox.getValue().isEmpty()) g.drawString(font, "最大", maxBox.getX() + 1, maxBox.getY(), MarketTheme.c(0xFFB5B8AE), false);
        }
        // Totals sit on the title row, after the count.
        int sx = contentX + 2 + font.width(title) + 18;
        double income = 0, spent = 0;
        for (LedgerEntry entry : shown) {
            if (entry.kind() == LedgerEntry.Kind.SOLD_OUT) continue;
            if (entry.signed() >= 0) income += entry.signed(); else spent += -entry.signed();
        }
        double net = income - spent;
        String incomeText = "+" + String.format("%,.2f", income), spentText = "-" + String.format("%,.2f", spent), netText = (net >= 0 ? "+" : "-") + String.format("%,.2f", Math.abs(net));
        int summaryWidth = font.width("收入 " + incomeText + "  支出 " + spentText + "  净 " + netText + " 云币");
        if (sx + summaryWidth < contentX + contentWidth - 130) {
            g.drawString(font, "收入 ", sx, 47, MUTED, false); sx += font.width("收入 ");
            g.drawString(font, incomeText, sx, 47, GREEN(), false); sx += font.width(incomeText) + 12;
            g.drawString(font, "支出 ", sx, 47, MUTED, false); sx += font.width("支出 ");
            g.drawString(font, spentText, sx, 47, RED(), false); sx += font.width(spentText) + 12;
            g.drawString(font, "净 ", sx, 47, MUTED, false); sx += font.width("净 ");
            g.drawString(font, netText + " 云币", sx, 47, net >= 0 ? GREEN() : RED(), false);
        }
        if (prevPage != null) {
            int pages = Math.max(1, (shown.size() + visible - 1) / visible), page = Math.min(pages, offset / visible + 1);
            prevPage.active = offset > 0; nextPage.active = offset + visible < shown.size();
            String pageLabel = "第 " + page + " / " + pages + " 页 · 共 " + shown.size() + " 条";
            g.drawString(font, pageLabel, contentX + (contentWidth - font.width(pageLabel)) / 2, height - 19, MUTED, false);
        }
        if (stats) { statsPanel.render(g, contentX, 108, contentWidth, height - 108 - 10); return; }
        if (quotaVisible()) quotaPanel.render(g, contentX, quotaTop(), contentWidth, quotaHeight());
        int top = 85;
        linkHits.clear();
        if (shown.isEmpty()) {
            text(g, LedgerStore.visible().isEmpty() ? "还没有记录。在游戏里的商店出售、转账、打赏都会自动记在这里。" : "没有符合条件的记录。", contentX + 8, top + 6, contentWidth - 16, MUTED);
            return;
        }
        // Column header, then one column per field: time (Beijing), kind, counterparty, item, quantity, unit price, amount, ad.
        int check = selecting ? 16 : 0, base0 = contentX + check;
        int timeW = Math.max(font.width("00-00 00:00") + 8, font.width("时间（北京）") + 6), kindW = font.width("商店") + 10, qtyW = 34, amountW = 66;
        int priceW = contentWidth - check >= 340 ? 54 : 0;
        int flexible = contentWidth - check - timeW - kindW - qtyW - priceW - amountW - 8;
        int adW = flexible >= 250 ? flexible * 34 / 100 : 0, partyW = Math.max(30, (flexible - adW) / 2), itemW = Math.max(30, flexible - adW - partyW);
        int xTime = base0 + 2, xKind = xTime + timeW, xParty = xKind + kindW, xItem = xParty + partyW, xQty = xItem + itemW, xPrice = xQty + qtyW, xAmount = xPrice + priceW + amountW, xAd = xAmount + 8;
        g.drawString(font, "时间（北京）", xTime, top, MUTED, false); g.drawString(font, "类型", xKind, top, MUTED, false); g.drawString(font, "目标", xParty, top, MUTED, false);
        g.drawString(font, "详情", xItem, top, MUTED, false); g.drawString(font, "数量", xQty, top, MUTED, false); if (priceW > 0) g.drawString(font, "单价", xPrice, top, MUTED, false);
        g.drawString(font, "金额", xAmount - font.width("金额"), top, MUTED, false);
        if (adW > 0) g.drawString(font, "广告", xAd, top, MUTED, false);
        g.hLine(contentX, contentX + contentWidth, top + 11, LINE);
        top += 13;
        rowTop = top;
        visible = listRows();
        var format = java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm");
        for (int i = 0; i < visible && offset + i < shown.size(); i++) {
            LedgerEntry entry = shown.get(offset + i);
            int y = top + i * ROW, ty = y + 2, row = offset + i;
            g.hLine(contentX, contentX + contentWidth, y + ROW - 1, LINE);
            if (selecting) {
                g.renderOutline(contentX + 2, y + 2, 9, 9, LINE);
                if (selected.contains(entry.id())) g.fill(contentX + 4, y + 4, contentX + 9, y + 9, INK);
            }
            g.drawString(font, (LedgerOfficialDaily.summarized(entry) ? LedgerOfficial.day(entry.time()).toString().substring(5) + " 汇总" : LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(entry.time()), LedgerStore.BEIJING).format(format)), xTime, ty, MUTED, false);
            int tone = entry.kind().sign > 0 ? GREEN() : entry.kind().sign < 0 ? RED() : MUTED;
            g.drawString(font, shortKind(entry.kind()), xKind, ty, tone, false);
            boolean soldOut = entry.kind() == LedgerEntry.Kind.SOLD_OUT, shopTrade = (entry.kind().group().equals("shop") || entry.kind().group().equals("official")) && !soldOut;
            String party = soldOut ? entry.x() + "," + entry.y() + "," + entry.z() : entry.counterparty().isEmpty() ? "—" : entry.counterparty();
            text(g, party, xParty, ty, partyW - 8, soldOut || entry.counterparty().isEmpty() ? MUTED : INK);
            boolean cropIcon = entry.kind() == LedgerEntry.Kind.OFFICIAL_CROP && OfficialCropIcons.draw(g, entry.item(), xItem, ty - 1, 11);
            text(g, entry.item().isEmpty() ? "—" : entry.item(), xItem + (cropIcon ? 13 : 0), ty, itemW - 8 - (cropIcon ? 13 : 0), entry.item().isEmpty() ? MUTED : INK);
            g.drawString(font, shopTrade && entry.quantity() > 0 ? "×" + entry.quantity() : "—", xQty, ty, shopTrade ? INK : MUTED, false);
            if (priceW > 0) g.drawString(font, shopTrade && entry.quantity() > 0 ? String.format("%,.2f", entry.unitPrice()) : "—", xPrice, ty, shopTrade ? INK : MUTED, false);
            String amount = soldOut ? "—" : (entry.signed() >= 0 ? "+" : "-") + String.format("%,.2f", Math.abs(entry.amount()));
            g.drawString(font, amount, xAmount - font.width(amount), ty, soldOut ? MUTED : tone, false);
            if (adW <= 0) continue;
            // The ad this belongs to (click to open); a small switch when several of the player's products trade this item.
            int candidates = LedgerStore.candidates(entry).size();
            if (!entry.adCode().isEmpty()) {
                String label = entry.adTitle() + (entry.productName().isBlank() ? "" : " · " + entry.productName());
                int labelWidth = adW - (candidates > 1 ? 24 : 4);
                text(g, label, xAd, ty, labelWidth, ACCENT);
                linkHits.add(new int[]{ xAd, y + 1, Math.min(font.width(label), labelWidth), 11, 0, row });
                if (over(pointerX, pointerY, xAd, y + 1, Math.min(font.width(label), labelWidth), 11)) hoverMark(g, xAd, y + 1, Math.min(font.width(label), labelWidth), 10);
                if (candidates > 1) {
                    boolean swapHot = over(pointerX, pointerY, xAd + adW - 16, y + 1, 14, 11);
                    if (swapHot) hoverMark(g, xAd + adW - 16, y + 1, 14, 10);
                    g.drawString(font, "换", xAd + adW - 14, ty, swapHot ? ACCENT : MUTED, false); linkHits.add(new int[]{ xAd + adW - 16, y + 1, 14, 11, 1, row });
                }
            } else if (candidates > 1) {
                boolean pickHot = over(pointerX, pointerY, xAd, y + 1, font.width("选择广告"), 11);
                if (pickHot) hoverMark(g, xAd, y + 1, font.width("选择广告"), 10);
                g.drawString(font, "选择广告", xAd, ty, pickHot ? ACCENT : MUTED, false);
                linkHits.add(new int[]{ xAd, y + 1, font.width("选择广告"), 11, 1, row });
            } else if (!LedgerStore.separateAccounts() && LedgerStore.accountCount() > 1) {
                text(g, entry.accountName(), xAd, ty, adW - 4, MUTED);
            }
        }
    }
    /** Two-character kind for the list; the colour and the sign of the amount say which way the money went. */
    private static String shortKind(LedgerEntry.Kind kind) {
        return switch (kind.group()) { case "shop" -> kind == LedgerEntry.Kind.SOLD_OUT ? "脱销" : "商店"; case "player" -> switch (kind) { case TRANSFER_IN, TRANSFER_OUT -> "转账"; case TIP_IN, TIP_OUT -> "打赏"; default -> "红包"; }; case "official" -> switch (kind) { case OFFICIAL_CROP -> "农作"; case OFFICIAL_MINERAL -> "矿物"; case OFFICIAL_FISH -> "钓鱼"; case OFFICIAL_PURCHASE -> "官购"; default -> "官方"; }; default -> "领地"; };
    }
    /** The narrow sidebar needs centred two-character labels, without Action's icon padding. */
    private static final class FlowButton extends net.minecraft.client.gui.components.Button {
        private final boolean selected;
        FlowButton(String label, int x, int y, int w, boolean selected, OnPress press) {
            super(x, y, w, 13, Component.literal(label), press, DEFAULT_NARRATION); this.selected = selected;
        }
        @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), selected ? MarketTheme.c(0xFFE1E5DB) : isHoveredOrFocused() ? MarketTheme.c(0xFFEAECE5) : PAPER);
            if (selected || isFocused()) g.renderOutline(getX(), getY(), getWidth(), getHeight(), LINE);
            var font = PaleFont.get();
            g.drawString(font, getMessage(), getX() + (getWidth() - font.width(getMessage())) / 2, getY() + 2, INK, false);
        }
    }
    private int rowTop = 66;
    /** 「 · 单价 0.95」 for shop trades. */
    private static String price(LedgerEntry entry) { return entry.quantity() > 0 ? " · 单价 " + String.format("%,.2f", entry.unitPrice()) : ""; }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
