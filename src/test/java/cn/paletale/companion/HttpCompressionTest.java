package cn.paletale.companion;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

/** Every request offers gzip, and every response body is read back correctly whether or not the server compressed it. */
final class HttpCompressionTest {
    private HttpCompressionTest() {}
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static byte[] gzip(byte[] plain) throws Exception {
        var out = new ByteArrayOutputStream();
        try (var zip = new GZIPOutputStream(out)) { zip.write(plain); }
        return out.toByteArray();
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String encoding, byte[] body) throws java.io.IOException {
        if (encoding != null) exchange.getResponseHeaders().set("Content-Encoding", encoding);
        // 304/204 不能带响应体；其余状态长度即实际字节数。
        boolean empty = body.length == 0;
        exchange.sendResponseHeaders(status, empty ? -1 : body.length);
        if (!empty) try (var out = exchange.getResponseBody()) { out.write(body); }
        exchange.close();
    }

    private static <T> java.net.http.HttpResponse<T> get(HttpServer server, String path, java.net.http.HttpResponse.BodyHandler<T> handler) throws Exception {
        var uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
        return HttpBodies.CLIENT.send(HttpBodies.request(uri).timeout(Duration.ofSeconds(5)).GET().build(), handler);
    }

    private static void fails(String message, String fragment, java.util.concurrent.Callable<?> call) throws Exception {
        try { call.call(); }
        catch (Exception failure) {
            Throwable cause = failure;
            while (cause.getCause() != null && !String.valueOf(cause.getMessage()).contains(fragment)) cause = cause.getCause();
            require(String.valueOf(cause.getMessage()).contains(fragment), message + " (got: " + cause + ")");
            return;
        }
        throw new AssertionError(message + " (no failure)");
    }

    static void run() throws Exception {
        String text = "{\"name\":\"苍白秘酿\",\"note\":\"" + "酒".repeat(2000) + "\"}";
        byte[] plain = text.getBytes(StandardCharsets.UTF_8);
        var offered = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/plain", exchange -> { offered.set(exchange.getRequestHeaders().getFirst("Accept-Encoding")); reply(exchange, 200, null, plain); });
        server.createContext("/gzip", exchange -> { try { reply(exchange, 200, "gzip", gzip(plain)); } catch (Exception failure) { throw new java.io.IOException(failure); } });
        server.createContext("/xgzip", exchange -> { try { reply(exchange, 200, "X-GZIP", gzip(plain)); } catch (Exception failure) { throw new java.io.IOException(failure); } });
        server.createContext("/identity", exchange -> reply(exchange, 200, "identity", plain));
        server.createContext("/not-modified", exchange -> reply(exchange, 304, "gzip", new byte[0]));
        server.createContext("/missing", exchange -> { try { reply(exchange, 404, "gzip", gzip("{\"message\":\"没有找到\"}".getBytes(StandardCharsets.UTF_8))); } catch (Exception failure) { throw new java.io.IOException(failure); } });
        server.createContext("/bomb", exchange -> { try { reply(exchange, 200, "gzip", gzip(new byte[4_000_000])); } catch (Exception failure) { throw new java.io.IOException(failure); } });
        server.createContext("/brotli", exchange -> reply(exchange, 200, "br", plain));
        server.createContext("/corrupt", exchange -> reply(exchange, 200, "gzip", "not gzip data at all".getBytes(StandardCharsets.UTF_8)));
        server.createContext("/big", exchange -> reply(exchange, 200, null, new byte[3000]));
        server.start();
        try {
            require(HttpBodies.request(URI.create("https://www.paletale.cn/api/mcmod/market")).build().headers().firstValue("Accept-Encoding").orElse("").equals("gzip"), "Every request offers gzip");

            require(get(server, "/plain", HttpBodies.text()).body().equals(text), "Uncompressed text passes through unchanged");
            require("gzip".equals(offered.get()), "The server sees the gzip offer: " + offered.get());
            require(get(server, "/gzip", HttpBodies.text()).body().equals(text), "Gzip text is inflated to the same UTF-8 string");
            require(get(server, "/xgzip", HttpBodies.text()).body().equals(text), "x-gzip is accepted, case-insensitively");
            require(get(server, "/identity", HttpBodies.text()).body().equals(text), "An explicit identity encoding is plain");
            require(java.util.Arrays.equals(get(server, "/gzip", HttpBodies.limited(1 << 20)).body(), plain), "Bounded bytes are inflated too");

            var notModified = get(server, "/not-modified", HttpBodies.limited(1024));
            require(notModified.statusCode() == 304 && notModified.body().length == 0, "A 304 with an encoding header and no body is not inflated");

            var missing = get(server, "/missing", HttpBodies.text());
            require(missing.statusCode() == 404 && missing.body().contains("没有找到"), "Compressed error bodies are readable");

            fails("A small gzip body that expands past the limit is refused", "too large", () -> get(server, "/bomb", HttpBodies.limited(100_000)));
            fails("An encoding that was never offered fails loudly", "Unsupported content encoding", () -> get(server, "/brotli", HttpBodies.text()));
            fails("A damaged gzip body fails instead of returning garbage", "Invalid compressed", () -> get(server, "/corrupt", HttpBodies.text()));
            fails("The limit also applies to plain bodies", "too large", () -> get(server, "/big", HttpBodies.limited(1000)));
            require(get(server, "/big", HttpBodies.limited(3000)).body().length == 3000, "A body exactly at the limit is allowed");
        } finally { server.stop(0); }
        System.out.println("HTTP compression regression: 14 checks passed");
    }
}
