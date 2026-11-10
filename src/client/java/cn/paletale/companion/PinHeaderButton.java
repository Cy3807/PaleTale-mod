package cn.paletale.companion;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import static cn.paletale.companion.MarketTheme.*;
/** Collapsible heading of the pinned conversations, tinted like the pinned rows below it. */
final class PinHeaderButton extends Button {
 private final int count;private final boolean collapsed;
 PinHeaderButton(int count,boolean collapsed,int x,int y,int w,OnPress action){super(x,y,w,32,Component.literal("置顶聊天"),action,DEFAULT_NARRATION);this.count=count;this.collapsed=collapsed;}
 @Override protected void renderContents(GuiGraphics g,int mx,int my,float delta){int x=getX(),y=getY();g.fill(x,y,x+getWidth(),y+32,isHoveredOrFocused()?MarketTheme.c(0xFFDEE1D6):PrivateChatPeerButton.pinned());text(g,"置顶聊天 "+count,x+6,y+12,getWidth()-26,INK);text(g,collapsed?"▾":"▴",x+getWidth()-14,y+12,10,MUTED);}
}
