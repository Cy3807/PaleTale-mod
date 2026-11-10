package cn.paletale.companion;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;
import static cn.paletale.companion.ArchiveData.*;
import static cn.paletale.companion.MarketTheme.*;

/** Picks one wine effect (or all / unknown) for the item archive; Esc keeps the current choice. */
final class EffectPickerScreen extends WindowScreen {
    private final Screen parent;
    private final String current;
    private final Consumer<String> choose;
    EffectPickerScreen(Screen parent, String current, Consumer<String> choose) {
        super(Component.literal("酒的效果")); this.parent = parent; this.current = current; this.choose = choose;
    }
    @Override protected void init() {
        fitWindow();
        // "All effects" + every effect + "unknown".
        java.util.List<String[]> entries = new java.util.ArrayList<>();
        entries.add(new String[]{ "", "全部效果" });
        entries.addAll(java.util.Arrays.asList(WINE_EFFECTS));
        int columns = 4, cell = Math.min(110, (width - 40) / columns), rowHeight = 24;
        int rows = (entries.size() + columns - 1) / columns;
        int left = (width - columns * cell) / 2, top = Math.max(44, (height - rows * rowHeight) / 2);
        for (int index = 0; index < entries.size(); index++) {
            String key = entries.get(index)[0], label = entries.get(index)[1];
            // Four leading spaces leave room for the icon drawn in render().
            addRenderableWidget(new Action((key.isEmpty() ? "" : "    ") + label, left + index % columns * cell + 2, top + index / columns * rowHeight, cell - 4, 20, key.equals(current), null, b -> {
                minecraft.setScreen(parent); choose.accept(key);
            }));
        }
    }
    @Override protected void windowBackground(GuiGraphics g, int x, int y, float delta) { g.fill(0, 0, width, height, PAPER); }
    @Override protected void renderWindow(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderWindow(g, mouseX, mouseY, delta);
        text(g, "选择酒的效果 · 与 2D/3D 展示筛选互不影响", 20, 18, width - 40, INK);
        for (var child : children()) if (child instanceof Action button) {
            String key = keyOf(button);
            if (!key.isEmpty()) EffectIcons.draw(g, key, button.getX() + 4, button.getY() + 4, 12);
        }
    }
    private static String keyOf(Action button) {
        String label = button.getMessage().getString().strip();
        for (String[] effect : WINE_EFFECTS) if (effect[1].equals(label)) return effect[0];
        return "";
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
