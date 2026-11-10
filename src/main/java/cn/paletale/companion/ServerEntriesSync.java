package cn.paletale.companion;

import com.google.gson.JsonParser;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The extra connection addresses SIMMC hands out (to players who cannot reach the usual ones) are kept on the site, in /api/mcmod/servers, so a new address needs no new mod
 * version. The file is read once at startup (the copy of the last run is applied first, so the first join already knows them) and only from a site the mod trusts. At most 16 entries,
 * each a plain host or host:port; anything else in the file is ignored.
 */
final class ServerEntriesSync {
    private static final int MAX_BYTES = 16 * 1024, MAX_ENTRIES = 16;
    private ServerEntriesSync() {}

    /** Applies the saved copy at once, then asks the site for a fresh one in the background. Never throws; a failed read keeps what is applied. */
    static void refresh(URI site, Path cacheFile) {
        try { if (Files.isRegularFile(cacheFile) && Files.size(cacheFile) <= MAX_BYTES) ServerAccess.trustRemote(parse(Files.readString(cacheFile, StandardCharsets.UTF_8))); }
        catch (Exception unreadable) { /* the site is asked next */ }
        if (!MarketApi.isAllowedSite(site)) return;
        Thread.ofPlatform().daemon().name("paletale-server-entries").start(() -> {
            try {
                var response = HttpBodies.CLIENT.send(HttpBodies.request(site.resolve("/api/mcmod/servers")).timeout(Duration.ofSeconds(10)).header("Accept", "application/json").GET().build(), HttpBodies.limited(MAX_BYTES));
                if (response.statusCode() != 200) return;
                String text = new String(response.body(), StandardCharsets.UTF_8);
                var entries = parse(text);
                ServerAccess.trustRemote(entries);
                try { Files.createDirectories(cacheFile.getParent()); Files.writeString(cacheFile, text, StandardCharsets.UTF_8); } catch (java.io.IOException diskUnavailable) { /* kept in memory */ }
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            catch (Exception offline) { /* keeps the saved copy */ }
        });
    }

    /** The entries of a server-entries file; throws for anything that is not one. */
    static List<String> parse(String text) {
        var root = JsonParser.parseString(text).getAsJsonObject();
        if (!"server-entries".equals(root.get("kind").getAsString()) || root.get("version").getAsInt() < 1) throw new IllegalArgumentException("header");
        var out = new ArrayList<String>();
        for (var element : root.getAsJsonArray("entries")) { if (out.size() >= MAX_ENTRIES) break; out.add(element.getAsString()); }
        return out;
    }
}
