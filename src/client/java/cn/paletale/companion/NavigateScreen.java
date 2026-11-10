package cn.paletale.companion;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;
import static cn.paletale.companion.MarketTheme.*;

/** A pop-up over the window to choose which chest shop to go to (a table: ad, product, X Z Y); the choice starts the navigation at once. */
final class NavigateScreen extends Screen {
    private static final int ROW = 14, PAD = 10, GAP = 12;
    private final Screen parent;
    private final String title;
    private final List<NavTargets.Target> targets;
    private int scroll, panelX, panelY, panelWidth, panelHeight, rowsTop, visibleRows;
    private int[] columns = new int[5];

    NavigateScreen(Screen parent, String title, List<NavTargets.Target> targets) { super(Component.literal("导航")); this.parent = parent; this.title = title; this.targets = targets; }

    private static String[] cells(NavTargets.Target t) {
        String[] parts = t.name().split(" · ", 2);
        return new String[]{ parts[0], parts.length > 1 ? parts[1] : "", String.valueOf(t.x()), String.valueOf(t.z()), t.y() == null ? "—" : String.valueOf(t.y()) };
    }

    @Override protected void init() {
        Navigation.ensureData();
        String[] header = { "广告", "商品", "X", "Z", "Y" };
        int[] wide = new int[5];
        for (int i = 0; i < 5; i++) wide[i] = font.width(header[i]);
        for (var t : targets) { String[] row = cells(t); for (int i = 0; i < 5; i++) wide[i] = Math.max(wide[i], font.width(row[i])); }
        int room = width - 60 - 2 * PAD - 4 * GAP;
        wide[0] = Math.min(wide[0], Math.max(40, (room - wide[2] - wide[3] - wide[4]) / 2)); wide[1] = Math.min(wide[1], Math.max(40, room - wide[0] - wide[2] - wide[3] - wide[4]));
        columns = wide;
        panelWidth = Math.max(240, 2 * PAD + 4 * GAP + wide[0] + wide[1] + wide[2] + wide[3] + wide[4]);
        visibleRows = Math.max(3, Math.min(targets.size(), (height - 140) / ROW));
        panelHeight = 24 + 16 + visibleRows * ROW + 34;
        panelX = (width - panelWidth) / 2; panelY = (height - panelHeight) / 2; rowsTop = panelY + 24 + 16;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, targets.size() - visibleRows)));
        addRenderableWidget(new Action("取消导航", panelX + PAD, panelY + panelHeight - 26, 70, 17, false, null, b -> { Navigation.cancel(); onClose(); })).active = Navigation.target() != null;
        addRenderableWidget(new Action("返回", panelX + panelWidth - PAD - 50, panelY + panelHeight - 26, 50, 17, false, null, b -> onClose()));
    }

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        parent.renderBackground(g, -1, -1, delta);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        parent.render(g, -1, -1, delta); // the window below, seen through the dim
        g.nextStratum(); // Parent text, models and images must remain behind the modal dim and paper.
        g.fill(0, 0, width, height, 0x88000000);
        g.fill(panelX + 3, panelY + 4, panelX + panelWidth + 3, panelY + panelHeight + 4, 0x55000000);
        g.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PAPER);
        g.renderOutline(panelX, panelY, panelWidth, panelHeight, LINE);
        compass(g, panelX + PAD, panelY + 6, 12);
        text(g, "导航 · " + title + " · 选择去哪家", panelX + PAD + 16, panelY + 8, panelWidth - 2 * PAD - 16, INK);
        // The table: header, a line, the rows (hover highlights one).
        String[] header = { "广告", "商品", "X", "Z", "Y" };
        int[] at = new int[5]; at[0] = panelX + PAD; for (int i = 1; i < 5; i++) at[i] = at[i - 1] + columns[i - 1] + GAP;
        for (int i = 0; i < 5; i++) text(g, header[i], at[i], panelY + 27, columns[i], MUTED);
        line(g, panelX + PAD, rowsTop - 3, panelWidth - 2 * PAD);
        for (int r = 0; r < visibleRows && scroll + r < targets.size(); r++) {
            int y = rowsTop + r * ROW;
            boolean hover = mouseX >= panelX + PAD && mouseX < panelX + panelWidth - PAD && mouseY >= y && mouseY < y + ROW;
            if (hover) g.fill(panelX + PAD - 3, y - 1, panelX + panelWidth - PAD + 3, y + ROW - 2, MarketTheme.c(0xFFE4E8DE));
            String[] row = cells(targets.get(scroll + r));
            for (int i = 0; i < 5; i++) text(g, row[i], at[i], y + 1, columns[i], i == 1 ? ACCENT : INK);
        }
        if (targets.size() > visibleRows) text(g, (scroll + 1) + "–" + Math.min(targets.size(), scroll + visibleRows) + " / " + targets.size(), panelX + panelWidth / 2 - 24, panelY + panelHeight - 22, 80, MUTED);
        super.render(g, mouseX, mouseY, delta);
    }

    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double mx = event.x(), my = event.y();
            if (mx < panelX || mx >= panelX + panelWidth || my < panelY || my >= panelY + panelHeight) { onClose(); return true; }
            if (mx >= panelX + PAD && mx < panelX + panelWidth - PAD && my >= rowsTop && my < rowsTop + visibleRows * ROW) {
                int index = scroll + (int) ((my - rowsTop) / ROW);
                if (index < targets.size()) {
                    var t = targets.get(index);
                    Navigation.start(new Navigation.Target(t.name(), t.x(), t.y(), t.z(), t.kind(), t.marks()));
                    onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        scroll = Math.max(0, Math.min(Math.max(0, targets.size() - visibleRows), scroll + (vertical > 0 ? -1 : vertical < 0 ? 1 : 0)));
        return true;
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
