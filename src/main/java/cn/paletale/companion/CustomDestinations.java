package cn.paletale.companion;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;

/** The player's own saved destinations: pure list logic and JSON, kept on this computer only (the file is written by the client). */
public final class CustomDestinations {
    public static final int MAX = 100, NAME_MAX = 24, NOTE_MAX = 60;
    /** server is empty on the main server, otherwise the id of the expedition server the point belongs to; land is the territory at the point (main world only, empty when unknown). */
    public record Entry(String name, String note, int x, int y, int z, String world, String server, String land, long time) {}
    private CustomDestinations() {}

    /** A new entry at the front, or null when the name is blank or the list is full. The text is trimmed and cut to its limits. */
    public static List<Entry> add(List<Entry> list, String name, String note, int x, int y, int z, String world, String server, String land, long time) {
        String n = clean(name, NAME_MAX);
        if (n.isEmpty() || list.size() >= MAX) return null;
        var out = new ArrayList<Entry>(list.size() + 1);
        out.add(new Entry(n, clean(note, NOTE_MAX), x, y, z, world == null ? "" : world, server == null ? "" : server, clean(land, NAME_MAX), time));
        out.addAll(list);
        return out;
    }
    public static List<Entry> remove(List<Entry> list, Entry entry) { var out = new ArrayList<>(list); out.remove(entry); return out; }
    static String clean(String value, int max) {
        String v = value == null ? "" : value.replaceAll("[\\p{Cntrl}§]", " ").strip();
        return v.length() > max ? v.substring(0, max) : v;
    }
    public static String encode(List<Entry> list) {
        var array = new JsonArray();
        for (var e : list) {
            var o = new JsonObject();
            o.addProperty("name", e.name()); o.addProperty("note", e.note()); o.addProperty("x", e.x()); o.addProperty("y", e.y()); o.addProperty("z", e.z());
            o.addProperty("world", e.world()); o.addProperty("server", e.server()); o.addProperty("land", e.land()); o.addProperty("time", e.time());
            array.add(o);
        }
        var root = new JsonObject(); root.addProperty("version", 1); root.add("items", array);
        return root.toString();
    }
    /** Empty for anything broken; single bad rows are skipped. */
    public static List<Entry> decode(String json) {
        var out = new ArrayList<Entry>();
        try {
            for (var element : JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("items")) {
                try {
                    var o = element.getAsJsonObject();
                    String name = clean(o.get("name").getAsString(), NAME_MAX);
                    if (name.isEmpty() || out.size() >= MAX) continue;
                    out.add(new Entry(name, clean(o.has("note") ? o.get("note").getAsString() : "", NOTE_MAX), o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt(),
                            o.has("world") ? o.get("world").getAsString() : "", o.has("server") ? o.get("server").getAsString() : "", o.has("land") ? clean(o.get("land").getAsString(), NAME_MAX) : "", o.has("time") ? o.get("time").getAsLong() : 0L));
                } catch (RuntimeException skip) { /* one bad row */ }
            }
        } catch (RuntimeException broken) { return new ArrayList<>(); }
        return out;
    }
}
