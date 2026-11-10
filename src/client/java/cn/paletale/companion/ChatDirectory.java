package cn.paletale.companion;
import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.fabricmc.loader.api.FabricLoader;
import static cn.paletale.companion.ChatTokens.*;
/** One cancellable background refresh, six-hour compact disk cache, no work in render/hit testing. */
final class ChatDirectory {
    static volatile Index index=new Index(List.of());
    private static volatile long generation;
    private static volatile Thread worker;
    private static volatile CompletableFuture<?> request;
    private static long next;private static URI site;
    static volatile String status="目录尚未同步";
    static synchronized void clear(){generation++;if(request!=null)request.cancel(true);if(worker!=null)worker.interrupt();index=new Index(List.of());next=0;site=null;status="目录尚未同步";}
    static synchronized void ensure(){
        var client=Minecraft.getInstance();
        if(!PaleTaleClient.ACCOUNT.canLogin(client)||!(ModSettings.chatPlaces()||ModSettings.chatPlayers()||ModSettings.chatItems()))return;
        URI current=SiteConfig.current();if(!current.equals(site)){clear();site=current;}
        if(worker!=null||System.currentTimeMillis()<next)return;
        long run=generation;next=System.currentTimeMillis()+360000;status="目录同步中";
        worker=Thread.ofVirtual().name("paletale-chat-catalog").unstarted(()->refresh(current,run));worker.start();
    }
    private static void alive(long run)throws InterruptedException{if(run!=generation||Thread.currentThread().isInterrupted())throw new InterruptedException();}
    private static synchronized void install(Index ready,long run)throws InterruptedException{alive(run);index=ready;}
    private static <T> T await(CompletableFuture<T> task,long run)throws Exception{
        synchronized(ChatDirectory.class){try{alive(run);request=task;}catch(InterruptedException cancelled){task.cancel(true);throw cancelled;}}
        try{return task.get(45,TimeUnit.SECONDS);}finally{if(!task.isDone())task.cancel(true);synchronized(ChatDirectory.class){if(request==task)request=null;}}
    }
    private record Reply(int status,String body,String etag) {}
    private record Snapshot(String body,String etag,List<Target> names,List<String> nations,List<ChatExclusions.Rule> exclusions,int format) {}
    private static final int MAX_BODY=3_000_000, MAX_CACHE=6_000_000;
    private static String normalizedEtag(String raw){
        if(raw==null)return null;String value=raw.trim();if(value.startsWith("W/"))value=value.substring(2);
        return value.matches("\\\"[0-9a-f]{64}\\\"")?value:null;
    }
    private static Reply fetch(URI uri,String etag,long run)throws Exception{
        var builder=HttpBodies.request(uri).timeout(Duration.ofSeconds(30));
        if(etag!=null)builder.header("If-None-Match",etag);
        var response=await(HttpBodies.CLIENT.sendAsync(builder.GET().build(),HttpBodies.limited(MAX_BODY)),run);
        if(response.statusCode()==304)return new Reply(304,"",normalizedEtag(response.headers().firstValue("ETag").orElse(etag)));
        if(response.statusCode()!=200)throw new java.io.IOException("catalog HTTP "+response.statusCode());
        // 已由 HttpBodies 解压并限制解压后大小。
        byte[] bytes=response.body();
        return new Reply(200,new String(bytes,StandardCharsets.UTF_8),normalizedEtag(response.headers().firstValue("ETag").orElse(null)));
    }
    private static Snapshot readSnapshot(Path path)throws java.io.IOException{
        if(!Files.isRegularFile(path)||Files.size(path)>MAX_CACHE)return null;
        String raw=Files.readString(path);var root=JsonParser.parseString(raw).getAsJsonObject();String body,etag;
        int format=root.has("version")?root.get("version").getAsInt():0;
        if(format==2||format==3||format==4){body=root.get("body").getAsString();etag=normalizedEtag(root.get("etag").getAsString());if(!ChatSync.sha(body.getBytes(StandardCharsets.UTF_8)).equals(root.get("bodySha").getAsString()))throw new IllegalArgumentException("Corrupt names cache");}
        else{body=raw;etag="\""+ChatSync.sha(raw.getBytes(StandardCharsets.UTF_8))+"\"";}
        if(body.getBytes(StandardCharsets.UTF_8).length>MAX_BODY)throw new IllegalArgumentException("Names cache exceeds limit");
        return new Snapshot(body,etag,ChatCatalog.names(new java.io.StringReader(body)),ChatCatalog.nations(JsonParser.parseString(body).getAsJsonObject()),ChatCatalog.exclusions(JsonParser.parseString(body).getAsJsonObject()),format);
    }
    private static String encode(Collection<Target> entries,List<String> nations,List<ChatExclusions.Rule> exclusions){
        var root=new JsonObject();root.addProperty("version",1);var rows=new JsonArray();
        for(var target:entries){var row=new JsonArray();row.add(target.kind().name().toLowerCase(Locale.ROOT));row.add(target.name());row.add(target.key());rows.add(row);}root.add("names",rows);if(!nations.isEmpty()){var countries=new JsonArray();for(String nation:nations)countries.add(nation);root.add("nations",countries);}var blocked=new JsonArray();for(var rule:exclusions){var row=new JsonArray();row.add(rule.kind());row.add(rule.name());blocked.add(row);}root.add("exclusions",blocked);
        return new GsonBuilder().disableHtmlEscaping().create().toJson(root);
    }
    private static void save(Path path,String body,String etag,long run)throws Exception{
        var root=new JsonObject();root.addProperty("version",4);root.addProperty("body",body);root.addProperty("bodySha",ChatSync.sha(body.getBytes(StandardCharsets.UTF_8)));root.addProperty("etag",etag==null?"":etag);
        String encoded=new GsonBuilder().disableHtmlEscaping().create().toJson(root);if(encoded.getBytes(StandardCharsets.UTF_8).length>MAX_CACHE)throw new java.io.IOException("Names cache too large");
        Files.createDirectories(path.getParent());var temp=path.resolveSibling(path.getFileName()+".tmp");
        try{Files.writeString(temp,encoded);alive(run);Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}
    }
    private static void refresh(URI current,long run){
        try{
            var cache=FabricLoader.getInstance().getGameDir().resolve("paletale/cache/chat-names-1.0.1-beta-"+Integer.toHexString(current.toString().hashCode())+"-"+(ModSettings.chatPlaces()||ModSettings.chatPlayers()?"world":"")+(ModSettings.chatItems()?"items":"")+".json");
            long now=System.currentTimeMillis();Snapshot saved=null;
            try{saved=readSnapshot(cache);if(saved!=null&&!saved.names().isEmpty()){
                install(new Index(saved.names(),saved.nations(),saved.exclusions()),run);
                if(now-Files.getLastModifiedTime(cache).toMillis()<21600000&&(saved.format()>=4)){synchronized(ChatDirectory.class){alive(run);next=now+21600000;status="聊天目录已就绪";}return;}
            }}catch(InterruptedException cancelled){throw cancelled;}catch(Exception invalidCache){saved=null;}
            List<Target> base=saved!=null?saved.names():List.of();
            if(base.isEmpty())try(var bundled=ChatDirectory.class.getResourceAsStream("/assets/paletale/chat-names-1.0.1-beta.json")){
                if(bundled!=null)base=ChatCatalog.names(new java.io.InputStreamReader(bundled,StandardCharsets.UTF_8));
                install(new Index(base),run);
            }
            Reply reply=fetch(current.resolve("/api/mcmod/chat/names?manifest=1"),saved==null?null:saved.etag(),run);
            if(reply.status()==304){
                if(saved==null)throw new IllegalStateException("Unexpected 304");
                alive(run);if(saved.format()<4)save(cache,saved.body(),saved.etag(),run);else Files.setLastModifiedTime(cache,java.nio.file.attribute.FileTime.fromMillis(now));
                synchronized(ChatDirectory.class){alive(run);next=System.currentTimeMillis()+21600000;status="聊天名称目录已是最新";}return;
            }
            var root=JsonParser.parseString(reply.body()).getAsJsonObject();List<Target> fresh;List<String> nations=ChatCatalog.nations(root);List<ChatExclusions.Rule> exclusions=ChatCatalog.exclusions(root);String body;
            if(root.has("names")){ // Older servers ignore the manifest query and return a complete list.
                body=reply.body();fresh=ChatCatalog.names(new java.io.StringReader(body));
            }else{
                if(!root.has("version")||root.get("version").getAsInt()!=1||!root.has("shards"))throw new IllegalArgumentException("Invalid names manifest");
                var array=root.getAsJsonArray("shards");if(array.size()!=ChatSync.SHARDS)throw new IllegalArgumentException("Invalid names manifest");
                String[] expected=new String[ChatSync.SHARDS];for(int i=0;i<expected.length;i++){expected[i]=array.get(i).getAsString();if(!expected[i].matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid shard digest");}
                String[] previous=ChatSync.hashes(base);var changed=new TreeSet<Integer>();for(int i=0;i<expected.length;i++)if(!expected[i].equals(previous[i]))changed.add(i);
                if(changed.isEmpty())fresh=base;
                else{
                    String selected=changed.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                    var update=fetch(current.resolve("/api/mcmod/chat/names?shards="+selected),null,run);
                    if(update.status()!=200)throw new IllegalStateException("Invalid names delta");
                    fresh=ChatSync.merge(base,ChatCatalog.names(new java.io.StringReader(update.body())),changed,expected);
                }
                body=encode(fresh,nations,exclusions);
            }
            var ready=new Index(fresh,nations,exclusions);alive(run);save(cache,body,reply.etag(),run);
            synchronized(ChatDirectory.class){alive(run);index=ready;next=System.currentTimeMillis()+21600000;status="聊天名称目录已就绪";}
        }catch(InterruptedException ignored){Thread.currentThread().interrupt();}
        catch(Exception failure){if(run==generation)status="名称接口暂不可用，保留上次名称；稍后重试";}
        finally{synchronized(ChatDirectory.class){worker=null;request=null;}}
    }
}
