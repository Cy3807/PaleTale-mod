package cn.paletale.companion;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.zip.ZipFile;

/**
 * One check per client session: asks the site which companion version is newest and remembers it. A click on the version badge then downloads that build
 * ("soft update"): the new jar is checked, kept beside the installed one as "*.jar.new", and swapped in by a small helper process once the game has quit,
 * because a running jar cannot be replaced on Windows. Nothing happens without the click, and the next start loads the new version.
 */
final class ModUpdate {
    private static volatile String latest = "";
    private static long lastTry;
    private static volatile boolean changed;
    /** True once after the newest version became known, so an open screen can rebuild and show the update button. */
    static boolean consumeChange() { boolean value = changed; changed = false; return value; }
    static void begin() { resume(); newerVersion(); }
    private ModUpdate() {}
    static String installed() {
        return FabricLoader.getInstance().getModContainer("paletale").map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("0");
    }
    /** The newest version when it is newer than the installed one, else null. Triggers the one-off check on first call. */
    static String newerVersion() {
        long now = System.currentTimeMillis();
        // Until the answer arrived, ask again at most once a minute.
        if (latest.isEmpty() && now - lastTry > 60_000) { lastTry = now; check(); }
        String value = latest;
        return !value.isEmpty() && compare(value, installed()) > 0 ? value : null;
    }
    private static void check() {
        HttpBodies.CONTROL
                .sendAsync(HttpBodies.request(SiteConfig.current().resolve("/api/mcmod/version")).timeout(Duration.ofSeconds(10)).header("Accept", "application/json").GET().build(),
                        HttpBodies.text())
                .thenAccept(response -> {
                    try {
                        if (response.statusCode() != 200 || response.body().length() > 512) return;
                        var data = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
                        String value = data.get(data.has("latestBuild")?"latestBuild":"latest").getAsString();
                        if (ModVersion.published(value)) { latest = value; changed = true; }
                    } catch (RuntimeException ignored) { }
                });
    }
    static int compare(String a, String b) { return ModVersion.compare(a,b); }

    enum State { IDLE, DOWNLOADING, READY, FAILED }
    private static final long MAX_JAR = 24L * 1024 * 1024;
    private static final java.util.regex.Pattern STAGED = java.util.regex.Pattern.compile("^Paletale-([0-9A-Za-z.\\-]+?)-(1\\.[0-9.]+)\\.jar\\.new$");
    private static volatile State state = State.IDLE;
    private static volatile String progress = "", failure = "", stagedVersion = "";
    private static boolean swapScheduled;
    static State state() { return state; }
    static String progress() { return progress; }
    static String failure() { return failure; }
    static String stagedVersion() { return stagedVersion; }

    /** The installed jar when it is a plain file in the mods folder (not a development run), else null. */
    private static Path jarCache; private static boolean jarKnown;
    static synchronized Path installedJar() {
        if (!jarKnown) { jarKnown = true; jarCache = lookupJar(); }
        return jarCache;
    }
    private static Path lookupJar() {
        return FabricLoader.getInstance().getModContainer("paletale")
                .flatMap(mod -> mod.getOrigin().getPaths().stream().filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar")).findFirst()).orElse(null);
    }
    static boolean canUpdate() { return installedJar() != null && state != State.READY; }
    private static String minecraftVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft").map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("");
    }
    private static void fail(String message) { failure = message; state = State.FAILED; }

    /** The CDN the site hands out signed addresses for; anything else is refused, whatever the site's answer says. */
    private static final String CDN_HOST = "img.cy3807.com", CDN_PATH = "/paletale/public/guarded/mod-downloads/";

    /**
     * Downloads the newest build for this Minecraft version (one click, no background work). The site answers with a short-lived signed CDN address and the jar's
     * SHA-256; the file is fetched from there and must match that hash. A site that serves the jar itself (CDN off) is accepted too, without the hash.
     */
    static void download() {
        String wanted = newerVersion();
        Path jar = installedJar();
        if (state == State.DOWNLOADING || state == State.READY || wanted == null || jar == null) return;
        String game = minecraftVersion();
        if (game.isEmpty()) { fail("读不到游戏版本，请到官方 QQ 群下载。"); return; }
        state = State.DOWNLOADING; progress = "0%"; failure = "";
        var ask = HttpBodies.request(SiteConfig.current().resolve("/api/mod/download?mc=" + URLEncoder.encode(game, StandardCharsets.UTF_8) + "&mode=url")).timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json, application/java-archive").setHeader("Accept-Encoding", "identity").GET().build();
        HttpBodies.CLIENT.sendAsync(ask, HttpResponse.BodyHandlers.ofInputStream()).thenCompose(response -> {
            try {
                if (response.statusCode() != 200) { try (InputStream body = response.body()) { fail(serverMessage(body, response.statusCode())); } return java.util.concurrent.CompletableFuture.<Void>completedFuture(null); }
                if (!response.headers().firstValue("Content-Type").orElse("").contains("application/json")) { receive(jar, game, wanted, response, null, null); return java.util.concurrent.CompletableFuture.<Void>completedFuture(null); }
                String hash, signed; java.net.URI address;
                try (InputStream body = response.body()) {
                    var data = com.google.gson.JsonParser.parseString(new String(body.readNBytes(8192), StandardCharsets.UTF_8)).getAsJsonObject();
                    hash = data.get("sha256").getAsString();
                    signed = data.has("signature") && data.get("signature").isJsonPrimitive() ? data.get("signature").getAsString() : null;
                    address = java.net.URI.create(data.get("url").getAsString());
                }
                if (!hash.matches("[0-9a-f]{64}") || !"https".equals(address.getScheme()) || !CDN_HOST.equals(address.getHost()) || address.getPort() != -1 || !address.getPath().startsWith(CDN_PATH)) {
                    fail("服务器返回的下载地址不可信，已取消。请到官方 QQ 群下载。"); return java.util.concurrent.CompletableFuture.<Void>completedFuture(null);
                }
                var fetch = HttpBodies.request(address).timeout(Duration.ofMinutes(5)).header("Accept", "application/java-archive").setHeader("Accept-Encoding", "identity").GET().build();
                String expected = hash, signature = signed;
                return HttpBodies.CLIENT.sendAsync(fetch, HttpResponse.BodyHandlers.ofInputStream()).thenAccept(file -> receive(jar, game, wanted, file, expected, signature));
            } catch (IOException | RuntimeException error) { fail("下载没有完成，请稍后重试。"); return java.util.concurrent.CompletableFuture.<Void>completedFuture(null); }
        }).exceptionally(error -> { if (state == State.DOWNLOADING) fail("连不上服务器，请稍后重试。"); return null; });
    }

    /** Writes the response to a temporary file (checking size and, when known, the SHA-256), verifies it as a PaleTale jar and stages it for the swap. */
    private static void receive(Path jar, String game, String wanted, HttpResponse<InputStream> response, String sha256, String announcedSignature) {
        Path part = jar.resolveSibling(".paletale-update-" + ProcessHandle.current().pid() + ".part");
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) { fail(serverMessage(body, response.statusCode())); return; }
            long length = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (length > MAX_JAR) { fail("下载文件异常，已取消。"); return; }
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            try (var out = Files.newOutputStream(part)) {
                byte[] buffer = new byte[65536]; long done = 0; int count;
                while ((count = body.read(buffer)) > 0) {
                    done += count;
                    if (done > MAX_JAR) throw new IOException("too large");
                    out.write(buffer, 0, count); digest.update(buffer, 0, count);
                    if (length > 0) progress = (int) (done * 100 / length) + "%";
                }
            }
            String actual = java.util.HexFormat.of().formatHex(digest.digest());
            if (sha256 != null && !actual.equals(sha256)) { Files.deleteIfExists(part); fail("下载的文件和官网记录不一致，已取消。请稍后重试或到官方 QQ 群下载。"); return; }
            String version = verified(part, wanted, game);
            if (version == null) { Files.deleteIfExists(part); fail("下载的文件校验没有通过，已取消。请到官方 QQ 群下载。"); return; }
            // The offline release signature decides, whatever the website or CDN said: a jar its publisher did not sign is never staged.
            String signature = announcedSignature != null ? announcedSignature : response.headers().firstValue("X-Artifact-Signature").orElse(null);
            if (!UpdateTrust.verify("Paletale-" + version + "-" + game + ".jar", actual, signature)) { Files.deleteIfExists(part); fail("下载的文件没有有效的官方签名，已取消。请到官方 QQ 群下载。"); return; }
            Path staged = jar.resolveSibling("Paletale-" + version + "-" + game + ".jar.new");
            Files.move(part, staged, StandardCopyOption.REPLACE_EXISTING);
            stagedVersion = version; state = State.READY; changed = true;
            scheduleSwap(jar, staged);
        } catch (IOException | RuntimeException | java.security.NoSuchAlgorithmException error) {
            try { Files.deleteIfExists(part); } catch (IOException ignored) { }
            fail("下载没有完成，请稍后重试。");
        }
    }
    private static String serverMessage(InputStream body, int status) {
        try {
            var data = com.google.gson.JsonParser.parseString(new String(body.readNBytes(4096), StandardCharsets.UTF_8)).getAsJsonObject();
            if (data.has("message")) return data.get("message").getAsString();
        } catch (IOException | RuntimeException ignored) { }
        return "服务器暂时不能下载（" + status + "），请稍后重试。";
    }
    /** The jar's own version when it really is a newer PaleTale build for this Minecraft version (mod id, version and game dependency read from the jar), else null. */
    private static String verified(Path file, String announced, String game) {
        try (var zip = new ZipFile(file.toFile())) {
            var entry = zip.getEntry("fabric.mod.json");
            if (entry == null || entry.getSize() > 65536) return null;
            try (var in = zip.getInputStream(entry)) {
                var data = com.google.gson.JsonParser.parseString(new String(in.readNBytes(65537), StandardCharsets.UTF_8)).getAsJsonObject();
                String version = data.get("version").getAsString();
                // Built for exactly this Minecraft version: Fabric refuses a mod whose dependency does not match, so a wrong build must never replace a working one.
                var depends = data.getAsJsonObject("depends");
                boolean forThisGame = depends != null && depends.has("minecraft") && depends.get("minecraft").isJsonPrimitive() && game.equals(depends.get("minecraft").getAsString());
                boolean ok = forThisGame && "paletale".equals(data.get("id").getAsString()) && ModVersion.published(version) && compare(version, installed()) > 0 && compare(version, announced) >= 0;
                return ok ? version : null;
            }
        } catch (IOException | RuntimeException invalid) { return null; }
    }

    /** At start-up: a jar downloaded earlier whose swap never ran is picked up again; one that is no longer newer is removed. */
    static void resume() {
        Path jar = installedJar();
        if (jar == null) return;
        try (var files = Files.list(jar.getParent())) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String name = file.getFileName().toString();
                // A download cut short by a crash leaves a ".part"; one that another running game is still writing is younger than an hour and left alone.
                if (name.startsWith(".paletale-update-") && name.endsWith(".part")) { if (System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis() > 3_600_000L) Files.deleteIfExists(file); continue; }
                var match = STAGED.matcher(name);
                if (!match.matches()) continue;
                if (ModVersion.published(match.group(1)) && compare(match.group(1), installed()) > 0) { stagedVersion = match.group(1); state = State.READY; leftover = true; scheduleSwap(jar, file); }
                else Files.deleteIfExists(file);
            }
        } catch (IOException | RuntimeException ignored) { }
    }

    /** True when this start found a downloaded jar whose swap never happened (the helper was killed, or the game was closed by force). */
    private static volatile boolean leftover;
    static boolean leftover() { return leftover; }

    /**
     * The swap is done by {@link UpdateHelper}, started right away as its own windowless JVM (not from a shutdown hook, which a launcher that kills the game, or a crash, never
     * lets run; and not as a batch file, whose console window would sit open all session on Windows). It waits for this process to end, moves the installed jar aside
     * (retrying for two minutes while it is still locked), puts the new jar in place and logs every step to paletale/update.log. A failed swap leaves the old version working
     * and the downloaded file in place; the next start finds it and tries again.
     */
    private static synchronized void scheduleSwap(Path oldJar, Path staged) {
        if (swapScheduled) return;
        swapScheduled = true;
        Path finalJar = staged.resolveSibling(staged.getFileName().toString().replaceFirst("\\.new$", ""));
        try {
            Path dir = Files.createTempDirectory("paletale-update-");
            Path classFile = dir.resolve("cn/paletale/companion/UpdateHelper.class");
            Files.createDirectories(classFile.getParent());
            try (InputStream in = UpdateHelper.class.getResourceAsStream("UpdateHelper.class")) {
                if (in == null) throw new IOException("helper class missing");
                Files.copy(in, classFile);
            }
            boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
            Path bin = Path.of(System.getProperty("java.home"), "bin");
            Path java = bin.resolve(windows ? "javaw.exe" : "java");
            if (!Files.isRegularFile(java)) java = bin.resolve(windows ? "java.exe" : "java");
            Path log = ModPaths.config().resolve("update.log");
            Files.createDirectories(log.getParent());
            new ProcessBuilder(java.toString(), "-Xmx16m", "-Xss512k", "-XX:+UseSerialGC", "-cp", dir.toString(), "cn.paletale.companion.UpdateHelper",
                    String.valueOf(ProcessHandle.current().pid()), staged.toString(), finalJar.toString(), oldJar.toString(), log.toString())
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException | RuntimeException error) { swapScheduled = false; fail("无法安排自动替换，请到官方 QQ 群下载。"); }
    }
}
