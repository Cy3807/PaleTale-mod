package cn.paletale.companion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

public final class HttpBodies {
    /**
     * Two connection pools instead of one: {@link #CONTROL} carries sign-in, session checks, the heartbeat and the version check, while
     * {@link #CLIENT} carries browsing (archive, market, pictures, pages). Both speak HTTP/2 with one handshake and many requests at a time; keeping
     * them apart means a stalled picture pipeline can no longer hold the login or the heartbeat hostage on the same TCP connection.
     *
     * <p>Every exchange also goes through {@link TracingClient}: it times the first byte and the whole answer, records what happened in
     * {@link NetworkTrace}, retries a failed plain read once, can fall back from HTTP/2 to HTTP/1.1 for a site whose middleboxes mangle one of them,
     * and asks {@link StallProbe} for a fresh-connection check when an answer takes unusually long. Write requests are never retried, and neither is a
     * request that already got its headers (the failure was in the body).</p>
     */
    public static final HttpClient CLIENT = new TracingClient();
    public static final HttpClient CONTROL = new TracingClient();
    private static volatile String agent = "PaleTale/dev";
    /** Site clock minus this machine's clock, learned from Date headers so reports carry a time the site accepts even when the PC clock drifts. */
    private static volatile long clockOffset;

    /** Set once at start-up: every request names the mod and Minecraft version, so the site can tell which builds are in use. */
    public static void identify(String modVersion, String minecraftVersion) {
        String mod = modVersion.replaceAll("[^0-9A-Za-z.+-]", ""), game = minecraftVersion.replaceAll("[^0-9A-Za-z.+-]", "");
        agent = "PaleTale/" + (mod.isEmpty() ? "dev" : mod) + (game.isEmpty() ? "" : " Minecraft/" + game);
    }
    static String agent() { return agent; }

    /** The current time as the site would tell it; falls back to the local clock until a response has been seen. */
    public static long now() { return System.currentTimeMillis() + clockOffset; }

    /** Date headers are second-precision; a slow damping keeps one delayed response from skewing every later report. */
    private static void observeServerDate(String value) {
        try {
            long server = java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
            long sample = server - System.currentTimeMillis();
            if (Math.abs(sample) > 86_400_000L) return;
            long held = clockOffset;
            clockOffset = held == 0 ? sample : (held * 3 + sample) / 4;
        } catch (RuntimeException ignored) { /* an unreadable Date header changes nothing */ }
    }

    /**
     * Node development servers do not implement h2c Upgrade; HTTPS keeps its normal HTTP/2 negotiation.
     * Every request says it accepts gzip; java.net.http never inflates by itself, so every body handler below does.
     */
    public static HttpRequest.Builder request(java.net.URI uri) {
        var builder = HttpRequest.newBuilder(uri).header("User-Agent", agent).header("Accept-Encoding", "gzip");
        if ("http".equalsIgnoreCase(uri.getScheme())) builder.version(HttpClient.Version.HTTP_1_1);
        return builder;
    }

    private HttpBodies() {}

    private static HttpClient newClient(HttpClient.Version version) {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NEVER).version(version).build();
    }

    /** A plain read with a short timeout may be retried once; downloads, writes and long polls never are. */
    static boolean retryable(HttpRequest request) {
        String method = request.method();
        if (!method.equals("GET") && !method.equals("HEAD")) return false;
        if (request.timeout().orElse(Duration.ofSeconds(30)).compareTo(Duration.ofSeconds(20)) > 0) return false;
        String path = request.uri().getPath();
        return !path.startsWith("/api/mod/download") && !path.contains("/mod-downloads/");
    }

    /**
     * A retry only pays off for a connection-level failure. A timeout on a long deadline usually means the answer itself is stuck, so it is retried
     * only when the deadline was short (a startup probe or a small rules file) and waiting again costs little.
     */
    static boolean worthRetrying(String kind, HttpRequest request) {
        if (kind.equals("timeout")) return request.timeout().orElse(Duration.ofSeconds(30)).compareTo(Duration.ofSeconds(8)) <= 0;
        return kind.equals("connect") || kind.equals("reset") || kind.equals("io") || kind.equals("tls");
    }

    /** Measures when the response headers arrived (the first byte the client can use) and what they said. */
    private static final class Timed<T> implements HttpResponse.BodyHandler<T> {
        private final HttpResponse.BodyHandler<T> delegate;
        private volatile long firstByteNanos;
        private volatile int status;
        private volatile HttpClient.Version version;
        Timed(HttpResponse.BodyHandler<T> delegate) { this.delegate = delegate; }
        @Override public HttpResponse.BodySubscriber<T> apply(HttpResponse.ResponseInfo info) {
            firstByteNanos = System.nanoTime();
            status = info.statusCode();
            version = info.version();
            return delegate.apply(info);
        }
        boolean received() { return firstByteNanos != 0; }
    }

    private static final class TracingClient extends HttpClient {
        private final HttpClient h2 = newClient(HttpClient.Version.HTTP_2);
        private final HttpClient h1 = newClient(HttpClient.Version.HTTP_1_1);
        /** Sites where HTTP/2 failed at least once and HTTP/1.1 then worked: all later requests start on HTTP/1.1. */
        private final Set<String> h1Sites = ConcurrentHashMap.newKeySet();

        @Override public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException, InterruptedException {
            String site = host(request.uri());
            boolean retryable = retryable(request);
            int attempts = retryable ? 2 : 1;
            String failureKind = "";
            long tracked = StallProbe.begin(request);
            try {
                for (int attempt = 1; ; attempt++) {
                    HttpClient client = clientFor(site, attempt);
                    Timed<T> timed = new Timed<>(handler);
                    long started = System.nanoTime();
                    try {
                        HttpResponse<T> response = client.send(request, timed);
                        record(site, request, response, timed, started, attempt, "");
                        // Only a connection-level problem says something about HTTP/2; a slow answer is not a reason to pin the site to HTTP/1.1.
                        if (attempt > 1 && !failureKind.equals("timeout") && !failureKind.equals("body") && !h1Sites.contains(site)) {
                            h1Sites.add(site); NetworkTrace.fallback();
                        }
                        return response;
                    } catch (IOException failure) {
                        failureKind = failedAfterHeaders(failure, timed);
                        record(site, request, null, timed, started, attempt, failureKind);
                        if (!retryable || attempt >= attempts || timed.received() || !worthRetrying(failureKind, request)) throw failure;
                        Thread.sleep(NetworkTrace.jitter(250, 600));
                    }
                }
            } finally {
                StallProbe.end(tracked);
            }
        }

        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            return sendAsync(request, handler, null);
        }

        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler,
                                                                          HttpResponse.PushPromiseHandler<T> push) {
            return attempt(request, handler, push, 1, retryable(request), true);
        }

        private <T> CompletableFuture<HttpResponse<T>> attempt(HttpRequest request, HttpResponse.BodyHandler<T> handler,
                                                               HttpResponse.PushPromiseHandler<T> push, int attempt, boolean retryable,
                                                               boolean sticky) {
            String site = host(request.uri());
            HttpClient client = clientFor(site, attempt);
            Timed<T> timed = new Timed<>(handler);
            long started = System.nanoTime();
            long tracked = attempt == 1 ? StallProbe.begin(request) : 0;
            CompletableFuture<HttpResponse<T>> source = push == null
                    ? client.sendAsync(request, timed) : client.sendAsync(request, timed, push);
            CompletableFuture<HttpResponse<T>> next = source.handle((response, failure) -> {
                if (failure == null) {
                    record(site, request, response, timed, started, attempt, "");
                    if (attempt > 1 && sticky && !h1Sites.contains(site)) { h1Sites.add(site); NetworkTrace.fallback(); }
                    return CompletableFuture.completedFuture(response);
                }
                Throwable cause = unwrap(failure);
                String kind = failedAfterHeaders(cause, timed);
                record(site, request, null, timed, started, attempt, kind);
                if (!retryable || attempt >= 2 || timed.received() || !worthRetrying(kind, request)
                        || cause instanceof CancellationException || cause instanceof InterruptedException)
                    return CompletableFuture.<HttpResponse<T>>failedFuture(cause);
                boolean keep = sticky && !kind.equals("timeout") && !kind.equals("body");
                return CompletableFuture.supplyAsync(() -> null, CompletableFuture.delayedExecutor(
                                NetworkTrace.jitter(250, 600), java.util.concurrent.TimeUnit.MILLISECONDS))
                        .thenCompose(ignored -> attempt(request, handler, push, attempt + 1, true, keep));
            }).thenCompose(java.util.function.Function.identity());
            // Keep the caller's cancel meaningful: cancelling the chain also drops the socket work underneath it.
            next.whenComplete((value, failure) -> {
                StallProbe.end(tracked);
                if (next.isCancelled()) source.cancel(true);
            });
            return next;
        }

        private HttpClient clientFor(String site, int attempt) {
            if (h1Sites.contains(site)) return h1;
            return attempt > 1 ? h1 : h2;
        }

        private static String failedAfterHeaders(Throwable failure, Timed<?> timed) {
            return timed.received() ? "body" : NetworkTrace.category(failure);
        }

        private static void record(String site, HttpRequest request, HttpResponse<?> response, Timed<?> timed,
                                   long started, int attempt, String error) {
            long now = System.nanoTime();
            long ttfb = timed.firstByteNanos == 0 ? (now - started) / 1_000_000 : (timed.firstByteNanos - started) / 1_000_000;
            long bytes = response == null ? -1 : response.headers().firstValueAsLong("Content-Length").orElse(-1);
            HttpClient.Version version = response != null ? response.version() : timed.version;
            String protocol = version == null ? "-" : version == HttpClient.Version.HTTP_2 ? "h2" : "h1.1";
            if (response != null) response.headers().firstValue("Date").ifPresent(HttpBodies::observeServerDate);
            // Keep the status the server did send even when the body broke: a 404 with a cut body is still a 404.
            int status = response != null ? response.statusCode() : timed.status;
            NetworkTrace.record(new NetworkTrace.Exchange(System.currentTimeMillis(), site, request.method(),
                    request.uri().getPath(), status, protocol, (now - started) / 1_000_000, Math.max(0, ttfb),
                    bytes, attempt, error));
        }

        private static Throwable unwrap(Throwable failure) {
            Throwable cause = failure;
            while ((cause instanceof java.util.concurrent.CompletionException || cause instanceof java.util.concurrent.ExecutionException)
                    && cause.getCause() != null) cause = cause.getCause();
            return cause;
        }

        private static String host(java.net.URI uri) {
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            return uri.getPort() == -1 ? host : host + ":" + uri.getPort();
        }

        @Override public Optional<CookieHandler> cookieHandler() { return h2.cookieHandler(); }
        @Override public Optional<Duration> connectTimeout() { return h2.connectTimeout(); }
        @Override public Redirect followRedirects() { return h2.followRedirects(); }
        @Override public Optional<ProxySelector> proxy() { return h2.proxy(); }
        @Override public SSLContext sslContext() { return h2.sslContext(); }
        @Override public SSLParameters sslParameters() { return h2.sslParameters(); }
        @Override public Optional<Authenticator> authenticator() { return h2.authenticator(); }
        @Override public Version version() { return h2.version(); }
        @Override public Optional<java.util.concurrent.Executor> executor() { return h2.executor(); }
        @Override public WebSocket.Builder newWebSocketBuilder() { return h2.newWebSocketBuilder(); }
    }

    private static HttpResponse.BodySubscriber<byte[]> raw(int maximum) {
        return new HttpResponse.BodySubscriber<>() {
            private final CompletableFuture<byte[]> body = new CompletableFuture<>();
            private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            private Flow.Subscription subscription;
            @Override public CompletionStage<byte[]> getBody() { return body; }
            @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
            @Override public void onNext(List<ByteBuffer> buffers) {
                for (ByteBuffer buffer : buffers) {
                    if (buffer.remaining() > maximum - bytes.size()) {
                        subscription.cancel(); body.completeExceptionally(new IllegalStateException("Response too large")); return;
                    }
                    byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
                }
                subscription.request(1);
            }
            @Override public void onError(Throwable error) { body.completeExceptionally(error); }
            @Override public void onComplete() { body.complete(bytes.toByteArray()); }
        };
    }

    /** Text bodies are small JSON documents; the cap only stops a runaway response from filling memory. */
    private static final int TEXT_LIMIT = 16 * 1024 * 1024;

    /**
     * The body as bytes, at most {@code maximum} long both on the wire and after gzip is inflated, so a small compressed
     * body cannot expand past the limit. Bodies the server did not compress pass through unchanged.
     */
    public static HttpResponse.BodyHandler<byte[]> limited(int maximum) {
        return info -> {
            var subscriber = raw(maximum);
            return isGzip(info) ? HttpResponse.BodySubscribers.mapping(subscriber, bytes -> inflate(bytes, maximum)) : subscriber;
        };
    }

    /** The body as UTF-8 text, inflating gzip like {@link #limited(int)}. */
    public static HttpResponse.BodyHandler<String> text() {
        return info -> HttpResponse.BodySubscribers.mapping(limited(TEXT_LIMIT).apply(info), bytes -> new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Only gzip is ever requested, so anything else (other than identity) is a server fault and fails loudly instead of being read as garbage. */
    private static boolean isGzip(HttpResponse.ResponseInfo info) {
        int status = info.statusCode();
        if (status == 204 || status == 304) return false;
        String encoding = info.headers().firstValue("Content-Encoding").orElse("").trim();
        if (encoding.isEmpty() || encoding.equalsIgnoreCase("identity")) return false;
        if (encoding.equalsIgnoreCase("gzip") || encoding.equalsIgnoreCase("x-gzip")) return true;
        throw new IllegalStateException("Unsupported content encoding");
    }

    static byte[] inflate(byte[] compressed, int maximum) {
        if (compressed.length == 0) return compressed;
        try (var in = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(compressed)); var out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (count > maximum - out.size()) throw new IllegalStateException("Response too large");
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Invalid compressed response", failure);
        }
    }
}
