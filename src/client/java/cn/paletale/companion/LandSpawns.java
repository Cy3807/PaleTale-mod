package cn.paletale.companion;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Where /l spawn put the player (x, z), remembered per territory name in paletale/config/land-spawns.json and shared through the site: the map does not say, the first trip does. */
final class LandSpawns {
    private static final Map<String, int[]> SPAWNS = new HashMap<>();
    private static final Map<String, Long> VISITED = new HashMap<>();
    private static boolean loaded;
    private static final Pattern TELEPORTED = Pattern.compile("领土\\s*>>\\s*成功传送到\\s*(.+?)\\s*领土的出生点");
    private static final HttpClient HTTP = HttpBodies.CLIENT;
    private static String pendingLand; private static int pendingTicks;
    private LandSpawns() {}

    static void register() { net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(LandSpawns::tick); }

    /**
     * Every trip to a territory's spawn point says so: 「领土>> 成功传送到 白渚 领土的出生点花费: $0.00」. That line names the territory, so no command has to be guessed; half a second later
     * (once the new position has arrived) the place where the player stands is that territory's spawn point.
     */
    static void onMessage(String raw) {
        var m = TELEPORTED.matcher(LedgerParser.clean(raw));
        if (m.find()) { pendingLand = m.group(1).strip(); pendingTicks = 10; }
    }
    private static void tick(Minecraft client) {
        if (pendingLand == null || client.player == null || --pendingTicks > 0) return;
        String land = pendingLand; pendingLand = null;
        int x = (int) Math.floor(client.player.getX()), z = (int) Math.floor(client.player.getZ());
        int[] known = get(land);
        if (known != null && known[0] == x && known[1] == z) { touch(land); return; }
        put(land, x, z);
        Navigation.spawnLearned();
        client.getToastManager().addToast(net.minecraft.client.gui.components.toasts.SystemToast.multiline(client, new net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId(),
                net.minecraft.network.chat.Component.literal("Paletale · 已记住领地传送点"), net.minecraft.network.chat.Component.literal(land + "：" + x + ", " + z)));
        upload(land, x, z);
    }
    private static final java.util.Set<String> SENT = new java.util.HashSet<>();
    /**
     * Shares remembered spawn points the site does not have yet (the upload may have failed, or the site did not know the way yet): every land whose outline box holds a remembered
     * point and for which the site has no spawn point gets it, once per session.
     */
    static void shareMissing(java.util.List<NavPlanner.Land> lands) {
        for (var land : lands) {
            if (land.spawn() != null || land.bounds() == null) continue;
            for (int[] point : all()) if (land.near(point[0], point[1], 0) && SENT.add(land.name() + "|" + point[0] + "," + point[1])) upload(land.name(), point[0], point[1]);
        }
    }
    /** Shares the spawn point with everyone (the site only takes it from a member of that land). Silent when it fails. */
    private static void upload(String land, int x, int z) {
        String token = PaleTaleClient.ACCOUNT.token();
        if (token == null) return;
        String nation = Navigation.lands().stream().filter(l -> l.name().equals(land)).map(NavPlanner.Land::nation).findFirst().orElse("");
        var body = new JsonObject(); body.addProperty("land", land); body.addProperty("nation", nation); body.addProperty("x", x); body.addProperty("z", z);
        HTTP.sendAsync(HttpBodies.request(SiteConfig.current().resolve("/api/mcmod/nav/land-spawn")).timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json").header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.discarding()).exceptionally(failure -> null);
    }
    private static Path file() { return ModPaths.readable("land-spawns.json"); }
    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.isRegularFile(file())) {
                JsonObject root = new Gson().fromJson(Files.readString(file()), JsonObject.class);
                for (var entry : root.entrySet()) { var array = entry.getValue().getAsJsonArray(); if (array.size() >= 2) { SPAWNS.put(entry.getKey(), new int[]{ array.get(0).getAsInt(), array.get(1).getAsInt() }); if (array.size() >= 3) VISITED.put(entry.getKey(), array.get(2).getAsLong()); } }
            }
        } catch (Exception ignored) { /* a broken file means nothing is known yet */ }
    }
    static synchronized int[] get(String land) { load(); return SPAWNS.get(land); }
    /** Every remembered spawn point (x, z), whatever name it was remembered under. */
    static synchronized java.util.List<int[]> all() { load(); return new java.util.ArrayList<>(SPAWNS.values()); }
    /** A territory this player has teleported to, newest first; local only, never shared. */
    record Visit(String land, int x, int z, long time) {}
    static synchronized java.util.List<Visit> visits() {
        load();
        var out = new java.util.ArrayList<Visit>();
        for (var entry : SPAWNS.entrySet()) out.add(new Visit(entry.getKey(), entry.getValue()[0], entry.getValue()[1], VISITED.getOrDefault(entry.getKey(), 0L)));
        out.sort(java.util.Comparator.comparingLong(Visit::time).reversed().thenComparing(Visit::land));
        return out;
    }
    private static synchronized void touch(String land) { load(); if (SPAWNS.containsKey(land)) { VISITED.put(land, System.currentTimeMillis()); save(); } }
    static synchronized void put(String land, int x, int z) {
        load(); SPAWNS.put(land, new int[]{ x, z }); VISITED.put(land, System.currentTimeMillis()); save();
    }
    private static void save() {
        try {
            JsonObject root = new JsonObject();
            for (var entry : SPAWNS.entrySet()) { var array = new com.google.gson.JsonArray(); array.add(entry.getValue()[0]); array.add(entry.getValue()[1]); array.add(VISITED.getOrDefault(entry.getKey(), 0L)); root.add(entry.getKey(), array); }
            Files.createDirectories(file().getParent());
            Files.writeString(file(), new Gson().toJson(root));
        } catch (Exception ignored) { /* remembered for this session at least */ }
    }
}
