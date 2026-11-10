package cn.paletale.companion;
import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.zip.GZIPOutputStream;
import net.fabricmc.loader.api.FabricLoader;
import static cn.paletale.companion.ChatTokens.*;
/** Uses a local public-only endpoint and the actual client refresh method; never connects to production. */
final class ChatDirectoryClientTest {
    private static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}
    private static byte[] gzip(String body)throws IOException{var out=new ByteArrayOutputStream();try(var zip=new GZIPOutputStream(out)){zip.write(body.getBytes(StandardCharsets.UTF_8));}return out.toByteArray();}
    private static String json(List<Target> targets){var root=new JsonObject();root.addProperty("version",1);var array=new JsonArray();for(var t:targets){var row=new JsonArray();row.add(t.kind().name().toLowerCase(Locale.ROOT));row.add(t.name());row.add(t.key());array.add(row);}root.add("names",array);return new Gson().toJson(root);}
    private static long generation(){try{var f=ChatDirectory.class.getDeclaredField("generation");f.setAccessible(true);return f.getLong(null);}catch(Exception e){throw new AssertionError(e);}}
    private static void refresh(URI site,long generation)throws Exception{var m=ChatDirectory.class.getDeclaredMethod("refresh",URI.class,long.class);m.setAccessible(true);m.invoke(null,site,generation);}
    private static void expire(Path path)throws IOException{Files.setLastModifiedTime(path,FileTime.fromMillis(System.currentTimeMillis()-7*3600000L));}
    static void runLive()throws Exception{
        URI site=SiteConfig.MAIN;var path=FabricLoader.getInstance().getGameDir().resolve("paletale/cache/chat-names-1.0.1-beta-"+Integer.toHexString(site.toString().hashCode())+"-worlditems.json");
        try{Files.deleteIfExists(path);ChatDirectory.clear();long run=generation();refresh(site,run);check(ChatDirectory.index.size()>19000&&ChatDirectory.status.equals("聊天名称目录已就绪"),"real server manifest + gzip/shards");String before=Files.readString(path);expire(path);refresh(site,run);check(ChatDirectory.status.contains("最新")&&Files.readString(path).equals(before),"real server gzip ETag 304 with cache");String country="衡序双生圣国";check(ChatTokens.parse(country,ChatDirectory.index,new Options(false,true,false,false,false)).stream().anyMatch(m->m.targets().stream().anyMatch(t->t.kind()==Kind.NATION&&t.key().equals(country))),"real synchronized nation is recognizable");String query=java.net.URLEncoder.encode(country,StandardCharsets.UTF_8);var response=new WorldApi().call(site,"/api/mcmod/chat/names?capital="+query,null,null).get(20,java.util.concurrent.TimeUnit.SECONDS);check(CapitalDestination.parse(response,country)!=null,"real public capital centre matches requested country");System.out.println("CHAT LIVE: "+ChatDirectory.index.size()+" names, country/capital, gzip/shard refresh and 304 passed");}finally{ChatDirectory.clear();Files.deleteIfExists(path);}
    }
    static void run()throws Exception{
        var rows=new AtomicReference<List<Target>>(java.util.stream.IntStream.range(1,1001).mapToObj(i->new Target(Kind.ITEM,"测试商品"+i,Integer.toString(i),null,null,null)).toList());
        var countries=new AtomicReference<List<String>>(List.of());var exclusions=new AtomicReference<List<ChatExclusions.Rule>>();
        var corrupt=new AtomicBoolean();var bomb=new AtomicBoolean();var requests=new AtomicInteger();var transferred=new AtomicLong();var lastQuery=new AtomicReference<>("");
        var server=com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{try{
            requests.incrementAndGet();check(exchange.getRequestURI().getPath().equals("/api/mcmod/chat/names"),"no unrelated downloads");check("gzip".equals(exchange.getRequestHeaders().getFirst("Accept-Encoding")),"client requests gzip");
            List<Target> current=rows.get();String full=json(current);if(!countries.get().isEmpty()||exclusions.get()!=null){var withCountries=JsonParser.parseString(full).getAsJsonObject();if(!countries.get().isEmpty()){var list=new JsonArray();for(String nation:countries.get())list.add(nation);withCountries.add("nations",list);}if(exclusions.get()!=null){var list=new JsonArray();for(var rule:exclusions.get()){var row=new JsonArray();row.add(rule.kind());row.add(rule.name());list.add(row);}withCountries.add("exclusions",list);}full=new Gson().toJson(withCountries);}String etag="\""+ChatSync.sha(full.getBytes(StandardCharsets.UTF_8))+"\"";String query=exchange.getRequestURI().getRawQuery();lastQuery.set(query);
            exchange.getResponseHeaders().set("ETag","W/"+etag);exchange.getResponseHeaders().set("Content-Type","application/json");
            if(etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))){exchange.sendResponseHeaders(304,-1);return;}
            String body;if(bomb.get())body="x".repeat(3_000_001);
            else if("manifest=1".equals(query)){var root=new JsonObject();root.addProperty("version",1);var hashes=new JsonArray();for(String value:ChatSync.hashes(current))hashes.add(value);root.add("shards",hashes);var nations=new JsonArray();for(String nation:countries.get())nations.add(nation);root.add("nations",nations);if(exclusions.get()!=null){var list=new JsonArray();for(var rule:exclusions.get()){var row=new JsonArray();row.add(rule.kind());row.add(rule.name());list.add(row);}root.add("exclusions",list);}body=root.toString();}
            else {check(query!=null&&query.startsWith("shards="),"only explicit changed shards requested");Set<Integer> wanted=new HashSet<>();for(String x:query.substring(7).split(","))wanted.add(Integer.valueOf(x));var selected=new ArrayList<Target>();for(var t:current)if(wanted.contains(ChatSync.shard(t)))selected.add(t);if(corrupt.get()&&!selected.isEmpty())selected.remove(0);body=json(selected);}
            byte[] bytes=gzip(body);transferred.addAndGet(bytes.length);exchange.getResponseHeaders().set("Content-Encoding","gzip");exchange.sendResponseHeaders(200,bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}
        }finally{exchange.close();}});server.start();
        var site=URI.create("http://127.0.0.1:"+server.getAddress().getPort());var path=FabricLoader.getInstance().getGameDir().resolve("paletale/cache/chat-names-1.0.1-beta-"+Integer.toHexString(site.toString().hashCode())+"-worlditems.json");
        try{
            Files.deleteIfExists(path);ChatDirectory.clear();long run=generation();refresh(site,run);
            check(requests.get()==2&&ChatDirectory.index.size()==1000&&Files.exists(path),"cold manifest + full changed shards");
            int count=requests.get();long bytes=transferred.get();expire(path);refresh(site,run);
            check(requests.get()==count+1&&transferred.get()==bytes,"unchanged stale cache uses 304 with zero body");
            var updated=new ArrayList<>(rows.get());updated.removeIf(t->t.key().equals("12"));updated.add(new Target(Kind.ITEM,"新增商品","9001",null,null,null));rows.set(List.copyOf(updated));
            count=requests.get();bytes=transferred.get();expire(path);refresh(site,run);
            check(requests.get()==count+2&&lastQuery.get().startsWith("shards=")&&lastQuery.get().split(",").length<=2,"add and delete fetch at most two changed shards");
            check(ChatDirectory.index.size()==1000&&!ChatTokens.parse("测试商品12",ChatDirectory.index,new Options(false,false,false,false,true)).stream().anyMatch(m->m.targets().stream().anyMatch(t->t.key().equals("12"))),"deleted name removed");
            check(!ChatTokens.parse("新增商品",ChatDirectory.index,new Options(false,false,false,false,true)).isEmpty(),"added name available");
            long deltaBytes=transferred.get()-bytes;check(deltaBytes<gzip(json(rows.get())).length,"delta transfers less than full gzip");
            var next=new ArrayList<>(rows.get());next.add(new Target(Kind.ITEM,"破损测试","9002",null,null,null));rows.set(List.copyOf(next));corrupt.set(true);expire(path);String previous=Files.readString(path);refresh(site,run);
            check(Files.readString(path).equals(previous)&&ChatDirectory.index.size()==1000,"bad delta rejected and prior data retained");
            corrupt.set(false);bomb.set(true);expire(path);refresh(site,run);check(Files.readString(path).equals(previous)&&ChatDirectory.index.size()==1000,"oversized gzip rejected before update");
            bomb.set(false);rows.set(List.copyOf(updated));Files.writeString(path,json(updated));expire(path);ChatDirectory.clear();run=generation();count=requests.get();bytes=transferred.get();refresh(site,run);
            check(requests.get()==count+1&&transferred.get()==bytes&&ChatDirectory.index.size()==1000&&ChatDirectory.status.contains("最新"),"beta.1 legacy cache migrates via 304 without full download");
            countries.set(List.of("独立国家"));expire(path);count=requests.get();refresh(site,run);check(requests.get()==count+1&&ChatDirectory.index.size()==1001&&!ChatTokens.parse("独立国家",ChatDirectory.index,new Options(false,true,false,false,false)).isEmpty(),"nation-only change updates metadata without downloading name shards");
            exclusions.set(List.of(new ChatExclusions.Rule("item","测试商品1")));expire(path);count=requests.get();refresh(site,run);check(requests.get()==count+1&&ChatDirectory.index.size()==1000&&ChatTokens.parse("测试商品1",ChatDirectory.index,new Options(false,false,false,false,true)).isEmpty(),"exclusion metadata alone updates index without downloading shards");
            exclusions.set(List.of());expire(path);count=requests.get();refresh(site,run);check(requests.get()==count+1&&ChatDirectory.index.size()==1001&&!ChatTokens.parse("测试商品1",ChatDirectory.index,new Options(false,false,false,false,true)).isEmpty(),"empty exclusion list restores name without downloading shards");
            System.out.println("CHAT SYNC: unchanged=304/0 body; beta.1 cache 304; changed shards="+deltaBytes+" bytes; full gzip="+gzip(json(updated)).length+" bytes; country/exclusion metadata, corruption and gzip limits passed");
        }finally{ChatDirectory.clear();server.stop(0);Files.deleteIfExists(path);}
    }
}
