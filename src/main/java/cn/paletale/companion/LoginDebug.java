package cn.paletale.companion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A step-by-step record of the sign-in, for finding out why it fails for a particular player: the conditions that gate it, every stage (the site's challenge, Mojang's join, the
 * site's completion) with its duration, and each failure down its chain of causes. Written to paletale/login-debug.log (the old half is kept as .1 once it passes 400 KB).
 * It never holds a token, a password, the Minecraft access token or the one-use challenge: anything shaped like one is blanked before it is written.
 */
public final class LoginDebug {
    private static volatile Path file;
    private static final Object LOCK = new Object();
    private static String lastState = ""; private static long lastStateAt;
    private LoginDebug() {}

    /** Where the log goes; nothing is written before this is set. */
    public static void use(Path target) { file = target; }

    static String clean(String text) {
        if (text == null) return "";
        return text.replaceAll("[\\r\\n]+", " ").replaceAll("(?i)bearer\\s+\\S+", "Bearer <hidden>").replaceAll("[A-Fa-f0-9]{40,64}", "<hex>").replaceAll("[A-Za-z0-9_-]{43,}", "<token>");
    }

    public static void log(String text) {
        var target = file;
        if (target == null) return;
        synchronized (LOCK) {
            try {
                Files.createDirectories(target.getParent());
                if (Files.exists(target) && Files.size(target) > 400_000) Files.move(target, target.resolveSibling(target.getFileName() + ".1"), StandardCopyOption.REPLACE_EXISTING);
                Files.writeString(target, LocalDateTime.now() + " " + clean(text) + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException | RuntimeException ignored) { /* a diagnostic must never break the sign-in */ }
        }
    }

    /** The failure with every cause under it: "IOException: timed out <- ConnectException: ...". */
    public static void failure(String what, Throwable error) {
        var chain = new StringBuilder(what).append(": ");
        int depth = 0;
        for (Throwable each = error; each != null && depth < 6; each = each.getCause(), depth++) {
            if (depth > 0) chain.append(" <- ");
            String message = each.getMessage();
            chain.append(each.getClass().getName()).append(message == null ? "" : ": " + (message.length() > 300 ? message.substring(0, 300) : message));
        }
        log(chain.toString());
    }

    /** Logs the gate's state only when it changed (and at most every few seconds), so waiting costs one line, not thousands. */
    public static void watch(String state) {
        long now = System.currentTimeMillis();
        if (state.equals(lastState) || now - lastStateAt < 2_000) return;
        lastState = state; lastStateAt = now;
        log("gate " + state);
    }

    /** The last lines of the log, for pasting into a bug report. */
    public static String tail(int lines) {
        var target = file;
        if (target == null) return "";
        synchronized (LOCK) {
            try {
                List<String> all = Files.isRegularFile(target) ? Files.readAllLines(target, StandardCharsets.UTF_8) : List.of();
                return String.join("\n", all.subList(Math.max(0, all.size() - lines), all.size()));
            } catch (IOException | RuntimeException unreadable) { return ""; }
        }
    }
    public static Path path() { return file; }
}
