package cn.paletale.companion;
import com.google.gson.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import static cn.paletale.companion.MarketTheme.*;
/** Independent bounded pages for each public search kind; the parent scrolls the combined sections. */
final class WorldSearchSections {
 private static final String[] KINDS={"玩家","领地","国家","传送点"},TYPES={"players","lands","nations","transport"};
 private static final class Group {String kind,error="";int page=1,size,total,serial;boolean busy,ready;List<JsonObject> rows=List.of();Group(String kind,int size){this.kind=kind;this.size=size;}}
 private record Heading(String text,int y){}
 private final WorldScreen parent;private final WorldApi api=new WorldApi();private final List<Group> groups=new ArrayList<>();private final List<Heading> headings=new ArrayList<>();private String signature="",query="";private MarketApi market;
 WorldSearchSections(WorldScreen parent){this.parent=parent;}
 void reset(){for(var group:groups)group.serial++;groups.clear();signature="";}
 void removed(){for(var group:groups){group.serial++;group.busy=false;}}
 static String val(JsonObject row,String key){return WorldShopTable.value(row,key);}
 static String enc(String text){return java.net.URLEncoder.encode(text,java.nio.charset.StandardCharsets.UTF_8);}
 void sync(MarketApi market,String query,String selected,int columns,JsonObject catalog){
  this.market=market;int size=Math.min(48,columns*2);String id=market.site()+"|"+query+"|"+selected;
  if(!id.equals(signature)){reset();signature=id;this.query=query;for(int i=0;i<KINDS.length;i++){if(!selected.equals("all")&&!selected.equals(TYPES[i]))continue;var group=new Group(KINDS[i],size);var rows=new ArrayList<JsonObject>();if(group.kind.equals("玩家")&&catalog.has("playerMatches")){for(var el:catalog.getAsJsonArray("playerMatches")){var row=el.getAsJsonObject().deepCopy();row.addProperty("kind","玩家");row.addProperty("href","/smc/players/"+enc(val(row,"name")));row.add("count",row.get("shops"));rows.add(row);}group.total=catalog.has("playerMatchesTotal")?catalog.get("playerMatchesTotal").getAsInt():rows.size();}else if(catalog.has("entityMatches")){for(var el:catalog.getAsJsonArray("entityMatches")){var row=el.getAsJsonObject();if(val(row,"kind").equals(group.kind))rows.add(row.deepCopy());}group.total=rows.size();}group.rows=List.copyOf(rows.subList(0,Math.min(rows.size(),size)));groups.add(group);}}
  for(var group:groups){if(group.size!=size){group.size=size;group.page=1;group.ready=false;group.serial++;group.busy=false;}if(!group.ready&&!group.busy)load(group);}
 }
 private void load(Group group){group.busy=true;group.error="";int run=++group.serial;String scope=signature;String path="/api/mcmod/world/search?"+query+"&kind="+enc(group.kind)+"&matchPage="+group.page+"&size="+group.size;
  var cached=WorldPages.CACHE.peek(market.site(),"batch:"+path);if(cached!=null&&WorldPages.CACHE.refreshIn(market.site(),"batch:"+path)>0){group.busy=false;group.ready=true;apply(group,cached);return;}
  WorldPages.batch(api,market.site(),path).whenComplete((result,error)->Minecraft.getInstance().execute(()->{if(!signature.equals(scope)||run!=group.serial)return;group.busy=false;group.ready=true;if(error!=null){group.error="更多结果需更新站点后刷新";}else apply(group,result);if(Minecraft.getInstance().screen==parent)parent.rebuildWidgets();}));
 }
 private void apply(Group group,JsonObject result){try{if(result.get("version").getAsInt()!=1||!val(result,"kind").equals(group.kind)||result.getAsJsonArray("entries").size()>group.size)throw new IllegalArgumentException();group.rows=result.getAsJsonArray("entries").asList().stream().map(e->e.getAsJsonObject().deepCopy()).toList();group.total=result.get("total").getAsInt();group.page=result.get("page").getAsInt();}catch(RuntimeException invalid){group.error="搜索数据暂时不可用";}}
 private static void add(Consumer<Button> add,Button button,int top,int bottom){button.visible=button instanceof Action?button.getY()>=top&&button.getY()+button.getHeight()<=bottom:button.getY()+button.getHeight()>top&&button.getY()<bottom;add.accept(button);}
 int layout(Consumer<Button> add,int x,int y,int width,int columns,int top,int bottom){headings.clear();int clipLeft=x,clipRight=x+width;int start=y,cardWidth=(width-(columns-1)*6)/columns;
  for(var group:groups){if(group.total==0&&!group.busy)continue;headings.add(new Heading(group.kind+"（"+group.total+"）"+(group.busy?" · 更新中":""),y+4));int pages=Math.max(1,(group.total+group.size-1)/group.size);var previous=new Action("上一页",x+width-130,y,44,16,false,null,b->{group.page--;load(group);parent.rebuildWidgets();});previous.active=!group.busy&&group.page>1;add(add,previous,top,bottom);headings.add(new Heading(group.page+"/"+pages,y+4));var next=new Action("下一页",x+width-44,y,44,16,false,null,b->{group.page++;load(group);parent.rebuildWidgets();});next.active=!group.busy&&group.error.isEmpty()&&group.page<pages;add(add,next,top,bottom);
   for(int i=0;i<Math.min(group.size,group.rows.size());i++){var row=group.rows.get(i);int bx=x+i%columns*(cardWidth+6),by=y+20+i/columns*50;var card=new Button(bx,by,cardWidth,44,Component.literal(val(row,"name")),b->open(row),supplier->Component.literal(val(row,"name"))){@Override protected void renderContents(GuiGraphics g,int mx,int my,float delta){g.enableScissor(clipLeft,top,clipRight,bottom);g.fill(getX()+1,getY()+1,getX()+getWidth()-1,getY()+getHeight()-1,isHoveredOrFocused()?MarketTheme.c(0xFFECEFE8):MarketTheme.c(0xFFFCFCFA));asset(g,"archive-frame",getX(),getY(),getWidth(),getHeight(),240,320);boolean player=group.kind.equals("玩家");if(player)WorldPortrait.draw(g,market,val(row,"name"),getX()+5,getY()+6,16);text(g,val(row,"name"),getX()+(player?25:5),getY()+8,getWidth()-(player?30:10),INK);text(g,val(row,"count")+" 家商店",getX()+5,getY()+30,getWidth()-10,MUTED);g.disableScissor();if(isHovered()&&my>=top&&my<bottom&&PaleFont.get().width(val(row,"name"))>getWidth()-(player?30:10))parent.fullNameTooltip(val(row,"name"));}@Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,boolean twice){return event.y()>=top&&event.y()<bottom&&super.mouseClicked(event,twice);}};String full=val(row,"name");int available=cardWidth-(group.kind.equals("玩家")?30:10);if(!full.isEmpty()&&PaleFont.get().width(full)>available)card.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(full)));card.active=!group.busy;add(add,card,top,bottom);}
   y+=20+Math.max(1,(Math.min(group.size,group.rows.size())+columns-1)/columns)*50;if(!group.error.isEmpty()){headings.add(new Heading(group.error,y));y+=14;}y+=10;
  }return y-start;
 }
 private void open(JsonObject row){String href=val(row,"href"),kind=val(row,"kind");if(!href.startsWith("/smc/")||!href.contains("/"))return;try{String key=java.net.URLDecoder.decode(href.substring(href.lastIndexOf('/')+1),java.nio.charset.StandardCharsets.UTF_8);Minecraft.getInstance().setScreen(new WorldEntityScreen(parent,market,kind.equals("玩家")?"player":kind.equals("领地")?"land":kind.equals("国家")?"nation":"traffic",key));}catch(IllegalArgumentException ignored){}}
 void render(GuiGraphics g,int x,int width,int top,int bottom){for(var h:headings)if(h.y>=top&&h.y+9<=bottom)text(g,h.text,h.text.matches("[0-9]+/[0-9]+")?x+width-80:x,h.y,h.text.matches("[0-9]+/[0-9]+")?36:Math.max(20,width-140),MUTED);}
}
