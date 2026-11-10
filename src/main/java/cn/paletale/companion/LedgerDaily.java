package cn.paletale.companion;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The small summary of one day of the ledger: income, spending, how many records, and the item sold most. */
public final class LedgerDaily {
    public record Summary(double income, double spent, int count, String topItem, long topQuantity) {
        public double net() { return income - spent; }
    }
    private LedgerDaily() {}

    /** Null when the day has no money records. {@code zone} is the server's (Beijing time). */
    public static Summary summarize(List<LedgerEntry> entries, LocalDate day, ZoneId zone) {
        double income = 0, spent = 0; int count = 0;
        Map<String, Long> sold = new HashMap<>();
        for (LedgerEntry entry : entries) {
            if (entry.kind() == LedgerEntry.Kind.SOLD_OUT || !Instant.ofEpochMilli(entry.time()).atZone(zone).toLocalDate().equals(day)) continue;
            count++;
            if (entry.signed() >= 0) income += entry.signed(); else spent += -entry.signed();
            if (entry.kind() == LedgerEntry.Kind.SHOP_SALE && !entry.item().isEmpty()) sold.merge(entry.item(), (long) Math.max(1, entry.quantity()), Long::sum);
        }
        if (count == 0) return null;
        String top = ""; long best = 0;
        for (var item : sold.entrySet()) if (item.getValue() > best || item.getValue() == best && item.getKey().compareTo(top) < 0) { top = item.getKey(); best = item.getValue(); }
        return new Summary(income, spent, count, top, best);
    }
}
