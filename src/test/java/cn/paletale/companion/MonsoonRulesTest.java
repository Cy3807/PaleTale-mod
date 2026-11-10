package cn.paletale.companion;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MonsoonRulesTest {
    public static void main(String[] args) throws Exception {
        MonsoonRules.resetForTest();
        String custom = """
            {"kind":"monsoon-rules","version":7,"calendar":{"daysPerMonth":20,"monthsPerYear":2,"minutesPerDay":10},
             "events":[{"month":2,"day":2,"ports":["乙"],"durationMinutes":5},
                       {"month":1,"day":1,"ports":["甲"],"durationMinutes":0}]}
            """;
        var rules = MonsoonRules.decode(custom.getBytes(StandardCharsets.UTF_8));
        check(rules.version() == 7 && rules.daysPerMonth() == 20 && rules.monthsPerYear() == 2 && rules.minutesPerDay() == 10, "custom calendar");
        check(rules.dayMillis() == 600_000L && rules.daysPerYear() == 40, "custom day and year");
        check(rules.events().getFirst().ports().equals("甲"), "events sorted by date");
        MonsoonRules.use(rules);
        check(YunMonsoon.dayMillis() == 600_000L, "calendar used by the clock");
        check(YunMonsoon.next(new YunMonsoon.Date(1, 1, 1)).event().ports().equals("甲"), "custom first event");
        check(YunMonsoon.next(new YunMonsoon.Date(1, 1, 2)).dayOffset() == 20, "custom month length");
        check(YunMonsoon.parse("Yun历: 1年 一月 21 日").isEmpty(), "day beyond the configured month rejected");
        MonsoonRules.resetForTest();
        check(YunMonsoon.dayMillis() == 1_200_000L, "bundled day restored");
        check(YunMonsoon.parse("Yun历: 1年 一月 30 日").isPresent() && YunMonsoon.parse("Yun历: 1年 一月 31 日").isEmpty(), "bundled month length");

        reject("{\"kind\":\"monsoon-rules\",\"version\":1,\"calendar\":{\"daysPerMonth\":10,\"monthsPerYear\":2,\"minutesPerDay\":10},\"events\":[{\"month\":1,\"day\":1,\"ports\":[\"甲\"],\"durationMinutes\":0},{\"month\":1,\"day\":1,\"ports\":[\"乙\"],\"durationMinutes\":0}]}".getBytes(StandardCharsets.UTF_8), "duplicate day rejected");
        reject("{\"kind\":\"monsoon-rules\",\"version\":1,\"calendar\":{\"daysPerMonth\":10,\"monthsPerYear\":2,\"minutesPerDay\":10},\"events\":[]}".getBytes(StandardCharsets.UTF_8), "empty events rejected");
        reject("{\"kind\":\"other\",\"version\":1,\"calendar\":{\"daysPerMonth\":10,\"monthsPerYear\":2,\"minutesPerDay\":10},\"events\":[{\"month\":1,\"day\":1,\"ports\":[\"甲\"],\"durationMinutes\":0}]}".getBytes(StandardCharsets.UTF_8), "kind rejected");

        var published = MonsoonRules.decode(Files.readAllBytes((Files.exists(Path.of("../public/mcmod/monsoon-v1.json")) ? Path.of("../public/mcmod/monsoon-v1.json") : Path.of("src/test/resources/monsoon-v1.json"))));
        check(published.version() == 1 && published.events().size() == 12, "website rules file");
        check(published.events().get(10).ports().equals("魔力果实酒馆") && published.events().get(6).durationMinutes() == 40, "website event data");
        System.out.println("Monsoon rules checks passed");
    }
    private static void reject(byte[] json, String message) {
        try { MonsoonRules.decode(json); throw new AssertionError(message); }
        catch (IllegalArgumentException expected) { }
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
