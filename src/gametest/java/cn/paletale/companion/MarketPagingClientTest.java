package cn.paletale.companion;
import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
final class MarketPagingClientTest {
 static void run(ClientGameTestContext context)throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();server.setExecutor(executor);
  var pause=new java.util.concurrent.atomic.AtomicBoolean();var arrived=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
  server.createContext("/api/",exchange->{String query=exchange.getRequestURI().getRawQuery();int status=404;String body="{}";
   if(exchange.getRequestURI().getPath().equals("/api/mcmod/market")){
    if(pause.getAndSet(false)){arrived.countDown();try{release.await(10,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}}
    int page=query!=null&&query.contains("page=2")?2:1;var envelope=new JsonObject();envelope.addProperty("version",1);envelope.addProperty("page",page);envelope.addProperty("pageSize",12);envelope.addProperty("total",24);envelope.addProperty("hasMore",page==1);var ads=new JsonArray();
    for(int i=(page-1)*12;i<page*12;i++){var ad=JsonParser.parseString(MarketClientGameTest.AD).getAsJsonObject();ad.addProperty("code",String.valueOf(100000+i));ad.addProperty("title","分页广告 "+(i+1));ads.add(ad);}envelope.add("ads",ads);body=envelope.toString();status=200;
   }
   byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}
  });server.start();
  try{
   context.runOnClient(c->{c.getWindow().setWindowed(1600,1200);c.options.guiScale().set(3);c.resizeDisplay();c.setScreen(new MarketScreen(null,new MarketApi(URI.create("http://127.0.0.1:"+server.getAddress().getPort()))));});
   Set<String> seen=new LinkedHashSet<>();
   for(int page=1;page<=5;page++){
    context.waitFor(c->c.screen instanceof MarketScreen screen&&!screen.isLoading()&&screen.result()!=null,600);
    final int current=page;
    context.runOnClient(c->{var result=((MarketScreen)c.screen).result();WorldClientGameTest.require(result.ads().size()==(current==5?4:5),"24 ads fill five-row pages, including cross-batch 11–15");for(var ad:result.ads())WorldClientGameTest.require(seen.add(ad.code()),"No repeated ad while paging");});
    if(page==3)context.takeScreenshot("market-continuous-11-15-v"+ModUpdate.installed());
    if(page<5)context.clickScreenButton("下一页");
   }
   WorldClientGameTest.require(seen.size()==24,"All 24 ads reachable");
   context.takeScreenshot("market-last-21-24-v"+ModUpdate.installed());context.clickScreenButton("上一页");context.waitFor(c->!((MarketScreen)c.screen).isLoading(),600);
   context.runOnClient(c->WorldClientGameTest.require(((MarketScreen)c.screen).result().ads().getFirst().code().equals("100015"),"Previous page returns 16–20"));
   context.runOnClient(c->((MarketApi)WorldClientGameTest.field(c.screen,"api")).invalidatePublic());
   pause.set(true);context.clickScreenButton("上一页");context.waitFor(c->arrived.getCount()==0,200);
   context.runOnClient(c->{c.getWindow().setWindowed(1920,1080);c.options.guiScale().set(2);c.resizeDisplay();});release.countDown();
   context.waitFor(c->c.screen instanceof MarketScreen screen&&!screen.isLoading()&&screen.result()!=null&&screen.result().pageSize()==6,600);
   context.runOnClient(c->WorldClientGameTest.require(((MarketScreen)c.screen).result().ads().getFirst().code().equals("100006"),"Resizing during an HTTP request preserves the anchor and six-row page"));
   context.takeScreenshot("market-resize-during-request-v"+ModUpdate.installed());
  }finally{release.countDown();context.runOnClient(c->c.setScreen(null));server.stop(0);executor.shutdownNow();}
 }
}
