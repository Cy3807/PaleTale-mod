package cn.paletale.companion;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import static cn.paletale.companion.MarketTheme.*;
/** Multiple links open a deliberate selector instead of a floating hover panel. */
final class WorldRelationsScreen extends WindowScreen {
 private final Screen parent;private final MarketApi api;private final List<WorldShopTable.Relation> entries;private int page;
 WorldRelationsScreen(Screen parent,MarketApi api,List<WorldShopTable.Relation> entries){super(Component.literal("关联位置"));this.parent=parent;this.api=api;this.entries=List.copyOf(entries);}
 static void open(Screen parent,MarketApi api,List<WorldShopTable.Relation> entries){if(entries.isEmpty())return;var mc=net.minecraft.client.Minecraft.getInstance();if(entries.size()==1){var e=entries.getFirst();mc.setScreen(new WorldEntityScreen(parent,api,e.type(),e.key()));}else mc.setScreen(new WorldRelationsScreen(parent,api,entries));}
 @Override protected void init(){fitWindow();addRenderableWidget(new Action("← 返回",12,14,60,18,false,null,b->onClose()));int size=Math.max(1,(height-90)/22);page=Math.min(page,(entries.size()-1)/size);for(int i=page*size;i<Math.min(entries.size(),(page+1)*size);i++){var e=entries.get(i);addRenderableWidget(new Action(e.name(),12,50+(i-page*size)*22,width-24,20,false,null,b->minecraft.setScreen(new WorldEntityScreen(this,api,e.type(),e.key()))));}addRenderableWidget(new Action("上一页",12,height-29,60,18,false,null,b->{page--;rebuildWidgets();})).active=page>0;addRenderableWidget(new Action("下一页",width-72,height-29,60,18,false,null,b->{page++;rebuildWidgets();})).active=(page+1)*size<entries.size();}
 @Override public void onClose(){minecraft.setScreen(parent);}
}
