package cn.paletale.companion;
import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import static cn.paletale.companion.MarketTheme.*;
/** Every chest of one merged shop row: its coordinates, each with copy and navigate. */
final class WorldPositionsScreen extends WindowScreen {
 private final Screen parent;private final JsonObject row;private final List<WorldMerge.Position> positions;private int page;private String copied="";
 WorldPositionsScreen(Screen parent,JsonObject row){super(Component.literal("同条件箱子"));this.parent=parent;this.row=row;this.positions=WorldMerge.safePositions(row);}
 private String title(){String item=WorldShopTable.value(row,"itemName"),owner=WorldShopTable.value(row,"owner");return (item.isEmpty()?"箱子":item)+(owner.isEmpty()?"":" · "+owner)+" · "+positions.size()+" 个箱子";}
 @Override protected void init(){
  fitWindow();addRenderableWidget(new Action("← 返回",12,14,60,18,false,null,b->onClose()));
  int size=Math.max(1,(height-96)/20);page=Math.min(page,Math.max(0,(positions.size()-1)/size));
  for(int i=page*size;i<Math.min(positions.size(),(page+1)*size);i++){var at=positions.get(i);int y=50+(i-page*size)*20;
   addRenderableWidget(new Action(copied.equals(at.coordinates())?"已复制":"复制坐标",width-170,y,76,17,false,null,b->{minecraft.keyboardHandler.setClipboard(at.coordinates());copied=at.coordinates();rebuildWidgets();}));
   addRenderableWidget(new Action("导航",width-90,y,78,17,false,"compass",b->{
    Navigation.start(new Navigation.Target(WorldShopTable.value(row,"itemName"),at.x(),at.y(),at.z(),"箱子商店",List.of(new int[]{at.x(),at.y(),at.z()}),CityNavigation.MAIN_WORLD));minecraft.setScreen(null);}));
  }
  addRenderableWidget(new Action("上一页",12,height-29,60,18,false,null,b->{page--;rebuildWidgets();})).active=page>0;
  addRenderableWidget(new Action("下一页",width-72,height-29,60,18,false,null,b->{page++;rebuildWidgets();})).active=(page+1)*size<positions.size();
 }
 @Override protected void renderWindow(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float delta){
  super.renderWindow(g,mx,my,delta);text(g,title(),82,19,width-170,INK);
  int size=Math.max(1,(height-96)/20);
  for(int i=page*size;i<Math.min(positions.size(),(page+1)*size);i++){var at=positions.get(i);int y=50+(i-page*size)*20;
   text(g,at.x()+", "+at.y()+", "+at.z()+(i==0?"  · 最近观察":""),14,y+5,width-200,i==0?ACCENT:INK);}
  text(g,(page+1)+" / "+Math.max(1,(positions.size()+size-1)/size),width/2-20,height-24,60,MUTED);
 }
 @Override public void onClose(){minecraft.setScreen(parent);}
}
