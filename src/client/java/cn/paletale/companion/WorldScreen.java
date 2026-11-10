package cn.paletale.companion;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import static cn.paletale.companion.MarketTheme.*;
/** Item totals first, then a compact shop ledger. All catalog navigation targets are in the main world. */
final class WorldScreen extends WindowScreen {
 private final Screen parent;private final MarketApi market;private final WorldApi api=new WorldApi();
 private record Saved(String text,String item,String category,String mode,String searchType,String sort,int page){}
 private static final Map<java.net.URI,Saved> SAVED=new LinkedHashMap<>();
 private String displayedField="";private List<String> categoryNames=WorldFilterIcon.CATEGORIES;private int categoryOffset;
 private JsonObject selectedRow;private EditBox query;private String text="",error="",selectedItem="",category="",mode="";private boolean loading;
 private boolean showingCurrent;private int displayedPage=1;private int loadedSize;private String searchType="all",sort="quantity",shopSort="price";private int sidebar,cardHeight,cardRows;private WorldShopTable table;
 /** Opened for one item from another list: it does not replace the remembered state of the main SMC browser. */
 private boolean standalone;
 private int page=1,left,panelWidth,rowsPerPage,offset,bodyX,bodyWidth,columns,cardWidth;private long generation;private Action account;
 private final WorldSearchSections searchSections=new WorldSearchSections(this);private int searchScroll,searchHeight,searchItemY;private String searchSignature="";
 private long nextRefresh;
 private java.net.URI loadedSite;private JsonObject result;private List<JsonObject> rows=List.of();
 WorldScreen(Screen parent,MarketApi market){super(Component.literal("SMC世界"));this.parent=parent;this.market=market;var saved=SAVED.get(market.site());if(saved!=null){text=saved.text;selectedItem=saved.item;category=saved.category;mode=saved.mode;searchType=saved.searchType;sort=saved.sort;page=saved.page;}}
 @Override protected void init(){
  ServerItemCatalogSync.refresh(market.site(),minecraft.gameDirectory.toPath().resolve("paletale/cache/catalog"));
  String draft=query==null?text:query.getValue();
  if(!market.site().equals(loadedSite)){loadedSite=market.site();generation++;result=null;rows=List.of();error="";loading=false;offset=0;}
  fitWindow();var layout=ArchiveLayout.of(width);panelWidth=layout.panelWidth();left=layout.left();sidebar=layout.sidebar();bodyX=layout.contentX();bodyWidth=layout.contentWidth();
  columns=Math.max(2,bodyWidth/84);columns=Math.min(16,columns);cardWidth=(bodyWidth-(columns-1)*6)/columns;cardRows=Math.max(1,Math.min(6,(height-112+6)/67));cardHeight=Math.max(61,Math.min(64,(height-112-(cardRows-1)*6)/cardRows));
  rowsPerPage=selectedItem.isEmpty()?Math.min(48,columns*cardRows):Math.min(48,Math.max(1,(height-130)/WorldShopTable.ROW));
  if(result!=null&&!loading&&windowed()&&loadedSize!=rowsPerPage){page=((page-1)*Math.max(1,loadedSize)+offset)/rowsPerPage+1;submit();return;}
  ArchiveNavigation.tabs(this::addRenderableWidget,parent,market,ArchiveNavigation.Tab.WORLD,left,panelWidth);
  int accountWidth=panelWidth<600?88:96;
  account=addRenderableWidget(new Action(PaleTaleClient.ACCOUNT.label(),left+panelWidth-accountWidth,13,accountWidth,18,false,"person",b->PaleTaleClient.ACCOUNT.checkStatus(minecraft)));
  query=new EditBox(font,left+4,67,sidebar-8,10,Component.literal("搜索物品、玩家、领地或交通点"));query.setMaxLength(100);query.setValue(draft);query.setBordered(false);query.setTextShadow(false);query.setTextColor(INK);query.setTextColorUneditable(MUTED);addRenderableWidget(query);
  addRenderableWidget(new Action("搜索",left,89,sidebar/2,17,false,null,b->{text=query.getValue();selectedItem="";page=1;submit();}));
  addRenderableWidget(new Action("清除",left+sidebar/2+1,89,sidebar/2-1,17,false,null,b->{query.setValue("");text="";category="";mode="";selectedItem="";page=1;submit();}));
  String[] modes={"","sell","buy"},labels={"全部","出售","收购"};for(int i=0;i<3;i++){final String next=modes[i];addRenderableWidget(new WorldTradeButton(labels[i],left+i*(sidebar/3),130,sidebar/3-1,mode.equals(next),b->{text=query.getValue();mode=next;page=1;submit();}));}
  String searchLabel=switch(searchType){case "lands"->"领地";case "transport"->"传送点";case "nations"->"国家";case "players"->"玩家";case "items"->"商品";default->"全部类型";};
  addRenderableWidget(new WorldFilterIcon("搜索类型："+searchLabel,WorldFilterIcon.search(searchType),left+sidebar-21,109,21,false,b->{var types=List.of("all","items","players","lands","transport","nations");searchType=types.get((types.indexOf(searchType)+1)%types.size());text=query.getValue();page=1;submit();}));
  int cols=sidebar>=80?4:3,step=(sidebar+2)/cols,visible=Math.max(1,(height-90-153)/20);categoryOffset=Math.min(categoryOffset,Math.max(0,(categoryNames.size()+cols-1)/cols-visible));
  for(int slot=0;slot<cols*visible;slot++){int index=categoryOffset*cols+slot;if(index>=categoryNames.size())break;String name=categoryNames.get(index);addRenderableWidget(new WorldFilterIcon(name.isEmpty()?"全部物品":name,WorldFilterIcon.category(name),left+(slot%cols)*step,153+(slot/cols)*20,step-2,category.equals(name),b->{if(category.equals(name))return;category=name;selectedItem="";page=1;submit();}));}
  addRenderableWidget(new Action("⟳ 刷新",left,height-76,sidebar,17,false,null,b->{searchSections.reset();WorldPages.CACHE.invalidate(market.site());submit();})).active=!loading;
  addRenderableWidget(new Action("在线玩家",left,height-54,sidebar,17,false,null,b->minecraft.setScreen(new WorldEntityScreen(this,market,"online","all"))));
  if(PaleTaleClient.ACCOUNT.citizen()!=null)addRenderableWidget(new Action("我的商店",left,height-32,sidebar,17,false,null,b->minecraft.setScreen(new WorldEntityScreen(this,market,"player",PaleTaleClient.ACCOUNT.citizen().mcName()))));
  if(!selectedItem.isEmpty())addRenderableWidget(new Action("← 物品目录",bodyX,46,90,18,false,null,b->{selectedItem="";selectedRow=null;page=1;submit();}));
  // The item catalog sorts by shop count first; a shop list is ordered price, quantity, recent and opens on price.
  boolean shopList=!selectedItem.isEmpty();String[] orders=shopList?new String[]{"price","quantity","recent"}:new String[]{"quantity","price","recent"},orderLabels=shopList?new String[]{"价格","数量","最近"}:new String[]{"数量","价格","最近"};for(int i=0;i<orders.length;i++){String next=orders[i];addRenderableWidget(new Action(orderLabels[i],bodyX+bodyWidth-108+i*36,46,34,18,order().equals(next),null,b->{if(order().equals(next))return;if(selectedItem.isEmpty())sort=next;else shopSort=next;page=1;submit();}));}
  offset=Math.max(0,Math.min(offset,Math.max(0,rows.size()-rowsPerPage)));
  if(selectedItem.isEmpty()){
   int itemY=70;
   if(searching()&&result!=null){String query="q="+encode(text)+"&mode="+mode+"&category="+encode(category)+"&searchType="+searchType;String signature=market.site()+query;if(!signature.equals(searchSignature)){searchSignature=signature;searchScroll=0;}searchSections.sync(market,query,searchType,columns,result);itemY+=searchSections.layout(this::addRenderableWidget,bodyX,70-searchScroll,bodyWidth,columns,70,height-46)+24-searchScroll;searchItemY=itemY-18;}
   int visibleRows=showSearchItems()||!searching()?Math.min(rows.size()-offset,rowsPerPage):0;
   for(int i=offset;i<offset+visibleRows;i++){int slot=i-offset;var card=new WorldCard(rows.get(i),bodyX+slot%columns*(cardWidth+6),itemY+slot/columns*(cardHeight+6),cardWidth);if(searching())card.visible=card.getY()+card.getHeight()>70&&card.getY()<height-46;addRenderableWidget(card);}
   if(searching()){searchHeight=itemY+searchScroll-70+(visibleRows>0?(visibleRows+columns-1)/columns*(cardHeight+6):0);int max=Math.max(0,searchHeight-(height-116));if(searchScroll>max){searchScroll=max;rebuildWidgets();return;}}
  }
  else {table=new WorldShopTable(bodyX,90,bodyWidth,false);table.controls(button->{button.active=!loading&&showingCurrent;addRenderableWidget(button);},market,rows,offset,rowsPerPage,row->minecraft.setScreen(new WorldEntityScreen(this,market,"player",value(row,"owner"))),row->Navigation.start(new Navigation.Target(value(row,"itemName"),row.get("x").getAsInt(),row.get("y").getAsInt(),row.get("z").getAsInt(),"箱子商店",List.of(new int[]{row.get("x").getAsInt(),row.get("y").getAsInt(),row.get("z").getAsInt()}),CityNavigation.MAIN_WORLD)),row->openAd(value(row,"adCode")),links->WorldRelationsScreen.open(this,market,links));}
  addRenderableWidget(new Action("上一页",bodyX,height-31,60,19,false,null,b->{if(offset>0){offset=Math.max(0,offset-rowsPerPage);rebuildWidgets();}else if(page>1){page--;submit();}})).active=!loading&&(!searching()||showSearchItems())&&(offset>0||page>1);
  addRenderableWidget(new Action("下一页",left+panelWidth-60,height-31,60,19,false,null,b->{if(offset+rowsPerPage<rows.size()){offset+=rowsPerPage;rebuildWidgets();}else{page++;submit();}})).active=!loading&&(!searching()||showSearchItems())&&(offset+rowsPerPage<rows.size()||result!=null&&page*(windowed()?rowsPerPage:24)<total());
  if(result==null&&!loading&&error.isEmpty())submit();
 }
 private void openAd(String code){if(!code.matches("[A-Za-z0-9-]{1,32}"))return;market.detail(code).whenComplete((ad,failure)->minecraft.execute(()->{if(minecraft.screen!=this)return;if(failure!=null){error="广告暂时无法读取。";return;}minecraft.setScreen(new MarketDetailScreen(this,null,market,ad));}));}
 private boolean windowed(){return true;}
 private boolean searching(){return selectedItem.isEmpty()&&!text.isBlank();}
 private boolean showSearchItems(){return searchType.equals("all")||searchType.equals("items");}
 private int total(){return result==null?0:result.get(selectedItem.isEmpty()?"itemTotal":"total").getAsInt();}
 private static String value(JsonObject row,String name){return row.has(name)&&!row.get(name).isJsonNull()?MarketData.text(row.get(name).getAsString()):"";}
 private static String price(JsonObject row,String name){String value=value(row,name);if(value.isEmpty())return "—";try{return new java.math.BigDecimal(value).stripTrailingZeros().toPlainString();}catch(NumberFormatException ignored){return value;}}
 private void apply(JsonObject data,int requestedSize){
    String field=selectedItem.isEmpty()?"items":"shops";
    if(data.get("version").getAsInt()!=1||!data.has("items")||data.getAsJsonArray(field).size()>48)throw new IllegalArgumentException();
    List<JsonObject> parsed=new ArrayList<>();for(var element:data.getAsJsonArray(field)){var row=element.getAsJsonObject();if(!selectedItem.isEmpty()&&(Math.abs(row.get("x").getAsLong())>1000000||Math.abs(row.get("z").getAsLong())>1000000||Math.abs(row.get("y").getAsLong())>2048))throw new IllegalArgumentException();WorldMerge.positions(row);parsed.add(row);}
    result=data;loadedSize=requestedSize;rows=List.copyOf(parsed);displayedField=field;showingCurrent=true;displayedPage=page;
    if(data.has("categories")){var names=new ArrayList<>(WorldFilterIcon.CATEGORIES);for(var value:data.getAsJsonArray("categories"))if(!names.contains(value.getAsString()))names.add(value.getAsString());categoryNames=List.copyOf(names);}

 }
 private String order(){return selectedItem.isEmpty()?sort:shopSort;}
 private void submit(){
  nextRefresh=System.currentTimeMillis()+WorldCatalogCache.REFRESH_MS;loading=true;showingCurrent=false;error="";offset=0;long run=++generation;int requestedSize=rowsPerPage;String field=selectedItem.isEmpty()?"items":"shops";
  if(!field.equals(displayedField)){result=null;rows=List.of();}
  String prefix="/api/mcmod/world/catalog?q="+encode(text)+"&item="+encode(selectedItem)+"&category="+encode(category)+"&mode="+mode+"&searchType="+searchType+"&sort="+order()+(selectedItem.isEmpty()?"":WorldMerge.PARAM)+"&page=";
  String path=prefix+page;var cached=windowed()?WorldPages.peek(market.site(),prefix,field,page,requestedSize):WorldPages.CACHE.peek(market.site(),"batch:"+path);
  if(cached!=null)try{apply(cached,requestedSize);}catch(RuntimeException ignored){}
  (windowed()?WorldPages.load(api,market.site(),prefix,field,page,requestedSize):WorldPages.batch(api,market.site(),path)).whenComplete((data,failure)->minecraft.execute(()->{
   if(run!=generation||minecraft.screen!=this)return;loading=false;
   if(failure!=null){error=rows.isEmpty()?"SMC目录暂时无法读取，请稍后重试。":"暂时无法刷新，保留上次目录。";rebuildWidgets();return;}
   try{apply(data,requestedSize);nextRefresh=System.currentTimeMillis()+Math.max(1000,windowed()?WorldPages.refreshIn(market.site(),prefix,page,requestedSize,total()):WorldPages.CACHE.refreshIn(market.site(),"batch:"+path));}catch(RuntimeException invalid){error="请切换到已更新的站点后重新搜索。";}
   rebuildWidgets();
   if(page*requestedSize<total())WorldPages.load(api,market.site(),prefix,field,page+1,requestedSize).exceptionally(ignored->null);
  }));
  if(minecraft!=null&&minecraft.screen==this)rebuildWidgets();
 }
 private static String encode(String value){return java.net.URLEncoder.encode(value,java.nio.charset.StandardCharsets.UTF_8);}
 private void icon(GuiGraphics g,JsonObject row,int x,int y,int size){WorldItemIcon.draw(g,market,value(row,"name"),value(row,"icon"),value(row,"vanillaId"),x,y,size);}
 @Override protected void windowBackground(GuiGraphics g,int x,int y,float delta){super.windowBackground(g,x,y,delta);ArchiveLayout.of(width).draw(g,height);}
 @Override protected void renderWindow(GuiGraphics g,int mouseX,int mouseY,float delta){
  account.setMessage(Component.literal(PaleTaleClient.ACCOUNT.label()));account.setAvatar(market,ArchiveNavigation.avatarOf(PaleTaleClient.ACCOUNT.citizen()));account.active=!PaleTaleClient.ACCOUNT.busy()&&(PaleTaleClient.ACCOUNT.loggedIn()||PaleTaleClient.ACCOUNT.canLogin(minecraft));
  ArchiveNavigation.brand(g,left,panelWidth);super.renderWindow(g,mouseX,mouseY,delta);
  text(g,"SMC世界",left+4,47,sidebar-8,INK);g.renderOutline(left,62,sidebar,20,LINE);hint(g,query,"搜索世界");text(g,"筛选",left+4,113,sidebar-28,MUTED);
  String status=!error.isEmpty()?error:result==null?"正在读取目录…":(selectedItem.isEmpty()?total()+" 种物品 · "+(sort.equals("price")?"按价格":sort.equals("recent")?"最近更新":"商店数量降序"):selectedItem+" · "+total()+" 家商店")+(loading?" · 更新中":"");
  boolean detailHead=!selectedItem.isEmpty()&&error.isEmpty()&&result!=null;
  int statusX=bodyX+(selectedItem.isEmpty()?0:94),statusWidth=Math.max(1,bodyWidth-(selectedItem.isEmpty()?0:94)-114);
  // In the shop list the title sits level with the 「← 物品目录」 label and carries the item's icon.
  if(detailHead&&selectedRow!=null&&value(selectedRow,"key").equals(selectedItem)){icon(g,selectedRow,statusX,48,14);statusX+=18;statusWidth=Math.max(1,statusWidth-18);}
  text(g,status,statusX,selectedItem.isEmpty()?49:51,statusWidth,MUTED);
  if(!selectedItem.isEmpty()&&table!=null)table.render(g,rows,offset,rowsPerPage,mouseX,mouseY);
  if(searching()){searchSections.render(g,bodyX,bodyWidth,70,height-46);if(showSearchItems()&&searchItemY>=70&&searchItemY+9<=height-46)text(g,"物品目录",bodyX,searchItemY,bodyWidth,INK);if(searchHeight>height-116){int area=height-116,thumb=Math.max(14,area*area/searchHeight),pos=70+(area-thumb)*searchScroll/Math.max(1,searchHeight-area);g.fill(bodyX+bodyWidth-2,pos,bodyX+bodyWidth,pos+thumb,LINE);}}
  if(result!=null&&(!searching()||showSearchItems())){int begin=windowed()?(displayedPage-1)*Math.max(1,loadedSize)+1:(displayedPage-1)*24+offset+1;String range=rows.isEmpty()?"0 / "+total():begin+"–"+Math.min(total(),begin+Math.min(rowsPerPage,rows.size()-offset)-1)+" / "+total();text(g,range,bodyX+66,height-25,Math.max(1,bodyWidth-132),MUTED);}
  if(result!=null&&rows.isEmpty()&&!loading)text(g,"暂无匹配结果。",bodyX+3,96,bodyWidth,MUTED);
 }
 private void priceLine(GuiGraphics g,String value,int x,int y,int width,int color){float scale=Math.min(1,width/(float)Math.max(1,font.width(value)));g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(scale,scale);g.drawString(font,value,0,0,color,false);g.pose().popMatrix();}
 private final class WorldCard extends Button {
  private final JsonObject row;
  WorldCard(JsonObject row,int x,int y,int width){super(x,y,width,cardHeight,Component.literal(value(row,"kind").isEmpty()?"商店":value(row,"name")),b->{if(!value(row,"kind").isEmpty()){String href=value(row,"href");String type=value(row,"kind").equals("玩家")?"player":value(row,"kind").equals("领地")?"land":value(row,"kind").equals("国家")?"nation":"traffic";String key=java.net.URLDecoder.decode(href.substring(href.lastIndexOf('/')+1),java.nio.charset.StandardCharsets.UTF_8);minecraft.setScreen(new WorldEntityScreen(WorldScreen.this,market,type,key));}else{selectedItem=value(row,"key");selectedRow=row;shopSort="price";page=1;submit();}},DEFAULT_NARRATION);this.row=row;active=!loading&&showingCurrent;String full=value(row,"name");if(!full.isEmpty()&&PaleFont.get().width(full)>width-12)setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(full)));}
  @Override protected void renderContents(GuiGraphics g,int mouseX,int mouseY,float delta){
   boolean clipped=searching();if(clipped)g.enableScissor(bodyX,70,bodyX+bodyWidth,WorldScreen.this.height-46);
   int x=getX(),y=getY(),w=getWidth(),h=getHeight();if(isHovered()&&(!clipped||mouseY>=70&&mouseY<WorldScreen.this.height-46)&&font.width(value(row,"name"))>w-12)fullNameTooltip(value(row,"name"));g.fill(x+2,y+2,x+w-2,y+h-2,isHoveredOrFocused()?MarketTheme.c(0xFFECEFE8):MarketTheme.c(0xFFFCFCFA));asset(g,"archive-frame",x,y,w,h,240,320);
   if(!value(row,"kind").isEmpty()){text(g,value(row,"kind"),x+6,y+6,w-12,MUTED);text(g,value(row,"name"),x+6,y+23,w-12,INK);text(g,value(row,"count")+" 家商店",x+6,y+h-13,w-12,MUTED);if(clipped)g.disableScissor();return;}
   int iconSize=14;icon(g,row,x+(w-iconSize)/2,y+3,iconSize);text(g,value(row,"name"),x+6,y+20,w-12,INK);
   priceLine(g,"售 "+price(row,"minSell"),x+6,y+31,w-12,ACCENT);priceLine(g,"收 "+price(row,"maxBuy"),x+6,y+41,w-12,MarketTheme.c(0xFF985326));text(g,value(row,"shopCount")+" 家商店",x+6,y+51,w-12,MUTED);if(clipped)g.disableScissor();

  }
  @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,boolean doubleClick){return (!searching()||event.y()>=70&&event.y()<WorldScreen.this.height-46)&&super.mouseClicked(event,doubleClick);}
  @Override public void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output){output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,Component.literal(value(row,"name")+"，"+value(row,"shopCount")+" 家商店"));}
 }
 @Override public void tick(){super.tick();ServerItemCatalogSync.refresh(market.site(),minecraft.gameDirectory.toPath().resolve("paletale/cache/catalog"));if(result!=null&&!loading&&System.currentTimeMillis()>=nextRefresh){searchSections.reset();WorldPages.CACHE.invalidate(market.site());submit();}}
 @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event){if(getFocused()==query&&(event.key()==257||event.key()==335)){text=query.getValue();selectedItem="";page=1;submit();return true;}return super.keyPressed(event);}
 /** The shops of one item, opened from another list (a player's or a land's shops); search text and filters start empty. */
 static WorldScreen searchItem(Screen parent,MarketApi market,String name){var screen=new WorldScreen(parent,market);screen.text=name;screen.selectedItem="";screen.category="";screen.mode="";screen.searchType="items";screen.page=1;return screen;}
 static WorldScreen item(Screen parent,MarketApi market,JsonObject shopRow){String itemName=value(shopRow,"itemName");var screen=new WorldScreen(parent,market);screen.text="";screen.category="";screen.mode="";screen.selectedItem=java.text.Normalizer.normalize(itemName,java.text.Normalizer.Form.NFKC).trim().toLowerCase(java.util.Locale.ROOT);var head=new JsonObject();head.addProperty("key",screen.selectedItem);head.addProperty("name",itemName);head.addProperty("icon",value(shopRow,"icon"));head.addProperty("vanillaId",value(shopRow,"vanillaId"));screen.selectedRow=head;screen.shopSort="price";screen.page=1;screen.standalone=true;return screen;}
 @Override public void onClose(){generation++;minecraft.setScreen(parent);}
 @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){double mx=(x-winX)/windowScale,my=(y-winY)/windowScale;if(searching()&&mx>=bodyX&&my>=70&&my<height-46){searchScroll=Math.max(0,Math.min(Math.max(0,searchHeight-(height-116)),searchScroll+(vertical<0?40:-40)));rebuildWidgets();return true;}if(mx>=left&&mx<left+sidebar&&my>=153&&my<height-90){int cols=sidebar>=80?4:3,visible=Math.max(1,(height-90-153)/20),last=Math.max(0,(categoryNames.size()+cols-1)/cols-visible);categoryOffset=Math.max(0,Math.min(last,categoryOffset+(vertical<0?1:-1)));rebuildWidgets();return true;}return super.mouseScrolled(x,y,horizontal,vertical);}
 @Override public void removed(){searchSections.removed();if(loadedSite!=null&&!standalone){if(SAVED.size()>=8&&!SAVED.containsKey(loadedSite))SAVED.remove(SAVED.keySet().iterator().next());SAVED.put(loadedSite,new Saved(text,selectedItem,category,mode,searchType,sort,page));}generation++;loading=false;}
 @Override public boolean isPauseScreen(){return false;}
}
