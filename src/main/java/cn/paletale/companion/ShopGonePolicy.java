package cn.paletale.companion;

import java.util.*;
import java.util.function.Predicate;

/**
 * Decides when a chest-shop sign the site still lists is really gone. A sign counts as missing only where the client can see the block
 * (loaded, close by); it is reported only after it stayed missing across scans a full minute apart, any sighting starts over,
 * and a report is repeated no more than every ten minutes. The site makes the final call (two players, or one player hours apart).
 */
public final class ShopGonePolicy {
    public record Spot(int x, int y, int z) { String key() { return x + "|" + y + "|" + z; } }
    private record Miss(long first, int count) {}
    static final long CONFIRM_MS = 60_000, REPEAT_MS = 600_000;
    static final int MAX_EXPECTED = 3000, MAX_REPORT = 30;
    private final Map<String, Spot> expected = new LinkedHashMap<>();
    private final Map<String, Miss> misses = new HashMap<>();
    private final Map<String, Long> reported = new HashMap<>();

    /** Signs that should exist: the site's list around the player, and shops this client has just read. */
    public void expect(Collection<Spot> spots) {
        for (Spot spot : spots) { expected.remove(spot.key()); expected.put(spot.key(), spot); }
        while (expected.size() > MAX_EXPECTED) { String eldest = expected.keySet().iterator().next(); expected.remove(eldest); misses.remove(eldest); reported.remove(eldest); }
    }

    /** One scan: {@code visible} = the block can be judged now (chunk loaded and near); {@code present} = a shop sign is there. Returns the spots to report as gone. */
    public List<Spot> observe(Predicate<Spot> visible, Predicate<Spot> present, long now) {
        List<Spot> gone = new ArrayList<>();
        for (Spot spot : expected.values()) {
            if (!visible.test(spot)) { misses.remove(spot.key()); continue; }
            String key = spot.key();
            if (present.test(spot)) { misses.remove(key); reported.remove(key); continue; }
            Miss miss = misses.merge(key, new Miss(now, 1), (old, ignored) -> new Miss(old.first(), old.count() + 1));
            Long last = reported.get(key);
            if (miss.count() >= 2 && now - miss.first() >= CONFIRM_MS && (last == null || now - last >= REPEAT_MS) && gone.size() < MAX_REPORT) { gone.add(spot); reported.put(key, now); }
        }
        return gone;
    }

    /** The report did not reach the site: allow it again at the next scan. */
    public void failed(Collection<Spot> spots) { for (Spot spot : spots) reported.remove(spot.key()); }

    public void clear() { expected.clear(); misses.clear(); reported.clear(); }
}
