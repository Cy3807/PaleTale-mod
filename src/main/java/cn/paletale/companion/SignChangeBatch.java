package cn.paletale.companion;

import cn.paletale.companion.ShopTouchPolicy.Spot;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Signs the server has just changed (a shop sold out, was restocked, was repriced): collected as the update packets arrive and handed over in one batch,
 * at most once every 8 seconds, so that a busy market never turns into a stream of requests (the site accepts 12 per minute per player).
 */
final class SignChangeBatch {
    private static final long COALESCE_MS = 3_000L, MIN_INTERVAL_MS = 8_000L;
    private static final int MAX = 300;

    private final LinkedHashSet<Spot> pending = new LinkedHashSet<>();
    private long dueAt, lastTaken;

    void add(Spot spot, long now) {
        if (pending.size() < MAX) pending.add(spot);
        if (dueAt == 0) dueAt = Math.max(now + COALESCE_MS, lastTaken + MIN_INTERVAL_MS);
    }

    /** The collected signs once the batch is due, else nothing. */
    List<Spot> take(long now) {
        if (dueAt == 0 || now < dueAt) return List.of();
        List<Spot> out = new ArrayList<>(pending);
        pending.clear(); dueAt = 0; lastTaken = now;
        return out;
    }

    void clear() { pending.clear(); dueAt = 0; }
    boolean isEmpty() { return pending.isEmpty(); }
}
