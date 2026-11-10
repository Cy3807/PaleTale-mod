package cn.paletale.companion;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
/** Local tab-list projection with an isolated server; no real online or favorite data. */
final class LocalRosterClientTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static void run(ClientGameTestContext context)throws Exception{
  check(!LocalRoster.primary(null),"mixed Tab roster cannot establish main-server membership");
  var generated=new java.util.ArrayList<LocalRoster.Player>();for(int i=0;i<659;i++)generated.add(new LocalRoster.Player("Player"+i,new java.util.UUID(0,i+1)));var first=LocalRoster.page(generated,1,28);var last=LocalRoster.page(generated,24,28);check(first.get("total").getAsInt()==659&&first.getAsJsonArray("players").size()==28&&last.getAsJsonArray("players").size()==15,"local 659-player roster paginates completely");
  var all=new AtomicInteger();var avatar=new AtomicInteger();var fail=new java.util.concurrent.atomic.AtomicBoolean();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",exchange->{String q=exchange.getRequestURI().getRawQuery();if(q!=null&&q.contains("view=all"))all.incrementAndGet();if(exchange.getRequestURI().getPath().contains("avatar"))avatar.incrementAndGet();byte[] body="{\"version\":1,\"total\":0,\"players\":[]}".getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(fail.get()&&q!=null&&q.contains("view=all")?503:200,body.length);try(var out=exchange.getResponseBody()){out.write(body);}});server.start();
  try{var api=new MarketApi(URI.create("http://127.0.0.1:"+server.getAddress().getPort()));context.runOnClient(client->client.setScreen(new WorldEntityScreen(null,api,"online","all",()->null,()->true)));context.waitTicks(8);
   context.runOnClient(client->{var screen=client.screen;check(((String)WorldClientGameTest.field(screen,"catalogPath")).equals("local-roster"),"all-online uses local roster");var catalog=(com.google.gson.JsonObject)WorldClientGameTest.field(screen,"catalog");check(catalog.get("total").getAsInt()>0,"local player list rendered");check(screen.children().stream().anyMatch(c->c instanceof WorldLocalPlayerCard),"local player cards use local skin");});context.takeScreenshot("102-beta3-local-online-players");check(all.get()==0&&avatar.get()==0,"no online-list or avatar network requests for local roster");
   fail.set(true);context.runOnClient(client->client.setScreen(new WorldEntityScreen(null,api,"online","all",()->null,()->false,()->true)));context.waitFor(client->client.screen instanceof WorldEntityScreen&&"local-roster".equals(WorldClientGameTest.field(client.screen,"catalogPath")),600);context.runOnClient(client->{String note=(String)WorldClientGameTest.field(client.screen,"error");check(note.contains("可能跨服务器"),"website failure labels local fallback scope");});check(all.get()>0,"fallback follows failed online request");
  }finally{server.stop(0);}
 }
}
