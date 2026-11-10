package cn.paletale.companion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

/**
 * The swap helper of the soft update, run as its own JVM (javaw on Windows: no console window) from a copy of this one class, so it never holds the mod jar it is about to
 * replace. It waits for the game process to end, moves the old jar aside (retrying while Windows or a virus scanner still holds it), puts the new jar in place and writes
 * every step to the log file. Only java.base is used, and there are no nested classes, so that single class file is all it needs.
 * Arguments: game pid, staged jar, final jar, old jar, log file.
 */
public final class UpdateHelper {
    private UpdateHelper() {}

    private static void log(Path file, String text) {
        try { Files.writeString(file, LocalDateTime.now() + " " + text + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
        catch (IOException | RuntimeException ignored) { /* no log is better than no update */ }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 5) return;
        long pid = Long.parseLong(args[0]);
        Path staged = Path.of(args[1]), target = Path.of(args[2]), old = Path.of(args[3]), log = Path.of(args[4]);
        Path backup = old.resolveSibling(old.getFileName() + ".bak");
        log(log, "helper started, waiting for game process " + pid + " to end: " + old.getFileName() + " -> " + target.getFileName());
        var game = ProcessHandle.of(pid);
        if (game.isPresent()) game.get().onExit().get();
        Thread.sleep(1500);
        if (!Files.exists(staged)) { log(log, "downloaded file is gone, nothing to do"); return; }
        try { Files.deleteIfExists(backup); } catch (IOException ignored) { /* replaced below */ }
        boolean aside = false;
        for (int attempt = 1; attempt <= 120 && !aside; attempt++) {
            try {
                if (Files.exists(old)) Files.move(old, backup, StandardCopyOption.REPLACE_EXISTING);
                aside = true;
            } catch (IOException busy) {
                if (attempt == 1 || attempt % 20 == 0) log(log, "old jar still in use (" + busy.getClass().getSimpleName() + "), retrying");
                Thread.sleep(1000);
            }
        }
        if (!aside) { log(log, "gave up: the old jar could not be moved, the new jar stays as " + staged.getFileName()); return; }
        try {
            Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(backup);
            log(log, "done: " + target.getFileName() + " is in place");
        } catch (IOException failed) {
            log(log, "could not move the new jar in (" + failed + "), putting the old one back");
            if (Files.exists(backup)) Files.move(backup, old, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
