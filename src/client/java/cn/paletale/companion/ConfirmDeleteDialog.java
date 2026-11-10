package cn.paletale.companion;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import static cn.paletale.companion.MarketTheme.*;

/** Asks before a saved destination is deleted; only the confirm button deletes. */
final class ConfirmDeleteDialog extends ChatDialog {
    private final CustomDestinations.Entry entry;
    ConfirmDeleteDialog(Screen parent, CustomDestinations.Entry entry) { super(parent, "删除目的地"); this.entry = entry; }
    @Override protected int contentHeight() { return 40; }
    @Override protected void layout() {
        int gap = 6, bw = (CARD_W - 2 * PAD - gap) / 2, y = cardY + cardH - PAD - 18;
        addRenderableWidget(new Action("删除", cardX + PAD, y, bw, 18, true, null, b -> { CustomDestinationStore.remove(entry); onClose(); }));
        addRenderableWidget(new Action("取消", cardX + PAD + bw + gap, y, bw, 18, false, null, b -> onClose()));
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) { CustomDestinationStore.remove(entry); onClose(); return true; }
        return super.keyPressed(event);
    }
    @Override protected void drawContent(GuiGraphics g, int mouseX, int mouseY) {
        String name = font.width(entry.name()) > CARD_W - 2 * PAD - 60 ? font.plainSubstrByWidth(entry.name(), CARD_W - 2 * PAD - 70) + "..." : entry.name();
        g.drawString(font, "确定删除「" + name + "」吗？", cardX + PAD, cardY + 26, INK, false);
        g.drawString(font, "删除后无法恢复。", cardX + PAD, cardY + 40, MUTED, false);
    }
}
