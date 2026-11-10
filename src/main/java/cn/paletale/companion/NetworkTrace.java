package cn.paletale.companion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Every HTTP exchange the mod makes, timed and counted: which endpoint, how long until the first byte arrived, how long the whole answer took,
 * which protocol (HTTP/2 or the HTTP/1.1 fallback), whether it was retried, and the kind of failure when there was one. The log is the raw material
 * a player sends after a bad connection; {@link #summary()} turns it into a few lines that fit in a chat message.
 *
 * <p>Privacy: only the entry host and the path are written — never the query string (it carries searches), never a header, token, body or account name.
 * The log is bounded (rotated once at 2 MB, 600 exchanges kept in memory) so a long session cannot grow without limit.</p>
 */
public final class NetworkTrace {
    /** One finished request attempt. {@code status == 0} means it never got an HTTP answer; {@code error} then names the failure kind. */
    public record Exchange(long at, String site, String method, String path, int status, String protocol,
                           long totalMs, long ttfbMs, long bytes, int attempt, String error) {}

    private static final int KEEP = 600;
    private static final int ERRORS_KEPT = 8;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private static final Map<String, String> ERROR_LABELS = Map.of(
            "timeout", "超时", "connect", "连接失败", "dns", "域名解析失败", "tls", "TLS 失败",
            "reset", "连接重置", "body", "响应中断", "io", "网络中断", "http", "HTTP 非 200", "other", "其他");

    private static final Object LOCK = new Object();
    private static final Deque<Exchange> RECENT = new ArrayDeque<>();
    private static final Map<String, Stat> PATHS = new LinkedHashMap<>();
    private static final Map<String, Integer> ERRORS = new LinkedHashMap<>();
    private static volatile Path file;
    private static volatile Path summaryFile;
    private static long startedAt;
    private static String session = "";
    private static long total, networkFailures, httpFailures, retried, fallbacks;
    private static long probes, probesOk;
    private static final Deque<String> PROBE_LOG = new ArrayDeque<>();

    private NetworkTrace() {}

    /** Where the raw log goes; nothing is written before this is set. */
    public static void use(Path target) { file = target; }

    /** One line at start-up naming the build and the machine, so a pasted log is self-describing. */
    public static void begin(String modVersion, String minecraft, String java, String system, String site) {
        session = modVersion + " · Minecraft " + minecraft + " · Java " + java + " · " + system;
        startedAt = System.currentTimeMillis();
        write("# PaleTale network log v1 · " + session + " · site " + site
                + System.lineSeparator() + "# time\tsite\tmethod\tpath\tstatus\tprotocol\ttotal_ms\tttfb_ms\tbytes\tattempt\terror");
    }

    /** Records the attempt and, when a file is configured, appends it. Safe from any thread. */
    public static void record(Exchange exchange) {
        write(exchange.at() <= 0 ? line(exchange) : format(exchange));
        synchronized (LOCK) {
            total++;
            if (exchange.attempt() > 1) retried++;
            if (exchange.status() == 0) networkFailures++;
            else if (exchange.status() >= 400) httpFailures++;
            else if (!exchange.error().isEmpty()) networkFailures++; /* headers arrived, the body did not */
            if (!exchange.error().isEmpty()) ERRORS.merge(exchange.error(), 1, Integer::sum);
            RECENT.addLast(exchange);
            while (RECENT.size() > KEEP) RECENT.removeFirst();
            if (errorsKept() > 64) ERRORS.clear();
            String bucket = bucket(exchange);
            PATHS.computeIfAbsent(bucket, ignored -> new Stat()).add(exchange);
            if (PATHS.size() > 120) PATHS.remove(PATHS.keySet().iterator().next());
        }
    }

    private static int errorsKept() { return ERRORS.values().stream().mapToInt(Integer::intValue).sum(); }

    /** A completed HTTP/2 → HTTP/1.1 fallback is worth one line in the summary. */
    public static void fallback() { synchronized (LOCK) { fallbacks++; } }

    /**
     * One fresh-connection check taken while a request was stalled: did a brand-new connection reach the same entry? A "yes" means the old connection
     * was stuck; a "no" points at the path or the site. Kept apart from the request counters so it does not distort them.
     */
    public static void probe(String site, boolean ok, long millis, String error) {
        long now = System.currentTimeMillis();
        write(format(new Exchange(now, site, "PROBE", "/api/mcmod/version", ok ? 200 : 0, "h1.1",
                millis, millis, -1, 1, ok ? "" : error)));
        synchronized (LOCK) {
            probes++;
            if (ok) probesOk++;
            String line = CLOCK.format(LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), java.time.ZoneId.systemDefault()))
                    + " " + (ok ? "新连接可通 " : "新连接不通 ") + millis + " ms" + (ok ? "" : " · " + label(error));
            PROBE_LOG.addLast(line);
            while (PROBE_LOG.size() > 12) PROBE_LOG.removeFirst();
        }
    }

    /** A free-form line in the raw log (site switches), written as a comment so the summary and its parser ignore it. */
    public static void note(String text) {
        write("# " + STAMP.format(LocalDateTime.now()) + " " + text.replaceAll("[\\r\\n]+", " "));
    }

    static String category(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof java.util.concurrent.CompletionException || cause instanceof java.util.concurrent.ExecutionException)
                && cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof java.net.http.HttpTimeoutException || cause instanceof java.net.SocketTimeoutException) return "timeout";
        if (cause instanceof java.net.UnknownHostException || cause instanceof java.nio.channels.UnresolvedAddressException) return "dns";
        if (cause instanceof java.net.ConnectException) return "connect";
        if (cause instanceof javax.net.ssl.SSLException) return "tls";
        if (cause instanceof IOException) {
            String message = String.valueOf(cause.getMessage()).toLowerCase(Locale.ROOT);
            return message.contains("reset") || message.contains("closed") || message.contains("eof")
                    || message.contains("goaway") || message.contains("broken pipe") ? "reset" : "io";
        }
        return "other";
    }

    static String label(String error) {
        return error == null || error.isEmpty() ? "成功" : ERROR_LABELS.getOrDefault(error, error);
    }

    /**
     * The last few exchanges as one compact text block: totals, percentiles, a line per endpoint, and the most recent failures. Small enough to paste
     * into a chat message; the file holds the full detail.
     */
    public static String summary() {
        List<Exchange> events;
        Map<String, Stat> paths = new LinkedHashMap<>();
        Map<String, Integer> errors = new LinkedHashMap<>();
        long started, count, net, http, again, switched, probeCount, probeOk;
        java.util.List<String> probeLog = new ArrayList<>();
        String who;
        synchronized (LOCK) {
            events = new ArrayList<>(RECENT);
            PATHS.forEach((key, value) -> paths.put(key, value.copy()));
            errors.putAll(ERRORS);
            started = startedAt;
            count = total; net = networkFailures; http = httpFailures; again = retried; switched = fallbacks;
            probeCount = probes; probeOk = probesOk;
            probeLog.addAll(PROBE_LOG);
            who = session;
        }
        long ok = count - net - http;
        long now = System.currentTimeMillis();
        StringBuilder text = new StringBuilder();
        text.append("PaleTale 网络诊断").append(who.isEmpty() ? "" : " · " + who).append('\n');
        if (started > 0) text.append("记录 ").append(minutes(now - started)).append(" 分钟 · 请求 ").append(count)
                .append(" 次 · 成功 ").append(ok).append(percent(ok, count))
                .append(" · 网络失败 ").append(net).append(" · HTTP 错误 ").append(http)
                .append(" · 重试 ").append(again).append(" 次 · 改用 HTTP/1.1 ").append(switched).append(" 次\n");
        if (!events.isEmpty()) {
            List<Long> totals = new ArrayList<>(), firstBytes = new ArrayList<>();
            for (Exchange exchange : events) if (exchange.status() > 0) { totals.add(exchange.totalMs()); firstBytes.add(exchange.ttfbMs()); }
            text.append("耗时 p50 ").append(percentile(totals, 50)).append(" ms · p95 ").append(percentile(totals, 95))
                    .append(" ms · 首字节 p50 ").append(percentile(firstBytes, 50)).append(" ms")
                    .append(" · 最慢 ").append(totals.stream().mapToLong(Long::longValue).max().orElse(0)).append(" ms\n");
        }
        if (!errors.isEmpty()) {
            text.append("失败分类：");
            boolean first = true;
            for (String key : ERROR_LABELS.keySet()) {
                Integer value = errors.get(key);
                if (value == null || value == 0) continue;
                if (!first) text.append(" · ");
                text.append(label(key)).append(' ').append(value);
                first = false;
            }
            text.append('\n');
        }
        Map<String, long[]> sites = new LinkedHashMap<>();
        for (Exchange exchange : events) {
            long[] site = sites.computeIfAbsent(exchange.site(), ignored -> new long[3]);
            site[0]++;
            if (exchange.status() > 0 && exchange.status() < 400) site[1]++;
            if (exchange.error().equals("timeout")) site[2]++;
        }
        if (!sites.isEmpty()) {
            text.append("入口：\n");
            for (Map.Entry<String, long[]> entry : sites.entrySet()) {
                long[] site = entry.getValue();
                text.append("  ").append(entry.getKey()).append(' ').append(site[0]).append(" 次 ")
                        .append(percent(site[1], site[0]));
                if (site[2] > 0) text.append(" · 超时 ").append(site[2]);
                text.append('\n');
            }
        }
        if (probeCount > 0) {
            text.append("停顿探针：").append(probeCount).append(" 次 · 新连接可通 ").append(probeOk).append(" 次");
            text.append(probeOk > 0 ? "（旧连接卡住，换连接/重连有意义）" : "（新连接也不通，更像链路或服务端停顿）").append('\n');
            for (int i = Math.max(0, probeLog.size() - 3); i < probeLog.size(); i++) text.append("  ").append(probeLog.get(i)).append('\n');
        }
        if (!paths.isEmpty()) {
            text.append("接口：\n");
            List<Map.Entry<String, Stat>> sorted = new ArrayList<>(paths.entrySet());
            sorted.sort(Comparator.comparingLong((Map.Entry<String, Stat> entry) -> entry.getValue().count).reversed());
            int shown = 0;
            for (Map.Entry<String, Stat> entry : sorted) {
                if (shown++ >= 12) { text.append("  … 其余 ").append(sorted.size() - 12).append(" 个接口\n"); break; }
                Stat stat = entry.getValue();
                text.append("  ").append(entry.getKey()).append(' ').append(stat.count).append(" 次 ")
                        .append(stat.count == 0 ? "—" : percent(stat.ok, stat.count))
                        .append(" p50 ").append(percentile(stat.totals, 50)).append(" p95 ").append(percentile(stat.totals, 95));
                List<String> notes = new ArrayList<>();
                if (stat.network > 0) notes.add("网络失败 " + stat.network);
                if (stat.http > 0) notes.add("HTTP " + stat.http);
                if (stat.slow > 0) notes.add("超过 2 秒 " + stat.slow);
                if (!notes.isEmpty()) text.append(" · ").append(String.join(" · ", notes));
                text.append('\n');
            }
        }
        if (!events.isEmpty()) {
            text.append("最近失败：\n");
            int shown = 0;
            for (int i = events.size() - 1; i >= 0 && shown < ERRORS_KEPT; i--) {
                Exchange exchange = events.get(i);
                if (exchange.status() > 0 && exchange.status() < 400) continue;
                text.append("  ").append(CLOCK.format(LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(exchange.at()),
                                java.time.ZoneId.systemDefault())))
                        .append(' ').append(exchange.method()).append(' ').append(bucket(exchange))
                        .append(' ').append(exchange.status() == 0 ? label(exchange.error()) : "HTTP " + exchange.status())
                        .append(' ').append(exchange.totalMs()).append(" ms");
                if (exchange.attempt() > 1) text.append(" · 第 ").append(exchange.attempt()).append(" 次");
                text.append('\n');
                shown++;
            }
            if (shown == 0) text.append("  无\n");
        }
        return text.toString().stripTrailing();
    }

    /** Writes the summary beside the raw log so the player can also send the file itself. Returns null when writing is impossible. */
    public static Path writeSummary() {
        Path target = file == null ? null : file.resolveSibling("network-summary.txt");
        if (target == null) return null;
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, summary() + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            summaryFile = target;
            return target;
        } catch (IOException | RuntimeException failure) { return null; }
    }

    public static Path path() { return file; }
    public static Path summaryPath() { return summaryFile; }

    /** Test helper: forget everything recorded so far (the file, if any, is untouched). */
    static void reset() {
        synchronized (LOCK) {
            RECENT.clear(); PATHS.clear(); ERRORS.clear(); PROBE_LOG.clear();
            total = networkFailures = httpFailures = retried = fallbacks = probes = probesOk = 0;
        }
    }

    /** Endpoint buckets strip identifiers, so every page of one endpoint is counted together. */
    static String bucket(Exchange exchange) { return bucket(exchange.path()); }
    static String bucket(String path) {
        if (path == null || path.isEmpty()) return "/";
        String[] parts = path.split("/", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            out.append('/').append(part.matches("(?:[0-9]{1,10}|[a-f0-9]{24,64}|[A-Za-z0-9_-]{43})") ? "{id}" : part);
        }
        return out.length() == 0 ? "/" : out.toString();
    }

    private static String format(Exchange exchange) {
        return STAMP.format(LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(exchange.at()), java.time.ZoneId.systemDefault()))
                + '\t' + exchange.site() + '\t' + exchange.method() + '\t' + exchange.path() + '\t' + exchange.status()
                + '\t' + exchange.protocol() + '\t' + exchange.totalMs() + '\t' + exchange.ttfbMs() + '\t' + exchange.bytes()
                + '\t' + exchange.attempt() + '\t' + (exchange.error().isEmpty() ? "-" : exchange.error());
    }

    private static String line(Exchange exchange) { return format(new Exchange(System.currentTimeMillis(), exchange.site(),
            exchange.method(), exchange.path(), exchange.status(), exchange.protocol(), exchange.totalMs(),
            exchange.ttfbMs(), exchange.bytes(), exchange.attempt(), exchange.error())); }

    private static void write(String text) {
        Path target = file;
        if (target == null) return;
        synchronized (LOCK) {
            try {
                Files.createDirectories(target.getParent());
                if (Files.exists(target) && Files.size(target) > 2_000_000)
                    Files.move(target, target.resolveSibling(target.getFileName() + ".1"), StandardCopyOption.REPLACE_EXISTING);
                Files.writeString(target, text + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException | RuntimeException ignored) { /* A diagnostic must never break the request it describes. */ }
        }
    }

    private static String percent(long part, long whole) {
        return whole <= 0 ? "" : String.format(Locale.ROOT, "（%.1f%%）", part * 100.0 / whole);
    }

    private static String minutes(long millis) {
        long minutes = Math.max(0, millis) / 60_000L;
        return minutes < 60 ? String.valueOf(minutes) : (minutes / 60) + "h" + (minutes % 60) + "m";
    }

    private static long percentile(List<Long> values, int percent) {
        if (values.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compare);
        int index = (int) Math.ceil(percent / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }

    private static final class Stat {
        long count, ok, network, http, slow;
        final List<Long> totals = new ArrayList<>();
        void add(Exchange exchange) {
            count++;
            if (exchange.status() > 0 && exchange.status() < 400) { ok++; totals.add(exchange.totalMs()); }
            if (exchange.status() == 0) network++;
            else if (exchange.status() >= 400) http++;
            if (exchange.totalMs() > 2000) slow++;
        }
        Stat copy() {
            Stat copy = new Stat();
            copy.count = count; copy.ok = ok; copy.network = network; copy.http = http; copy.slow = slow;
            copy.totals.addAll(totals);
            return copy;
        }
    }

    /** A stable jitter for retry waits, so a hundred clients do not retry in the same instant. */
    static long jitter(long minimum, long maximum) {
        return minimum < maximum ? ThreadLocalRandom.current().nextLong(minimum, maximum) : minimum;
    }
}
