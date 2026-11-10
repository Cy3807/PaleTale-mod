package cn.paletale.companion;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import static cn.paletale.companion.MarketTheme.*;
/** Compact SMC scope sheets: public roster/lands, observations and navigable chest shops. */
final class WorldEntityScreen extends WindowScreen {
 private final Screen parent;private final MarketApi market;private final String type,key;private final WorldApi api=new WorldApi();
 private JsonObject catalog,observation;private String catalogPath="";private String error="",tab="商店";private boolean loading,querying;private int page=1,offset,generation;private int left,w,start=108,perPage;private List<JsonObject> shops=List.of();private List<Entry> entries=List.of();
 private long nextRefresh,localRevision=-1,localRefreshAt;private Object localConnection;private String localWorld="";private int rosterLoadedSize,shopLoadedSize;private String sort="price",mode="";
 private final Map<String,Integer> onlineCounts=new HashMap<>();private final Set<String> countsRequested=new HashSet<>();
 private WorldShopTable table;private WorldFavoriteControl favorite;private final java.util.function.Supplier<String> tokens;private final java.util.function.BooleanSupplier localPrimary,localFallback;private String favoritesToken;
 private record Entry(String label,String type,String key){}
 private static String cachedActor;private static java.net.URI cachedSite;private static final LinkedHashMap<String,JsonObject> FAVORITE_CACHE=new LinkedHashMap<>();
 static JsonObject cachedFavoritePage(String actor,java.net.URI site,int page,int size){if(actor==null||!Objects.equals(actor,cachedActor)||!Objects.equals(site,cachedSite))return null;var row=FAVORITE_CACHE.get("收藏玩家:"+page+":"+size);return row==null?null:row.deepCopy();}
 static void rememberFavoritePage(String actor,java.net.URI site,int page,int size,JsonObject row){if(!Objects.equals(actor,cachedActor)||!Objects.equals(site,cachedSite)){clearFavoriteCache();cachedActor=actor;cachedSite=site;}FAVORITE_CACHE.put("收藏玩家:"+page+":"+size,row.deepCopy());while(FAVORITE_CACHE.size()>32)FAVORITE_CACHE.remove(FAVORITE_CACHE.keySet().iterator().next());}
 static void clearFavoriteCache(){FAVORITE_CACHE.clear();cachedActor=null;cachedSite=null;}
 /** Player pages refresh balance, last online time and territories from the game first, then load the rest. */
 private boolean autoQuery;private int queryRetries;
 /** A player's SMC world page that refreshes balance, territories and online time as soon as it opens (the command wheel's player query). */
 static WorldEntityScreen playerQuery(MarketApi market,String name){var screen=new WorldEntityScreen(null,market,"player",name);return screen;}
 static WorldEntityScreen favorites(Screen parent,MarketApi market){var screen=new WorldEntityScreen(parent,market,"online","all");screen.tab="收藏玩家";return screen;}

 WorldEntityScreen(Screen parent,MarketApi market,String type,String key){this(parent,market,type,key,PaleTaleClient.ACCOUNT::token);}
 WorldEntityScreen(Screen parent,MarketApi market,String type,String key,java.util.function.Supplier<String> tokens){this(parent,market,type,key,tokens,()->LocalRoster.primary(net.minecraft.client.Minecraft.getInstance()));}
 WorldEntityScreen(Screen parent,MarketApi market,String type,String key,java.util.function.Supplier<String> tokens,java.util.function.BooleanSupplier localPrimary){this(parent,market,type,key,tokens,localPrimary,()->LocalRoster.trustedServer(net.minecraft.client.Minecraft.getInstance()));}
 WorldEntityScreen(Screen parent,MarketApi market,String type,String key,java.util.function.Supplier<String> tokens,java.util.function.BooleanSupplier localPrimary,java.util.function.BooleanSupplier localFallback){super(Component.literal("SMC世界"));autoQuery=type.equals("player");this.tokens=tokens;this.localPrimary=localPrimary;this.localFallback=localFallback;this.favoritesToken=tokens.get();this.parent=parent;this.market=market;this.type=type;this.key=key;if(type.equals("player"))favorite=new WorldFavoriteControl(this,market,key);}
 private static String val(JsonObject value,String key){return value!=null&&value.has(key)&&!value.get(key).isJsonNull()?MarketData.text(value.get(key).getAsString()):"";}
 private static String refreshed(JsonObject value){try{long seconds=Math.max(0,java.time.Duration.between(java.time.Instant.parse(val(value,"checked_at")),java.time.Instant.now()).toSeconds());return seconds<60?"刚刚":seconds<3600?seconds/60+"分钟前":seconds/3600+"小时前";}catch(RuntimeException ignored){return "尚未刷新";}}
 private static String enc(String text){return java.net.URLEncoder.encode(text,java.nio.charset.StandardCharsets.UTF_8);}
 private boolean favoritesView(){return type.equals("online")&&(tab.equals("收藏玩家")||tab.equals("收藏在线"));}
 private String landNation(){var scope=scope();if(!type.equals("land")||scope==null||!scope.has("lands"))return "";for(var el:scope.getAsJsonArray("lands")){var row=el.getAsJsonObject();if(val(row,"key").equals(key))return val(row,"nation");}return "";}
 private String displayTitle(){var value=scope();if(type.equals("traffic"))return value!=null&&!val(value,"title").isEmpty()?val(value,"title"):"传送点";if(type.equals("land")){if(value!=null&&!val(value,"title").isEmpty())return val(value,"title");int split=key.indexOf('|');return split>=0&&split+1<key.length()?key.substring(split+1):key;}return key;}
 private boolean localAll(){return type.equals("online")&&tab.equals("全部在线")&&catalogPath.equals("local-roster");}
 private boolean playerGrid(){return type.equals("online")||tab.equals("玩家");}
 private int total(){if(catalog==null)return type.equals("online")?onlineCounts.getOrDefault(tab,0):0;if(type.equals("online")||tab.equals("商店"))return catalog.get("total").getAsInt();if(tab.equals("玩家")&&scope()!=null)return scope().get("playerCount").getAsInt();return entries.size();}
 @Override protected void init(){
  fitWindow();left=12;w=width-24;if(type.equals("online")&&tab.equals("商店"))tab="全部在线";
  start=type.equals("player")?108:type.equals("online")?70:100;
  String[] tabs=type.equals("online")?new String[]{"全部在线","档案在线","模组在线","收藏在线"}:type.equals("player")?new String[]{"商店","领地","国籍"}:new String[]{"商店","玩家","领地","传送点"};
  int cursor=left,tabRows=1;for(String name:tabs){int size=tabWidth(name);if(cursor>left&&cursor+tabPackWidth(name)>left+w){tabRows++;cursor=left;}cursor+=size+4;}if(type.equals("online"))start+=22*(tabRows-1);
  boolean playerGrid=playerGrid(),shopsView=tab.equals("商店");int columns=Math.max(2,w/84),gridRows=Math.max(1,Math.min(4,(height-start-45)/44));
  perPage=playerGrid?Math.min(48,columns*gridRows):shopsView?Math.min(48,Math.max(1,(height-start-50)/WorldShopTable.ROW)):Math.max(1,(height-start-36)/11);
  if(catalog!=null&&!loading&&(playerGrid&&rosterLoadedSize!=perPage||shopsView&&shopLoadedSize!=perPage)){page=1;offset=0;load();return;}
  addRenderableWidget(new Action("← 返回",left,14,64,18,false,null,b->onClose()));addRenderableWidget(new Action(type.equals("online")?"⟳ 刷新":"刷新",width-70,14,58,18,false,null,b->{if(favoritesView()){clearFavoriteCache();PaleTaleClient.FAVORITE_PRESENCE.invalidate(tokens.get(),market.site());}if(favorite!=null)favorite.reset();countsRequested.clear();WorldPages.CACHE.invalidate(market.site());load();}));
  cursor=left;int tabY=type.equals("online")?46:start-24;
  for(String name:tabs){int size=tabWidth(name);if(cursor>left&&cursor+tabPackWidth(name)>left+w){cursor=left;tabY+=22;}var button=new Action(name,cursor,tabY,size,18,tab.equals(name),null,b->selectTab(name));button.setDisplayLabel(tabLabel(name));addRenderableWidget(button);cursor+=size+4;}
  String nation=landNation();if(!nation.isEmpty()){int size=Math.min(w/2,font.width("所属国家："+nation)+12);addRenderableWidget(new Action("所属国家："+nation,width-12-size,43,size,18,false,null,b->minecraft.setScreen(new WorldEntityScreen(this,market,"nation",nation))));}
  if(favorite!=null)addRenderableWidget(favorite.button(width-166,14));
  if(type.equals("land")||type.equals("nation")||type.equals("traffic"))addRenderableWidget(new Action("在地图中查看",width-166,14,94,18,false,null,b->openOnMap()));
  if(shopsView){String[] orders={"price","quantity","recent"},labels={"价格","数量","最近"};for(int i=0;i<orders.length;i++){String next=orders[i];addRenderableWidget(new Action(labels[i],width-120+i*36,start-24,34,18,sort.equals(next),null,b->{sort=next;page=1;offset=0;load();}));}
   String[] modes={"","sell","buy"},modeLabels={"全部","出售","收购"};for(int i=0;i<modes.length;i++){String next=modes[i];addRenderableWidget(new Action(modeLabels[i],width-120-3*36-8+i*36,start-24,34,18,mode.equals(next),null,b->{if(mode.equals(next))return;mode=next;page=1;offset=0;load();}));}}
  if(type.equals("traffic")&&scope()!=null&&scope().has("point")&&scope().get("point").isJsonObject()){var point=scope().getAsJsonObject("point");addRenderableWidget(new Action("导航",width-60,55,48,18,false,"compass",b->Navigation.start(new Navigation.Target(val(point,"name"),point.get("x").getAsInt(),null,point.get("z").getAsInt(),"传送点",List.of(),CityNavigation.MAIN_WORLD))));}
  if(type.equals("player")){addRenderableWidget(new Action(querying?"查询中…":"⟳ 刷新",width-100,51,88,18,false,null,b->{queryRetries=0;query(false);})).active=!querying;int nx=left+98+font.width(key)+10;if(key.matches("[A-Za-z0-9_]{1,16}")&&nx+162<width-185){addRenderableWidget(new Action("档案",nx+108,14,50,18,false,null,b->openArchive()));}if(key.matches("[A-Za-z0-9_]{1,16}")&&nx+108<width-185){addRenderableWidget(new Action("私聊",nx,14,50,18,false,null,b->minecraft.setScreen(PrivateChatScreen.conversation(this,market,key))));addRenderableWidget(new Action("转账",nx+54,14,50,18,false,null,b->minecraft.setScreen(new net.minecraft.client.gui.screens.ChatScreen("/pay "+key+" ",false))));}}
  entries=relations();
  if(shopsView){table=new WorldShopTable(left,start+14,w,true,type.equals("player")?"owner":type.equals("land")?"lands":type.equals("traffic")?"routes":"");table.controls(this::addRenderableWidget,market,shops,0,perPage,row->minecraft.setScreen(new WorldEntityScreen(this,market,"player",val(row,"owner"))),this::navigate,row->openAd(val(row,"adCode")),links->WorldRelationsScreen.open(this,market,links),row->minecraft.setScreen(WorldScreen.item(this,market,row)));}
  else if(playerGrid){int cardWidth=(w-(columns-1)*6)/columns,cardHeight=Math.max(40,Math.min(94,(height-start-45-(gridRows-1)*6)/gridRows));for(int i=0;i<Math.min(entries.size(),perPage);i++){var entry=entries.get(i);var row=favoritesView()?catalog.getAsJsonArray("players").get(i).getAsJsonObject():null;net.minecraft.client.gui.components.Button.OnPress open=b->{if(row!=null&&!canOpenWorldFavorite(row)&&row.has("playerId")&&!row.get("playerId").isJsonNull())minecraft.setScreen(new ArchiveDetailScreen(this,market,ArchiveData.Kind.PLAYERS,row.get("playerId").getAsInt()));else minecraft.setScreen(new WorldEntityScreen(this,market,"player",entry.key));};var card=localAll()?new WorldLocalPlayerCard(entry.key,java.util.UUID.fromString(val(catalog.getAsJsonArray("players").get(i).getAsJsonObject(),"uuid")),market,left+i%columns*(cardWidth+6),start+i/columns*(cardHeight+6),cardWidth,cardHeight,open):new ArchiveCardButton(row==null?WorldPortrait.entry(entry.key,entry.type.isEmpty()?"在线玩家":"查看 SMC 资料",!entry.type.isEmpty()):WorldPortrait.favorite(row),market,left+i%columns*(cardWidth+6),start+i/columns*(cardHeight+6),cardWidth,cardHeight,open);card.active=!loading&&(row==null?!entry.type.isEmpty():canOpenWorldFavorite(row)||row.has("playerId")&&!row.get("playerId").isJsonNull());if(!entry.key.isEmpty()&&font.width(entry.key)>cardWidth-16)card.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(entry.key)));addRenderableWidget(card);}}
  else for(int i=offset;i<Math.min(entries.size(),offset+perPage);i++){var entry=entries.get(i);addRenderableWidget(new Action(entry.label,left,start+(i-offset)*11,w,11,false,null,b->{if(!entry.type.isEmpty())minecraft.setScreen(new WorldEntityScreen(this,market,entry.type,entry.key));}));}
  boolean remote=playerGrid||shopsView;
  addRenderableWidget(new Action("上一页",left,height-29,60,18,false,null,b->{if(remote){page--;offset=0;load();}else{offset=Math.max(0,offset-perPage);rebuildWidgets();}})).active=!loading&&(remote?page>1:offset>0);
  addRenderableWidget(new Action("下一页",width-72,height-29,60,18,false,null,b->{if(remote){page++;offset=0;load();}else{offset+=perPage;rebuildWidgets();}})).active=!loading&&(remote?page*perPage<total():offset+perPage<entries.size());
  if(catalog==null&&!loading&&error.isEmpty()&&!(autoQuery&&type.equals("player")))load();if(type.equals("online"))requestOnlineCounts();
  if(autoQuery&&type.equals("player"))minecraft.execute(()->{if(autoQuery&&minecraft.screen==this){autoQuery=false;query(true);if(catalog==null&&!loading)load();}});
 }
 private String tabLabel(String name){return name+(type.equals("online")?"（"+(onlineCounts.containsKey(name)?onlineCounts.get(name):"—")+"）":"");}
 private int tabPackWidth(String name){return tabWidth(name);}
 private int tabWidth(String name){return type.equals("online")?Math.min(w,Math.max(66,font.width(tabLabel(name))+10)):50;}
 private static boolean canOpenWorldFavorite(JsonObject row){return row.has("current")&&row.get("current").getAsBoolean()&&row.has("eligible")&&row.get("eligible").getAsBoolean();}
 private void requestOnlineCounts(){
  String[] names={"全部在线","档案在线","模组在线"},views={"all","archives","mod"};var site=market.site();
  for(int i=0;i<names.length;i++){String name=names[i];if(name.equals("全部在线")&&localPrimary.getAsBoolean()){countsRequested.add(name);onlineCounts.put(name,LocalRoster.players(minecraft).size());continue;}if(!countsRequested.add(name))continue;WorldPages.batch(api,site,"/api/mcmod/world/online?view="+views[i]+"&page=1").whenComplete((result,failure)->minecraft.execute(()->{if(!market.site().equals(site)||failure!=null||name.equals("全部在线")&&localAll())return;try{onlineCounts.put(name,result.get("total").getAsInt());}catch(RuntimeException ignored){return;}if(minecraft.screen==this)rebuildWidgets();}));}
 }
 private List<Entry> relations(){
  if(catalog==null)return List.of();var list=new ArrayList<Entry>();
  if(type.equals("online")){for(var el:catalog.getAsJsonArray("players")){var row=el.getAsJsonObject();list.add(new Entry(val(row,"name"),row.get("eligible").getAsBoolean()?"player":"",val(row,"name")));}return list;}
  if(type.equals("player")&&catalog.has("memberships")){var nations=new LinkedHashSet<String>();for(var el:catalog.getAsJsonArray("memberships")){var row=el.getAsJsonObject();nations.add(val(row,"nation"));if(tab.equals("领地"))list.add(new Entry(val(row,"name")+" · "+val(row,"role"),"land",val(row,"id")));}if(tab.equals("国籍"))for(String nation:nations)if(!nation.isEmpty())list.add(new Entry(nation,"nation",nation));}
  var scope=scope();String field=tab.equals("玩家")?"players":tab.equals("领地")?"lands":tab.equals("传送点")?"transport":"";
  if(scope!=null&&scope.has(field))for(var el:scope.getAsJsonArray(field)){var row=el.getAsJsonObject();list.add(new Entry(val(row,"name")+(tab.equals("传送点")?" · "+val(row,"kind"):""),tab.equals("玩家")?"player":tab.equals("领地")?"land":"traffic",tab.equals("玩家")?val(row,"name"):val(row,"key")));}
  return list;
 }
 /** The button at the top right of land, nation and teleport point pages: opens Xaero's world map at the place (a nation: the middle of its territories). */
 private void openOnMap(){
  var sc=scope();
  if(sc==null){RadialWheel.toast(minecraft,"页面数据还没加载完，请稍后再试。");return;}
  java.util.concurrent.CompletableFuture<List<MapPins.Pin>> pins;
  if(type.equals("traffic")){
   if(!sc.has("point")||!sc.get("point").isJsonObject()){RadialWheel.toast(minecraft,"网站没有这个传送点的坐标。");return;}
   var point=sc.getAsJsonObject("point");pins=java.util.concurrent.CompletableFuture.completedFuture(List.of(MapPins.trafficPin(val(point,"name").isEmpty()?key:val(point,"name"),point.get("x").getAsInt(),point.get("z").getAsInt())));
  }else if(type.equals("land")){
   int bar=key.indexOf('|');pins=MapPins.landPins(List.of(bar>=0?key.substring(bar+1):key),bar>=0?key.substring(0,bar):null,false);
  }else pins=MapPins.landPins(MapPins.landNames(sc.has("lands")&&sc.get("lands").isJsonArray()?sc.getAsJsonArray("lands"):null),key,true);
  pins.whenComplete((list,failure)->minecraft.execute(()->{
   if(failure!=null){RadialWheel.toast(minecraft,"没能取得坐标，请稍后再试。");return;}
   if(list.isEmpty()){RadialWheel.toast(minecraft,"网站没有这里的坐标。");return;}
   if(!MapPins.view(list))RadialWheel.toast(minecraft,"没有检测到 Xaero 世界地图，或它还没有加载当前世界。");
  }));
 }
 private JsonObject scope(){return catalog!=null&&catalog.has("scope")&&catalog.get("scope").isJsonObject()?catalog.getAsJsonObject("scope"):null;}
 private void selectTab(String name){
  boolean samePage=page==1;boolean privateChange=favoritesView()||type.equals("online")&&(name.equals("收藏玩家")||name.equals("收藏在线"));if(privateChange){generation++;catalog=null;catalogPath="";shops=List.of();entries=List.of();rosterLoadedSize=0;}tab=name;page=1;offset=0;
  // These tabs are projections of one catalog response. Rebuild now so no old row widgets survive.
  if(!type.equals("online")&&catalog!=null&&samePage&&!name.equals("玩家")&&!name.equals("商店")){rebuildWidgets();return;}
  load();
 }
 private void applyCatalog(JsonObject result){catalog=result;var rows=new ArrayList<JsonObject>();if(result.has("shops"))for(var el:result.getAsJsonArray("shops"))rows.add(el.getAsJsonObject());shops=List.copyOf(rows);}
 private void loadFavorites(){
  int run=++generation;String actor=tokens.get();favoritesToken=actor;if(!Objects.equals(actor,cachedActor)||!Objects.equals(market.site(),cachedSite)){clearFavoriteCache();cachedActor=actor;cachedSite=market.site();}String cacheKey=tab+":"+page+":"+perPage;var cached=FAVORITE_CACHE.get(cacheKey);catalog=cached==null?null:cached.deepCopy();if(catalog!=null){rosterLoadedSize=perPage;onlineCounts.put(tab,catalog.get("total").getAsInt());}shops=List.of();entries=List.of();catalogPath="";error="";loading=actor!=null&&cached==null;nextRefresh=System.currentTimeMillis()+30000;
  if(actor==null){error="请先登录后查看收藏玩家。";rebuildWidgets();return;}int size=perPage;String selected=tab;
  api.call(market.site(),"/api/mcmod/world/favorites?view="+(tab.equals("收藏在线")?"online":"all")+"&page="+page+"&size="+size,actor,null).whenComplete((result,failure)->minecraft.execute(()->{
   if(run!=generation||minecraft.screen!=this||!Objects.equals(actor,tokens.get()))return;loading=false;if(failure!=null){String reason=WorldApi.problem(failure);if(reason.startsWith("登录已过期")){catalog=null;clearFavoriteCache();error=reason;}else error=catalog==null?reason:reason+" 显示缓存。";}else try{if(result.get("version").getAsInt()!=1||result.getAsJsonArray("players").size()>size)throw new IllegalArgumentException();for(var el:result.getAsJsonArray("players"))if(!val(el.getAsJsonObject(),"uuid").matches("[a-f0-9]{32}"))throw new IllegalArgumentException();boolean unchanged=catalog!=null&&catalog.equals(result)&&error.isEmpty();catalog=result;FAVORITE_CACHE.put(cacheKey,result.deepCopy());while(FAVORITE_CACHE.size()>32)FAVORITE_CACHE.remove(FAVORITE_CACHE.keySet().iterator().next());rosterLoadedSize=size;page=result.get("page").getAsInt();onlineCounts.put(selected,result.get("total").getAsInt());nextRefresh=System.currentTimeMillis()+30000;if(result.has("complete")&&!result.get("complete").getAsBoolean())error="部分收藏的身份暂时无法核对，可稍后刷新。";if(unchanged&&error.isEmpty())return;}catch(RuntimeException invalid){catalog=null;error="收藏数据暂不可用，请刷新重试。";}rebuildWidgets();
  }));rebuildWidgets();
 }
 private void localRoster(String note){generation++;loading=false;String capNote="本地名单超过容量，仅显示部分玩家。";error=note.replace(capNote,"").strip();catalog=LocalRoster.page(minecraft,page,perPage);if(LocalRoster.truncated())error=(error.isEmpty()?"":error+" ")+capNote;page=catalog.get("page").getAsInt();rosterLoadedSize=perPage;catalogPath="local-roster";localRevision=LocalRoster.revision();localRefreshAt=System.currentTimeMillis()+1000;localConnection=minecraft.getConnection();localWorld=Navigation.world(minecraft);onlineCounts.put("全部在线",catalog.get("total").getAsInt());nextRefresh=Long.MAX_VALUE;shops=List.of();rebuildWidgets();}
 private void load(){
  if(type.equals("online")&&tab.equals("全部在线")&&localPrimary.getAsBoolean()){localRoster("");return;}
  if(favoritesView()){loadFavorites();return;}
  nextRefresh=System.currentTimeMillis()+WorldCatalogCache.REFRESH_MS;loading=true;error="";int run=++generation;String filter=type.equals("player")?"player":type.equals("land")?"land":type.equals("nation")?"nation":"traffic";
  boolean roster=playerGrid(),shopsView=tab.equals("商店"),nested=!type.equals("online");int size=perPage;
  String prefix=type.equals("online")?"/api/mcmod/world/online?view="+(tab.equals("档案在线")?"archives":tab.equals("模组在线")?"mod":"all")+"&page=":"/api/mcmod/world/catalog?"+filter+"="+enc(key)+"&sort="+sort+"&mode="+mode+(shopsView?WorldMerge.PARAM:"")+"&page=";
  String path=prefix+page,cacheKey=roster?WorldRosterPages.key(prefix,page,size,nested):shopsView?WorldPages.key(prefix,"shops",page,size):"batch:"+path;
  var cached=WorldPages.CACHE.peek(market.site(),cacheKey);
  if(cached!=null){rosterLoadedSize=roster?size:0;shopLoadedSize=shopsView?size:0;applyCatalog(cached);}else if(!cacheKey.equals(catalogPath)){catalog=null;shops=List.of();}catalogPath=cacheKey;
  rebuildWidgets();
  (roster?WorldRosterPages.load(api,market.site(),prefix,page,size,nested):shopsView?WorldPages.load(api,market.site(),prefix,"shops",page,size):WorldPages.batch(api,market.site(),path)).whenComplete((result,failure)->minecraft.execute(()->{if(run!=generation||minecraft.screen!=this)return;loading=false;if(failure!=null&&type.equals("online")&&tab.equals("全部在线")&&localFallback.getAsBoolean()&&!LocalRoster.players(minecraft).isEmpty()){localRoster("网站在线名单暂不可用，显示 Tab 玩家（可能跨服务器）。");return;}if(failure!=null)error="世界资料暂时无法读取。";else{rosterLoadedSize=roster?size:0;shopLoadedSize=shopsView?size:0;applyCatalog(result);if(type.equals("online"))onlineCounts.put(tab,result.get("total").getAsInt());long remaining=roster||shopsView?WorldPages.refreshIn(market.site(),prefix,page,size,roster?WorldRosterPages.total(result,nested):total()):WorldPages.CACHE.refreshIn(market.site(),"batch:"+path);nextRefresh=System.currentTimeMillis()+Math.max(1000,remaining);}rebuildWidgets();}));
  if(type.equals("player"))api.call(market.site(),"/api/mcmod/world/players/"+enc(key),null,null).whenComplete((result,failure)->minecraft.execute(()->{if(run==generation&&minecraft.screen==this&&failure==null&&result.has("value")&&result.get("value").isJsonObject())observation=result.getAsJsonObject("value");}));
 }
 private void query(boolean silent){
  querying=true;error="";int run=generation;
  // Eligibility is checked through the public world endpoint before sending either game command.
  api.call(market.site(),"/api/mcmod/world/players/"+enc(key),null,null).whenComplete((allowed,failure)->minecraft.execute(()->{
   if(minecraft.screen!=this||run!=generation){querying=false;return;}if(failure!=null){querying=false;error="没有可查询的世界玩家记录。";rebuildWidgets();return;}
   PlayerQuery.start(minecraft,key).whenComplete((result,problem)->minecraft.execute(()->{if(minecraft.screen!=this){querying=false;return;}
    // Another query (or its cooldown), a closed container: try again a moment later instead of leaving the page without its balance, territories and last seen time.
    if(problem!=null){if(queryRetries<5){queryRetries++;java.util.concurrent.CompletableFuture.delayedExecutor(2500,java.util.concurrent.TimeUnit.MILLISECONDS).execute(()->minecraft.execute(()->{if(minecraft.screen==this)query(silent);}));return;}querying=false;error="暂时读不到这位玩家的余额和领地，请点“刷新”重试。";rebuildWidgets();return;}
    querying=false;queryRetries=0;
    observation=result.deepCopy();observation.add("last_seen",result.get("lastSeen"));observation.add("offline_for",result.get("offlineFor"));observation.addProperty("checked_at",val(result,"observedAt"));
    String token=PaleTaleClient.ACCOUNT.token();if(token!=null)api.call(market.site(),"/api/mcmod/world/players/"+enc(key),token,result).whenComplete((saved,failed)->minecraft.execute(()->{if(minecraft.screen!=this)return;if(failed!=null)error="本次结果已显示，云端暂未同步。";else if(saved.has("accepted")&&saved.get("accepted").getAsBoolean()){WorldPages.CACHE.invalidate(market.site());load();}}));
    rebuildWidgets();
   }));
  }));rebuildWidgets();
 }
 private void navigate(JsonObject row){try{int x=row.get("x").getAsInt(),y=row.get("y").getAsInt(),z=row.get("z").getAsInt();Navigation.start(new Navigation.Target(val(row,"itemName"),x,y,z,"箱子商店",List.of(new int[]{x,y,z}),CityNavigation.MAIN_WORLD));}catch(RuntimeException ignored){error="商店坐标暂不可用。";}}
 private void openAd(String code){if(!code.matches("[A-Za-z0-9-]{1,32}"))return;market.detail(code).whenComplete((ad,problem)->minecraft.execute(()->{if(minecraft.screen!=this)return;if(problem!=null){error="广告暂时无法读取。";return;}minecraft.setScreen(new MarketDetailScreen(this,null,market,ad));}));}
 @Override protected void renderWindow(GuiGraphics g,int mx,int my,float delta){
  super.renderWindow(g,mx,my,delta);for(var child:children())if(child instanceof ArchiveCardButton card&&card.isHovered()&&font.width(card.entry.name())>card.getWidth()-16)fullNameTooltip(card.entry.name());text(g,type.equals("online")?(tab.equals("收藏玩家")?"收藏玩家":"在线玩家")+"（"+(onlineCounts.containsKey(tab)?onlineCounts.get(tab):"—")+"）":displayTitle(),left+(type.equals("player")?98:74),18,w-(favorite==null?166:260),INK);if(type.equals("player"))WorldPortrait.draw(g,market,key,left+74,14,18);
  if(type.equals("player")){String seen=observation==null?"尚未查询":val(observation,"last_seen");if(observation!=null&&observation.has("online")&&observation.get("online").isJsonPrimitive()&&observation.get("online").getAsBoolean())seen="刷新时在线";text(g,"上次在线："+seen,left,48,w-100,INK);text(g,"余额："+(observation==null||val(observation,"balance").isEmpty()?"尚未查询":val(observation,"balance")+" 云币"),left,60,w-100,ACCENT);text(g,"最近刷新："+(observation==null?"尚未刷新":refreshed(observation)),left,72,w,MUTED);}
  else{var scope=scope();if(scope!=null){text(g,val(scope,"title")+" · 箱子商店数："+(catalog==null?"—":val(catalog,"scopeTotal")),left,47,landNation().isEmpty()?w:w/2-6,INK);var stats=scope.getAsJsonObject("stats");if(type.equals("traffic")&&scope.has("point")&&scope.get("point").isJsonObject()){var point=scope.getAsJsonObject("point");text(g,val(point,"kind")+" · X "+val(point,"x")+" Z "+val(point,"z")+" · "+(val(point,"maintained").equals("true")?"维护正常":"维护状态待确认"),left,63,w-60,MUTED);}else text(g,"领地（"+val(scope,"landCount")+"） · 玩家（"+val(scope,"playerCount")+"） · 区块 "+val(stats,"chunks")+" · 余额 "+val(stats,"balance"),left,63,w,MUTED);}}
  if(tab.equals("商店")&&table!=null)table.render(g,shops,0,perPage,mx,my);
  int total=total(),current=playerGrid()||tab.equals("商店")?page:offset/perPage+1,pages=Math.max(1,(total+perPage-1)/perPage);text(g,"第 "+current+" / "+pages+" 页 · 共 "+total+(tab.equals("商店")?" 家商店":playerGrid()?" 人":" 项"),left+66,height-24,w-132,MUTED);
  if(favoritesView()&&!loading&&error.isEmpty()&&catalog!=null&&entries.isEmpty())text(g,"暂无符合条件的收藏玩家。",left,start+8,w,MUTED);
  if(!error.isEmpty())text(g,error,left,height-43,w,MarketTheme.c(0xFFAF4545));else if(loading)text(g,"正在刷新…",left,height-43,w,MUTED);
 }
 @Override public void tick(){super.tick();if(type.equals("online")&&!Objects.equals(favoritesToken,tokens.get())){favoritesToken=tokens.get();onlineCounts.remove("收藏玩家");onlineCounts.remove("收藏在线");if(favoritesView()){page=1;load();}else rebuildWidgets();}if(favorite!=null)favorite.tick();if(localAll()&&(localConnection!=minecraft.getConnection()||!localWorld.equals(Navigation.world(minecraft)))){catalog=null;catalogPath="";countsRequested.remove("全部在线");error="";load();return;}if(localAll()&&localRevision!=LocalRoster.revision()&&System.currentTimeMillis()>=localRefreshAt){localRoster(error);return;}if(catalog!=null&&!loading&&!querying&&System.currentTimeMillis()>=nextRefresh){WorldPages.CACHE.invalidate(market.site());load();}}
 @Override public void removed(){generation++;loading=false;if(favoritesView()){catalog=null;entries=List.of();}if(favorite!=null)favorite.reset();}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
 private void openArchive(){new ArchiveApi(market).search(ArchiveData.Kind.PLAYERS,key,"","",0,"id",1).whenComplete((page,failure)->minecraft.execute(()->{var hit=failure==null&&page!=null?page.entries().stream().filter(e->e.name().equalsIgnoreCase(key)).findFirst():java.util.Optional.<ArchiveData.Entry>empty();if(hit.isPresent())minecraft.setScreen(new ArchiveDetailScreen(this,market,ArchiveData.Kind.PLAYERS,hit.get().id()));else error="没有找到 "+key+" 的玩家档案。";}));}
}
