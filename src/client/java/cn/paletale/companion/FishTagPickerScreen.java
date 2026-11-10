package cn.paletale.companion;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import static cn.paletale.companion.MarketTheme.*;

/** Multi-select for the fish archive's tags (same set as the website): every chosen tag must match; 可获得 and 已绝版 exclude each other. */
final class FishTagPickerScreen extends WindowScreen {
    static final String[] OPTIONS = { "可获得", "已绝版", "主世界", "血肉", "地狱", "末地", "事件", "第一赛季", "第二赛季" };
    private final Screen parent;
    private final Set<String> chosen;
    private final Runnable changed;
    FishTagPickerScreen(Screen parent, Set<String> chosen, Runnable changed) {
        super(Component.literal("鱼类标签")); this.parent = parent; this.chosen = chosen; this.changed = changed;
    }
    @Override protected void init() {
        fitWindow();
        int columns = 3, cell = Math.min(120, (width - 40) / columns), rowHeight = 24;
        int rows = (OPTIONS.length + columns - 1) / columns;
        int left = (width - columns * cell) / 2, top = Math.max(44, (height - rows * rowHeight) / 2 - 10);
        for (int index = 0; index < OPTIONS.length; index++) {
            String tag = OPTIONS[index];
            addRenderableWidget(new Action(tag, left + index % columns * cell + 2, top + index / columns * rowHeight, cell - 4, 20, chosen.contains(tag), null, b -> {
                if (!chosen.remove(tag)) {
                    if (tag.equals("可获得")) chosen.remove("已绝版");
                    if (tag.equals("已绝版")) chosen.remove("可获得");
                    chosen.add(tag);
                }
                rebuildWidgets();
            }));
        }
        int y = top + rows * rowHeight + 10;
        addRenderableWidget(new Action("清除标签", left + 2, y, cell - 4, 20, false, null, b -> { chosen.clear(); rebuildWidgets(); }));
        addRenderableWidget(new Action("完成", left + columns * cell - cell + 2, y, cell - 4, 20, true, null, b -> onClose()));
    }
    @Override protected void windowBackground(GuiGraphics g, int x, int y, float delta) { g.fill(0, 0, width, height, PAPER); }
    @Override protected void renderWindow(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderWindow(g, mouseX, mouseY, delta);
        text(g, "选择鱼类标签 · 同时满足所选全部标签；可获得与已绝版互斥", 20, 18, width - 40, INK);
    }
    @Override public void onClose() { minecraft.setScreen(parent); changed.run(); }
    @Override public boolean isPauseScreen() { return false; }
}
