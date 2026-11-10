package cn.paletale.companion;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import java.util.*;
import java.util.function.*;
import static cn.paletale.companion.MarketTheme.*;
/** Ledger-density shop rows. Each relationship has an explicit action; rows never cover the table with a tooltip. */
final class WorldShopTable {
 static final int ROW=13;
 private record Col(String key,String label,int x,int width){}
 record Relation(String name,String type,String key){}
 private final int x,y,width;private final List<Col> cols=new ArrayList<>();private MarketApi market;
 /** The column the list is already filtered by (itemName, owner, lands or routes): drawn highlighted and not clickable, so it cannot reopen the same view. */
 private final String active;
 WorldShopTable(int x,int y,int width,boolean items){this(x,y,width,items,"");}
 WorldShopTable(int x,int y,int width,boolean items,String active){this.x=x;this.y=y;this.width=width;this.active=active==null?"":active;
  int room=Math.max(90,width-72);String[] keys=items?new String[]{"itemName","owner","mode","quantity","price","lands","routes","coordinates"}:new String[]{"owner","mode","quantity","price","lands","routes","coordinates"};
  String[] labels=items?new String[]{"商品","店主","类型","数量","单价","领地","传送点","坐标"}:new String[]{"店主","类型","数量","单价","领地","传送点","坐标"};
  // 类型/数量放得下四位数，单价只留够 0.01 到 1234.5 的宽度，领地与传送点留出更多名称空间。
  double[] weights=items?new double[]{.13,.15,.08,.09,.09,.13,.13,.20}:new double[]{.20,.09,.10,.09,.15,.15,.22};int cursor=x;
  for(int i=0;i<keys.length;i++){int w=i==keys.length-1?x+room-cursor:(int)(room*weights[i]);cols.add(new Col(keys[i],labels[i],cursor,w));cursor+=w;}
 }
 /** Names longer than their column are cut with … when drawn; hovering shows the whole name. */
 private static void fullName(Button button,String name,int room){var font=PaleFont.get();if(font.width(name)>room)button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(net.minecraft.network.chat.Component.literal(name)));}
 static String value(JsonObject row,String field){return row.has(field)&&!row.get(field).isJsonNull()?MarketData.text(row.get(field).getAsString()):"";}
 private static String money(JsonObject row,String field){String v=value(row,field);return v.isEmpty()?"—":v;}
 static List<Relation> relations(JsonObject row,String field){
  var found=new LinkedHashMap<String,Relation>();String source=field.equals("nations")?"lands":field;
  if(row.has(source)&&row.get(source).isJsonArray())for(var el:row.getAsJsonArray(source)){var entry=el.getAsJsonObject();String name=value(entry,field.equals("nations")?"nation":"name"),key=field.equals("nations")?name:value(entry,field.equals("lands")?"id":"key");if(!name.isEmpty()&&!key.isEmpty())found.putIfAbsent(key,new Relation(name,field.equals("lands")?"land":field.equals("routes")?"traffic":"nation",key));}
  return List.copyOf(found.values());
 }
 private static String cell(JsonObject row,String field){return switch(field){case "mode"->switch(value(row,"mode")){case "buy"->"收购";case "both"->"售/收";default->"出售";};case "price"->value(row,"mode").equals("buy")?money(row,"buyPrice"):value(row,"mode").equals("both")?money(row,"sellPrice")+"/"+money(row,"buyPrice"):money(row,"sellPrice");case "coordinates"->WorldMerge.coordinates(row);default->value(row,field);};}
 void controls(Consumer<Button> add,MarketApi api,List<JsonObject> rows,int offset,int size,Consumer<JsonObject> owner,Consumer<JsonObject> navigate,Consumer<JsonObject> ad,Consumer<List<Relation>> scope){controls(add,api,rows,offset,size,owner,navigate,ad,scope,null);}
 /** {@code item} (may be null) opens the shops of the row's item; the active column gets no buttons. */
 void controls(Consumer<Button> add,MarketApi api,List<JsonObject> rows,int offset,int size,Consumer<JsonObject> owner,Consumer<JsonObject> navigate,Consumer<JsonObject> ad,Consumer<List<Relation>> scope,Consumer<JsonObject> item){
  this.market=api;
  for(int i=offset;i<Math.min(rows.size(),offset+size);i++){var row=rows.get(i);int top=y+(i-offset)*ROW;
   for(var col:cols){
    if(col.key.equals(active))continue;
    if(col.key.equals("itemName")&&item!=null&&!value(row,"itemName").isEmpty()){var button=new Action("",col.x,top,Math.max(1,col.width-2),12,false,null,b->item.accept(row));fullName(button,value(row,"itemName"),col.width-15);add.accept(button);}
    if(col.key.equals("owner")){var button=new Action(value(row,"owner"),col.x,top,Math.max(1,col.width-2),12,false,null,b->owner.accept(row));button.setAvatar(api,WorldPortrait.path(value(row,"owner")));fullName(button,value(row,"owner"),col.width-2-22);add.accept(button);}
    if(List.of("lands","routes").contains(col.key)){var related=relations(row,col.key);if(!related.isEmpty()){String label=related.getFirst().name()+(related.size()>1?" +"+(related.size()-1):"");var relation=new Action(label,col.x,top,Math.max(1,col.width-2),12,false,null,b->scope.accept(related));String all=related.stream().map(Relation::name).collect(java.util.stream.Collectors.joining("、"));if(related.size()>1||PaleFont.get().width(label)>col.width-2-10)relation.setTooltip(net.minecraft.client.gui.components.Tooltip.create(net.minecraft.network.chat.Component.literal(related.size()>1?all+"（点击按这些查看）":all)));add.accept(relation);}}
   }
   add.accept(new Action("导航",x+width-44,top,44,12,false,"compass",b->{
     // A merged group (several chests) opens a small list of all its coordinates; a single chest navigates at once, exactly as before.
     if(WorldMerge.grouped(row))net.minecraft.client.Minecraft.getInstance().setScreen(new WorldPositionsScreen(net.minecraft.client.Minecraft.getInstance().screen,row));else navigate.accept(row);}));
   if(!value(row,"adCode").isEmpty())add.accept(new Action("广告",x+width-72,top,28,12,false,null,b->ad.accept(row)));
  }
 }
 void render(GuiGraphics g,List<JsonObject> rows,int offset,int size,int mx,int my){
  for(var col:cols)if(col.key.equals(active)){int bottom=y+Math.max(0,Math.min(rows.size(),offset+size)-offset)*ROW;g.fill(col.x,y-15,col.x+col.width-2,Math.max(y,bottom-1),MarketTheme.c(0xFFE6EBE2));}
  for(var col:cols)text(g,col.label+(col.key.equals(active)?" ·筛选中":""),col.x+2,y-13,col.width-3,col.key.equals(active)?ACCENT:MUTED);text(g,"广告",x+width-72,y-13,28,MUTED);text(g,"导航",x+width-44,y-13,44,MUTED);line(g,x,y-3,width);
  for(int i=offset;i<Math.min(rows.size(),offset+size);i++){var row=rows.get(i);int top=y+(i-offset)*ROW;
   for(var col:cols)if(col.key.equals("itemName")){if(market!=null)WorldItemIcon.draw(g,market,value(row,"itemName"),value(row,"icon"),value(row,"vanillaId"),col.x+1,top+1,10);text(g,cell(row,col.key),col.x+13,top+2,col.width-15,INK);}else if(col.key.equals(active)&&List.of("owner","lands","routes").contains(col.key)){var related=col.key.equals("owner")?List.<Relation>of():relations(row,col.key);String label=col.key.equals("owner")?value(row,"owner"):related.isEmpty()?"":related.getFirst().name()+(related.size()>1?" +"+(related.size()-1):"");text(g,label,col.x+2,top+2,col.width-4,ACCENT);}
   else if(!List.of("owner","lands","routes").contains(col.key))text(g,cell(row,col.key),col.x+2,top+2,col.width-4,col.key.equals("price")||col.key.equals("mode")?value(row,"mode").equals("buy")?MarketTheme.c(0xFF985326):ACCENT:INK);
   line(g,x,top+12,width);
  }
 }
}
