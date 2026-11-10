package cn.paletale.companion;

import java.nio.file.Files;
import java.util.List;

/** Reads and writes the saved destinations in paletale/config/custom-destinations.json (this computer only). */
final class CustomDestinationStore {
    private static List<CustomDestinations.Entry> list; private static final String FILE = "custom-destinations.json";
    private CustomDestinationStore() {}
    static synchronized List<CustomDestinations.Entry> all() {
        if (list == null) {
            try { var file = ModPaths.readable(FILE); list = Files.isRegularFile(file) ? CustomDestinations.decode(Files.readString(file)) : List.of(); }
            catch (Exception unreadable) { list = List.of(); }
        }
        return list;
    }
    /** Saves the current position; false when the name is blank or the list is full. */
    static synchronized boolean add(String name, String note, int x, int y, int z, String world, String server, String land) {
        var next = CustomDestinations.add(all(), name, note, x, y, z, world, server, land, System.currentTimeMillis());
        if (next == null) return false;
        list = next; save(); return true;
    }
    static synchronized void remove(CustomDestinations.Entry entry) { list = CustomDestinations.remove(all(), entry); save(); }
    /** The server or dimension the point is in: an expedition server's short name (血肉…), else the dimension (主世界、地狱、末地). */
    static String worldLabel(String world, String server) {
        if (!server.isEmpty()) { var w = ExpeditionWorld.byId(server); return w == null ? server : w.shortName(); }
        return switch (world) { case "minecraft:overworld", "" -> "主世界"; case "minecraft:the_nether" -> "地狱"; case "minecraft:the_end" -> "末地"; case CityNavigation.WORLD -> "主城"; default -> world.substring(world.indexOf(':') + 1); };
    }
    /** Territories are only recorded in the main world of the main server. */
    static boolean recordsLand(String world, String server) { return server.isEmpty() && CityNavigation.MAIN_WORLD.equals(world); }
    private static void save() {
        try { var file = ModPaths.config().resolve(FILE); Files.createDirectories(file.getParent()); Files.writeString(file, CustomDestinations.encode(list)); }
        catch (Exception ignored) { /* kept for this session at least */ }
    }
}
