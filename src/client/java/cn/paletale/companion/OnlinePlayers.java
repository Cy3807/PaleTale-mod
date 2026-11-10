package cn.paletale.companion;

import net.minecraft.client.Minecraft;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Who is on the server right now, as far as this client can see from its own tab list (so only while the player is online). */
final class OnlinePlayers {
    private static final Pattern AVATAR = Pattern.compile("^/api/players/([0-9a-fA-F-]{32,36})/avatar");
    private OnlinePlayers() {}
    /** Null when not connected to a server: the online filter / indicator is then unavailable. */
    static Set<String> uuids(Minecraft client) {
        var connection = client.getConnection();
        if (connection == null || client.level == null) return null;
        return connection.getOnlinePlayers().stream().map(info -> compact(info.getProfile().id().toString())).collect(Collectors.toSet());
    }
    /** The player UUID an archive entry belongs to, read from its avatar path. */
    static String uuidOf(String imagePath) {
        if (imagePath == null) return null;
        var match = AVATAR.matcher(imagePath);
        return match.find() ? compact(match.group(1)) : null;
    }
    private static String compact(String uuid) { return uuid.replace("-", "").toLowerCase(Locale.ROOT); }
}
