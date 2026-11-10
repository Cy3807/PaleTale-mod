package cn.paletale.companion;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import static cn.paletale.companion.MarketTheme.*;

/**
 * A small card in the middle of the screen over a dimmed game, in the style of the mod's windows (paper, grey line, ink text, green accent), with one or two input boxes and a
 * row of buttons: the base of the transfer and the record-search pop-ups. The input box that is shown is focused at once.
 */
abstract class ChatDialog extends Screen {
    protected static final int CARD_W = 270, PAD = 14, FIELD_H = 18;
    protected final Screen parent;
    protected int cardX, cardY, cardH, cardW = CARD_W;
    private boolean closing;

    protected ChatDialog(Screen parent, String title) { super(Component.literal(title)); this.parent = parent; }

    /** The card's height for what it holds; subclasses lay their widgets out inside it from {@link #cardX} and {@link #cardY}. */
    /** The card's width; the pop-ups are 270 unless they need more. */
    protected int cardWidth() { return CARD_W; }
    protected abstract int contentHeight();
    protected abstract void layout();
    protected abstract void drawContent(GuiGraphics g, int mouseX, int mouseY);

    @Override protected void init() {
        PaleFont.apply(this);
        cardH = contentHeight() + 2 * PAD + 18;
        cardW = cardWidth(); cardX = (width - cardW) / 2; cardY = Math.max(8, (height - cardH) / 2);
        layout();
    }

    protected EditBox input(int y, String hint, String value, int max) {
        var box = addRenderableWidget(new PaleInput(font, cardX + PAD, cardY + y, cardW - 2 * PAD, FIELD_H, Component.literal(hint)));
        box.setMaxLength(max); box.setHint(Component.literal(hint)); box.setValue(value);
        return box;
    }
    protected EditBox primary;
    /** Typing goes to the box focused on the screen itself (setInitialFocus is ignored after mouse input); a click on empty card gives focus back to the last box. */
    protected void focus(EditBox box) { primary = box; setFocused(box); box.setFocused(true); }
    @Override public void tick() { if (primary != null && getFocused() == null) focus(primary); }

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) { g.fill(0, 0, width, height, 0x88000000); }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g, mouseX, mouseY, delta);
        g.fill(cardX + 2, cardY + 3, cardX + cardW + 2, cardY + cardH + 3, 0x55000000);
        g.fill(cardX, cardY, cardX + cardW, cardY + cardH, PAPER);
        g.renderOutline(cardX, cardY, cardW, cardH, LINE);
        g.drawString(font, getTitle().getString(), cardX + PAD, cardY + 8, INK, false);
        g.fill(cardX + PAD, cardY + 8 + font.lineHeight + 3, cardX + cardW - PAD, cardY + 8 + font.lineHeight + 4, LINE);
        super.render(g, mouseX, mouseY, delta);
        drawContent(g, mouseX, mouseY);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(event);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (event.button() == 1) { onClose(); return true; }
        return super.mouseClicked(event, twice);
    }
    @Override public void onClose() { if (closing) return; closing = true; minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
