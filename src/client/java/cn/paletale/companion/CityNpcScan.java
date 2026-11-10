package cn.paletale.companion;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerSkin;

/** Only client-loaded, registered city NPCs. Never treats arbitrary nearby players as NPCs. */
public final class CityNpcScan {
    private static final Map<String, Entity> nearby = new LinkedHashMap<>();
    private static final Map<String, PlayerSkin> skins = new HashMap<>();
    private static Object level;
    private static long nextPending;
    private static final Map<Entity,Integer> waiting = new LinkedHashMap<>();
    private static final Map<Entity,NpcDiscovery.Seen> observations=new HashMap<>();
    private static Entity highlighted;
    private static Object highlightLevel;
    private static NavPlanner.Plan highlightPlan;
    private static long sightingsRevision, highlightSightings = -1, highlightCatalog = -1;
    private CityNpcScan() {}
    static void register() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.ENTITY_LOAD.register((entity,world)->{
            if(CityNavigation.WORLD.equals(world.dimension().identifier().toString())) candidate(entity);
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.ENTITY_UNLOAD.register((entity,world)->forget(entity));
    }
    static void remember(String id, Entity entity) {
        if(nearby.put(id,entity)!=entity)sightingsRevision++;
        refreshHighlight(Minecraft.getInstance());
    }
    static void forget(Entity entity) {
        waiting.remove(entity);
        observations.remove(entity);
        if(nearby.values().removeIf(e->e==entity))sightingsRevision++;
        if(highlighted==entity)highlighted=null;
    }
    private static void candidate(Entity entity) {
        String type=BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        var places=new java.util.ArrayList<>(CityCatalog.npcs());places.addAll(CityCatalog.shortcuts());
        if(places.stream().anyMatch(p->CityCatalog.matches(p,type,entity.getX(),entity.getY(),entity.getZ()))||NpcDiscovery.supports(type))waiting.putIfAbsent(entity,0);
    }
    static void tick(Minecraft client) {
        CityNpcSync.prepare();
        if(level!=client.level){level=client.level;nearby.clear();skins.clear();waiting.clear();observations.clear();sightingsRevision++;clearHighlight();NpcEntityIcons.clear();if(client.level!=null)CityNpcSync.preload();if(client.level!=null&&CityNavigation.WORLD.equals(Navigation.world(client)))for(var e:client.level.entitiesForRendering())candidate(e);}
        CityNpcSync.tick(client);
        if(client.player==null||!CityNavigation.WORLD.equals(Navigation.world(client))||waiting.isEmpty()||System.currentTimeMillis()<nextPending)return;
        nextPending=System.currentTimeMillis()+5000;
        for(var entity:List.copyOf(waiting.keySet())) {
            if(entity.distanceToSqr(client.player)>96*96)continue;
            int tries=waiting.get(entity)+1;waiting.put(entity,tries);
            if(!entity.isAlive()||entity.level()!=client.level||tries>12){waiting.remove(entity);observations.remove(entity);continue;}
            var found=find(CityNavigation.WORLD,client.player,List.of(entity));
            if(found.isEmpty()) {
                boolean listed=entity instanceof AbstractClientPlayer player&&player.getGameProfile().name().matches("[A-Za-z0-9_]{1,16}")&&client.getConnection()!=null&&client.getConnection().getListedOnlinePlayers().stream().anyMatch(info->info.getProfile().id().equals(player.getUUID()));
                if(listed){waiting.remove(entity);observations.remove(entity);continue;}
                String type=BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                String name=serviceName(entity);
                var place=NpcDiscovery.place(Navigation.world(client),type,name,entity.getX(),entity.getY(),entity.getZ(),listed);
                if(place!=null) {
                    var now=new NpcDiscovery.Seen(name,entity.getX(),entity.getY(),entity.getZ());
                    var before=observations.put(entity,now);
                    if(NpcDiscovery.stable(before,now)) {
                        CityNpcSync.discovered(place);
                        found=find(CityNavigation.WORLD,client.player,List.of(entity));
                    }
                }
            }
            for(var pair:found.entrySet()) {
                remember(pair.getKey(),entity);
                var places=new java.util.ArrayList<>(CityCatalog.npcs());places.addAll(CityCatalog.shortcuts());
                var place=places.stream().filter(p->p.id().equals(pair.getKey())).findFirst().orElseThrow();
                if(entity instanceof AbstractClientPlayer player) {
                    var skin=player.getSkin();String hash=skin.body() instanceof net.minecraft.core.ClientAsset.DownloadedTexture texture ? NpcProfile.textureHash(texture.url()) : null;
                    if(hash==null)for(var property:player.getGameProfile().properties().get("textures")){hash=NpcProfile.skinHash(property.value());if(hash!=null)break;}
                    String path=skin.body().texturePath().getPath();
                    if(hash==null&&path.matches("skins/[a-f0-9]{64}"))hash=path.substring(6);
                    if(!path.startsWith("textures/entity/player/"))skins.put(place.id(),skin);
                    boolean ready=!path.startsWith("textures/entity/player/");
                    boolean ownReady=!client.player.getSkin().body().texturePath().getPath().startsWith("textures/entity/player/");
                    boolean personalized=ready&&ownReady&&skin.body().texturePath().equals(client.player.getSkin().body().texturePath());
                    if(ready&&ownReady&&(personalized||hash!=null)){CityNpcSync.learn(place,hash,personalized);waiting.remove(entity);}
                } else {CityNpcSync.learn(place,null);waiting.remove(entity);}
            }
        }
    }
    private static String serviceName(Entity entity) {
        for(var name:List.of(entity.getName().getString(),entity.getDisplayName()==null?"":entity.getDisplayName().getString(),entity.getCustomName()==null?"":entity.getCustomName().getString())) {
            String clean=NpcDiscovery.cleanName(name);if(NpcDiscovery.serviceName(clean))return clean;
        }
        return "";
    }
    static void scan(Minecraft client) { if(client.level!=null&&CityNavigation.WORLD.equals(Navigation.world(client)))for(var e:client.level.entitiesForRendering())candidate(e); }
    static Map<String, Entity> find(String world, Entity observer, Iterable<Entity> entities) {
        Map<String, Entity> found = new LinkedHashMap<>();
        if (observer == null || !CityNavigation.WORLD.equals(world)) return found;
        var places = new java.util.ArrayList<>(CityCatalog.npcs()); places.addAll(CityCatalog.shortcuts());
        for (var entity : entities) {
            if (entity == observer || !entity.isAlive() || entity.distanceToSqr(observer) > 96 * 96) continue;
            String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            for (var place : places) {
                if (!CityCatalog.matches(place, type, entity.getX(), entity.getY(), entity.getZ())) continue;
                if (!CityCatalog.matchesName(place, entity.getName().getString(), entity.getDisplayName() == null ? "" : entity.getDisplayName().getString())) continue;
                found.put(place.id(), entity);
            }
        }
        return found;
    }
    static List<CityCatalog.Place> nearby() {
        var result = new java.util.ArrayList<CityCatalog.Place>();
        for (var place : CityCatalog.npcs()) if (seen(place)) result.add(place);
        for (var place : CityCatalog.shortcuts()) if (seen(place)) result.add(place);
        return result;
    }
    static boolean seen(CityCatalog.Place place) { var e=nearby.get(place.id());var c=Minecraft.getInstance();return c.player!=null&&CityNavigation.WORLD.equals(Navigation.world(c))&&e!=null&&e.isAlive()&&e.level()==c.level&&e.distanceToSqr(c.player)<=96*96; }
    /** Resolve only when route, sightings, catalog or world changes; never scan NPCs in an entity render hook. */
    static void refreshHighlight(Minecraft client) {
        var goal=Navigation.target();var plan=Navigation.currentPlan();
        if(goal==null||plan==null||client.player==null||!CityNavigation.WORLD.equals(goal.world())||!Navigation.inTargetWorld(client)){clearHighlight();return;}
        long catalog=CityCatalog.revision();
        if(highlightLevel==client.level&&highlightPlan==plan&&highlightSightings==sightingsRevision&&highlightCatalog==catalog)return;
        highlightLevel=client.level;highlightPlan=plan;highlightSightings=sightingsRevision;highlightCatalog=catalog;highlighted=null;
        var guide=plan.guide();
        for(var places:List.of(CityCatalog.npcs(),CityCatalog.shortcuts()))for(var place:places) {
            if(place.blockX()!=guide.x()||place.blockZ()!=guide.z()||(guide.y()!=null&&Math.abs(place.y()-guide.y())>=2))continue;
            var entity=nearby.get(place.id());
            if(entity!=null&&entity.level()==client.level&&entity.isAlive()){highlighted=entity;return;}
        }
    }
    static void clearHighlight() { highlighted=null;highlightLevel=null;highlightPlan=null; }
    static boolean hasHighlight() { return highlighted!=null&&highlighted(highlighted); }
    /** Called for every rendered entity: constant time, no catalog lookup, string formatting or allocation. */
    public static boolean highlighted(Entity entity) { return entity==highlighted&&entity.isAlive()&&entity.level()==Minecraft.getInstance().level; }
    static boolean head(GuiGraphics graphics, CityCatalog.Place place, int x, int y, int size) {
        if(place.entityType()==null)return false;
        if(!place.entityType().equals("minecraft:player"))return NpcEntityIcons.draw(graphics,place,nearby.get(place.id()),x,y,size);
        var profile=CityNpcSync.profile(place.id());
        var client=Minecraft.getInstance();
        if(profile!=null&&profile.personalized()&&client.player!=null){PlayerFaceRenderer.draw(graphics,client.player.getSkin(),x,y,size);return true;}
        if(profile!=null&&profile.avatarPath()!=null&&MarketImages.INSTANCE.draw(graphics,PaleTaleClient.MARKET,profile.avatarPath(),x,y,size))return true;
        var skin = skins.get(place.id());
        if (skin == null) {
            if(place.entityType()==null)return false;
            PlayerFaceRenderer.draw(graphics,net.minecraft.resources.Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png"),x,y,size,true,false,-1);return true;
        }
        PlayerFaceRenderer.draw(graphics, skin, x, y, size); return true;
    }
}
