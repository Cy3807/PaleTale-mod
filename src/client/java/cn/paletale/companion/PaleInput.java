package cn.paletale.companion;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * The input box of every PaleTale window, in the look of the chat composer: no vanilla black frame, ink text on the window's paper, a grey outline that turns green with focus,
 * and the hint drawn in the muted ink. Colours follow the theme. Border, colour, shadow and hint calls from older code are accepted and ignored so the look cannot be broken.
 */
final class PaleInput extends EditBox {
    private String hintText = "";
    /** Draws the outline red (an entry the caller refused); cleared by the next edit. */
    boolean error;
    PaleInput(Font font, int x, int y, int width, int height, Component label) { super(font, x, y, width, height, label); super.setBordered(false); super.setTextShadow(false); }
    @Override public void setBordered(boolean bordered) { }
    @Override public void setTextShadow(boolean shadow) { }
    @Override public void setTextColor(int color) { }
    @Override public void setTextColorUneditable(int color) { }
    @Override public void setHint(Component hint) { hintText = hint == null ? "" : hint.getString(); }

    @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        g.fill(x, y, x + w, y + h, MarketTheme.c(0xFFFFFFFF));
        g.renderOutline(x, y, w, h, error ? MarketTheme.c(0xFFB3392F) : isFocused() ? MarketTheme.ACCENT : MarketTheme.LINE);
        super.setTextColor(MarketTheme.INK); super.setTextColorUneditable(MarketTheme.MUTED);
        // The text of an unframed box sits at its top left; shift the box so it is inset and centred, draw, put it back.
        int inset = 5, lift = Math.max(0, (h - 8) / 2);
        setX(x + inset); setY(y + lift); setWidth(w - 2 * inset);
        super.renderWidget(g, mouseX, mouseY, delta);
        if (!hintText.isEmpty()) MarketTheme.hint(g, this, hintText);
        setX(x); setY(y); setWidth(w);
    }
}
