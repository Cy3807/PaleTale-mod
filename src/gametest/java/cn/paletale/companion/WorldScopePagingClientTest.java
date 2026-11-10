package cn.paletale.companion;
import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.Button;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
final class WorldScopePagingClientTest {
 static Object field(Object o,String key){return WorldClientGameTest.field(o,key);}static void require(boolean ok,String label){if(!ok)throw new AssertionError(label);}
 static void loaded(ClientGameTestContext c){c.waitFor(mc->field(mc.screen,"catalog")!=null&&!((Boolean)field(mc.screen,"loading")),600);}
 static void run(ClientGameTestContext context)throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var workers=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();server.setExecutor(workers);var lastSort=new AtomicReference<>("");
  server.createContext("/api/",ex->{JsonObject root=new JsonObject();int status=200;
   if(ex.getRequestURI().getPath().endsWith("/catalog")){var params=WorldSearchClientTest.query(ex.getRequestURI());lastSort.set(params.getOrDefault("sort",""));root=WorldDensityClientTest.payload(ex.getRequestURI());root.addProperty("total",73);root.addProperty("scopeTotal",73);var shops=new JsonArray();for(var el:root.getAsJsonArray("shops")){var row=el.getAsJsonObject();if(row.get("id").getAsInt()<73){row.addProperty("owner","Tester");shops.add(row);}}root.add("shops",shops);var lands=new JsonArray();for(int i=0;i<15;i++){var row=new JsonObject();row.addProperty("id","国家"+i%3+"|领地"+i);row.addProperty("name","领地"+i);row.addProperty("nation","国家"+i%3);row.addProperty("role","成员");lands.add(row);}root.add("memberships",lands);}
   else if(ex.getRequestURI().getPath().equals("/api/mcmod/world/players/Tester"))root.add("value",new JsonObject());else status=404;
   byte[] data=root.toString().getBytes(StandardCharsets.UTF_8);ex.getResponseHeaders().set("Content-Type","application/json");ex.sendResponseHeaders(status,data.length);try(var out=ex.getResponseBody()){out.write(data);}
  });server.start();var api=new MarketApi(URI.create("http://127.0.0.1:"+server.getAddress().getPort()));double old=ModSettings.uiScale();
  try{
   context.runOnClient(c->{ModSettings.setUiScale(1);c.getWindow().setWindowed(1920,1080);c.options.guiScale().set(3);c.resizeDisplay();c.setScreen(new WorldEntityScreen(null,api,"player","Tester"));});loaded(context);int capacity=context.computeOnClient(c->(Integer)field(c.screen,"perPage"));int pages=(73+capacity-1)/capacity;var seen=new HashSet<Integer>();
   for(int page=1;page<=pages;page++){loaded(context);int current=page;context.runOnClient(c->{var rows=(List<?>)field(c.screen,"shops");require(rows.size()==Math.min(capacity,73-(current-1)*capacity),"Shop page fills the viewport across fixed API batches");for(var el:rows)require(seen.add(((JsonObject)el).get("id").getAsInt()),"No shop repeated across pages");});if(page==2)context.takeScreenshot("smc-v21-shop-page2-full");if(page<pages)context.clickScreenButton("下一页");}require(seen.size()==73,"Every one of 73 shops is reachable");
   context.clickScreenButton("领地");loaded(context);context.runOnClient(c->{var entries=(List<?>)field(c.screen,"entries");require(entries.size()==15&&(Integer)field(c.screen,"perPage")>=15,"Fifteen lands fit on one normal GUI3 page");require(c.screen.children().stream().filter(b->b instanceof Button button&&button.getMessage().getString().contains(" · 成员")).count()==15,"All fifteen compact land controls are rendered");});context.takeScreenshot("smc-v21-fifteen-lands");
   context.clickScreenButton("国籍");context.runOnClient(c->require(((List<?>)field(c.screen,"entries")).size()==3,"Nationality uses the same compact complete list"));context.takeScreenshot("smc-v21-compact-nations");
   context.clickScreenButton("商店");loaded(context);context.clickScreenButton("价格");loaded(context);require(lastSort.get().equals("price"),"Entity shop price sorting reaches the server");
   context.runOnClient(c->c.setScreen(new WorldScreen(null,api)));context.waitFor(c->field(c.screen,"result")!=null&&!((Boolean)field(c.screen,"loading")),600);
   context.runOnClient(c->{var cards=c.screen.children().stream().filter(b->b instanceof Button button&&button.getMessage().getString().equals("商店")).map(b->(Button)b).toList();require(cards.stream().map(Button::getY).distinct().count()==3,"Three rows of compact products fit the normal GUI3 window");require(cards.stream().allMatch(b->b.getHeight()<=80),"Cards no longer stretch to create empty middle lines");});context.takeScreenshot("smc-v21-three-product-rows");context.clickScreenButton("最近");context.waitFor(c->!((Boolean)field(c.screen,"loading")),600);require(lastSort.get().equals("recent"),"Main world recent sorting reaches server");
  }finally{context.runOnClient(c->{ModSettings.setUiScale(old);c.setScreen(null);});server.stop(0);workers.shutdownNow();}
  if(Boolean.getBoolean("paletale.test.liveLocalV21")){
   var local=new MarketApi(URI.create("http://localhost:3000"));context.runOnClient(c->{ModSettings.setUiScale(1);c.getWindow().setWindowed(1920,1080);c.options.guiScale().set(3);c.resizeDisplay();c.setScreen(new WorldEntityScreen(null,local,"player","Cy3807"));});loaded(context);
   context.runOnClient(c->{var data=(JsonObject)field(c.screen,"catalog");require(((List<?>)field(c.screen,"shops")).size()==Math.min((Integer)field(c.screen,"perPage"),data.get("total").getAsInt()),"Actual local player sheet fills its first shop page");});context.takeScreenshot("smc-v21-live-player-shops");
   context.runOnClient(c->{var screen=new WorldScreen(null,local);for(String key:List.of("text","selectedItem","mode","category"))WorldClientGameTest.field(screen,key,"");WorldClientGameTest.field(screen,"sort","quantity");WorldClientGameTest.field(screen,"searchType","all");WorldClientGameTest.field(screen,"page",1);c.setScreen(screen);});context.waitFor(c->field(c.screen,"result")!=null&&!((Boolean)field(c.screen,"loading")),1200);context.takeScreenshot("smc-v21-live-three-rows");
   context.clickScreenButton("价格");context.waitFor(c->!((Boolean)field(c.screen,"loading")),1200);context.runOnClient(c->{double previous=-Double.MAX_VALUE;for(var el:(List<?>)field(c.screen,"rows")){var row=(JsonObject)el;String key=row.has("minSell")&&!row.get("minSell").isJsonNull()?"minSell":"maxBuy";if(row.has(key)&&!row.get(key).isJsonNull()){double price=row.get(key).getAsDouble();require(price>=previous,"Actual local item prices are globally ordered");previous=price;}}});context.takeScreenshot("smc-v21-live-price-sort");context.runOnClient(c->{ModSettings.setUiScale(old);c.setScreen(null);});
  }
 }
}
