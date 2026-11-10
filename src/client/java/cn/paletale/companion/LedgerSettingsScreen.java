package cn.paletale.companion;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import static cn.paletale.companion.MarketTheme.*;

/** Ledger settings: keep accounts apart, how long records are kept, where the files are. */
final class LedgerSettingsScreen extends WindowScreen {
    private final Screen parent;
    private String note = "";
    LedgerSettingsScreen(Screen parent) { super(Component.literal("账本设置")); this.parent = parent; }

    @Override protected void init() {
        fitWindow();
        int w = Math.min(340, width - 40), x = (width - w) / 2;
        addRenderableWidget(new Action(LedgerStore.separateAccounts() ? "账号：只显示当前账号" : "账号：显示全部账号", x, 36, w, 17, LedgerStore.separateAccounts(), null, b -> { LedgerStore.setSeparateAccounts(!LedgerStore.separateAccounts()); rebuildWidgets(); }));
        addRenderableWidget(new Action("保留期限：" + LedgerStore.retentionLabel(LedgerStore.retentionDays()), x, 92, w, 17, true, null, b -> {
            int current = LedgerStore.retentionDays(), next = LedgerStore.RETENTION_CHOICES[0];
            for (int i = 0; i < LedgerStore.RETENTION_CHOICES.length; i++) if (LedgerStore.RETENTION_CHOICES[i] == current) next = LedgerStore.RETENTION_CHOICES[(i + 1) % LedgerStore.RETENTION_CHOICES.length];
            LedgerStore.setRetentionDays(next); rebuildWidgets();
        }));
        addRenderableWidget(new Action("打开账本文件夹", x, 148, w, 17, false, null, b -> {
            try { java.nio.file.Files.createDirectories(LedgerStore.folder()); net.minecraft.util.Util.getPlatform().openFile(LedgerStore.folder().toFile()); note = ""; } catch (java.io.IOException | RuntimeException failed) { note = "无法自动打开，路径：" + LedgerStore.folder(); }
        }));
        addRenderableWidget(new Action("返回", x, height - 34, 70, 18, false, null, b -> onClose()));
    }
    @Override protected void windowBackground(GuiGraphics g, int x, int y, float delta) { g.fill(0, 0, width, height, PAPER); }
    @Override protected void renderWindow(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderWindow(g, mouseX, mouseY, delta);
        int w = Math.min(340, width - 40), x = (width - w) / 2;
        text(g, "账本设置", x, 14, w, INK);
        paragraph(g, "只显示当前账号时，换账号玩不会混账；改为显示全部账号，可看到文件里所有账号的记录（包括别人分享给你的账本）。", x, 56, w, 3);
        paragraph(g, "超过期限的记录会自动删除（每日备份仍保留 7 天）。默认 3 个月。", x, 112, w, 2);
        paragraph(g, "账本只保存在本机：" + LedgerStore.folder().resolve("ledger.json") + "。每天一份备份放在 backups 文件夹（保留 7 天）。分享账本把 ledger.json 发给对方即可。", x, 168, w, 4);
        if (!note.isEmpty()) text(g, note, x, height - 52, w, MarketTheme.c(0xFF9E4942));
    }
    private void paragraph(GuiGraphics g, String value, int x, int y, int w, int maxLines) {
        var lines = font.split(Component.literal(value), w);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) g.drawString(font, lines.get(i), x, y + i * 10, MUTED, false);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
