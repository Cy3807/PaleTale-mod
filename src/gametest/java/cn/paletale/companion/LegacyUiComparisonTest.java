package cn.paletale.companion;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.Screen;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** Identical viewport/data for old and new UI. Samples are synthetic; no real account/server access. */
final class LegacyUiComparisonTest {
    /** Same JSON shape the notification endpoint returns; synthetic samples only. */
    private record Note(int id,String kind,String adCode,String adTitle,String adOwner,String adOwnerId,int itemId,String product,String change,String detail,boolean read,String createdAt) {}
    private static final List<Note> NOTES = List.of(
        new Note(11,"price","123456","同条件样例商店","店主","SampleOwner",0,"樱花酒","64 → 48","樱花酒：64 → 48",false,"2026-10-22T10:00:00Z"),
        new Note(10,"effect_approved","","同条件样例酒","","",5,"","力量 II","同条件样例酒：力量 II",true,"2026-10-21T10:00:00Z"));
    private static JsonObject entry(String kind,int id) {
        var row=new JsonObject();boolean item=kind.equals("items");String type=item?new String[]{"map_art","wine","dish"}[(id-1)%3]:kind.equals("players")?"player":"org";
        row.addProperty("kind",kind);row.addProperty("id",id);row.addProperty("name",item?type.equals("map_art")?"【样例】长标题地图画·共同基线":type.equals("wine")?"样例酒·归途":"样例菜·春日午餐":kind.equals("players")?"SamplePlayer"+id:"样例组织"+id);
        row.addProperty("subtitle",item?"拥有者 · SampleOwner":"相同样例简介");row.addProperty("description","相同样例，仅用于本地视觉对照。");row.addProperty("category",type);row.addProperty("badge",item?type.equals("wine")?"2D 酒":type.equals("dish")?"菜":"地图画":kind.equals("players")?"玩家":"组织");row.addProperty("hasModel",false);
        row.addProperty("imagePath","/api/mcmod/items/"+id+"/image");row.addProperty("views",item?12:-1);row.addProperty("favorites",item?2:-1);row.addProperty("effect",type.equals("wine")?"speed":"");row.addProperty("effectText",type.equals("wine")?"迅捷 II":"");row.addProperty("facts",type.equals("map_art")?"中型 2×4 · 二次元 · 游戏 · 表情包 · 很长的标签":"");
        if(item){var owner=new JsonObject();owner.addProperty("playerId",1);owner.addProperty("name","SampleOwner");owner.addProperty("mcName","SampleOwner");owner.addProperty("verified",true);owner.addProperty("avatarPath","/api/players/11111111111111111111111111111111/avatar");row.add("owner",owner);}
        return row;
    }
    static void run(ClientGameTestContext context)throws Exception {
        var workers=Executors.newVirtualThreadPerTaskExecutor();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(workers);
        server.createContext("/api/",exchange->{
            String path=exchange.getRequestURI().getPath();byte[] bytes;
            if(path.endsWith("/image")||path.endsWith("/avatar")){try(var image=LegacyUiComparisonTest.class.getResourceAsStream("/assets/paletale/textures/gui/wine.png")){bytes=image.readAllBytes();}exchange.getResponseHeaders().set("Content-Type","image/png");}
            else {
                JsonObject body=new JsonObject();
                if(path.contains("notifications")){if(exchange.getRequestMethod().equals("POST"))body.addProperty("ok",true);else {body.addProperty("page",1);body.addProperty("pageSize",8);body.addProperty("total",2);body.addProperty("unreadTotal",1);body.add("items",new Gson().toJsonTree(NOTES));}}
                else if(path.endsWith("/version")){body.addProperty("version",1);body.addProperty("latest","0.0.0");}
                else {String kind=path.endsWith("/players")?"players":path.endsWith("/orgs")?"orgs":"items";int size=24;String query=exchange.getRequestURI().getRawQuery();if(query!=null)for(String part:query.split("&"))if(part.startsWith("size="))size=Integer.parseInt(part.substring(5));var rows=new JsonArray();for(int i=1;i<=Math.min(size,12);i++)rows.add(entry(kind,i));body.addProperty("version",1);body.addProperty("kind",kind);body.addProperty("page",1);body.addProperty("pageSize",size);body.addProperty("total",12);body.addProperty("hasMore",false);body.add("entries",rows);}
                bytes=body.toString().getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");
            }
            exchange.sendResponseHeaders(200,bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}
        });server.start();
        URI site=URI.create("http://127.0.0.1:"+server.getAddress().getPort());var media=new MarketApi(site);URI previous=context.computeOnClient(c->PaleTaleClient.MARKET.site());String version=context.computeOnClient(c->net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("paletale").orElseThrow().getMetadata().getVersion().getFriendlyString());
        var measurements=new JsonArray();
        try {
            for(int scale:new int[]{2,3,4}){
                context.runOnClient(c->{PaleTaleClient.MARKET.retarget(site);c.getWindow().setWindowed(1600,1000);c.options.guiScale().set(scale);c.resizeDisplay();});
                for(var kind:ArchiveData.Kind.values()){
                    context.runOnClient(c->{try{var f=NoticeWatch.class.getDeclaredField("unreadCount");f.setAccessible(true);if(f.getType()==int.class)f.setInt(null,0);else f.setLong(null,0);}catch(Exception e){throw new AssertionError(e);}c.setScreen(new ArchiveScreen(null,media,kind,0));});
                    context.waitFor(c->c.screen instanceof ArchiveScreen s&&s.result()!=null,600);context.waitTicks(10);context.getInput().setCursorPos(0,0);
                    JsonObject measurement=context.computeOnClient(c->{var row=new JsonObject();row.addProperty("scale",scale);row.addProperty("kind",kind.id);row.addProperty("logicalWidth",c.screen.width);row.addProperty("logicalHeight",c.screen.height);try {var f=ArchiveScreen.class.getDeclaredField("cardHeight");f.setAccessible(true);row.addProperty("cardHeight",f.getInt(c.screen));}catch(Exception e){throw new AssertionError(e);}return row;});measurements.add(measurement);
                    context.takeScreenshot("legacy-compare-"+kind.id+"-gui"+scale+"-v"+version);
                }
                context.runOnClient(c->{try {
                    Screen screen;
                    var ctor=NotificationsScreen.class.getDeclaredConstructor(Screen.class,NoticeApi.class,Supplier.class);ctor.setAccessible(true);screen=ctor.newInstance(null,new NoticeApi(site),(Supplier<String>)()->"comparison-token");
                    c.setScreen(screen);
                }catch(Exception e){throw new AssertionError(e);}});
                context.waitTicks(15);context.getInput().setCursorPos(0,0);context.takeScreenshot("legacy-compare-notifications-gui"+scale+"-v"+version);
            }
            Files.createDirectories(Path.of("comparison"));Files.writeString(Path.of("comparison","geometry-v"+version+".json"),measurements.toString());
        } finally {context.runOnClient(c->{c.setScreen(null);PaleTaleClient.MARKET.retarget(previous);});server.stop(0);workers.shutdownNow();}
    }
}
