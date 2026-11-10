package cn.paletale.companion;

import java.math.BigDecimal;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import static cn.paletale.companion.MarketTheme.*;

/** Transfer to a player: type an amount in the small box, then confirm on a second step; only the confirmation sends /pay <player> <amount>. */
final class TransferDialog extends ChatDialog {
    private final String player;
    private EditBox amount; private String typed = "", note = ""; private boolean confirming; private BigDecimal value;

    TransferDialog(Screen parent, String player) { super(parent, "转账给 " + player); this.player = player; }

    @Override protected int contentHeight() { return confirming ? 84 : 62; }

    @Override protected void layout() {
        int gap = 6, bw = (CARD_W - 2 * PAD - gap) / 2, y = cardY + cardH - PAD - 18;
        if (!confirming) {
            amount = input(34, "输入金额（云币）", typed, 12);
            amount.setFilter(text -> text.matches("[0-9]{0,9}(\\.[0-9]{0,2})?"));
            amount.setResponder(text -> { typed = text; note = ""; });
            focus(amount);
            addRenderableWidget(new Action("下一步", cardX + PAD, y, bw, 18, true, null, b -> next()));
            addRenderableWidget(new Action("取消", cardX + PAD + bw + gap, y, bw, 18, false, null, b -> onClose()));
        } else {
            addRenderableWidget(new Action("确认转账", cardX + PAD, y, bw, 18, true, null, b -> send()));
            addRenderableWidget(new Action("← 返回", cardX + PAD + bw + gap, y, bw, 18, false, null, b -> { confirming = false; rebuildWidgets(); }));
        }
    }

    private void next() {
        try {
            var parsed = new BigDecimal(typed.isBlank() ? "0" : typed);
            if (parsed.signum() <= 0) throw new NumberFormatException();
            value = parsed.stripTrailingZeros(); confirming = true; rebuildWidgets();
        } catch (NumberFormatException invalid) { note = "请输入大于 0 的金额。"; }
    }

    private void send() {
        String text = value.toPlainString();
        onClose();
        RadialWheel.send(minecraft, "pay " + player + " " + text);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) { if (confirming) send(); else next(); return true; }
        return super.keyPressed(event);
    }

    @Override protected void drawContent(GuiGraphics g, int mouseX, int mouseY) {
        if (!confirming) {
            g.drawString(font, "金额（云币）", cardX + PAD, cardY + 22, MUTED, false);
            if (!note.isEmpty()) g.drawString(font, note, cardX + PAD, cardY + 56, MarketTheme.c(0xFFB3392F), false);
        } else {
            g.drawString(font, "确认向 " + player + " 转账", cardX + PAD, cardY + 26, INK, false);
            // The amount, large and red: it is the thing to check before confirming.
            g.pose().pushMatrix(); g.pose().translate(cardX + PAD, cardY + 42); g.pose().scale(2.2f, 2.2f);
            g.drawString(font, value.toPlainString() + " 云币", 0, 0, MarketTheme.c(0xFFB3392F), false);
            g.pose().popMatrix();
            g.drawString(font, "确认后会立即执行 /pay，无法撤回。", cardX + PAD, cardY + 70, MUTED, false);
        }
    }
}
