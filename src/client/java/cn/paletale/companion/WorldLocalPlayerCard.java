package cn.paletale.companion;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
/** Uses the skin already available from the client connection, with no avatar HTTP request. */
final class WorldLocalPlayerCard extends ArchiveCardButton {
 private final UUID player;
 WorldLocalPlayerCard(String name,UUID player,MarketApi api,int x,int y,int w,int h,OnPress action){super(WorldPortrait.entry(name,"Tab 玩家",false),api,x,y,w,h,action);this.player=player;}
 @Override protected void renderContents(GuiGraphics g,int mx,int my,float delta){super.renderContents(g,mx,my,delta);var connection=Minecraft.getInstance().getConnection();var info=connection==null?null:connection.getPlayerInfo(player);if(info==null)return;int w=getWidth(),h=getHeight(),size=Math.min(64,Math.max(8,Math.min(w-14,h-(h<84?33:52))));PlayerFaceRenderer.draw(g,info.getSkin(),getX()+(w-size)/2,getY()+7,size);}
}
