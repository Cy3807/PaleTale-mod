package cn.paletale.companion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A view over receipts, never a destructive rewrite of the local ledger or quota source. */
public final class LedgerOfficialDaily {
    private LedgerOfficialDaily() {}
    public record View(List<LedgerEntry> rows, Map<String, List<LedgerEntry>> members) {}
    public static boolean summarized(LedgerEntry e) { return e.id().startsWith("daily:"); }
    public static View group(List<LedgerEntry> filtered) {
        Map<String, List<LedgerEntry>> members = new LinkedHashMap<>();
        for (var e : filtered) {
            boolean daily = e.kind() == LedgerEntry.Kind.OFFICIAL_CROP || e.kind() == LedgerEntry.Kind.OFFICIAL_MINERAL || e.kind() == LedgerEntry.Kind.OFFICIAL_FISH;
            String key = daily ? "daily:" + e.account() + ":" + LedgerOfficial.day(e.time()) + ":" + e.kind() + ":" + e.currency() : e.id();
            members.computeIfAbsent(key, ignored -> new ArrayList<>()).add(e);
        }
        List<LedgerEntry> rows = new ArrayList<>();
        for (var pair : members.entrySet()) {
            var list = pair.getValue(); var first = list.getFirst();
            if (!pair.getKey().startsWith("daily:")) { rows.add(first); continue; }
            String label = switch (first.kind()) { case OFFICIAL_CROP -> "卖菜"; case OFFICIAL_MINERAL -> "矿物"; default -> "钓鱼"; };
            rows.add(new LedgerEntry(pair.getKey(), list.stream().mapToLong(LedgerEntry::time).max().orElseThrow(), first.kind(), first.currency(),
                    list.stream().mapToDouble(LedgerEntry::amount).sum(), "官方", label + "收入（" + list.size() + "笔）", 0,
                    null, null, null, "", "", "", first.account(), first.accountName(), ""));
        }
        Map<String, List<LedgerEntry>> copy = new LinkedHashMap<>(); members.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return new View(List.copyOf(rows), Map.copyOf(copy));
    }
}
