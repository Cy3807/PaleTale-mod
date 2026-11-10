package cn.paletale.companion;
import net.minecraft.client.gui.GuiGraphics;
import static cn.paletale.companion.MarketTheme.*;
/** Only exact, validated world-player paths are allowed by the image loader and the existing public avatar endpoint. */
final class WorldPortrait {
 static String path(String name){return name.matches("[A-Za-z0-9_]{1,16}")?"/api/smc-world/players/"+name+"/avatar":"";}
 static void draw(GuiGraphics g,MarketApi api,String name,int x,int y,int size){if(!MarketImages.INSTANCE.draw(g,api,path(name),x,y,size)){g.fill(x,y,x+size,y+size,MarketTheme.c(0xFFE1E5DB));text(g,name.isEmpty()?"?":name.substring(0,1),x+size/2-3,y+size/2-4,size,MUTED);}}
 static ArchiveData.Entry favorite(com.google.gson.JsonObject row){var base=entry(WorldShopTable.value(row,"name"),WorldShopTable.value(row,"source"),false);String uuid=WorldShopTable.value(row,"uuid");return new ArchiveData.Entry(base.kind(),0,base.name(),base.subtitle(),"","","",false,uuid.matches("[a-f0-9]{32}")?"/api/players/"+uuid+"/avatar":"",null,null,-1,0,"","","","");}
 static ArchiveData.Entry entry(String name,String subtitle,boolean eligible){return new ArchiveData.Entry("players",0,name,subtitle,"","","",false,eligible?path(name):"",null,null,-1,0,"","","","");}
}
