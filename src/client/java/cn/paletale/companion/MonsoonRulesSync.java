package cn.paletale.companion;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.*;

/**
 * Keeps {@link MonsoonRules} current without costing startup time or traffic: the cached copy applies
 * first, then one conditional request (If-None-Match) runs on a background thread at most every six
 * hours. An unchanged file answers 304 with no body; a failed, oversized or invalid answer leaves the
 * cached or built-in rules untouched.
 */
final class MonsoonRulesSync {
    private static final long INTERVAL_MS = 6 * 3600_000L, RETRY_MS = 10 * 60_000L;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "paletale-monsoon-rules"); t.setDaemon(true); return t; });
    private static URI current;
    private static long generation, nextCheck;
    private static CompletableFuture<Void> inFlight;

    private MonsoonRulesSync() {}

    static synchronized CompletableFuture<Void> refresh(URI site, Path cacheRoot) {
        if (!MarketApi.isAllowedSite(site)) return CompletableFuture.completedFuture(null);
        if (!site.equals(current)) { current = site; generation++; nextCheck = 0; inFlight = null; MonsoonRules.use(null); }
        if (inFlight != null) return inFlight;
        if (System.currentTimeMillis() < nextCheck) return CompletableFuture.completedFuture(null);
        long run = generation;
        nextCheck = System.currentTimeMillis() + INTERVAL_MS;
        var future = CompletableFuture.runAsync(() -> load(site, cacheRoot, run), WORKER);
        inFlight = future;
        future.whenComplete((ignored, error) -> { synchronized (MonsoonRulesSync.class) { if (run == generation) { inFlight = null; if (error != null) nextCheck = System.currentTimeMillis() + RETRY_MS; } } });
        return future.exceptionally(error -> null);
    }

    private static void load(URI site, Path root, long run) {
        Path file, tag;
        try {
            String hash = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(site.toString().getBytes(StandardCharsets.UTF_8))).substring(0, 32);
            file = root.resolve(hash + "-monsoon-rules.json");
            tag = root.resolve(hash + "-monsoon-rules.etag");
        } catch (Exception error) { throw new CompletionException(error); }
        String etag = "";
        try {
            if (Files.isRegularFile(file) && Files.size(file) <= MonsoonRules.MAX_BYTES) {
                apply(run, MonsoonRules.decode(Files.readAllBytes(file)));
                if (Files.isRegularFile(tag) && Files.size(tag) <= 200) etag = Files.readString(tag).strip();
            }
        } catch (Exception invalid) { etag = ""; /* A corrupt cache is ignored and re-downloaded in full. */ }
        try {
            var request = HttpBodies.request(site.resolve("/mcmod/monsoon-v1.json")).timeout(Duration.ofSeconds(10)).header("Accept", "application/json");
            if (!etag.isEmpty() && etag.matches("[A-Za-z0-9/\"'+=._-]{1,150}")) request.header("If-None-Match", etag);
            var response = HttpBodies.CLIENT.send(request.GET().build(), HttpBodies.limited(MonsoonRules.MAX_BYTES));
            if (response.statusCode() == 304) return;
            if (response.statusCode() != 200) throw new IllegalStateException("Monsoon rules unavailable");
            byte[] bytes = response.body();
            var rules = MonsoonRules.decode(bytes);
            if (!apply(run, rules)) return;
            String newTag = response.headers().firstValue("ETag").orElse("");
            try { Files.createDirectories(root); write(root, file, bytes); write(root, tag, newTag.getBytes(StandardCharsets.UTF_8)); }
            catch (Exception diskUnavailable) { /* The network copy stays valid in memory. */ }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CompletionException(interrupted);
        } catch (Exception offline) { throw new CompletionException(offline); }
    }

    private static void write(Path root, Path target, byte[] bytes) throws java.io.IOException {
        Path temp = Files.createTempFile(root, "monsoon-", ".tmp");
        try {
            Files.write(temp, bytes);
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }

    private static synchronized boolean apply(long run, MonsoonRules rules) {
        if (run != generation) return false;
        MonsoonRules.use(rules);
        return true;
    }

    static synchronized void resetForTest() { generation++; current = null; nextCheck = 0; inFlight = null; MonsoonRules.resetForTest(); }
}
