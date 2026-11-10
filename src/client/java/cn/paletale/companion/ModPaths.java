package cn.paletale.companion;

import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Path;

/** Configuration and cache share the game's paletale root. Migration never replaces an existing destination. */
final class ModPaths {
    private static boolean migrated;
    private ModPaths() {}
    static synchronized Path config() {
        Path game = FabricLoader.getInstance().getGameDir(), old = FabricLoader.getInstance().getConfigDir();
        if (!migrated) {
            migrated = true;
            try { LocalConfigFiles.migrate(game, old); }
            catch (java.io.IOException failure) { System.getLogger("Paletale").log(System.Logger.Level.WARNING, "PaleTale 配置迁移未完成，旧文件保留并继续兼容读取。", failure); }
        }
        return game.resolve("paletale/config");
    }
    static Path readable(String name) {
        return LocalConfigFiles.readable(config(), FabricLoader.getInstance().getConfigDir(), name);
    }
}
