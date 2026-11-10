package cn.paletale.companion;
import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
/** Actual input dispatch, pagination, scale persistence and dense row geometry in a real Minecraft window. */
final class WorldDensityClientTest {
 static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
 static Object field(Object object,String name){return WorldClientGameTest.field(object,name);}
 static JsonObject payload(URI uri){
  var params=new HashMap<String,String>();for(String part:uri.getRawQuery().split("&")){String[] pair=part.split("=",2);params.put(pair[0],pair.length>1?URLDecoder.decode(pair[1],StandardCharsets.UTF_8):"");}
  int page=Integer.parseInt(params.getOrDefault("page","1"));var out=new JsonObject();out.addProperty("version",1);out.addProperty("total",96);out.addProperty("itemTotal",96);out.add("categories",new JsonArray());var items=new JsonArray();var shops=new JsonArray();
  for(int i=(page-1)*24;i<Math.min(96,page*24);i++){var item=new JsonObject();item.addProperty("key","item"+i);item.addProperty("name","测试商品"+i);item.addProperty("category","自然资源");item.addProperty("shopCount",96-i);item.addProperty("minSell",10+i);item.addProperty("maxBuy",9+i);item.addProperty("vanillaId","diamond");items.add(item);var shop=new JsonObject();shop.addProperty("id",i);shop.addProperty("owner","Owner"+i);shop.addProperty("itemName","测试商品"+i);shop.addProperty("x",30+i);shop.addProperty("y",64);shop.addProperty("z",40+i);shop.addProperty("world","minecraft:overworld");shop.addProperty("mode",i%2==0?"both":"buy");shop.addProperty("quantity",64);shop.addProperty("sellPrice",10);shop.addProperty("buyPrice",9);shop.addProperty("observedAt","2026-10-23T00:00:00Z");var land=new JsonObject();land.addProperty("name","完整长领地名称"+i);var lands=new JsonArray();lands.add(land);shop.add("lands",lands);shops.add(shop);}
  out.add("items",items);out.add("shops",shops);return out;
 }
 static void slider(net.minecraft.client.Minecraft client,boolean large){
  var screen=(WindowScreen)client.screen;
  // AbstractSliderButton is a widget rather than Button.
  var slider=screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.AbstractSliderButton).map(c->(net.minecraft.client.gui.components.AbstractSliderButton)c).findFirst().orElseThrow();
  double x=screen.winX+(slider.getX()+(large?slider.getWidth()-18:64))*screen.windowScale,y=screen.winY+(slider.getY()+5)*screen.windowScale;var info=new MouseButtonInfo(0,0);var down=new MouseButtonEvent(x,y,info);
  require(screen.mouseClicked(down,false),"Scale handle receives physical click");screen.mouseDragged(down,10,0);screen.mouseReleased(down);
 }
 static void run(ClientGameTestContext context)throws Exception{
  double original=ModSettings.uiScale();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var workers=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();server.setExecutor(workers);
  server.createContext("/api/mcmod/world/catalog",exchange->{var data=payload(exchange.getRequestURI()).toString().getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,data.length);try(var output=exchange.getResponseBody()){output.write(data);}});server.start();var api=new MarketApi(URI.create("http://127.0.0.1:"+server.getAddress().getPort()));
  try{
   var window=WorldPages.load(new WorldApi(),api.site(),"/api/mcmod/world/catalog?page=","items",2,45).get(15,java.util.concurrent.TimeUnit.SECONDS).getAsJsonArray("items");require(window.size()==45&&window.get(0).getAsJsonObject().get("key").getAsString().equals("item45")&&window.get(44).getAsJsonObject().get("key").getAsString().equals("item89"),"A viewport spanning three API batches has no missing or duplicated items");
   context.runOnClient(c->{ModSettings.setUiScale(1);c.getWindow().setWindowed(1920,1080);c.options.guiScale().set(2);c.resizeDisplay();c.setScreen(new WorldScreen(null,api));});
   context.waitFor(c->field(c.screen,"result")!=null&&!((Boolean)field(c.screen,"loading")),600);
   int capacity=context.computeOnClient(c->(Integer)field(c.screen,"rowsPerPage"));
   context.runOnClient(c->{require((Integer)field(c.screen,"sidebar")<=84,"SMC sidebar matches archive maximum");require((Integer)field(c.screen,"cardWidth")<=110,"Compact card width comparable to archive");require((Integer)field(c.screen,"rowsPerPage")>=18,"At least eighteen cards fit on this reference window");});context.takeScreenshot("smc-density-v16-grid");
   context.clickScreenButton("下一页");context.waitFor(c->field(c.screen,"result")!=null&&!((Boolean)field(c.screen,"loading")),600);
   context.runOnClient(c->{var rows=(List<?>)field(c.screen,"rows");require(((JsonObject)rows.getFirst()).get("key").getAsString().equals("item"+capacity),"Second viewport begins exactly after the first, across API batches");});
   context.runOnClient(c->{((net.minecraft.client.gui.components.EditBox)field(c.screen,"query")).setValue("未提交输入");slider(c,true);});context.waitFor(c->Math.abs(((WindowScreen)c.screen).windowScale-1.25)<.01,200);
   context.runOnClient(c->{require(((net.minecraft.client.gui.components.EditBox)field(c.screen,"query")).getValue().equals("未提交输入"),"Changing scale preserves draft search");require(c.options.guiScale().get()==2,"Mod scale does not change Minecraft HUD scale");require(Files.exists(ModPaths.config().resolve("paletale.properties")),"Scale preference is saved");try{var properties=new java.util.Properties();try(var reader=Files.newBufferedReader(ModPaths.config().resolve("paletale.properties"))){properties.load(reader);}require(properties.getProperty("uiScale").equals("1.25"),"Persisted scale has the actual selected value");}catch(java.io.IOException failure){throw new AssertionError(failure);}});context.takeScreenshot("smc-density-v16-large");
   context.runOnClient(c->c.screen.keyPressed(new KeyEvent(263,0,0)));context.waitFor(c->((WindowScreen)c.screen).windowScale<1.25,200);
   context.runOnClient(c->slider(c,false));context.waitFor(c->Math.abs(((WindowScreen)c.screen).windowScale-.75)<.01,200);context.waitFor(c->field(c.screen,"result")!=null&&!((Boolean)field(c.screen,"loading")),600);context.takeScreenshot("smc-density-v16-small");
   context.runOnClient(c->{var first=c.screen.children().stream().filter(b->b instanceof Button button&&button.getMessage().getString().equals("商店")).map(b->(Button)b).findFirst().orElseThrow();var screen=(WindowScreen)c.screen;double px=screen.winX+(first.getX()+4)*screen.windowScale,py=screen.winY+(first.getY()+4)*screen.windowScale;require(screen.mouseClicked(new MouseButtonEvent(px,py,new MouseButtonInfo(0,0)),false),"Scaled card click uses transformed coordinates");});
   context.waitFor(c->field(c.screen,"result")!=null&&!((Boolean)field(c.screen,"loading")),600);
   context.runOnClient(c->{require(WorldShopTable.ROW==13,"Shop table matches ledger density");require((Integer)field(c.screen,"rowsPerPage")>=18,"Small UI fits many dense shop rows");require(c.screen.children().stream().anyMatch(b->b instanceof Button button&&button.getMessage().getString().equals("导航")),"Each row retains navigation");});context.takeScreenshot("smc-density-v16-table");
   context.runOnClient(c->{ModSettings.setUiScale(1);c.setScreen(new NavigationHubScreen(null,api));});context.takeScreenshot("smc-density-v16-navigation");
   context.runOnClient(c->{require((Integer)field(c.screen,"startX")<=106,"Navigation sidebar narrows to the archive width");c.setScreen(new LedgerScreen(null,api));slider(c,true);});context.waitFor(c->((WindowScreen)c.screen).windowScale>1,200);context.takeScreenshot("smc-density-v16-ledger-scale");
  }finally{context.runOnClient(c->{ModSettings.setUiScale(original);c.setScreen(null);});server.stop(0);workers.shutdownNow();}
 }
}
