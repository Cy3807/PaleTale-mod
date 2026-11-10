package cn.paletale.companion;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
final class ChatChannelButton extends Button {
 private final ItemStack icon;private final boolean selected;
 ChatChannelButton(String label,Item icon,int x,int y,int width,boolean selected,OnPress action){super(x,y,width,14,Component.literal(label),action,DEFAULT_NARRATION);this.icon=new ItemStack(icon);this.selected=selected;setTooltip(Tooltip.create(Component.literal(label)));}
 protected void renderContents(GuiGraphics g,int mx,int my,float d){if(selected||isHoveredOrFocused())g.fill(getX(),getY(),getX()+getWidth(),getY()+14,MarketTheme.c(0xFFE1E5DB));if(selected)g.renderOutline(getX(),getY(),getWidth(),14,MarketTheme.LINE);MarketTheme.item(g,icon,getX()+(getWidth()-10)/2,getY()+2,10);}
}
