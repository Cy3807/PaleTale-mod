package cn.paletale.companion;

public final class YunMonsoonTest {
    private static final long MINUTE = YunMonsoon.MINUTE_MILLIS;

    public static void main(String[] args) {
        MonsoonRules.resetForTest();
        var date = YunMonsoon.parse("在线:886\nYun历: 93年 凋敝季 十月 29 日 星期三").orElseThrow();
        check(date.equals(new YunMonsoon.Date(93, 10, 29)), "screenshot date");
        check(YunMonsoon.next(date).event().ports().equals("魔力果实酒馆"), "next event");
        check(YunMonsoon.next(date).dayOffset() == 23, "next event is 23 Yun days away");
        check(YunMonsoon.next(new YunMonsoon.Date(93, 12, 24)).event().ports().equals("神蛙、雪樱"), "year wrap");
        check(YunMonsoon.next(new YunMonsoon.Date(93, 12, 24)).dayOffset() == 16, "year wrap day count");
        check(YunMonsoon.next(new YunMonsoon.Date(93, 3, 20)).event().ports().equals("烧鸡"), "event day retained");
        check(YunMonsoon.previous(new YunMonsoon.Date(93, 11, 23)).dayOffset() == -1, "previous event day");
        check(YunMonsoon.nextAfter(new YunMonsoon.Date(93, 11, 22)).event().ports().equals("辉梦"), "next after the running event");
        check(YunMonsoon.nextAfter(new YunMonsoon.Date(93, 11, 22)).dayOffset() == 31, "next after the running event is 31 days away");
        check(YunMonsoon.nextAfter(new YunMonsoon.Date(93, 11, 23)).dayOffset() == 30, "next after a running event that began yesterday");
        check(YunMonsoon.nextAfter(new YunMonsoon.Date(93, 12, 23)).dayOffset() == 17, "next after the last event wraps the year");
        check(YunMonsoon.dayOfYear(new YunMonsoon.Date(93, 12, 30)) == 359, "last day of the year");
        check(YunMonsoon.daysBetween(new YunMonsoon.Date(93, 12, 30), new YunMonsoon.Date(94, 1, 1)) == 1, "year boundary is one day");
        check(YunMonsoon.parse("Yun历: 1年 十二月 23 日").orElseThrow().month() == 12, "twelfth month");
        check(YunMonsoon.parse("Yun历: 1年 十二月 30 日").isPresent(), "day thirty accepted");
        check(YunMonsoon.parse("Yun历: 1年 十二月 31 日").isEmpty(), "day thirty-one rejected with thirty-day months");
        check(YunMonsoon.parse("Yun历: 1年 十三月 23 日").isEmpty(), "invalid month rejected");
        check(YunMonsoon.parse("Yun历: 1年 二月 99 日").isEmpty(), "invalid day rejected");

        long dayStart = 1_000_000_000L, now = dayStart + 7 * MINUTE;
        var notStarted = YunMonsoon.estimate(new YunMonsoon.Date(93, 10, 29), now, dayStart);
        check(notStarted.state() == YunMonsoon.State.NOT_STARTED && notStarted.exact(), "exact future event");
        check(notStarted.minRemainingMillis() == 453 * MINUTE, "23 days minus 7 minutes");
        check(notStarted.maxRemainingMillis() == 453 * MINUTE, "exact estimate is a point");
        var nextSlot = YunMonsoon.next(new YunMonsoon.Date(93, 10, 29));
        var exactUntil = YunMonsoon.until(nextSlot, now, dayStart);
        check(exactUntil.exact() && exactUntil.minMillis() == 453 * MINUTE, "until exact next time");
        var rangeUntil = YunMonsoon.until(nextSlot, now, -1);
        check(!rangeUntil.exact() && rangeUntil.minMillis() == 440 * MINUTE && rangeUntil.maxMillis() == 460 * MINUTE, "until range next time");
        var uncalibrated = YunMonsoon.estimate(new YunMonsoon.Date(93, 10, 29), now, -1);
        check(!uncalibrated.exact(), "uncalibrated estimate is a range");
        check(uncalibrated.minRemainingMillis() == 440 * MINUTE && uncalibrated.maxRemainingMillis() == 460 * MINUTE, "one-day uncertainty range");
        var running = YunMonsoon.estimate(new YunMonsoon.Date(93, 11, 23), now, dayStart);
        check(running.state() == YunMonsoon.State.IN_PROGRESS && running.exact(), "one-hour event still running the next day");
        check(running.minRemainingMillis() == 33 * MINUTE, "remaining after 27 minutes");
        check(running.event().ports().equals("魔力果实酒馆"), "running event keeps its own ports");
        var runningRange = YunMonsoon.estimate(new YunMonsoon.Date(93, 11, 23), now, -1);
        check(runningRange.state() == YunMonsoon.State.IN_PROGRESS && !runningRange.exact(), "uncalibrated running range");
        check(runningRange.minRemainingMillis() == 20 * MINUTE && runningRange.maxRemainingMillis() == 40 * MINUTE, "running range spans one day");
        var eventDay = YunMonsoon.estimate(new YunMonsoon.Date(93, 11, 22), now, dayStart);
        check(eventDay.state() == YunMonsoon.State.IN_PROGRESS && eventDay.minRemainingMillis() == 53 * MINUTE, "event day starts at 00:00");
        var shortEvent = YunMonsoon.estimate(new YunMonsoon.Date(93, 7, 17), now, dayStart);
        check(shortEvent.event().durationMinutes() == 40 && shortEvent.minRemainingMillis() == 13 * MINUTE, "forty-minute event");
        var unknownEnd = YunMonsoon.estimate(new YunMonsoon.Date(93, 3, 20), now, dayStart);
        check(unknownEnd.state() == YunMonsoon.State.IN_PROGRESS && !unknownEnd.durationKnown(), "unknown duration stays open");
        var afterUnknown = YunMonsoon.estimate(new YunMonsoon.Date(93, 3, 21), now, dayStart);
        check(afterUnknown.state() == YunMonsoon.State.NOT_STARTED, "unknown duration does not leak into the next day");
        System.out.println("Yun calendar and monsoon schedule checks passed");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
