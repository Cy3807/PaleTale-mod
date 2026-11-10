package cn.paletale.companion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;

/** Migrates only PaleTale-owned config files, preserving conflicting legacy copies rather than overwriting newer data. */
public final class LocalConfigFiles {
    private LocalConfigFiles() {}
    public static Path migrate(Path game, Path legacy) throws IOException {
        Path target = game.resolve("paletale/config");
        Files.createDirectories(target);
        Path old = legacy.resolve("paletale");
        if (Files.isDirectory(old, LinkOption.NOFOLLOW_LINKS)) {
            try (var paths = Files.walk(old)) {
                for (Path file : paths.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).toList()) move(file, target.resolve(old.relativize(file)), target);
            }
            try (var paths = Files.walk(old)) {
                for (Path dir : paths.sorted(Comparator.reverseOrder()).filter(p -> Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)).toList()) {
                    try { Files.delete(dir); } catch (java.nio.file.DirectoryNotEmptyException retainedLink) { /* Leave unknown symlinks untouched. */ }
                }
            }
        }
        move(legacy.resolve("paletale.properties"), target.resolve("paletale.properties"), target);
        move(legacy.resolve("paletale_companion.properties"), target.resolve("paletale.properties"), target);
        move(legacy.resolve("paletale-ledger.json"), target.resolve("ledger.json"), target);
        // Earlier releases also left CSV exports and other PaleTale-owned files directly in config/.
        if (Files.isDirectory(legacy)) try (var files = Files.list(legacy)) {
            for (Path file : files.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).toList()) {
                String name = file.getFileName().toString();
                if (name.startsWith("paletale.") || name.startsWith("paletale-") || name.startsWith("paletale_")) move(file, target.resolve(name), target);
            }
        }
        return target;
    }
    /** Only PaleTale-owned historical paths are eligible; config/ledger.json could belong to another mod. */
    public static Path readable(Path target, Path legacy, String name) {
        Path current = target.resolve(name);
        if (Files.isRegularFile(current)) return current;
        Path inFolder = legacy.resolve("paletale").resolve(name);
        if (Files.isRegularFile(inFolder)) return inFolder;
        if (name.equals("paletale.properties")) {
            for (String old : new String[]{ "paletale.properties", "paletale_companion.properties" }) if (Files.isRegularFile(legacy.resolve(old))) return legacy.resolve(old);
        }
        if (name.equals("ledger.json") && Files.isRegularFile(legacy.resolve("paletale-ledger.json"))) return legacy.resolve("paletale-ledger.json");
        return current;
    }
    private static void move(Path source, Path destination, Path root) throws IOException {
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) return;
        Files.createDirectories(destination.getParent());
        if (!Files.exists(destination)) { Files.move(source, destination); return; }
        if (Files.mismatch(source, destination) == -1) { Files.delete(source); return; }
        Path conflicts = root.resolve("legacy-conflicts"); Files.createDirectories(conflicts);
        // A conflicting ledger/settings copy remains recoverable in its entirety, including on repeated migrations.
        Files.move(source, conflicts.resolve(java.util.UUID.randomUUID() + "-" + source.getFileName()));
    }
}
