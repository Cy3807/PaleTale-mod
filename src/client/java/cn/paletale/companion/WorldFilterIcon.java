package cn.paletale.companion;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import java.util.*;
import static cn.paletale.companion.MarketTheme.*;
/** Vanilla icons remain readable without a server resource pack. Accessible labels and hover names stay complete. */
final class WorldFilterIcon extends Button {
 static final List<String> CATEGORIES=List.of("","地图画","建筑材料","武器装备","特色商品","自然资源","酒","菜","食物耗材");
 private final ItemStack icon;private final boolean selected;
 WorldFilterIcon(String label,Item item,int x,int y,int width,boolean selected,OnPress press){super(x,y,width,18,Component.literal(label),press,DEFAULT_NARRATION);this.icon=new ItemStack(item);this.selected=selected;setTooltip(Tooltip.create(Component.literal(label)));}
 static Item category(String name){return switch(name){case "地图画"->Items.PAINTING;case "建筑材料"->Items.OAK_PLANKS;case "武器装备"->Items.DIAMOND_SWORD;case "特色商品"->Items.NETHER_STAR;case "自然资源"->Items.GRASS_BLOCK;case "酒"->Items.POTION;case "菜"->Items.BAKED_POTATO;case "食物耗材"->Items.BREAD;default->Items.CHEST;};}
 static Item search(String name){return switch(name){case "items"->Items.CHEST;case "players"->Items.PLAYER_HEAD;case "lands"->Items.OAK_SIGN;case "nations"->Items.WHITE_BANNER;case "transport"->Items.COMPASS;default->Items.SPYGLASS;};}
 @Override protected void renderContents(GuiGraphics g,int mx,int my,float delta){if(selected||isHoveredOrFocused())g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),selected?MarketTheme.c(0xFFE1E5DB):MarketTheme.c(0xFFEAECE5));if(selected||isFocused())g.renderOutline(getX(),getY(),getWidth(),getHeight(),LINE);g.renderItem(icon,getX()+(getWidth()-16)/2,getY()+1);}
}
