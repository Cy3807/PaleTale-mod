package cn.paletale.companion;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import static cn.paletale.companion.MarketTheme.*;

/** Every original receipt behind a daily row remains individually inspectable. */
final class LedgerDailyScreen extends WindowScreen {
    private final Screen parent;
    private final LedgerEntry summary;
    private final List<LedgerEntry> members;
    private int page, rows;
    LedgerDailyScreen(Screen parent, LedgerEntry summary, List<LedgerEntry> members) { super(Component.literal("每日收入明细")); this.parent = parent; this.summary = summary; this.members = members; }
    @Override protected void init() {
        fitWindow(); rows = Math.max(1, (height - 85) / 20);
        addRenderableWidget(new Action("返回", 16, height - 32, 50, 17, false, null, b -> onClose()));
        addRenderableWidget(new Action("上一页", width - 128, height - 32, 50, 17, false, null, b -> { page--; rebuildWidgets(); })).active = page > 0;
        addRenderableWidget(new Action("下一页", width - 68, height - 32, 50, 17, false, null, b -> { page++; rebuildWidgets(); })).active = (page + 1) * rows < members.size();
        for (int i = page * rows; i < Math.min(members.size(), (page + 1) * rows); i++) {
            var e = members.get(i); int y = 54 + (i - page * rows) * 20;
            addRenderableWidget(new Action("详情", width - 62, y, 44, 16, false, null, b -> minecraft.setScreen(new LedgerDetailScreen(this, e))));
        }
    }
    @Override protected void renderWindow(GuiGraphics g, int mx, int my, float delta) {
        text(g, LedgerOfficial.day(summary.time()) + " · " + summary.item(), 16, 16, width - 32, INK);
        text(g, String.format("合计 +%,.2f 云币 · %d 笔", summary.amount(), members.size()), 16, 34, width - 32, ACCENT);
        var format = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
        for (int i = page * rows; i < Math.min(members.size(), (page + 1) * rows); i++) {
            var e = members.get(i); int y = 54 + (i - page * rows) * 20;
            String time = java.time.Instant.ofEpochMilli(e.time()).atZone(LedgerStore.BEIJING).format(format);
            text(g, time + "  " + e.item() + (e.quantity() > 0 ? " ×" + e.quantity() : "") + String.format("  +%,.2f", e.amount()), 16, y + 3, width - 86, INK);
            line(g, 16, y + 18, width - 32);
        }
        super.renderWindow(g, mx, my, delta);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
