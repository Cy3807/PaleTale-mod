package cn.paletale.companion;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * When to re-read a chest-shop sign right away instead of waiting for the next scan: the server prints a 「商店信息」 block when a player clicks a shop sign,
 * and 「支付成功」 / 「出售成功」 when a trade went through (the stock has just changed). The sign the player is looking at when the block appears is the shop;
 * a result line belongs to the shop of the last block. Feed it chat lines; it only decides, the caller reads the sign.
 */
final class ShopTouchPolicy {
    enum Kind { NONE, INFO, RESULT }
    record Spot(int x, int y, int z) {}

    private static final Pattern RESULT = Pattern.compile("^(支付成功|出售成功)");
    private static final long INFO_STICKY_MS = 3_000L, RESULT_STICKY_MS = 120_000L, MIN_GAP_MS = 1_000L;

    private Spot last; private long lastAt;
    private final Map<Spot, Long> readAt = new HashMap<>();

    static Kind classify(String raw) {
        if (raw == null) return Kind.NONE;
        String line = LedgerShopTalk.strip(raw);
        if (line.startsWith("商店信息")) return Kind.INFO;
        return RESULT.matcher(line).find() ? Kind.RESULT : Kind.NONE;
    }

    /** Which sign to read for this line: {@code looking} is the sign under the crosshair (null when it is not a sign). */
    Spot choose(Kind kind, Spot looking, long now) {
        switch (kind) {
            case INFO -> {
                if (looking != null) { last = looking; lastAt = now; return looking; }
                return last != null && now - lastAt <= INFO_STICKY_MS ? last : null;
            }
            case RESULT -> {
                if (last != null && now - lastAt <= RESULT_STICKY_MS) return last;
                if (looking != null) { last = looking; lastAt = now; return looking; }
                return null;
            }
            default -> { return null; }
        }
    }

    /** Milliseconds after the line at which the sign is read: at once after a click, a little later after a trade (the sign is updated by the server a moment after the payment). */
    static long[] delays(Kind kind) {
        return switch (kind) { case INFO -> new long[] { 0, 1_500 }; case RESULT -> new long[] { 1_200, 4_000 }; default -> new long[0]; };
    }

    /** At most one read per sign per second, whatever the number of lines. */
    boolean mayRead(Spot spot, long now) {
        Long before = readAt.get(spot);
        if (before != null && now - before < MIN_GAP_MS) return false;
        if (readAt.size() > 64) readAt.clear();
        readAt.put(spot, now);
        return true;
    }
}
