package cn.paletale.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import static cn.paletale.companion.MarketTheme.*;

/** Statistics shown inside the ledger page (no separate screen): per day, per ad product, top buyers, top items and sales speed over the entries the ledger currently shows. */
final class LedgerStatsPanel {
    private static int GREEN() { return MarketTheme.c(0xFF2F6B44); } private static int RED() { return MarketTheme.c(0xFF9E4942); }
    private enum View { DAY("按天"), AD("广告商品"), BUYER("买家排名"), ITEM("物品排名"), PACE("销售速度"); final String label; View(String label) { this.label = label; } }
    private record Row(String name, long count, double income, double spent) {}
    private final Supplier<List<LedgerEntry>> source;
    private View view = View.DAY;
    private int offset, visibleRows;
    LedgerStatsPanel(Supplier<List<LedgerEntry>> source) { this.source = source; }

    /** The view tabs, laid out from (x, y); {@code rebuild} refreshes the screen after a switch. */
    void buttons(Consumer<Button> add, int x, int y, Runnable rebuild) {
        var font = PaleFont.get();
        for (View option : View.values()) {
            int w = font.width(option.label) + 14;
            add.accept(new Action(option.label, x, y, w, 16, view == option, null, b -> { view = option; offset = 0; rebuild.run(); }));
            x += w + 3;
        }
    }
    void scroll(double vertical) { offset = Math.max(0, offset + (vertical > 0 ? -3 : vertical < 0 ? 3 : 0)); }
    /** Sales speed per product over the last 30 days (independent of the period shown elsewhere): count in 30 days, in 7 days, per day. */
    private List<Row> paceRows() {
        long now = System.currentTimeMillis(), day = 86_400_000L;
        Map<String, double[]> groups = new LinkedHashMap<>();
        for (LedgerEntry e : LedgerStore.visible()) {
            long age = now - e.time();
            if (e.kind() != LedgerEntry.Kind.SHOP_SALE || e.item().isEmpty() || age < 0 || age > 30 * day) continue;
            String key = e.adCode().isEmpty() ? e.item() : e.adTitle() + (e.productName().isBlank() ? "" : " · " + e.productName());
            double[] sums = groups.computeIfAbsent(key, k -> new double[3]); // month, week, first sale time
            long count = Math.max(1, e.quantity());
            sums[0] += count; if (age <= 7 * day) sums[1] += count;
            sums[2] = sums[2] == 0 ? e.time() : Math.min(sums[2], e.time());
        }
        List<Row> list = new ArrayList<>();
        groups.forEach((name, sums) -> list.add(new Row(name, (long) sums[0], sums[1], sums[0] / Math.max(1.0, Math.min(30.0, (now - sums[2]) / (double) day + 1)))));
        list.sort(Comparator.comparingDouble(Row::spent).reversed());
        return list;
    }

    private List<Row> rows() {
        if (view == View.PACE) return paceRows();
        Map<String, double[]> groups = new LinkedHashMap<>();
        for (LedgerEntry e : source.get()) {
            String key; boolean income = e.signed() > 0;
            switch (view) {
                case AD -> { if (e.adCode().isEmpty() || e.kind() == LedgerEntry.Kind.SOLD_OUT) continue; key = e.adTitle() + (e.productName().isBlank() ? "" : " · " + e.productName()); }
                case BUYER -> { if (e.kind() != LedgerEntry.Kind.SHOP_SALE) continue; key = e.counterparty(); }
                case ITEM -> { if (e.item().isEmpty() || !e.kind().group().equals("shop") || e.kind() == LedgerEntry.Kind.SOLD_OUT) continue; key = e.item(); }
                case PACE -> { if (e.kind() != LedgerEntry.Kind.SHOP_SALE || e.item().isEmpty()) continue; key = e.adCode().isEmpty() ? e.item() : e.adTitle() + (e.productName().isBlank() ? "" : " · " + e.productName()); }
                default -> { continue; }
            }
            double[] sums = groups.computeIfAbsent(key, k -> new double[3]);
            sums[0] += Math.max(1, e.quantity());
            if (income) sums[1] += e.amount(); else sums[2] += e.amount();
        }
        List<Row> list = new ArrayList<>();
        groups.forEach((name, sums) -> list.add(new Row(name, (long) sums[0], sums[1], sums[2])));
        list.sort(Comparator.comparingDouble((Row row) -> row.income() + row.spent()).reversed());
        return list;
    }

    void render(GuiGraphics g, int x, int y, int w, int h) {
        var font = PaleFont.get();
        if (view == View.DAY) { renderDays(g, font, x, y, w, h); return; }
        List<Row> rows = rows();
        int top = y, nameWidth = w - 210;
        visibleRows = Math.max(3, (h - 26) / 13);
        g.drawString(font, view == View.BUYER ? "买家" : view == View.AD || view == View.PACE ? "广告 · 商品" : "物品", x + 2, top, MUTED, false);
        String[] heads = view == View.PACE ? new String[]{ "近30天", "近7天", "日均" } : new String[]{ view == View.ITEM ? "件数" : "购买件数", "收入", "支出" };
        for (int i = 0; i < 3; i++) g.drawString(font, heads[i], x + w - (3 - i) * 70 + 10, top, MUTED, false);
        g.hLine(x, x + w, top + 11, LINE);
        if (rows.isEmpty()) { text(g, view == View.AD ? "还没有关联到广告的记录。" : view == View.PACE ? "近 30 天还没有商店销售记录。" : "这个范围内还没有相关记录。", x + 2, top + 20, w - 4, MUTED); return; }
        offset = Math.min(offset, Math.max(0, rows.size() - visibleRows));
        for (int i = 0; i < visibleRows && offset + i < rows.size(); i++) {
            Row row = rows.get(offset + i);
            int ry = top + 15 + i * 13;
            text(g, (offset + i + 1) + ". " + (row.name().isBlank() ? "（未知）" : row.name()), x + 2, ry, nameWidth, INK);
            g.drawString(font, String.valueOf(row.count()), x + w - 210 + 10, ry, INK, false);
            if (view == View.PACE) {
                g.drawString(font, String.valueOf((long) row.income()), x + w - 140 + 10, ry, INK, false);
                g.drawString(font, String.format("%.1f", row.spent()), x + w - 70 + 10, ry, GREEN(), false);
                continue;
            }
            g.drawString(font, String.format("%,.2f", row.income()), x + w - 140 + 10, ry, GREEN(), false);
            g.drawString(font, String.format("%,.2f", row.spent()), x + w - 70 + 10, ry, RED(), false);
        }
    }

    /** Income and spending per day (Beijing time) as paired bars, the most recent days that fit. */
    private void renderDays(GuiGraphics g, net.minecraft.client.gui.Font font, int x0, int y0, int w, int h) {
        TreeMap<LocalDate, double[]> days = new TreeMap<>();
        for (LedgerEntry e : source.get()) {
            if (e.kind() == LedgerEntry.Kind.SOLD_OUT) continue;
            LocalDate day = java.time.Instant.ofEpochMilli(e.time()).atZone(LedgerStore.BEIJING).toLocalDate();
            double[] sums = days.computeIfAbsent(day, k -> new double[2]);
            if (e.signed() >= 0) sums[0] += e.signed(); else sums[1] += -e.signed();
        }
        if (days.isEmpty()) { text(g, "这个范围内还没有收支记录。", x0 + 2, y0, w - 4, MUTED); return; }
        int slot = 30, count = Math.max(1, w / slot);
        List<Map.Entry<LocalDate, double[]>> shownDays = new ArrayList<>(days.entrySet());
        shownDays = shownDays.subList(Math.max(0, shownDays.size() - count), shownDays.size());
        double max = 1, income = 0, spent = 0;
        for (var d : shownDays) { max = Math.max(max, Math.max(d.getValue()[0], d.getValue()[1])); income += d.getValue()[0]; spent += d.getValue()[1]; }
        int baseline = y0 + h - 36, chartTop = y0 + 22, chartHeight = Math.max(20, baseline - chartTop);
        g.drawString(font, "最近 " + shownDays.size() + " 天：收入 " + String.format("%,.2f", income) + "   支出 " + String.format("%,.2f", spent) + "   净 " + String.format("%,.2f", income - spent) + "   （单日最高 " + String.format("%,.2f", max) + "）", x0 + 2, y0, MUTED, false);
        g.hLine(x0, x0 + w, baseline, LINE);
        int x = x0 + (w - shownDays.size() * slot) / 2;
        var format = java.time.format.DateTimeFormatter.ofPattern("MM-dd");
        for (var d : shownDays) {
            int incomeHeight = (int) Math.round(d.getValue()[0] / max * chartHeight), spentHeight = (int) Math.round(d.getValue()[1] / max * chartHeight);
            if (d.getValue()[0] > 0) g.fill(x + 3, baseline - Math.max(1, incomeHeight), x + 13, baseline, GREEN());
            if (d.getValue()[1] > 0) g.fill(x + 15, baseline - Math.max(1, spentHeight), x + 25, baseline, RED());
            g.drawString(font, d.getKey().format(format), x + 1, baseline + 4, MUTED, false);
            x += slot;
        }
        g.fill(x0 + 2, y0 + h - 14, x0 + 10, y0 + h - 6, GREEN()); g.drawString(font, "收入", x0 + 14, y0 + h - 14, MUTED, false);
        g.fill(x0 + 50, y0 + h - 14, x0 + 58, y0 + h - 6, RED()); g.drawString(font, "支出", x0 + 62, y0 + h - 14, MUTED, false);
    }
}
