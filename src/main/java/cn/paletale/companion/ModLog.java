package cn.paletale.companion;

import java.util.concurrent.ConcurrentHashMap;

/**
 * One short line in the game log when a PaleTale feature fails, so a player's latest.log tells what went wrong. Only the plain reason is written: never an
 * address, a token, a stack trace or other internals. The same message is written at most once every five minutes, so a failing loop cannot flood the log.
 */
public final class ModLog {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("PaleTale");
    private static final ConcurrentHashMap<String, Long> LAST = new ConcurrentHashMap<>();
    private static final long QUIET_MS = 5 * 60_000L;
    private ModLog() {}

    /** Writes "[PaleTale] <feature>失败：<reason>" unless the same line was written in the last five minutes. */
    public static void failed(String feature, Throwable error) { failed(feature, reason(error)); }
    public static void failed(String feature, String reason) {
        String line = feature + "失败：" + reason;
        long now = System.currentTimeMillis();
        Long last = LAST.get(line);
        if (last != null && now - last < QUIET_MS) return;
        if (LAST.size() > 200) LAST.clear();
        LAST.put(line, now);
        try { LOG.warn("[PaleTale] {}", line); } catch (Throwable ignored) { /* Logging must never break the feature that failed. */ }
    }

    /** A player-readable cause. Only messages the mod wrote itself for players (Chinese text) are passed on; anything else becomes a generic category. */
    public static String reason(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof java.util.concurrent.CompletionException || cause instanceof java.util.concurrent.ExecutionException) && cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof java.net.http.HttpTimeoutException || cause instanceof java.net.SocketTimeoutException) return "网络连接超时";
        if (cause instanceof java.net.ConnectException || cause instanceof java.nio.channels.UnresolvedAddressException || cause instanceof java.net.UnknownHostException) return "无法连接网站";
        if (cause instanceof java.io.IOException) return "网络连接中断";
        String message = cause == null ? null : cause.getMessage();
        if (message != null && message.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) && message.length() <= 80
                && !message.matches("(?s).*(https?://|\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|[A-Za-z]:\\\\|/[a-z]+/).*")) return message;
        return "未知错误";
    }
}
