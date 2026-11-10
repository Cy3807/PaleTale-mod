package cn.paletale.companion;

/**
 * Learns when the current Yun day began from the tab list and keeps that anchor across sessions.
 *
 * <p>The tab list only names the date. On the first observation of a date the elapsed part of the
 * day is unknown; once this client sees the date change, the boundary is known. A saved anchor
 * also lets later sessions derive the current day's start from the number of Yun days that passed,
 * because the calendar keeps running while the game is closed.
 */
final class YunWatch {
    private static final long WATCH_WINDOW_MILLIS = 90_000L;
    private static final int MAX_INFERRED_DAYS = 400;
    private static boolean loaded;
    private static YunMonsoon.Date date;
    private static long start;
    private static boolean exact;
    private static YunMonsoon.Date observed;
    private static long lastSeen;

    private YunWatch() {}

    /**
     * @return milliseconds since the current Yun day started, or -1 when this client has not yet
     *         observed a day boundary for the date.
     */
    static synchronized long elapsed(YunMonsoon.Date current, long now) {
        load();
        if (date != null && date.equals(current)) {
            observed = current;
            lastSeen = now;
            if (exact) {
                long elapsed = now - start;
                if (elapsed >= 0 && elapsed < YunMonsoon.dayMillis()) return elapsed;
                exact = false;
                start = now;
                save();
            }
            return -1;
        }
        if (date != null && exact) {
            int passed = YunMonsoon.daysBetween(date, current);
            if (passed >= 0 && passed <= MAX_INFERRED_DAYS) {
                long candidate = start + passed * YunMonsoon.dayMillis();
                long elapsed = now - candidate;
                if (elapsed >= 0 && elapsed < YunMonsoon.dayMillis()) {
                    date = current;
                    start = candidate;
                    observed = current;
                    lastSeen = now;
                    save();
                    return elapsed;
                }
            }
        }
        if (observed != null && observed.equals(date) && YunMonsoon.daysBetween(date, current) == 1
                && now - lastSeen <= WATCH_WINDOW_MILLIS) {
            date = current;
            start = now;
            exact = true;
            observed = current;
            lastSeen = now;
            save();
            return 0;
        }
        date = current;
        start = now;
        exact = false;
        observed = current;
        lastSeen = now;
        save();
        return -1;
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        String raw = ModSettings.yunAnchor();
        if (raw.isBlank()) return;
        String[] parts = raw.split("\\|");
        if (parts.length != 5) return;
        try {
            date = new YunMonsoon.Date(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            start = Long.parseLong(parts[3]);
            exact = "1".equals(parts[4]);
            observed = date;
        } catch (NumberFormatException invalid) {
            date = null;
            exact = false;
        }
    }

    private static void save() {
        if (date == null) return;
        ModSettings.setYunAnchor(date.year() + "|" + date.month() + "|" + date.day() + "|" + start + "|" + (exact ? "1" : "0"));
    }
}
