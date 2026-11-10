package cn.paletale.companion;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import static cn.paletale.companion.MarketTheme.*;

/** The chat's record query: search the conversation for a text, or jump to a time or date, in one small pop-up. */
final class ChatRecordDialog extends ChatDialog {
    private final String query; private final Consumer<String> search, jump;
    private EditBox searchBox, jumpBox;

    ChatRecordDialog(Screen parent, String query, Consumer<String> search, Consumer<String> jump) {
        super(parent, "查询记录"); this.query = query; this.search = search; this.jump = jump;
    }

    @Override protected int contentHeight() { return 104; }

    @Override protected void layout() {
        searchBox = input(34, "要搜索的内容", query, 100);
        jumpBox = input(72, "例如 2026-10-01、21:00 或 2026-10-01 21:00:00", "", 40);
        focus(searchBox);
        int gap = 6, bw = (CARD_W - 2 * PAD - 2 * gap) / 3, y = cardY + cardH - PAD - 18;
        addRenderableWidget(new Action("搜索", cardX + PAD, y, bw, 18, true, null, b -> doSearch()));
        addRenderableWidget(new Action("跳转", cardX + PAD + bw + gap, y, bw, 18, false, null, b -> doJump()));
        addRenderableWidget(new Action("清除搜索", cardX + PAD + 2 * (bw + gap), y, bw, 18, false, null, b -> { onClose(); search.accept(""); }));
    }

    private void doSearch() { String text = searchBox.getValue().strip(); onClose(); search.accept(text); }
    private void doJump() { String text = jumpBox.getValue().strip(); if (text.isEmpty()) return; onClose(); jump.accept(text); }

    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) { if (jumpBox.isFocused() || getFocused() == jumpBox) doJump(); else doSearch(); return true; }
        if (event.key() == GLFW.GLFW_KEY_TAB) { var next = getFocused() == searchBox ? jumpBox : searchBox; searchBox.setFocused(false); jumpBox.setFocused(false); focus(next); return true; }
        return super.keyPressed(event);
    }

    @Override protected void drawContent(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, "搜索聊天记录", cardX + PAD, cardY + 22, MUTED, false);
        g.drawString(font, "跳转到时间 / 日期", cardX + PAD, cardY + 60, MUTED, false);
    }
}
