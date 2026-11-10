package cn.paletale.companion;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;

/** Silently asks which territory the player stands in: /l info opens a menu titled with the territory's name; the title is read and the menu is closed before it is drawn. No menu in time means wilderness. */
public final class LandHere {
    public static final String WILDERNESS = "荒野";
    private static final long TIMEOUT = 3000;
    private static boolean reading, sent; private static long startedAt; private static int menuId = -1; private static String title;
    private static Object connection; private static Consumer<String> done;
    private static int recentlyClosedId = -1; private static long recentlyClosedUntil; private static Object recentlyClosedConnection;
    private LandHere() {}
    static void register() { net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(LandHere::tick); }
    static boolean busy() { return reading; }

    /** Starts the silent read; false (and no callback) when something else is using menus or the command cannot go out now. The callback gets the territory name or {@link #WILDERNESS}, or null when it could not be read. */
    static boolean start(Consumer<String> then) {
        var client = Minecraft.getInstance();
        if (reading || LandMenu.busy() || PlayerQuery.busy() || ChatChannelIcons.busy() || client.player == null || client.getConnection() == null
                || client.player.containerMenu != client.player.inventoryMenu || client.screen instanceof AbstractContainerScreen<?>) return false;
        LandMenuGuard.readStarted();
        if (!ClientCommandQueue.direct("l info")) return false;
        reading = true; sent = true; startedAt = System.currentTimeMillis(); menuId = -1; title = null; done = then; connection = client.getConnection();
        return true;
    }

    /** Called before a server menu is shown: the answer to our command is taken and hidden. */
    public static boolean opening(Screen screen) {
        var client = Minecraft.getInstance();
        if (!reading || !sent || client.player == null || client.getConnection() != connection || !(screen instanceof AbstractContainerScreen<?> menu)) return false;
        if (menuId >= 0) return menu.getMenu().containerId == menuId;
        if (client.player.containerMenu != menu.getMenu()) return false;
        menuId = menu.getMenu().containerId; title = clean(menu.getTitle().getString());
        return true;
    }
    public static boolean closing(int id) {
        var client = Minecraft.getInstance();
        if (reading && id == menuId && client.getConnection() == connection) { menuId = -2; return true; }
        return id == recentlyClosedId && System.currentTimeMillis() < recentlyClosedUntil && client.getConnection() == recentlyClosedConnection;
    }

    private static void tick(Minecraft client) {
        if (!reading) return;
        if (client.player == null || client.getConnection() != connection) { finish(null); return; }
        if (title != null) {
            int id = client.player.containerMenu.containerId;
            if (menuId >= 0 && id == menuId) {
                recentlyClosedId = menuId; recentlyClosedUntil = System.currentTimeMillis() + 5000; recentlyClosedConnection = connection;
                client.getConnection().send(new ServerboundContainerClosePacket(menuId)); client.player.containerMenu = client.player.inventoryMenu;
            }
            finish(title.isEmpty() ? WILDERNESS : title); return;
        }
        if (System.currentTimeMillis() - startedAt > TIMEOUT) finish(WILDERNESS);
    }
    private static void finish(String land) {
        reading = false; var callback = done; done = null; menuId = -1; title = null; connection = null;
        if (callback != null) callback.accept(land);
    }
    private static String clean(String raw) { return raw == null ? "" : raw.replaceAll("§.", "").replaceAll("[\\p{Cntrl}\\p{Co}\\p{Cf}]", "").strip(); }
}
