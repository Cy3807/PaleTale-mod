package cn.paletale.companion;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;

/**
 * Last line of defence for the territory / nation menus: any command the mod itself sends that could answer with one of them
 * (/l, /l menu, /l spawn …, /l player …, /n …) arms a short window, and a menu that still reaches the screen inside it is
 * closed before it is drawn. The readers (LandMenu, PlayerQuery, ChatChannelIcons) hide their own menus; this catches what they
 * cannot: a late answer after their timeout, a name the server did not recognise and answered with the menu, a screen change
 * while the command was queued. Menus the player opened by typing the command themselves are never armed and stay visible.
 */
public final class LandMenuGuard {
    private static final long WINDOW = 12_000L;
    private static long until, closedUntil; private static Object connection; private static int closedId = -1;
    private static long userUntil;
    private LandMenuGuard() {}

    /** The player asked for a menu themselves (command wheel): for a few seconds nothing is hidden, nothing in the background is sent, and no armed window stays. */
    static void userSent() { userUntil = System.currentTimeMillis() + 4000; until = 0; }
    /** A background read has begun: the menu it asks for must stay hidden, whatever the player opened a moment ago. */
    static void readStarted() { userUntil = 0; }
    /** True while a menu the player asked for may show: no background read of ours is waiting for a menu of its own. */
    public static boolean playerOwnsMenu() { return userActive() && !LandMenu.busy() && !PlayerQuery.busy() && !ChatChannelIcons.busy() && !LandHere.busy(); }
    public static boolean userActive() { return System.currentTimeMillis() < userUntil; }

    /** Every command the mod sends on its own goes through here first. */
    static void sent(String command) {
        if (command == null || !command.strip().toLowerCase(Locale.ROOT).matches("(l|land|lands|n|nation|nations)(\\s.*)?")) return;
        until = System.currentTimeMillis() + WINDOW; connection = Minecraft.getInstance().getConnection();
    }

    /** True (the screen is cancelled and the menu closed on the server) for a land / nation menu that arrives in the window. */
    public static boolean opening(Screen screen) {
        var client = Minecraft.getInstance(); long now = System.currentTimeMillis();
        // A reader that is reading a menu on purpose owns it; the guard never closes a menu from under it.
        if (LandMenu.busy() || PlayerQuery.busy() || ChatChannelIcons.busy() || LandHere.busy()) return false;
        if (now >= until || client.player == null || client.getConnection() == null || client.getConnection() != connection) return false;
        if (!(screen instanceof AbstractContainerScreen<?> menu) || client.player.containerMenu != menu.getMenu() || !looksLikeLands(menu.getTitle().getString())) return false;
        int id = menu.getMenu().containerId;
        client.getConnection().send(new ServerboundContainerClosePacket(id));
        client.player.containerMenu = client.player.inventoryMenu;
        closedId = id; closedUntil = now + 5000; until = 0;
        return true;
    }

    /** The server's own close for a menu already closed here must not close whatever the player has open now. */
    public static boolean closing(int id) {
        var client = Minecraft.getInstance();
        return id == closedId && System.currentTimeMillis() < closedUntil && client.getConnection() == connection;
    }

    /** The title of a territory or nation menu. The server's pack decorates titles with private-use glyphs, so those are ignored and the words matched anywhere. */
    static boolean looksLikeLands(String title) {
        if (title == null) return false;
        String s = title.replaceAll("§.", "").replaceAll("[\\p{Cntrl}\\p{Co}\\p{Cf}\\p{Zs}]", "").toLowerCase(Locale.ROOT);
        boolean named = s.contains("领地") || s.contains("领土") || s.contains("国家");
        return named && (s.contains("菜单") || s.contains("列表") || s.contains("我的") || s.contains("你的") || s.contains("所有"))
                || s.matches(".*(?:my|your)?lands?(?:menu|list)?.*") && s.contains("land");
    }
}
