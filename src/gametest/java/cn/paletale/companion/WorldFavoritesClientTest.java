package cn.paletale.companion;
import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.Button;
final class WorldFavoritesClientTest {
 static final String UUID="11111111111141118111111111111111";
 static Object field(Object o,String key){return WorldClientGameTest.field(o,key);}
 static void require(boolean value,String label){if(!value)throw new AssertionError(label);}
 static void run(ClientGameTestContext context)throws Exception{
  var token=new AtomicReference<String>("fixture-A");var saved=new AtomicBoolean();var writes=new AtomicInteger();var reads=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var workers=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();server.setExecutor(workers);
  server.createContext("/api/",ex->{if(ex.getRequestURI().getPath().startsWith("/api/players/")){byte[] png=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAFklEQVR4nGP4n5Dw//8JIMZBM4wMBQAlN7yBcc2FBwAAAABJRU5ErkJggg==");ex.getResponseHeaders().set("Content-Type","image/png");ex.sendResponseHeaders(200,png.length);try(var out=ex.getResponseBody()){out.write(png);}return;}String path=ex.getRequestURI().getPath();String body="{}";int status=200;
   if(path.equals("/api/mcmod/world/favorites")){
    String auth=ex.getRequestHeaders().getFirst("Authorization");if(auth==null||!auth.equals("Bearer "+token.get())){status=401;}else if(ex.getRequestMethod().equals("POST")){var request=JsonParser.parseString(new String(ex.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();require(request.get("uuid").getAsString().equals(UUID),"Mutation uses UUID");saved.set(request.get("on").getAsBoolean());writes.incrementAndGet();body="{\"uuid\":\""+UUID+"\",\"favorited\":"+saved.get()+"}";}else{reads.incrementAndGet();String row="{\"uuid\":\""+UUID+"\",\"name\":\"ShopKeeper\",\"favorited\":"+saved.get()+",\"eligible\":true,\"current\":true,\"registered\":false,\"playerId\":null,\"source\":\"暂未认证来源\"}";body=ex.getRequestURI().getQuery().contains("name=")?row:"{\"version\":1,\"total\":"+(saved.get()?1:0)+",\"page\":1,\"complete\":true,\"players\":"+(saved.get()?"["+row+"]":"[]")+"}";}
   }else if(path.equals("/api/mcmod/world/catalog"))body=WorldInteractionClientTest.payload().toString();else if(path.equals("/api/mcmod/world/players/ShopKeeper"))body="{\"value\":{}}";else status=404;
   byte[] bytes=body.getBytes(StandardCharsets.UTF_8);ex.getResponseHeaders().set("Content-Type","application/json");ex.sendResponseHeaders(status,bytes.length);try(var out=ex.getResponseBody()){out.write(bytes);}
  });server.start();var api=new MarketApi(URI.create("http://127.0.0.1:"+server.getAddress().getPort()));double old=ModSettings.uiScale();
  try{
   context.runOnClient(c->{ModSettings.setUiScale(1);c.getWindow().setWindowed(1920,1080);c.options.guiScale().set(2);c.resizeDisplay();var screen=new WorldEntityScreen(null,api,"player","ShopKeeper");WorldClientGameTest.field(screen,"favorite",new WorldFavoriteControl(screen,api,"ShopKeeper",token::get));c.setScreen(screen);});
   context.waitFor(c->c.screen.children().stream().anyMatch(b->b instanceof Button a&&a.getMessage().getString().equals("☆ 收藏")&&a.active),600);context.clickScreenButton("☆ 收藏");context.waitFor(c->c.screen.children().stream().anyMatch(b->b instanceof Button a&&a.getMessage().getString().equals("★ 已收藏")&&a.active),600);require(saved.get()&&writes.get()==1,"Profile favorite stores one UUID mutation");context.takeScreenshot("smc-v22-profile-uuid-favorite");
   context.runOnClient(c->c.setScreen(new WorldFavoritesScreen(null,api,false,token::get)));context.waitFor(c->!((Boolean)field(c.screen,"loading"))&&((java.util.List<?>)field(c.screen,"rows")).size()==1,600);context.runOnClient(c->{var rows=(java.util.List<?>)field(c.screen,"rows");require(((JsonObject)rows.getFirst()).get("source").getAsString().equals("暂未认证来源"),"Unregistered identity stays in favorites without archive creation");require(c.screen.children().stream().anyMatch(b->b instanceof ArchiveCardButton),"UUID favorite renders actual player card");});context.takeScreenshot("smc-v22-unregistered-favorite");
   context.clickScreenButton("收藏在线");context.waitFor(c->!((Boolean)field(c.screen,"loading")),600);require(reads.get()>=3,"Online favorites request private endpoint");
   token.set(null);context.waitFor(c->((java.util.List<?>)field(c.screen,"rows")).isEmpty(),100);token.set("fixture-B");context.waitFor(c->!((Boolean)field(c.screen,"loading"))&&((java.util.List<?>)field(c.screen,"rows")).size()==1,600);context.clickScreenButton("取消收藏 ShopKeeper");context.waitFor(c->!((Boolean)field(c.screen,"loading"))&&((java.util.List<?>)field(c.screen,"rows")).isEmpty(),600);require(!saved.get()&&writes.get()==2,"Removal uses same UUID after account state resets");context.takeScreenshot("smc-v22-favorite-removed");
   var toastMethod=LedgerWatch.class.getDeclaredMethod("soldOutToast",LedgerEntry.class);toastMethod.setAccessible(true);var toastId=(net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId)WorldInteractionClientTest.staticField(LedgerWatch.class,"SOLD_OUT_ID");
   context.runOnClient(c->{try{for(int i=0;i<3;i++)toastMethod.invoke(null,new LedgerEntry("fixture"+i,System.currentTimeMillis(),LedgerEntry.Kind.SOLD_OUT,"云币",0,"","Fixture",0,i,64,0,"","",""));}catch(Exception e){throw new AssertionError(e);}});
   context.waitFor(c->c.getToastManager().getToast(net.minecraft.client.gui.components.toasts.SystemToast.class,toastId)!=null,600);
   var toast=context.computeOnClient(c->c.getToastManager().getToast(net.minecraft.client.gui.components.toasts.SystemToast.class,toastId));context.runOnClient(c->{try{toastMethod.invoke(null,new LedgerEntry("fixture4",System.currentTimeMillis(),LedgerEntry.Kind.SOLD_OUT,"云币",0,"","Fixture",0,4,64,0,"","",""));}catch(Exception e){throw new AssertionError(e);}});context.waitTicks(30);
   context.runOnClient(c->require(c.getToastManager().getToast(net.minecraft.client.gui.components.toasts.SystemToast.class,toastId)==toast,"Sold-out bursts update the same toast instead of queuing more"));context.takeScreenshot("smc-v22-single-sold-out-toast");context.runOnClient(c->net.minecraft.client.gui.components.toasts.SystemToast.forceHide(c.getToastManager(),toastId));

  }finally{context.runOnClient(c->{ModSettings.setUiScale(old);c.setScreen(null);});server.stop(0);workers.shutdownNow();}
 }
}
