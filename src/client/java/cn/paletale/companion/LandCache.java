package cn.paletale.companion;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * The one store of the player's territories, shared by everything that needs them (the command wheel, the navigation): what the last /l read found, kept in
 * paletale/config/lands.txt. One line per territory: name, icon (the item JSON the game itself uses), nation, 1 when the player owns it. Lines "@alias" remember which menu
 * entry turned out to be which territory (an icon item that hides the territory's name), so a refresh never has to ask the site twice. Refreshed when the player asks, and when
 * something shows the list went stale.
 */
final class LandCache {
    record Land(String name, String nation, boolean owned) {}
    private static List<Land> lands;
    private static final Map<String, String> JSON = new HashMap<>(), ALIASES = new LinkedHashMap<>();
    private static final Map<String, ItemStack> DECODED = new HashMap<>();
    private static int version;
    private LandCache() {}

    /** Changes with every save, so a display can tell its copy is old. */
    static synchronized int version() { return version; }

    private static synchronized void ensure() {
        if (lands != null) return;
        lands = List.of();
        try {
            var file = ModPaths.readable("lands.txt");
            if (!Files.isRegularFile(file)) return;
            var list = new ArrayList<Land>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] part = line.split("\t", 5);
                if (part[0].equals("@alias") && part.length >= 3) { ALIASES.put(part[1], part[2]); continue; }
                String name = part[0].strip();
                if (name.isEmpty() || name.length() > 60 || list.stream().anyMatch(l -> l.name().equals(name))) continue;
                if (part.length > 1 && !part[1].isBlank()) JSON.put(name, part[1].strip());
                list.add(new Land(name, part.length > 2 ? part[2].strip() : "", part.length > 3 && part[3].strip().equals("1")));
            }
            lands = List.copyOf(list);
        } catch (java.io.IOException | RuntimeException unreadable) { lands = List.of(); JSON.clear(); ALIASES.clear(); }
    }

    static synchronized List<Land> lands() { ensure(); return lands; }
    static synchronized List<String> load() { ensure(); return lands.stream().map(Land::name).toList(); }
    static synchronized Map<String, String> aliases() { ensure(); return Map.copyOf(ALIASES); }

    /** Stores the entries of a read (menu names are turned into real names through the remembered aliases) and asks the site about any entry that is still unexplained. */
    static void record(List<LandEntries.Entry> entries, Map<String, ItemStack> stacks, Set<String> unverified) {
        ensure();
        store(entries, stacks);
        var menuNames = entries.stream().map(LandEntries.Entry::name).toList();
        var open = unverified.stream().filter(n -> !aliasOf(n).isPresent() && menuNames.contains(n)).collect(java.util.stream.Collectors.toSet());
        if (open.isEmpty()) return;
        // Only entries no alias explains are put to the site; the answer is remembered.
        LandResolver.aliases(menuNames, open).whenComplete((found, failure) -> Minecraft.getInstance().execute(() -> {
            if (failure != null || found.isEmpty()) return;
            synchronized (LandCache.class) { ALIASES.putAll(found); }
            store(entries, stacks);
        }));
    }
    private static synchronized java.util.Optional<String> aliasOf(String menuName) { return java.util.Optional.ofNullable(ALIASES.get(menuName)); }

    /** The stack each menu entry had when it was last encoded, so an unchanged icon is not encoded (or decoded) again by the next read. */
    private static final Map<String, ItemStack> SEEN = new HashMap<>();
    private static final java.util.concurrent.ExecutorService DISK = java.util.concurrent.Executors.newSingleThreadExecutor(r -> { var t = new Thread(r, "paletale-lands"); t.setDaemon(true); return t; });

    private static synchronized void store(List<LandEntries.Entry> entries, Map<String, ItemStack> stacks) {
        var level = Minecraft.getInstance().level;
        var list = new ArrayList<Land>(); var lines = new ArrayList<String>();
        for (var entry : entries) {
            String name = ALIASES.getOrDefault(entry.name(), entry.name());
            if (list.stream().anyMatch(l -> l.name().equals(name))) continue;
            String json = JSON.get(name);
            // A name that turned out to be another one (an alias found later) takes over the icon already encoded for the menu name.
            if (json == null && !name.equals(entry.name()) && JSON.containsKey(entry.name())) { json = JSON.get(entry.name()); JSON.put(name, json); }
            var stack = stacks.get(entry.name()); var seen = SEEN.get(entry.name());
            if (stack != null && level != null && (json == null || seen == null || !ItemStack.isSameItemSameComponents(seen, stack))) {
                var encoded = ItemStack.CODEC.encodeStart(level.registryAccess().createSerializationContext(JsonOps.INSTANCE), stack.copyWithCount(1)).result();
                if (encoded.isPresent()) { json = encoded.get().toString(); JSON.put(name, json); DECODED.remove(name); SEEN.put(entry.name(), stack.copyWithCount(1)); }
            }
            list.add(new Land(name, entry.nation(), entry.owned()));
            lines.add(name + "\t" + (json == null ? "" : json) + "\t" + entry.nation() + "\t" + (entry.owned() ? "1" : "0"));
        }
        for (var alias : ALIASES.entrySet()) lines.add("@alias\t" + alias.getKey() + "\t" + alias.getValue());
        lands = List.copyOf(list); version++;
        var snapshot = List.copyOf(lines);
        DISK.execute(() -> {
            try { var file = ModPaths.config().resolve("lands.txt"); Files.createDirectories(file.getParent()); Files.write(file, snapshot, StandardCharsets.UTF_8); }
            catch (java.io.IOException ignored) { /* the list still works until the game closes */ }
        });
    }

    /** Whether some cached territory has no saved icon (a list written by an older version): worth one silent refresh. */
    static synchronized boolean missingIcons() { ensure(); return lands.stream().anyMatch(l -> !JSON.containsKey(l.name())); }

    /** The territory's own icon: the saved one, else whatever the chat channels learned, else a grass block. */
    static synchronized ItemStack icon(String name) {
        ensure();
        var held = DECODED.get(name);
        if (held != null) return held;
        var level = Minecraft.getInstance().level; String json = JSON.get(name);
        if (json != null && level != null) {
            try {
                var parsed = ItemStack.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(json)).result();
                if (parsed.isPresent() && !parsed.get().isEmpty()) { DECODED.put(name, parsed.get()); return parsed.get(); }
            } catch (RuntimeException broken) { JSON.remove(name); }
        }
        return ChatChannelIcons.icon("领地:" + name);
    }
}
