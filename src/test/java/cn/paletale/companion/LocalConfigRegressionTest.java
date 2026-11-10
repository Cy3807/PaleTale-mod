package cn.paletale.companion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

final class LocalConfigRegressionTest {
    static void run() throws Exception {
        Path game = Files.createTempDirectory("paletale-config-test");
        try {
            Path old = game.resolve("config"); Files.createDirectories(old.resolve("paletale/backups"));
            Files.writeString(old.resolve("ledger.json"), "another mod's data");
            require(LocalConfigFiles.readable(game.resolve("paletale/config"), old, "ledger.json").equals(game.resolve("paletale/config/ledger.json")), "Fallback never reads another mod's generic config/ledger.json");
            Files.writeString(old.resolve("paletale.properties"), "site=backup\nautoFavorite=true\n");
            Files.writeString(old.resolve("paletale/ledger.json"), "current ledger with entries");
            Files.writeString(old.resolve("paletale/backups/ledger-2026-10-20.json"), "daily backup");
            Files.writeString(old.resolve("paletale/land-spawns.json"), "land spawns");
            Files.writeString(old.resolve("paletale-ledger.json"), "older conflicting ledger");
            Files.writeString(old.resolve("paletale_companion.properties"), "site=main\n");
            Files.writeString(old.resolve("paletale-ledger.csv"), "legacy CSV export");
            Files.writeString(old.resolve("other-mod.json"), "unrelated");
            Path target = LocalConfigFiles.migrate(game, old);
            require(target.equals(game.resolve("paletale/config")), "Configuration sits next to the paletale/cache folder");
            require(Files.readString(target.resolve("paletale.properties")).contains("site=backup"), "The modern site choice and other preferences survive");
            require(Files.readString(target.resolve("ledger.json")).equals("current ledger with entries") && Files.readString(target.resolve("backups/ledger-2026-10-20.json")).equals("daily backup"), "Ledger entries and daily backups migrate together");
            require(Files.readString(target.resolve("land-spawns.json")).equals("land spawns") && Files.readString(old.resolve("other-mod.json")).equals("unrelated"), "PaleTale extras migrate while other mods remain untouched");
            try (var files = Files.list(target.resolve("legacy-conflicts"))) { var saved = files.map(p -> { try { return Files.readString(p); } catch (Exception e) { throw new RuntimeException(e); } }).toList(); require(saved.contains("older conflicting ledger") && saved.contains("site=main\n"), "Conflicting legacy ledger and preferences remain recoverable"); }
            require(Files.readString(target.resolve("paletale-ledger.csv")).equals("legacy CSV export") && !Files.exists(old.resolve("paletale-ledger.csv")), "Legacy CSV exports also leave the config root");
            require(!Files.exists(old.resolve("paletale")) && !Files.exists(old.resolve("paletale.properties")) && !Files.exists(old.resolve("paletale-ledger.json")), "Migrated files leave the old config root");
            Files.createDirectories(old.resolve("paletale")); Files.writeString(old.resolve("paletale/ledger.json"), "current ledger with entries");
            LocalConfigFiles.migrate(game, old);
            require(!Files.exists(old.resolve("paletale/ledger.json")) && Files.readString(target.resolve("ledger.json")).equals("current ledger with entries"), "Repeating a migration is idempotent and never overwrites the current file");
            System.out.println("PaleTale config migration checks passed: 9");
        } finally { try (var files = Files.walk(game)) { for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); } }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
