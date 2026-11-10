package cn.paletale.companion;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/** A dropped plain read is tried once more; a write is never replayed, because the site may already have acted on it. */
final class NetworkRetryTest {
    private NetworkRetryTest() {}
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    static void run() throws Exception {
        NetworkTrace.reset();
        var hits = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // The first request is dropped without any response; the retry is answered.
        server.createContext("/drop-once", exchange -> {
            if (hits.getAndIncrement() == 0) { exchange.close(); return; }
            byte[] body = "{\"ok\":true}".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpRequest read = HttpBodies.request(URI.create(base + "/drop-once")).timeout(Duration.ofSeconds(5)).GET().build();
            var response = HttpBodies.CLIENT.send(read, HttpBodies.text());
            require(response.statusCode() == 200 && response.body().contains("true"), "The retried read reaches the site");
            require(hits.get() == 2, "Exactly one retry is made, hits=" + hits.get());

            hits.set(0);
            HttpRequest write = HttpBodies.request(URI.create(base + "/drop-once")).timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
            boolean failed = false;
            try { HttpBodies.CLIENT.send(write, HttpBodies.text()); }
            catch (java.io.IOException expected) { failed = true; }
            require(failed, "A dropped write fails instead of being replayed");
            require(hits.get() == 1, "The write is sent once, hits=" + hits.get());

            boolean downloadSkipped = !HttpBodies.retryable(HttpBodies.request(URI.create(base + "/api/mod/download?mc=1")).timeout(Duration.ofMinutes(5)).GET().build());
            require(downloadSkipped, "A long download is not retried on the connection pool's behalf");

            HttpRequest longRead = HttpBodies.request(URI.create(base + "/x")).timeout(Duration.ofSeconds(20)).GET().build();
            HttpRequest shortRead = HttpBodies.request(URI.create(base + "/x")).timeout(Duration.ofSeconds(6)).GET().build();
            require(!HttpBodies.worthRetrying("timeout", longRead), "A stalled long request is not waited on twice");
            require(HttpBodies.worthRetrying("timeout", shortRead), "A short probe may be retried after a timeout");
            require(HttpBodies.worthRetrying("reset", longRead), "A reset connection is retried even on a long request");
        } finally { server.stop(0); NetworkTrace.reset(); }
        System.out.println("Network retry regression: 8 checks passed");
    }
}
