package cn.paletale.companion;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Website-managed monsoon rules: calendar lengths, event dates, ports and durations.
 *
 * <p>The mod fetches {@code /mcmod/monsoon-v1.json} the same way it fetches the chat rules; the
 * bundled copy below is only the fallback. Replacing that JSON on the site changes the schedule
 * without releasing a new mod build.
 */
public final class MonsoonRules {
    public static final int MAX_BYTES = 16 * 1024;
    private static final int MAX_EVENTS = 24;
    private static final int MAX_PORTS = 8;
    private static final int MAX_PORT_LENGTH = 32;

    private final int version, daysPerMonth, monthsPerYear, minutesPerDay;
    private final List<YunMonsoon.Event> events;

    private static final MonsoonRules BUNDLED = new MonsoonRules(1, 30, 12, 20, List.of(
        new YunMonsoon.Event(1, 10, "神蛙、雪樱", 60), new YunMonsoon.Event(2, 11, "露露西亚", 60),
        new YunMonsoon.Event(3, 20, "烧鸡", 0), new YunMonsoon.Event(4, 22, "夏乡、阿瓦罗萨、雾月港", 0),
        new YunMonsoon.Event(5, 9, "千帆、黄昏", 0), new YunMonsoon.Event(6, 25, "霞樱、焰煌", 0),
        new YunMonsoon.Event(7, 16, "烧鸡、雾月港", 40), new YunMonsoon.Event(8, 12, "清粥", 0),
        new YunMonsoon.Event(9, 14, "夏乡、阿瓦罗萨、雾月港", 0), new YunMonsoon.Event(10, 16, "露露西亚", 0),
        new YunMonsoon.Event(11, 22, "魔力果实酒馆", 60), new YunMonsoon.Event(12, 23, "辉梦", 60)));
    private static volatile MonsoonRules current = BUNDLED;

    private MonsoonRules(int version, int daysPerMonth, int monthsPerYear, int minutesPerDay, List<YunMonsoon.Event> events) {
        this.version = version;
        this.daysPerMonth = daysPerMonth;
        this.monthsPerYear = monthsPerYear;
        this.minutesPerDay = minutesPerDay;
        this.events = List.copyOf(events);
    }

    public static MonsoonRules current() { return current; }
    static void use(MonsoonRules rules) { current = rules == null ? BUNDLED : rules; }
    static void resetForTest() { current = BUNDLED; }

    public int version() { return version; }
    public int daysPerMonth() { return daysPerMonth; }
    public int monthsPerYear() { return monthsPerYear; }
    public int minutesPerDay() { return minutesPerDay; }
    public int daysPerYear() { return daysPerMonth * monthsPerYear; }
    public long dayMillis() { return minutesPerDay * 60_000L; }
    public List<YunMonsoon.Event> events() { return events; }
    public static MonsoonRules bundled() { return BUNDLED; }

    /** Parses and validates a website rules document; anything unusual keeps the previous rules. */
    public static MonsoonRules decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Rules size");
        try {
            JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!"monsoon-rules".equals(text(root, "kind"))) throw new IllegalArgumentException("Rules header");
            int version = integer(root, "version", 1, 1_000_000);
            JsonObject calendar = object(root, "calendar");
            int daysPerMonth = integer(calendar, "daysPerMonth", 20, 31);
            int monthsPerYear = integer(calendar, "monthsPerYear", 1, 24);
            int minutesPerDay = integer(calendar, "minutesPerDay", 1, 240);
            JsonArray rows = array(root, "events", 1, MAX_EVENTS);
            var events = new ArrayList<YunMonsoon.Event>();
            var days = new LinkedHashSet<String>();
            for (var node : rows) {
                if (!node.isJsonObject()) throw new IllegalArgumentException("Rules event");
                JsonObject row = node.getAsJsonObject();
                int month = integer(row, "month", 1, monthsPerYear);
                int day = integer(row, "day", 1, daysPerMonth);
                int duration = integer(row, "durationMinutes", 0, 240);
                JsonArray ports = array(row, "ports", 1, MAX_PORTS);
                var names = new ArrayList<String>();
                for (var port : ports) {
                    if (!port.isJsonPrimitive() || !port.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Rules port");
                    String name = port.getAsString().strip();
                    if (name.isEmpty() || name.length() > MAX_PORT_LENGTH || name.contains("、")) throw new IllegalArgumentException("Rules port");
                    names.add(name);
                }
                if (!days.add(month + "/" + day)) throw new IllegalArgumentException("Duplicate event day");
                events.add(new YunMonsoon.Event(month, day, String.join("、", names), duration));
            }
            events.sort(Comparator.comparingInt(event -> event.month() * 100 + event.day()));
            return new MonsoonRules(version, daysPerMonth, monthsPerYear, minutesPerDay, events);
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        } catch (RuntimeException broken) {
            throw new IllegalArgumentException("Rules format", broken);
        }
    }

    private static JsonObject object(JsonObject node, String key) {
        var value = node.get(key);
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException("Missing " + key);
        return value.getAsJsonObject();
    }
    private static JsonArray array(JsonObject node, String key, int min, int max) {
        var value = node.get(key);
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() < min || value.getAsJsonArray().size() > max)
            throw new IllegalArgumentException("Bad list " + key);
        return value.getAsJsonArray();
    }
    private static int integer(JsonObject node, String key, int min, int max) {
        var value = node.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Missing " + key);
        int number = value.getAsInt();
        if (number < min || number > max) throw new IllegalArgumentException("Bad number " + key);
        return number;
    }
    private static String text(JsonObject node, String key) {
        var value = node.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Missing " + key);
        return value.getAsString();
    }
}
