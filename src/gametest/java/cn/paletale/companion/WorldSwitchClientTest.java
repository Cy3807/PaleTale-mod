package cn.paletale.companion;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.Button;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
final class WorldSwitchClientTest {
 static Object field(Object o,String name){return WorldClientGameTest.field(o,name);}static void require(boolean ok,String label){if(!ok)throw new AssertionError(label);}
 static void await(ClientGameTestContext c){c.waitFor(mc->mc.screen instanceof WorldScreen&&field(mc.screen,"result")!=null&&!((Boolean)field(mc.screen,"loading")),600);}
 static void run(ClientGameTestContext context)throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var workers=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();server.setExecutor(workers);var calls=new AtomicInteger();var slow=new AtomicBoolean();var fail=new AtomicBoolean();
  server.createContext("/api/mcmod/world/catalog",exchange->{calls.incrementAndGet();if(slow.get())try{Thread.sleep(500);}catch(InterruptedException ignored){}int status=fail.get()?503:200;byte[] bytes=WorldDensityClientTest.payload(exchange.getRequestURI()).toString().getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}});server.start();var api=new MarketApi(URI.create("http://127.0.0.1:"+server.getAddress().getPort()));
  double old=ModSettings.uiScale();
  try{
   for(double scale:new double[]{.75,1,1.25}){
    context.runOnClient(c->{ModSettings.setUiScale(scale);c.getWindow().setWindowed(2560,1440);c.options.guiScale().set(2);c.resizeDisplay();c.setScreen(new ArchiveScreen(null,api,ArchiveData.Kind.ITEMS,0));});
    int[] archive=context.computeOnClient(c->new int[]{(Integer)field(c.screen,"left"),(Integer)field(c.screen,"panelWidth"),(Integer)field(c.screen,"sidebar"),(Integer)field(c.screen,"contentX")});
    context.runOnClient(c->{c.setScreen(new WorldScreen(null,api));require(Arrays.equals(archive,new int[]{(Integer)field(c.screen,"left"),(Integer)field(c.screen,"panelWidth"),(Integer)field(c.screen,"sidebar"),(Integer)field(c.screen,"bodyX")}),"World and archive frame coordinates are identical at "+scale);c.setScreen(new NavigationHubScreen(null,api));require(archive[0]==(Integer)field(c.screen,"left")&&archive[1]==(Integer)field(c.screen,"panelWidth")&&archive[3]==(Integer)field(c.screen,"startX"),"Navigation and archive use identical separators at "+scale);});
   }
   context.runOnClient(c->{ModSettings.setUiScale(1);c.getWindow().setWindowed(1920,1080);c.options.guiScale().set(2);c.resizeDisplay();c.setScreen(new WorldScreen(null,api));});await(context);int before=calls.get();
   context.runOnClient(c->{c.setScreen(new NavigationHubScreen(null,api));c.setScreen(new WorldScreen(null,api));require(field(c.screen,"result")!=null&&!((List<?>)field(c.screen,"rows")).isEmpty(),"Re-entering SMC immediately paints the cached snapshot");});context.waitTicks(4);require(calls.get()==before,"Returning to a warm tab makes no new query");
   context.runOnClient(c->{var trades=c.screen.children().stream().filter(b->b instanceof WorldTradeButton).map(b->(Button)b).toList();require(trades.size()==3&&trades.stream().allMatch(b->b.getWidth()>=c.font.width(b.getMessage())),"Trade filters use fully visible text");});
   context.clickScreenButton("⟳ 刷新");await(context);context.waitTicks(10);require(calls.get()>=before+1&&calls.get()<=before+4,"Manual world refresh bypasses a fresh hourly cache (plus the window and next-page prefetch)");context.takeScreenshot("smc-v19-text-and-refresh");
   slow.set(true);context.clickScreenButton("地图画");context.runOnClient(c->{require((Boolean)field(c.screen,"loading")&&!((List<?>)field(c.screen,"rows")).isEmpty(),"A cold category refresh retains the prior visible grid");require(c.screen.children().stream().filter(b->b instanceof WorldFilterIcon||b instanceof WorldTradeButton).count()>=12,"Icon filters do not disappear while loading");});await(context);context.waitTicks(10);int refreshed=calls.get();
   context.clickScreenButton("全部物品");await(context);require(calls.get()==refreshed,"Returning to a known category reuses its snapshot");
   fail.set(true);context.clickScreenButton("酒");context.waitFor(c->!((Boolean)field(c.screen,"loading")),600);context.runOnClient(c->{require(!((List<?>)field(c.screen,"rows")).isEmpty()&&!((String)field(c.screen,"error")).isEmpty(),"Failed refresh preserves visible content and explains the failure");require(c.screen.children().stream().filter(b->b instanceof Button button&&button.getMessage().getString().equals("商店")).map(b->(Button)b).noneMatch(b->b.active),"Old rows cannot accidentally act as the failed new category");});
   context.takeScreenshot("smc-switch-v17-refresh-retained");fail.set(false);context.clickScreenButton("全部物品");await(context);context.takeScreenshot("smc-switch-v17-icons");
  }finally{context.runOnClient(c->{ModSettings.setUiScale(old);c.setScreen(null);});server.stop(0);workers.shutdownNow();}
 }
}
