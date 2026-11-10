package cn.paletale.companion;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

final class ServerItemCatalogSyncTest {
 static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static void run(){
  if(!Files.exists(Path.of("../public/smc-items/v25/catalog-v25.json"))){System.out.println("Skipped: needs the website-published catalog-v25.json (only present in the original monorepo)");return;}
  HttpServer server=null;
  try{
   byte[] published=Files.readAllBytes(Path.of("../public/smc-items/v25/catalog-v25.json"));
   var root=JsonParser.parseString(new String(published,StandardCharsets.UTF_8)).getAsJsonObject();
   require(root.getAsJsonArray("items").size()>=1404,"Full website catalog includes legacy and the reviewed new appearances");
   var alias=new JsonObject();alias.addProperty("name","仅供显示的远程名称");alias.addProperty("itemModel","smc:soil_surveyor");alias.addProperty("baseItem","minecraft:apple");alias.addProperty("displayApproved",true);root.getAsJsonArray("items").add(alias);
   byte[] body=root.toString().getBytes(StandardCharsets.UTF_8);var calls=new AtomicInteger();
   server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/smc-items/v25/catalog-v25.json",exchange->{calls.incrementAndGet();exchange.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
   var site=URI.create("http://127.0.0.1:"+server.getAddress().getPort());Path cache=Files.createTempDirectory("paletale-catalog-v25-");
   ServerItemCatalogSync.resetForTest();ServerItemCatalogSync.refresh(site,cache).get(15,TimeUnit.SECONDS);
   require(ServerItemCatalog.appearance("仅供显示的远程名称")!=null,"Published exact display mapping is installed");require(!ServerItemCatalog.sameName("仅供显示的远程名称","仅供显示的远程名称"),"Remote display names never expand physical binding aliases");
   require(ServerItemCatalog.appearance("蠕虫").model().equals("smc:fish_worm_4"),"Published worm model remains exact");require(ServerItemCatalog.appearance("坚毅茸茸菇")==null,"Pending model remains disabled");
   ServerItemCatalogSync.refresh(site,cache).get(15,TimeUnit.SECONDS);require(calls.get()==1,"Fresh metadata does not download on every screen rebuild");
   server.stop(0);server=null;ServerItemCatalogSync.resetForTest();ServerItemCatalogSync.refresh(site,cache).get(15,TimeUnit.SECONDS);require(ServerItemCatalog.appearance("仅供显示的远程名称")!=null,"Validated disk catalog is available offline");
   var corrupt=root.deepCopy();corrupt.getAsJsonArray("items").get(0).getAsJsonObject().addProperty("itemModel","../../bad");
   try{ServerItemCatalog.apply(ServerItemCatalog.decode(corrupt.toString().getBytes(StandardCharsets.UTF_8)));throw new AssertionError("Malformed model rejected atomically");}catch(IllegalArgumentException expected){}
   require(ServerItemCatalog.appearance("仅供显示的远程名称")!=null,"Malformed response does not discard the last valid snapshot");
   var blocked=root.deepCopy();var pending=new JsonObject();pending.addProperty("name","蠕虫");blocked.getAsJsonArray("pending").add(pending);ServerItemCatalog.apply(ServerItemCatalog.decode(blocked.toString().getBytes(StandardCharsets.UTF_8)));require(ServerItemCatalog.appearance("蠕虫")==null,"Pending list overrides both remote and bundled appearances");
   try(var files=Files.list(cache)){for(var file:files.toList())Files.writeString(file,"{bad");}
   ServerItemCatalogSync.resetForTest();ServerItemCatalogSync.refresh(site,cache).get(15,TimeUnit.SECONDS);require(ServerItemCatalog.appearance("仅供显示的远程名称")==null,"Corrupt disk snapshot is ignored");require(ServerItemCatalog.appearance("蠕虫").model().equals("smc:fish_worm_4"),"Offline corruption falls back to reviewed bundled mapping");
  }catch(Exception error){throw new AssertionError(error);}finally{if(server!=null)server.stop(0);ServerItemCatalogSync.resetForTest();}
 }
}
