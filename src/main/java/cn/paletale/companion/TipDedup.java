package cn.paletale.companion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A large tip is announced twice: as a chat/action-bar line and as a boss bar. Small tips only get the chat line.
 * The second sighting of a tip from the other source is the same tip; repeated sightings from one source are real repeats.
 */
final class TipDedup {
    static final long WINDOW_MS = 8_000L;
    private record Seen(LedgerEntry.Kind kind, String who, double amount, boolean boss, long at, boolean paired) {}
    private final List<Seen> seen = new ArrayList<>();

    /** True when this tip is the other-source twin of one already recorded; call only for tip entries. */
    synchronized boolean duplicate(LedgerEntry entry, boolean boss, long now) {
        seen.removeIf(s -> now - s.at() > WINDOW_MS || now < s.at());
        String who = entry.counterparty().strip().toLowerCase(Locale.ROOT);
        for (int i = 0; i < seen.size(); i++) {
            var s = seen.get(i);
            if (!s.paired() && s.boss() != boss && s.kind() == entry.kind() && s.who().equals(who) && s.amount() == entry.amount()) {
                seen.set(i, new Seen(s.kind(), s.who(), s.amount(), s.boss(), s.at(), true));
                return true;
            }
        }
        seen.add(new Seen(entry.kind(), who, entry.amount(), boss, now, false));
        return false;
    }

    static boolean isTip(LedgerEntry entry) {
        return entry.kind() == LedgerEntry.Kind.TIP_IN || entry.kind() == LedgerEntry.Kind.TIP_OUT;
    }
}
