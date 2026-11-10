package cn.paletale.companion;
import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.Button;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.*;
/** Actual Fabric UI and HTTP fixtures; no real SIMMC account or production writes. */
final class WorldClientGameTest {
 static Object field(Object o,String name){try{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
 static void field(Object o,String name,Object value){try{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new AssertionError(e);}}
 static void require(boolean ok,String label){if(!ok)throw new AssertionError(label);}
 static void run(ClientGameTestContext context)throws Exception{
  var detailCalls=new AtomicInteger();var fail=new AtomicBoolean();
  // The binding regression begins with an already loaded detail; a skeleton would trigger an unrelated automatic fetch.
  String completeAd=MarketRegressionTestBody.VALUE.replace("\"code\":\"123456\"","\"code\":\"123456\",\"body\":\"已加载的广告正文\"");
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var workers=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();server.setExecutor(workers);
  String world="{\"version\":1,\"total\":1,\"itemTotal\":1,\"categories\":[\"自然资源\"],\"items\":[{\"key\":\"测试钻石\",\"name\":\"测试钻石\",\"category\":\"自然资源\",\"quantity\":64,\"shopCount\":1,\"minSell\":100,\"maxBuy\":null,\"vanillaId\":\"diamond\",\"icon\":\"/vanilla-items/diamond.png\"}],\"shops\":[{\"id\":\"1\",\"world\":\"worlds:spawn\",\"x\":30,\"y\":64,\"z\":30,\"owner\":\"ShopKeeper\",\"itemName\":\"测试钻石\",\"mode\":\"sell\",\"quantity\":64,\"sellPrice\":100,\"buyPrice\":null,\"observedAt\":\"2026-10-22T00:00:00Z\",\"stale\":false,\"lands\":[{\"name\":\"新领地\"}]}],\"memberships\":[{\"name\":\"新领地\",\"role\":\"成员\"}],\"trafficLands\":[]}";
  server.createContext("/api/",exchange->{String path=exchange.getRequestURI().getPath();int status=200;String body;
   if(path.equals("/api/mcmod/world/catalog"))body=world;
   else if(path.equals("/api/mcmod/world/players/ShopKeeper"))body="{\"value\":{\"online\":false,\"last_seen\":\"10/23/2026 03:53 PM\",\"balance\":2409198.09,\"checked_at\":\"2026-10-23T10:00:00Z\"}}";
   else if(path.equals("/api/mcmod/market/123456")){detailCalls.incrementAndGet();status=fail.get()?503:200;body=completeAd;}
   else if(path.equals("/api/mcmod/market"))body=MarketRegressionTestBody.VALUE;
   else {status=404;body="{}";}
   byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}
  });server.start();URI site=URI.create("http://127.0.0.1:"+server.getAddress().getPort());var media=new MarketApi(site);
  URI old=context.computeOnClient(c->PaleTaleClient.MARKET.site());
  try{
   for(int scale:new int[]{2,3}){
    context.runOnClient(c->{c.getWindow().setWindowed(1920,1080);c.options.guiScale().set(scale);c.resizeDisplay();c.setScreen(new NavigationHubScreen(null,media));});
    var header=context.computeOnClient(c->header(c.screen));
    context.takeScreenshot("navigation-header-v"+ModUpdate.installed()+"-gui"+scale);
    context.runOnClient(c->c.setScreen(new WorldScreen(null,media)));
    context.waitFor(c->c.screen instanceof WorldScreen&&!((Boolean)field(c.screen,"loading")),600);
    context.runOnClient(c->require(header.equals(header(c.screen)),"World and navigation have identical header labels/geometry at GUI "+scale));
    context.takeScreenshot("world-header-v"+ModUpdate.installed()+"-gui"+scale);
   }
   context.runOnClient(c->{var previous=SiteConfig.current();PaleTaleClient.useSite(SiteConfig.LOCAL);require(SiteConfig.local()&&PaleTaleClient.MARKET.site().equals(SiteConfig.LOCAL)&&PaleTaleClient.ACCOUNT.token()==null,"Local entry retargets APIs and clears the other backend session");PaleTaleClient.useSite(previous);});
   context.runOnClient(c->{PaleTaleClient.retargetAll(site);c.getWindow().setWindowed(1600,1000);c.options.guiScale().set(3);c.resizeDisplay();c.setScreen(new WorldScreen(null,media));});
   context.waitFor(c->c.screen instanceof WorldScreen&&!((Boolean)field(c.screen,"loading"))&&field(c.screen,"result")!=null,600);
   var hover=context.computeOnClient(c->{var s=(WindowScreen)c.screen;return new int[]{s.winX+(Integer)field(s,"bodyX")+12,s.winY+82};});context.getInput().setCursorPos(hover[0],hover[1]);context.waitTicks(12);
   context.takeScreenshot("smc-world-v"+ModUpdate.installed()+"-desktop-no-tooltip");
   context.clickScreenButton("商店");context.waitFor(c->!((Boolean)field(c.screen,"loading"))&&field(c.screen,"result")!=null,600);
   context.runOnClient(c->((Button)c.screen.children().stream().filter(b->b instanceof Button button&&button.getY()>80&&button.getMessage().getString().equals("导航")).findFirst().orElseThrow()).onPress(new net.minecraft.client.input.KeyEvent(257,0,0)));context.runOnClient(c->require(Navigation.target()!=null&&Navigation.target().world().equals(CityNavigation.MAIN_WORLD)&&Navigation.target().x()==30,"World navigation uses main world"));
   context.clickScreenButton("ShopKeeper");context.waitFor(c->c.screen instanceof WorldEntityScreen&&!((Boolean)field(c.screen,"loading")),600);
   context.runOnClient(c->require(field(c.screen,"key").equals("ShopKeeper"),"Owner action opens SMC player sheet"));
   context.takeScreenshot("smc-world-v"+ModUpdate.installed()+"-owner");
   context.runOnClient(c->c.setScreen(new WorldScreen(null,media)));
   context.runOnClient(c->{c.getWindow().setWindowed(1024,768);c.options.guiScale().set(3);c.resizeDisplay();});context.waitTicks(5);
   context.runOnClient(c->{var buttons=c.screen.children().stream().filter(b->b instanceof Button).map(b->(Button)b).filter(b->b.getY()==13).toList();require(buttons.stream().allMatch(a->buttons.stream().noneMatch(b->a!=b&&a.getX()<b.getX()+b.getWidth()&&a.getX()+a.getWidth()>b.getX())),"Seven tabs and account controls do not overlap");});
   context.takeScreenshot("smc-world-v"+ModUpdate.installed()+"-compact");
   var original=MarketApi.parse(completeAd).ads().getFirst();
   var detail=new AtomicReference<MarketDetailScreen>();var bind=new AtomicReference<ShopBindScreen>();
   context.runOnClient(c->{c.getWindow().setWindowed(1600,1000);c.options.guiScale().set(3);c.resizeDisplay();var list=new MarketScreen(null,media);var d=new MarketDetailScreen(list,list,media,original);detail.set(d);c.setScreen(d);var s=new ShopBindScreen(d,list,original,0);bind.set(s);c.setScreen(s);s.onClose();require(c.screen==d,"Unchanged binding returns to same ad");});
   require(detailCalls.get()==0,"No unneeded reload for unchanged binding");
   context.runOnClient(c->{var s=new ShopBindScreen(detail.get(),null,original,0);field(s,"changed",true);c.setScreen(s);s.onClose();require(c.screen==detail.get(),"Changed binding returns to same ad even without list");});
   context.waitFor(c->detailCalls.get()==1&&!((Boolean)field(detail.get(),"reloading")),600);
   context.runOnClient(c->require(((String)field(detail.get(),"reloadError")).isEmpty(),"Ad data was refreshed"));context.takeScreenshot("smc-binding-return-v"+ModUpdate.installed());
   fail.set(true);context.runOnClient(c->detail.get().refreshAfterBinding());context.waitFor(c->!((Boolean)field(detail.get(),"reloading")),600);
   context.runOnClient(c->require(c.screen==detail.get()&&!((String)field(detail.get(),"reloadError")).isEmpty(),"Refresh failure preserves original ad and retry"));
   fail.set(false);context.clickScreenButton("重试刷新");context.waitFor(c->!((Boolean)field(detail.get(),"reloading")),600);
   context.runOnClient(c->require(((String)field(detail.get(),"reloadError")).isEmpty(),"Retry refreshed original ad"));

   context.runOnClient(c->{
    var originalScreen=new WorldEntityScreen(null,media,"player","ShopKeeper");c.setScreen(originalScreen);
    setStatic(PlayerQuery.class,"pending",new java.util.concurrent.CompletableFuture<JsonObject>());setStatic(PlayerQuery.class,"name","ShopKeeper");setStatic(PlayerQuery.class,"original",originalScreen);setStatic(PlayerQuery.class,"connection",c.getConnection());setStatic(PlayerQuery.class,"deadline",System.currentTimeMillis()+10000);
    var inventory=new net.minecraft.world.SimpleContainer(27);var menu=net.minecraft.world.inventory.ChestMenu.threeRows(42,c.player.getInventory(),inventory);
    var incoming=new net.minecraft.client.gui.screens.inventory.ContainerScreen(menu,c.player.getInventory(),net.minecraft.network.chat.Component.literal("玩家 ShopKeeper"));
    c.setScreen(incoming);require(c.screen==originalScreen,"Matching player GUI is hidden by actual Minecraft.setScreen mixin");require(PlayerQuery.closing(42)&&!PlayerQuery.closing(43),"Only owned query container close is intercepted");
    setStatic(PlayerQuery.class,"pending",null);c.setScreen(incoming);require(c.screen==incoming,"Unsolicited menu still opens normally after query ends");c.setScreen(originalScreen);
   });
   context.takeScreenshot("smc-player-query-preserved-v"+ModUpdate.installed());
  }finally{context.runOnClient(c->{Navigation.clear();c.setScreen(null);PaleTaleClient.retargetAll(old);});server.stop(0);workers.shutdownNow();}
  if(Boolean.getBoolean("paletale.test.liveWorldV15")){
   context.runOnClient(c->c.setScreen(new WorldEntityScreen(null,new MarketApi(URI.create("https://www.paletale.cn")),"player","xiaochenqq520")));
   context.waitFor(c->c.screen instanceof WorldEntityScreen&&field(c.screen,"catalog")!=null&&field(c.screen,"observation")!=null,900);
   context.runOnClient(c->{var catalog=(JsonObject)field(c.screen,"catalog");var observation=(JsonObject)field(c.screen,"observation");require(catalog.getAsJsonArray("memberships").asList().stream().anyMatch(row->row.getAsJsonObject().get("name").getAsString().equals("乌瑞亚王国")),"Actual production menu observation supplements missing map membership in native UI");require(observation.get("balance").getAsDouble()>=0,"Native player sheet reads actual production balance");});
   context.takeScreenshot("live-production-player-v"+ModUpdate.installed());
   context.runOnClient(c->{c.getWindow().setWindowed(1920,1080);c.options.guiScale().set(2);c.resizeDisplay();c.setScreen(new WorldScreen(null,new MarketApi(URI.create("https://www.paletale.cn"))));});
   context.waitFor(c->field(c.screen,"result")!=null&&!((Boolean)field(c.screen,"loading")),900);
   context.runOnClient(c->require(((java.util.List<?>)field(c.screen,"rows")).size()>4,"Actual production catalog fills the compact native grid"));context.getInput().setCursorPos(0,0);context.takeScreenshot("live-production-world-grid-v"+ModUpdate.installed());
   context.clickScreenButton("商店");context.waitFor(c->field(c.screen,"result")!=null&&!((Boolean)field(c.screen,"loading")),900);context.getInput().setCursorPos(0,0);context.takeScreenshot("live-production-world-table-v"+ModUpdate.installed());context.runOnClient(c->c.setScreen(null));
  }
  if(Boolean.getBoolean("paletale.test.liveLocal")){
   context.runOnClient(c->{c.getWindow().setWindowed(1920,1080);c.options.guiScale().set(2);c.resizeDisplay();c.setScreen(new WorldScreen(null,new MarketApi(SiteConfig.LOCAL)));});
   context.waitFor(c->c.screen instanceof WorldScreen&&!((Boolean)field(c.screen,"loading"))&&field(c.screen,"result")!=null,600);
   context.runOnClient(c->require(((JsonObject)field(c.screen,"result")).get("total").getAsInt()>0,"Real local site returns existing public shops to the actual mod UI"));
   context.waitFor(c->((java.util.Map<?,?>)field(MarketImages.INSTANCE,"images")).keySet().stream().anyMatch(key->key.toString().startsWith("/api/mcmod/market/")),600);
   context.takeScreenshot("live-local-world-v"+ModUpdate.installed());
   context.runOnClient(c->c.setScreen(null));
  }
 }
 static void setStatic(Class<?> type,String name,Object value){try{var field=type.getDeclaredField(name);field.setAccessible(true);field.set(null,value);}catch(Exception failure){throw new AssertionError(failure);}}
 static java.util.List<String> header(net.minecraft.client.gui.screens.Screen screen){
  return screen.children().stream().filter(b->b instanceof Button button&&(button.getY()==13||button.getMessage().getString().equals("打开苍白档案网站"))).map(b->{var button=(Button)b;return button.getMessage().getString()+"|"+button.getX()+"|"+button.getY()+"|"+button.getWidth();}).toList();
 }
 /** The public envelope is identical to the production parser's response, without any private fields. */
 static class MarketRegressionTestBody{static final String VALUE="{\"version\":1,\"page\":1,\"pageSize\":12,\"total\":1,\"hasMore\":false,\"ads\":["+MarketClientGameTest.AD.replace("苍白测试酒馆 · 商店与服务联动","原广告 · 绑定后仍停留这里")+"]}";}
}
