package cn.paletale.companion;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
final class ChatSearchButton extends Button {
 ChatSearchButton(int x,int y,OnPress action){super(x,y,23,20,Component.literal("搜索 / 新会话"),action,DEFAULT_NARRATION);}
 protected void renderContents(GuiGraphics g,int mx,int my,float d){var font=PaleFont.get();if(isHoveredOrFocused())g.fill(getX(),getY(),getX()+23,getY()+20,MarketTheme.c(0xFFE1E5DB));g.drawString(font,"🔍",getX()+(23-font.width("🔍"))/2,getY()+6,MarketTheme.INK,false);}
}
