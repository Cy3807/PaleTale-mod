package cn.paletale.companion;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import org.joml.Vector2i;
import org.joml.Vector2ic;

/**
 * Widget tooltips are positioned in the window's own (scaled, shifted) coordinates, but the game draws them in real screen coordinates; while a
 * {@link WindowScreen} draws its window, the tooltip's position is converted back (see WindowTooltipMixin).
 */
public final class WindowTooltips {
    private static WindowScreen current;
    private static boolean converting;
    private WindowTooltips() {}
    static void enter(WindowScreen screen) { current = screen; }
    static void leave() { current = null; }
    /** True while a window is being drawn and the tooltip being set still has window coordinates. */
    public static boolean pending() { return current != null && !converting; }
    public static void converting(boolean on) { converting = on; }
    /**
     * Where a widget's tooltip goes: beside the pointer (the game's default placement), converted to real screen coordinates. The widget's own
     * placement (above or below the widget's frame) is dropped: on tall cards it put the text far from the pointer.
     */
    public static ClientTooltipPositioner convert(ClientTooltipPositioner ignored) {
        WindowScreen screen = current;
        ClientTooltipPositioner inWindow = DefaultTooltipPositioner.INSTANCE;
        if (screen == null) return inWindow;
        return (screenWidth, screenHeight, mouseX, mouseY, tooltipWidth, tooltipHeight) -> {
            double scale = screen.windowScale;
            Vector2ic spot = inWindow.positionTooltip(screen.width, screen.height, mouseX, mouseY, (int) Math.ceil(tooltipWidth / scale), (int) Math.ceil(tooltipHeight / scale));
            int x = screen.winX + (int) Math.round(spot.x() * scale), y = screen.winY + (int) Math.round(spot.y() * scale);
            return new Vector2i(Math.max(0, Math.min(x, screenWidth - tooltipWidth)), Math.max(0, Math.min(y, screenHeight - tooltipHeight)));
        };
    }
}
