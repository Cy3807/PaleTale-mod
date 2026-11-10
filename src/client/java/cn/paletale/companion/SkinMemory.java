package cn.paletale.companion;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The skins of players this client has seen online, kept in paletale/config/skins.json (name → the signed skin data the server gave for them), so that their heads on the wheel keep
 * their own skin when they are offline. A player never seen online falls back to the game's own lookup by name.
 */
final class SkinMemory {
    private record Saved(String id, String name, String value, String signature) {}
    private static final int LIMIT = 300;
    private static final Map<String, Saved> SAVED = new LinkedHashMap<>();
    private static boolean loaded;
    private SkinMemory() {}

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try {
            var file = ModPaths.readable("skins.json");
            if (Files.isRegularFile(file)) {
                Map<String, Saved> read = new Gson().fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<LinkedHashMap<String, Saved>>() { }.getType());
                if (read != null) SAVED.putAll(read);
            }
        } catch (java.io.IOException | RuntimeException unreadable) { SAVED.clear(); }
    }

    /** Keeps the skin data of a profile the server just showed us (only when it differs from what is kept). */
    static synchronized void remember(GameProfile profile) {
        load();
        var textures = profile.properties().get("textures");
        if (textures == null || textures.isEmpty() || profile.id() == null || profile.name() == null) return;
        var first = textures.iterator().next();
        var saved = new Saved(profile.id().toString(), profile.name(), first.value(), first.signature());
        String key = profile.name().toLowerCase(java.util.Locale.ROOT);
        if (saved.equals(SAVED.get(key))) return;
        SAVED.remove(key); SAVED.put(key, saved);
        while (SAVED.size() > LIMIT) SAVED.remove(SAVED.keySet().iterator().next());
        var json = new Gson().toJson(new LinkedHashMap<>(SAVED));
        Thread.ofPlatform().daemon().start(() -> {
            try { var file = ModPaths.config().resolve("skins.json"); Files.createDirectories(file.getParent()); Files.writeString(file, json, StandardCharsets.UTF_8); }
            catch (java.io.IOException ignored) { /* kept in memory for this session */ }
        });
    }

    /** A profile with the remembered skin, or null when the player was never seen. */
    static synchronized GameProfile profile(String name) {
        load();
        var saved = SAVED.get(name.toLowerCase(java.util.Locale.ROOT));
        if (saved == null) return null;
        try { return new GameProfile(UUID.fromString(saved.id()), saved.name(), new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", saved.value(), saved.signature())))); }
        catch (RuntimeException broken) { return null; }
    }
}
