package cn.paletale.companion;

import java.util.List;

/** How fast an item or ad product sells, counted from the player's own sales in the ledger. */
public final class LedgerSales {
    private static final long DAY = 86_400_000L;
    public record Pace(long week, long month, double perDay) {
        /** "近 7 天卖出 21 件，近 30 天 60 件，日均约 3 件" */
        public String describe() { return "近 7 天卖出 " + week + " 件，近 30 天 " + month + " 件，日均约 " + (perDay >= 10 ? String.valueOf(Math.round(perDay)) : String.format("%.1f", perDay)) + " 件"; }
    }
    private LedgerSales() {}

    /** Pace of the product a ledger entry belongs to (over the given ledger entries): the same linked ad product, else the same item name. Null when nothing was ever sold. */
    public static Pace of(List<LedgerEntry> all, LedgerEntry entry, long now) {
        long week = 0, month = 0; long first = now;
        for (LedgerEntry other : all) {
            if (other.kind() != LedgerEntry.Kind.SHOP_SALE || !sameProduct(entry, other)) continue;
            long age = now - other.time();
            if (age < 0 || age > 30 * DAY) continue;
            long count = Math.max(1, other.quantity());
            month += count; first = Math.min(first, other.time());
            if (age <= 7 * DAY) week += count;
        }
        if (month == 0) return null;
        // A product only a few days old is judged over those days, not diluted over a month.
        double days = Math.max(1.0, Math.min(30.0, (now - first) / (double) DAY + 1));
        return new Pace(week, month, month / days);
    }
    private static boolean sameProduct(LedgerEntry a, LedgerEntry b) {
        if (!a.adCode().isEmpty() && a.adCode().equals(b.adCode()) && a.productName().equals(b.productName())) return true;
        return !a.item().isEmpty() && a.item().equalsIgnoreCase(b.item());
    }
}
