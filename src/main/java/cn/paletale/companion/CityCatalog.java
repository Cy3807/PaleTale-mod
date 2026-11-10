package cn.paletale.companion;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Curated from player/NPC captures and the actual shortcut menu, never from filenames or entity facing. */
public final class CityCatalog {
    public record Position(double x, double y, double z) {}
    public record Place(String id, String name, String role, String entityType, String icon, String model,
                        String menu, String slot, List<String> areas, double x, double y, double z) {
        public int blockX() { return (int) Math.floor(x); }
        public int blockY() { return (int) Math.floor(y); }
        public int blockZ() { return (int) Math.floor(z); }
        public String description() { return areas == null ? role : String.join(" / ", areas) + " · 快捷传送 NPC"; }
    }
    private record Data(String world, Position spawn, List<Place> npcs, List<Place> shortcuts, List<Place> destinations) {}
    private static volatile Data DATA = load();
    private static final java.util.Map<String,Place> DISCOVERED=new java.util.LinkedHashMap<>();
    private static long revision;
    public static long revision() { return revision; }
    private CityCatalog() {}
    private static Data load() {
        try (var stream = CityCatalog.class.getResourceAsStream("city-navigation.json")) {
            if (stream == null) throw new IllegalStateException("Missing city navigation catalog");
            var data = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Data.class);
            if (!CityNavigation.WORLD.equals(data.world())) throw new IllegalStateException("Wrong city world");
            return data;
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    public static boolean accept(String json) {
        try {
            if (json == null || json.length() > 200_000) return false;
            var next = new Gson().fromJson(json, Data.class);
            if (!CityNavigation.WORLD.equals(next.world()) || next.spawn() == null || next.npcs().isEmpty() || next.npcs().size() > 500 || next.shortcuts().size() > 100 || next.destinations().size() > 100) return false;
            var ids = new java.util.HashSet<String>();
            var all = new java.util.ArrayList<>(next.npcs()); all.addAll(next.shortcuts()); all.addAll(next.destinations());
            for (var p : all) if (p.id()==null || !p.id().matches("(?:npc|shortcut|area)-[0-9]{1,3}") || !ids.add(p.id()) || p.role()==null || p.name() == null || p.name().length() > 120 || !Double.isFinite(p.x()+p.y()+p.z()) || Math.abs(p.x())>1_000_000 || Math.abs(p.z())>1_000_000 || Math.abs(p.y())>2048 || p.icon()==null || !p.icon().matches("[a-z0-9_]+:[a-z0-9_/]+")) return false;
            if(!next.equals(DATA)){DATA = next;revision++;} return true;
        } catch (RuntimeException invalid) { return false; }
    }
    public static Position spawn() { return DATA.spawn(); }
    public static List<Place> npcs() { var all=new java.util.ArrayList<>(DATA.npcs());all.addAll(DISCOVERED.values());return List.copyOf(all); }
    public static List<Place> discovered() { return List.copyOf(DISCOVERED.values()); }
    public static boolean discover(Place place) {
        if(!NpcDiscovery.valid(place)||DISCOVERED.size()>=400||DISCOVERED.containsKey(place.id()))return false;
        var all=new java.util.ArrayList<>(npcs());all.addAll(shortcuts());
        if(all.stream().anyMatch(p->matches(p,place.entityType(),place.x(),place.y(),place.z())&&matchesName(p,place.name(),place.name())))return false;
        DISCOVERED.put(place.id(),place);revision++;return true;
    }
    public static List<Place> shortcuts() { return DATA.shortcuts(); }
    public static List<Place> destinations() { return DATA.destinations(); }
    public static Place shop(String role) { return npcs().stream().filter(p -> p.role().equals(role)).findFirst().orElseThrow(); }
    public static double distance(double x, double y, double z, double tx, double ty, double tz) { return Math.sqrt(Math.pow(x - tx, 2) + Math.pow(y - ty, 2) + Math.pow(z - tz, 2)); }
    /** Require both the captured type and location. A nearby real player must not become an NPC just by proximity. */
    public static String plainName(String name) { return LedgerParser.clean(name).replace("(", "").replace(")", "").replace("（", "").replace("）", "").strip(); }
    public static boolean matchesName(Place p, String name, String display) { return plainName(p.name()).equals(plainName(name)) || plainName(p.name()).equals(plainName(display)); }
    public static boolean matches(Place p, String type, double x, double y, double z) {
        return p.entityType() != null && p.entityType().equals(type) && distance(x, y, z, p.x(), p.y(), p.z()) <= 1.5;
    }
}
