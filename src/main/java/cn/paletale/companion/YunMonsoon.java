package cn.paletale.companion;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Yun calendar dates and the twelve monsoon dates supplied by the player.
 *
 * <p>The calendar and event table come from {@link MonsoonRules}, fetched from the website like the
 * chat rules. The bundled defaults are 20 real minutes per Yun day, 30 days per month and 12 months
 * per year (10 hours per month, 5 days per year).
 *
 * <p>The table carries dates and, where the observations recorded one, a duration. The start hour
 * is not part of the table, so an event is counted from 00:00 on its listed day; the scoreboard
 * marks an estimate as uncalibrated until the client has observed a Yun day change (see
 * {@code YunWatch}). No start hour is invented here.
 */
public final class YunMonsoon {
    public static final long MINUTE_MILLIS = 60_000L;

    public record Date(int year, int month, int day) {}
    /** {@code durationMinutes} is 0 when the source did not record a duration. */
    public record Event(int month, int day, String ports, int durationMinutes) {
        public boolean durationKnown() { return durationMinutes > 0; }
        public long durationMillis() { return durationMinutes * MINUTE_MILLIS; }
    }
    public enum State { NOT_STARTED, IN_PROGRESS }
    /**
     * An occurrence relative to the current Yun date. {@code dayOffset} is 0 on the event day,
     * positive for the next event and negative for an event that began on an earlier day.
     */
    public record Slot(Event event, int year, int dayOffset) {}
    /**
     * A bounded estimate. When {@code exact} is false the day start has not been observed yet, so
     * the remaining time is a range rather than a pretended point value.
     */
    public record Estimate(Slot slot, State state, boolean exact, long minRemainingMillis, long maxRemainingMillis) {
        public Event event() { return slot.event(); }
        public boolean durationKnown() { return slot.event().durationKnown(); }
    }
    /** Remaining time to a slot; a range when the current Yun day's start is not known yet. */
    public record Window(boolean exact, long minMillis, long maxMillis) {}

    private static final Pattern DATE = Pattern.compile("Yun\\s*历\\s*[:：]\\s*(\\d+)\\s*年[^\\r\\n]*?([一二三四五六七八九十]{1,3})\\s*月\\s*(\\d{1,2})\\s*日", Pattern.CASE_INSENSITIVE);
    /** Current calendar length in real milliseconds (website rules). */
    public static long dayMillis() { return MonsoonRules.current().dayMillis(); }

    private YunMonsoon() {}

    public static Optional<Date> parse(String footer) {
        if (footer == null || footer.length() > 16384) return Optional.empty();
        var m = DATE.matcher(footer);
        if (!m.find()) return Optional.empty();
        try {
            int year = Integer.parseInt(m.group(1)), month = month(m.group(2)), day = Integer.parseInt(m.group(3));
            return year > 0 && month > 0 && day >= 1 && day <= MonsoonRules.current().daysPerMonth() ? Optional.of(new Date(year, month, day)) : Optional.empty();
        } catch (NumberFormatException invalid) { return Optional.empty(); }
    }

    private static int month(String value) {
        return switch (value) {
            case "一" -> 1; case "二" -> 2; case "三" -> 3; case "四" -> 4;
            case "五" -> 5; case "六" -> 6; case "七" -> 7; case "八" -> 8;
            case "九" -> 9; case "十" -> 10; case "十一" -> 11; case "十二" -> 12;
            default -> 0;
        };
    }

    /** Zero-based day inside the current Yun year. */
    public static int dayOfYear(Date date) { return (date.month() - 1) * MonsoonRules.current().daysPerMonth() + date.day() - 1; }
    public static int dayOfYear(Event event) { return (event.month() - 1) * MonsoonRules.current().daysPerMonth() + event.day() - 1; }
    public static long absoluteDay(Date date) { return (long) (date.year() - 1) * MonsoonRules.current().daysPerYear() + dayOfYear(date); }
    public static int daysBetween(Date from, Date to) { return (int) (absoluteDay(to) - absoluteDay(from)); }

    /** The next event on or after {@code date}; the same day is retained. */
    public static Slot next(Date date) {
        var events = MonsoonRules.current().events();
        int today = dayOfYear(date);
        for (Event event : events) {
            int day = dayOfYear(event);
            if (day >= today) return new Slot(event, date.year(), day - today);
        }
        Event first = events.getFirst();
        return new Slot(first, date.year() + 1, MonsoonRules.current().daysPerYear() - today + dayOfYear(first));
    }

    /** The event after the one on {@code date}; when today is not an event day this is {@link #next(Date)}. */
    public static Slot nextAfter(Date date) {
        Slot next = next(date);
        if (next.dayOffset() > 0) return next;
        var events = MonsoonRules.current().events();
        int index = events.indexOf(next.event());
        Event following = events.get((index + 1) % events.size());
        int today = dayOfYear(date), day = dayOfYear(following), days = day - today, year = date.year();
        if (days <= 0) { days += MonsoonRules.current().daysPerYear(); year++; }
        return new Slot(following, year, days);
    }

    /** The most recent event on or before {@code date}. */
    public static Slot previous(Date date) {
        var events = MonsoonRules.current().events();
        int today = dayOfYear(date);
        for (int i = events.size() - 1; i >= 0; i--) {
            Event event = events.get(i);
            int day = dayOfYear(event);
            if (day <= today) return new Slot(event, date.year(), day - today);
        }
        Event last = events.getLast();
        return new Slot(last, date.year() - 1, dayOfYear(last) - today - MonsoonRules.current().daysPerYear());
    }

    /**
     * Resolve the event that is running now, or the next one.
     *
     * @param date current Yun date
     * @param now current real epoch millis
     * @param dayStartMillis real epoch millis when the current Yun day started, or -1 when unknown
     */
    public static Estimate estimate(Date date, long now, long dayStartMillis) {
        long dayMillis = MonsoonRules.current().dayMillis();
        long elapsed = dayStartMillis < 0 ? -1 : now - dayStartMillis;
        if (elapsed < 0 || elapsed >= dayMillis) elapsed = -1;
        boolean exact = elapsed >= 0;

        Slot previous = previous(date);
        long duration = previous.event().durationMillis();
        long sinceMin = -(long) previous.dayOffset() * dayMillis + (exact ? elapsed : 0);
        long sinceMax = exact ? sinceMin : sinceMin + dayMillis;
        if (duration > 0 && sinceMin < duration) {
            long min = Math.max(0, duration - sinceMax), max = Math.max(min, duration - sinceMin);
            return new Estimate(previous, State.IN_PROGRESS, exact, min, max);
        }
        if (duration == 0 && previous.dayOffset() == 0) {
            return new Estimate(previous, State.IN_PROGRESS, exact, -1, -1);
        }

        Slot next = next(date);
        if (next.dayOffset() == 0) {
            if (!next.event().durationKnown()) return new Estimate(next, State.IN_PROGRESS, exact, -1, -1);
            long min = Math.max(0, next.event().durationMillis() - (exact ? elapsed : dayMillis));
            long max = Math.max(min, next.event().durationMillis() - (exact ? elapsed : 0));
            return new Estimate(next, State.IN_PROGRESS, exact, min, max);
        }
        long until = (long) next.dayOffset() * dayMillis - (exact ? elapsed : 0);
        long min = exact ? until : Math.max(0, until - dayMillis), max = until;
        return new Estimate(next, State.NOT_STARTED, exact, min, max);
    }

    /** Remaining time until {@code slot}, as a range while the current day's start is unknown. */
    public static Window until(Slot slot, long now, long dayStartMillis) {
        long dayMillis = MonsoonRules.current().dayMillis();
        long elapsed = dayStartMillis < 0 ? -1 : now - dayStartMillis;
        if (elapsed < 0 || elapsed >= dayMillis) elapsed = -1;
        long until = (long) slot.dayOffset() * dayMillis - (elapsed >= 0 ? elapsed : 0);
        if (elapsed >= 0) return new Window(true, Math.max(0, until), Math.max(0, until));
        return new Window(false, Math.max(0, until - dayMillis), Math.max(0, until));
    }
}
