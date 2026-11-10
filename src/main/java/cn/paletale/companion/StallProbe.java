package cn.paletale.companion;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * While a request is taking unusually long, asks the same entry once from a brand-new HTTP/1.1 connection. That one bit separates the two cases that
 * look identical in a normal log: "this TCP connection is stuck (loss, retransmission backoff, HTTP/2 head-of-line blocking)" from "the whole path or
 * the site is stalled". It runs at most once every 15 seconds and only while something has been waiting over 4 seconds, so it cannot become a load
 * source of its own. Long requests (downloads) are never tracked.
 */
final class StallProbe {
    private static final long THRESHOLD_MS = 4_000, COOLDOWN_MS = 15_000;
    private static final Map<Long, Waiting> ACTIVE = new ConcurrentHashMap<>();
    private static final AtomicBoolean PROBING = new AtomicBoolean();
    private static final ScheduledExecutorService WATCH = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "paletale-stall-probe");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile boolean started;
    private static volatile long lastProbe;

    private record Waiting(long at, URI uri) {}

    private StallProbe() {}

    /** Starts watching one request; returns 0 for requests that must not be tracked (long downloads). */
    static long begin(HttpRequest request) {
        if (request.timeout().orElse(Duration.ofSeconds(30)).compareTo(Duration.ofSeconds(20)) > 0) return 0;
        long id = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        ACTIVE.put(id, new Waiting(System.nanoTime(), request.uri()));
        watch();
        return id;
    }

    static void end(long id) {
        if (id != 0) ACTIVE.remove(id);
    }

    private static void watch() {
        if (started) return;
        synchronized (StallProbe.class) {
            if (started) return;
            started = true;
        }
        WATCH.scheduleWithFixedDelay(StallProbe::check, 1, 2, TimeUnit.SECONDS);
    }

    private static void check() {
        try {
            Waiting oldest = null;
            for (Waiting waiting : ACTIVE.values()) if (oldest == null || waiting.at() < oldest.at()) oldest = waiting;
            if (oldest == null) return;
            if ((System.nanoTime() - oldest.at()) / 1_000_000 < THRESHOLD_MS) return;
            long now = System.currentTimeMillis();
            if (now - lastProbe < COOLDOWN_MS) return;
            if (!PROBING.compareAndSet(false, true)) return;
            lastProbe = now;
            probe(oldest.uri());
        } catch (RuntimeException ignored) { /* a diagnostic must never break the request it observes */ }
    }

    /** One fresh connection against the same entry; the result goes into the network summary, never into the player's way. */
    private static void probe(URI stalled) {
        URI origin = origin(stalled);
        String site = stalled.getHost() == null ? "" : stalled.getHost().toLowerCase(java.util.Locale.ROOT);
        try {
            if (origin == null || !MarketApi.isAllowedSite(origin)) return;
            HttpRequest request = HttpBodies.request(origin.resolve("/api/mcmod/version"))
                    .timeout(Duration.ofSeconds(5)).header("Accept", "application/json").GET().build();
            // A client of its own means a new TCP/TLS connection: a pooled, stalled connection cannot be reused here.
            HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NEVER).build();
            long started = System.nanoTime();
            try {
                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                long elapsed = (System.nanoTime() - started) / 1_000_000;
                NetworkTrace.probe(site, response.statusCode() == 200, elapsed, response.statusCode() == 200 ? "" : "http");
            } catch (Exception failure) {
                NetworkTrace.probe(site, false, (System.nanoTime() - started) / 1_000_000, NetworkTrace.category(failure));
            } finally {
                client.shutdownNow();
            }
        } finally {
            PROBING.set(false);
        }
    }

    /** The site entry (scheme + authority + "/") of a request, which is what MarketApi.isAllowedSite accepts. */
    private static URI origin(URI uri) {
        try {
            String scheme = uri.getScheme();
            if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) return null;
            return new URI(scheme, uri.getUserInfo(), uri.getHost(), uri.getPort(), "/", null, null);
        } catch (Exception invalid) {
            return null;
        }
    }
}
