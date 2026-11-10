package cn.paletale.companion;

import net.minecraft.client.Minecraft;

/**
 * Opens Xaero's World Map centred on a place. Xaero is never a dependency: its classes are reached by name, so without it (or when a release changes these names) this simply
 * reports that nothing was opened. Client thread only.
 */
final class XaeroWorldMap {
    private XaeroWorldMap() {}

    /** True when the map screen was opened. */
    static boolean open(int x, int z) {
        try {
            Class<?> sessions = Class.forName("xaero.map.WorldMapSession");
            Object session = sessions.getMethod("getCurrentSession").invoke(null);
            if (session == null || !(boolean) sessions.getMethod("isUsable").invoke(session)) return false;
            Class<?> screenClass = Class.forName("xaero.map.gui.GuiMap");
            Object processor = sessions.getMethod("getMapProcessor").invoke(session);
            var client = Minecraft.getInstance();
            var constructor = screenClass.getConstructors()[0];
            Object screen = constructor.newInstance(client.screen, client.screen, processor, client.getCameraEntity());
            set(screenClass, screen, "cameraX", x + 0.5); set(screenClass, screen, "cameraZ", z + 0.5);
            // Without these the map would jump back to following the player when it opens.
            var reset = screenClass.getDeclaredField("shouldResetCameraPos"); reset.setAccessible(true); reset.setBoolean(screen, false);
            var attached = screenClass.getDeclaredField("attachedCamera"); attached.setAccessible(true); attached.setBoolean(null, false);
            client.setScreen((net.minecraft.client.gui.screens.Screen) screen);
            return true;
        } catch (ClassNotFoundException missing) {
            return false;
        } catch (Throwable changed) {
            ModLog.failed("Xaero 世界地图", "Xaero 世界地图版本不兼容，无法打开地图");
            return false;
        }
    }

    private static void set(Class<?> type, Object target, String field, double value) throws Exception {
        var f = type.getDeclaredField(field); f.setAccessible(true); f.setDouble(target, value);
    }
}
