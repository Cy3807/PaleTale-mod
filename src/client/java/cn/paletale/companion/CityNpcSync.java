package cn.paletale.companion;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Durable shared NPC cache. Only newly learned appearances enter the outbox; a successful report is never resent. */
final class CityNpcSync {
    private static final Gson GSON = new com.google.gson.GsonBuilder().serializeNulls().create();
    private static final Map<String,NpcProfile> profiles = new LinkedHashMap<>();
    private static final Set<String> acknowledged = new HashSet<>();
    private static final java.util.concurrent.ExecutorService IO = java.util.concurrent.Executors.newSingleThreadExecutor(r -> { var t=new Thread(r,"PaleTale-NPC-cache");t.setDaemon(true);return t; });
    private static final HttpClient HTTP = HttpBodies.CLIENT;
    private static boolean loaded, fetching, uploading, inCity;
    private static long fetchAfter, uploadAfter;
    private static Path folder() { return Minecraft.getInstance().gameDirectory.toPath().resolve("paletale/cache/navigation"); }
    private record Saved(List<NpcProfile> profiles, Set<String> acknowledged, List<CityCatalog.Place> discovered) {}
    static void prepare() { load(); }
    private static void load() {
        if(loaded)return; loaded=true;
        try { var p=folder().resolve("npcs.json");if(Files.size(p)>1000000)return;var state=GSON.fromJson(Files.readString(p),Saved.class);
            if(state.discovered()!=null)for(var place:state.discovered())CityCatalog.discover(place);
            for(var row:state.profiles())if(row.valid())profiles.put(row.id(),row);acknowledged.addAll(state.acknowledged());
        } catch(Exception ignored) {}
        try { var p=folder().resolve("catalog.json"); if(Files.size(p)<=200000)CityCatalog.accept(Files.readString(p)); } catch(Exception ignored) {}
        preload();
    }
    private static void save() {
        String json=GSON.toJson(new Saved(List.copyOf(profiles.values()),Set.copyOf(acknowledged),CityCatalog.discovered()));
        IO.execute(()->write("npcs.json",json));
    }
    private static void write(String name,String json) {
        try { Files.createDirectories(folder());var tmp=Files.createTempFile(folder(),"npc-",".tmp");Files.writeString(tmp,json);Files.move(tmp,folder().resolve(name),StandardCopyOption.REPLACE_EXISTING); } catch(Exception ignored) {}
    }
    static void learn(CityCatalog.Place place, String hash) { learn(place,hash,false); }
    static void discovered(CityCatalog.Place place) { load();if(CityCatalog.discover(place)){save();learn(place,null);} }
    static void learn(CityCatalog.Place place, String hash, boolean personalized) {
        load(); var old=profiles.get(place.id());boolean fill=old!=null&&!old.personalized()&&old.skinHash()==null&&hash!=null;
        if(old!=null&&old.personalized()||old!=null&&!personalized&&!fill)return;
        var profile=NpcProfile.of(place,hash,personalized);if(!profile.valid())return;
        profiles.put(place.id(),profile);if(old!=null)acknowledged.removeIf(key->key.endsWith("|"+place.id()));save();uploadAfter=0;
        if(profile.avatarPath()!=null)MarketImages.INSTANCE.prefetch(PaleTaleClient.MARKET,profile.avatarPath());
    }
    static void preload() { for(var profile:profiles.values())if(profile.avatarPath()!=null)MarketImages.INSTANCE.prefetch(PaleTaleClient.MARKET,profile.avatarPath()); }
    static NpcProfile profile(String id) { load();return profiles.get(id); }
    static void tick(Minecraft client) {
        load();boolean city=client.player!=null&&CityNavigation.WORLD.equals(Navigation.world(client));
        if(city&&!inCity&&System.currentTimeMillis()>=fetchAfter)fetch();inCity=city;
        if(!city||uploading||System.currentTimeMillis()<uploadAfter)return;
        String token=PaleTaleClient.ACCOUNT.token();if(token==null)return;
        String site=SiteConfig.current().toString();
        var pending=profiles.values().stream().filter(p->!acknowledged.contains(site+"|"+p.id())).findFirst().orElse(null);
        if(pending==null)return;uploading=true;
        var request=HttpBodies.request(SiteConfig.current().resolve("/api/mcmod/nav/npcs")).timeout(Duration.ofSeconds(15)).header("Content-Type","application/json").header("Authorization","Bearer "+token).POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(pending))).build();
        HTTP.sendAsync(request,HttpBodies.limited(300000)).whenComplete((response,error)->client.execute(()->{
            boolean accepted=error==null&&accepted(response.statusCode(),response.body());
            uploading=false;uploadAfter=System.currentTimeMillis()+(accepted?500:600000);
            if(accepted&&pending.equals(profiles.get(pending.id()))){acknowledged.add(site+"|"+pending.id());save();}
        }));
    }
    static boolean accepted(int status,byte[] body) {
        if(status!=200)return false;
        try {var ok=JsonParser.parseString(new String(body,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().get("ok");return ok!=null&&ok.isJsonPrimitive()&&ok.getAsJsonPrimitive().isBoolean()&&ok.getAsBoolean();}
        catch(RuntimeException invalid){return false;}
    }
    private static void fetch() {
        if(fetching)return;fetching=true;fetchAfter=System.currentTimeMillis()+6*3600000L;String site=SiteConfig.current().toString();
        HTTP.sendAsync(HttpBodies.request(SiteConfig.current().resolve("/api/mcmod/nav/npcs")).timeout(Duration.ofSeconds(15)).GET().build(),HttpBodies.limited(300000)).whenComplete((response,error)->Minecraft.getInstance().execute(()->{
            fetching=false;if(!SiteConfig.current().toString().equals(site)||error!=null||response.statusCode()!=200||response.body().length>300000)return;
            try { var root=JsonParser.parseString(new String(response.body(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();String catalog=root.get("catalog").toString();long revision=CityCatalog.revision();if(!CityCatalog.accept(catalog))return;IO.execute(()->write("catalog.json",catalog));
                for(var item:root.getAsJsonArray("profiles")){var p=GSON.fromJson(item,NpcProfile.class);if(!p.valid())continue;
                    if(p.id().startsWith("scan-"))CityCatalog.discover(NpcDiscovery.place(p.world(),p.entityType(),p.name(),p.x(),p.y(),p.z(),false));
                    var old=profiles.get(p.id());if(old!=null&&(old.personalized()&&!p.personalized()||old.skinHash()!=null&&p.skinHash()==null&&!p.personalized()))continue;profiles.put(p.id(),p);acknowledged.add(site+"|"+p.id());}
                if(revision!=CityCatalog.revision())CityNpcScan.scan(Minecraft.getInstance());save();preload();
            }catch(RuntimeException ignored){}
        }));
    }
}
