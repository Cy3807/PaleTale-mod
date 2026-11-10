package cn.paletale.companion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import static cn.paletale.companion.MarketTheme.*;
/** Text-only trade filters remain legible in the narrow archive sidebar. */
final class WorldTradeButton extends Button {
 private final boolean selected;
 WorldTradeButton(String label,int x,int y,int width,boolean selected,OnPress action){super(x,y,width,18,Component.literal(label),action,DEFAULT_NARRATION);this.selected=selected;}
 @Override protected void renderContents(GuiGraphics g,int mx,int my,float delta){int x=getX(),y=getY();g.fill(x,y,x+getWidth(),y+getHeight(),selected?MarketTheme.c(0xFFE1E5DB):isHoveredOrFocused()?MarketTheme.c(0xFFEAECE5):PAPER);if(selected||isFocused())g.renderOutline(x,y,getWidth(),getHeight(),LINE);var font=PaleFont.get();g.drawString(font,getMessage(),x+(getWidth()-font.width(getMessage()))/2,y+5,INK,false);}
}
