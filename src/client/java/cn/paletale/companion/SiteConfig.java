package cn.paletale.companion;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Which PaleTale entry the mod talks to: the main site (www.paletale.cn) or the backup entry (pt.cy3807.com, the same server
 * behind a mainland proxy). The one setting lives in paletale/config/paletale.properties (earlier builds: paletale_companion.properties) as site=main|backup; the in-game
 * settings screen (Cloth Config) and the /pt net command change it. -Dpaletale.site=preview is a developer-only override.
 */
final class SiteConfig {
    static final URI MAIN = URI.create("https://www.paletale.cn"), BACKUP = URI.create("https://pt.cy3807.com"),
            PREVIEW = URI.create("https://preview.paletale.cn"), LOCAL = URI.create("http://127.0.0.1:3000");
    private static final String MODE = mode();
    private static volatile URI current = "local".equals(MODE) ? LOCAL : "preview".equals(MODE) ? PREVIEW : "backup".equals(MODE) ? BACKUP : MAIN;
    private SiteConfig() {}
    static URI current() { return current; }
    static boolean local() { return current.equals(LOCAL); }
    static boolean preview() { return current.equals(PREVIEW); }
    static boolean backup() { return current.equals(BACKUP); }
    static String label(URI site) { return site.equals(LOCAL) ? "本地测试站（localhost:3000）" : site.equals(BACKUP) ? "备用站（pt.cy3807.com）" : site.equals(PREVIEW) ? "预览站" : "主站（www.paletale.cn）"; }
    private static Path file() { return ModPaths.config().resolve("paletale.properties"); }
    /** Before the mod id became "paletale" the file was paletale_companion.properties; it is still read when the new one does not exist. */
    private static Path readable() {
        return ModPaths.readable("paletale.properties");
    }
    private static String mode() {
        String value = System.getProperty("paletale.site", "");
        if (value.isBlank()) try {
            if (Files.isRegularFile(readable())) {
                Properties properties = new Properties();
                try (var in = Files.newBufferedReader(readable())) { properties.load(in); }
                value = properties.getProperty("site", "");
            }
        } catch (Exception ignored) { /* A broken file just means the default. */ }
        value = value.trim().toLowerCase(Locale.ROOT);
        // Earlier test builds wrote official / mainland.
        return value.equals("mainland") ? "backup" : value.equals("official") ? "main" : value;
    }
    /** Remembers the choice (main or backup) in the config file, keeping any other lines the player has there. */
    static void save(URI site) {
        current = site;
        try {
            Properties properties = new Properties();
            if (Files.isRegularFile(readable())) try (var in = Files.newBufferedReader(readable())) { properties.load(in); }
            properties.setProperty("site", site.equals(LOCAL) ? "local" : site.equals(PREVIEW) ? "preview" : site.equals(BACKUP) ? "backup" : "main");
            Files.createDirectories(file().getParent());
            try (var out = Files.newBufferedWriter(file())) { properties.store(out, "PaleTale companion: site = main | backup"); }
        } catch (Exception ignored) { /* The choice still applies until the game closes. */ }
    }
    /** At start-up on the main site: if it does not answer, the backup entry is used for this session (not saved). */
    static void probe(Consumer<URI> switchTo) {
        if (!current.equals(MAIN)) return;
        HttpClient http = HttpBodies.CONTROL;
        reachable(http, MAIN).thenCompose(ok -> ok ? java.util.concurrent.CompletableFuture.completedFuture(false) : reachable(http, BACKUP))
                .thenAccept(useBackup -> { if (useBackup && current.equals(MAIN)) {
                    current = BACKUP; switchTo.accept(BACKUP);
                    NetworkTrace.note("启动探测：主站不可达，本会话改用备用站");
                } });
    }
    private static java.util.concurrent.CompletableFuture<Boolean> reachable(HttpClient http, URI site) {
        HttpRequest request = HttpBodies.request(site.resolve("/api/mcmod/version")).timeout(Duration.ofSeconds(6)).GET().build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.discarding()).handle((response, failure) -> failure == null && response.statusCode() == 200);
    }
}
