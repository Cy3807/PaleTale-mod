package cn.paletale.companion;
import com.google.gson.JsonObject;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import static cn.paletale.companion.MarketTheme.*;
/** Load the private favorite list once; observe player-list changes locally without polling online APIs. */
final class FavoritePresenceWatch {
 private static final int PAGE_SIZE=48,MAX_PAGES=21,MAX_VISIBLE=6,MAX_INITIAL=1000,MAX_PENDING=1020;
 private static final long DISPLAY_MS=8000, GAP_MS=250, MOTION_MS=450;
 static final class Popup {final FavoritePresenceTracker.Change change;final UUID id;final long start; double from, target, velocity;long moved;
  Popup(FavoritePresenceTracker.Change c,long now){change=c;UUID parsed;try{String v=c.id();parsed=UUID.fromString(v.substring(0,8)+"-"+v.substring(8,12)+"-"+v.substring(12,16)+"-"+v.substring(16,20)+"-"+v.substring(20));}catch(RuntimeException ex){parsed=null;}id=parsed;start=now;moved=now;}
  double position(long now){double t=Math.max(0,now-moved),k=0.012,d=from-target;return target+(d+(velocity+k*d)*t)*Math.exp(-k*t);}
  double speed(long now){double t=Math.max(0,now-moved),k=0.012,d=from-target;return (velocity-k*(velocity+k*d)*t)*Math.exp(-k*t);}
  void move(double to,long now){if(target==to)return;double pos=position(now),speed=speed(now);from=pos;velocity=speed;target=to;moved=now;}
 }
 record Pending(FavoritePresenceTracker.Change change,boolean sound){}
 private final WorldApi api=new WorldApi();private final FavoritePresenceTracker tracker=new FavoritePresenceTracker();private final ArrayDeque<Pending> queue=new ArrayDeque<>();private final ArrayList<Popup> popups=new ArrayList<>();private final Map<UUID,net.minecraft.world.entity.player.PlayerSkin> skins=new HashMap<>();
 private CompletableFuture<?> request;private Object connection;private UUID selfId;private String token,world;private URI site;private long accountId=-1,nextLoad,generation,baselineAfter,nextPopup;private boolean rosterDirty,initialAnnouncement;private Map<String,String> favorites;private Set<String> favoriteNames=Set.of();private Map<UUID,String> favoriteIds;private boolean refreshNeeded;
 void tick(Minecraft client){String actor=PaleTaleClient.ACCOUNT.token();var citizen=PaleTaleClient.ACCOUNT.citizen();long user=citizen==null?-1:citizen.playerId();Object conn=client.getConnection();URI current=SiteConfig.current();boolean active=actor!=null&&user>=0&&conn!=null&&client.level!=null&&client.player!=null&&PaleTaleClient.ACCOUNT.canLogin(client);
  if(!active){if(connection!=null||token!=null||request!=null||favorites!=null||!popups.isEmpty()||!queue.isEmpty())reset();return;}
  if(user!=accountId||conn!=connection||!Objects.equals(site,current)){reset();if(conn!=announcedConn||user!=announcedUser){announced.clear();announcedConn=conn;announcedUser=user;}token=actor;accountId=user;connection=conn;site=current;world=Navigation.world(client);selfId=client.player.getUUID();initialAnnouncement=true;}
  else if(!Objects.equals(actor,token))rotateToken(actor);
  long now=System.currentTimeMillis();if(!ModSettings.favoritePresence()){queue.clear();popups.clear();}else advance(animationClock());
  if((favorites==null||refreshNeeded)&&request==null&&now>=nextLoad){nextLoad=now+60_000;long run=++generation;var future=load(current,actor,"all",1,new LinkedHashMap<>(),new HashSet<>(),-1);request=future;
   future.whenComplete((result,error)->client.execute(()->{if(run!=generation)return;request=null;if(error!=null||!Objects.equals(token,actor)||connection!=client.getConnection()||!current.equals(SiteConfig.current()))return;favorites=result;favoriteNames=Set.copyOf(result.values());favoriteIds=indexed(result);refreshNeeded=false;tracker.reset();shown.clear();missingSince.clear();baselineAfter=System.currentTimeMillis()+1000;rosterDirty=true;}));}
  if(favorites==null||favoriteIds==null||!rosterDirty||now<baselineAfter)return;if(favoriteIds.isEmpty()){initialAnnouncement=false;rosterDirty=false;return;}rosterDirty=false;var players=client.getConnection().getListedOnlinePlayers();for(var info:players)if(info!=null&&info.getProfile()!=null&&!Objects.equals(selfId,info.getProfile().id())&&favoriteIds.containsKey(info.getProfile().id()))skins.put(info.getProfile().id(),info.getSkin());var visible=visibleFavorites(players,favoriteIds,selfId);if(initialAnnouncement&&!tracker.ready()){announceInitial(visible);initialAnnouncement=false;}observe(favorites,steady(visible,now));
 }
 /** Tab-list entries flicker (list refreshes, brief re-adds); a favourite counts as gone only after being missing this long, so a player who stayed online is never announced twice. */
 private static final long OFFLINE_GRACE=45_000;private final Map<String,String> shown=new HashMap<>();
 /** Favourites already announced online on this connection; a transient sign-in/site flicker resets the watcher but must not re-announce players who never left. */
 private Object announcedConn;private long announcedUser=-2;private final Set<String> announced=new HashSet<>();private final Map<String,Long> missingSince=new HashMap<>();
 private Map<String,String> steady(Map<String,String> visible,long now){
  var held=new HashMap<>(visible);missingSince.keySet().removeIf(visible::containsKey);
  for(var entry:shown.entrySet())if(!visible.containsKey(entry.getKey())){long since=missingSince.computeIfAbsent(entry.getKey(),k->now);if(now-since<OFFLINE_GRACE){held.put(entry.getKey(),entry.getValue());rosterDirty=true;baselineAfter=now+1000;}else missingSince.remove(entry.getKey());}
  shown.clear();shown.putAll(held);return held;
 }
 static Map<UUID,String> indexed(Map<String,String> favorites){var out=new HashMap<UUID,String>();for(var entry:favorites.entrySet()){String id=entry.getKey();if(!id.matches("[0-9a-f]{32}"))throw new IllegalArgumentException("invalid UUID");out.put(new UUID(Long.parseUnsignedLong(id.substring(0,16),16),Long.parseUnsignedLong(id.substring(16),16)),entry.getValue());}return Map.copyOf(out);}
 static Map<String,String> visibleFavorites(Collection<net.minecraft.client.multiplayer.PlayerInfo> players,Map<UUID,String> favorites){return visibleFavorites(players,favorites,null);}
 static Map<String,String> visibleFavorites(Collection<net.minecraft.client.multiplayer.PlayerInfo> players,Map<UUID,String> favorites,UUID self){
  var online=new HashMap<String,String>();for(var info:players){if(info==null||info.getProfile()==null)continue;UUID id=info.getProfile().id();if(id==null||id.equals(self))continue;String saved=favorites.get(id);if(saved!=null){String name=info.getProfile().name();online.put(id.toString().replace("-",""),name==null||name.isBlank()?saved:name);}}return online;
 }
 int onlineCount(){return favorites == null || !tracker.ready() ? -1 : shown.size();}
 Set<String> cachedNames(){return favoriteNames;}
 void announceInitial(Map<String,String> online){if(!ModSettings.favoritePresence())return;boolean first=true;for(var e:online.entrySet().stream().filter(e->!announced.contains(e.getKey())).sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER)).limit(MAX_INITIAL).toList()){announced.add(e.getKey());queue.addLast(new Pending(new FavoritePresenceTracker.Change(e.getKey(),e.getValue(),true),first));first=false;}}
 int queued(){return queue.size();}
 void rosterChanged(Object source,UUID id){if(source!=connection||favoriteIds==null||id==null||id.equals(selfId)||!favoriteIds.containsKey(id)||!(source instanceof net.minecraft.client.multiplayer.ClientPacketListener listener))return;
  if(!tracker.ready()){rosterDirty=true;return;}var info=listener.getPlayerInfo(id);boolean present=info!=null&&listener.getListedOnlinePlayers().contains(info);if(present!=tracker.isOnline(id.toString().replace("-","")))rosterDirty=true;
 }
 void observe(Map<String,String> all,Map<String,String> online){for(var event:tracker.accept(all,online)){if(event.online()){if(!announced.add(event.id()))continue;}else announced.remove(event.id());if(ModSettings.favoritePresence()&&queue.size()<MAX_PENDING)queue.addLast(new Pending(event,true));}}
 /** Session renewal for the same account must not discard or re-download its cached favorites. */
 void rotateToken(String renewed){if(Objects.equals(token,renewed))return;generation++;if(request!=null)request.cancel(true);request=null;token=renewed;if(favorites==null||refreshNeeded)nextLoad=0;}
 /** Called on the client thread after a successful favorite change, not on ordinary player activity. */
 static long currentAccountId(){var citizen=PaleTaleClient.ACCOUNT.citizen();return citizen==null?-1:citizen.playerId();}
 void invalidate(String actor,URI changedSite){invalidate(actor,changedSite,-1);}
 void invalidate(String actor,URI changedSite,long user){if(!owns(actor,changedSite,user))return;generation++;if(request!=null)request.cancel(true);request=null;refreshNeeded=true;tracker.reset();queue.clear();popups.clear();skins.clear();nextLoad=0;}
 private boolean owns(String actor,URI changedSite,long user){return Objects.equals(site,changedSite)&&(user>=0?user==accountId:Objects.equals(token,actor));}
 /** Successful in-mod changes update the cached UUID locally, without a second request. */
 void changed(String actor,URI changedSite,long user,String uuid,String name,boolean on){
  if(!owns(actor,changedSite,user))return;if(favorites==null||uuid==null||!uuid.matches("[0-9a-f]{32}")||on&&(name==null||!name.matches("[A-Za-z0-9_]{2,32}"))){invalidate(actor,changedSite,user);return;}
  var updated=new HashMap<>(favorites);if(on)updated.put(uuid,name);else updated.remove(uuid);if(updated.size()>1000){invalidate(actor,changedSite,user);return;}favorites=Map.copyOf(updated);favoriteNames=Set.copyOf(updated.values());favoriteIds=indexed(favorites);tracker.reset();queue.clear();popups.clear();skins.clear();baselineAfter=System.currentTimeMillis()+1000;rosterDirty=true;
  if(request!=null){generation++;request.cancel(true);request=null;refreshNeeded=true;nextLoad=0;}
 }
 CompletableFuture<Map<String,String>> load(URI site,String token,String view,int page,Map<String,String> rows,Set<String> seen,int expected){
  if(!view.equals("all")||page>MAX_PAGES)return CompletableFuture.failedFuture(new IllegalArgumentException("invalid favorites page"));
  return api.call(site,"/api/mcmod/world/favorites?view=all&page="+page+"&size="+PAGE_SIZE,token,null).thenCompose(json->{
   if(json.get("version").getAsInt()!=1)throw new IllegalArgumentException("invalid favorites");int total=json.get("total").getAsInt(),actual=json.get("page").getAsInt();if(total<0||total>1000||actual!=page||(expected>=0&&total!=expected))throw new IllegalArgumentException("unstable favorites");
   var entries=json.getAsJsonArray("players");if(entries.size()>PAGE_SIZE)throw new IllegalArgumentException("oversized page");for(var element:entries){JsonObject row=element.getAsJsonObject();String uuid=row.get("uuid").getAsString(),name=row.get("name").getAsString();if(!uuid.matches("[0-9a-f]{32}")||!name.matches("[A-Za-z0-9_]{2,32}"))throw new IllegalArgumentException("invalid favorite");if(!seen.add(uuid))throw new IllegalArgumentException("duplicate favorite");rows.put(uuid,name);}
   if(seen.size()==total)return CompletableFuture.completedFuture(Map.copyOf(rows));if(seen.size()>total||entries.isEmpty())throw new IllegalArgumentException("incomplete page");return load(site,token,view,page+1,rows,seen,total);
  });
 }
 private void reset(){shown.clear();missingSince.clear();generation++;if(request!=null)request.cancel(true);request=null;tracker.reset();queue.clear();popups.clear();skins.clear();token=null;connection=null;selfId=null;site=null;world=null;accountId=-1;favorites=null;favoriteNames=Set.of();favoriteIds=null;refreshNeeded=false;initialAnnouncement=false;nextPopup=0;nextLoad=0;rosterDirty=false;}
 static long animationClock(){return System.nanoTime()/1_000_000L;}
 static double slide(long now,long start,long end){long edge=MOTION_MS;double in=Math.max(0,Math.min(1,(now-start)/(double)edge)),out=Math.max(0,Math.min(1,(end-now)/(double)edge));double t=Math.min(in,out);return t*t*(3-2*t);}
 static float soundPitch(boolean online){return online?2.0f:1.0f;}
 void advance(long now){boolean changed=popups.removeIf(p->now>=p.start+DISPLAY_MS);if(!queue.isEmpty()&&popups.size()<MAX_VISIBLE&&now>=nextPopup){var pending=queue.removeFirst();var next=pending.change();popups.add(new Popup(next,now));if(pending.sound()&&ModSettings.favoriteSound())Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING,soundPitch(next.online())));nextPopup=now+GAP_MS;changed=true;}if(changed)for(int i=0;i<popups.size();i++)popups.get(i).move((popups.size()-1-i)*21,now);}
 String playerAt(double px,double py){var client=Minecraft.getInstance();if(client.screen==null||client.options.hideGui)return null;long now=animationClock();var font=client.font;int width=client.getWindow().getGuiScaledWidth(),height=client.getWindow().getGuiScaledHeight();for(int i=popups.size()-1;i>=0;i--){var p=popups.get(i);int available=Math.max(0,Math.min(150,width-55));String name=font.width(p.change.name())>available?font.plainSubstrByWidth(p.change.name(),Math.max(0,available-font.width("…")))+"…":p.change.name();int w=6+font.width("★")+4+12+4+font.width(name)+7;double x=width-w-8+(w+8)*(1-slide(now,p.start,p.start+DISPLAY_MS)),y=height-38-p.position(now);if(px>=x&&px<x+w&&py>=y&&py<y+18)return p.change.name();}return null;}
 void render(GuiGraphics g){render(g,false);}
 void renderOnPause(GuiGraphics g){render(g,true);}
 private void render(GuiGraphics g,boolean pause){var client=Minecraft.getInstance();if(popups.isEmpty()||(!pause&&client.screen!=null&&!(client.screen instanceof net.minecraft.client.gui.screens.ChatScreen))||client.options.hideGui)return;var font=client.font;long now=animationClock();for(var popup:popups){var change=popup.change;int color=change.online()?0xFF176B43:0xFFAF3434;int available=Math.max(0,Math.min(150,g.guiWidth()-55));String name=font.width(change.name())>available?font.plainSubstrByWidth(change.name(),Math.max(0,available-font.width("…")))+"…":change.name();int sw=font.width("★"),w=6+sw+4+12+4+font.width(name)+7;int x=g.guiWidth()-w-8,y=g.guiHeight()-38;float dx=(float)((w+8)*(1-slide(now,popup.start,popup.start+DISPLAY_MS))),dy=(float)-popup.position(now);g.pose().pushMatrix();g.pose().translate(dx,dy);
  g.fill(x,y,x+w,y+18,0x80F5F4F0);g.drawString(font,"★",x+5,y+5,0xFFFFC62F,false);UUID id=popup.id;var skin=id==null?null:skins.get(id);if(id!=null&&client.getConnection()!=null){var info=client.getConnection().getPlayerInfo(id);if(info!=null){skin=info.getSkin();skins.put(id,skin);}}if(skin!=null)net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g,skin,x+8+sw,y+3,12);g.drawString(font,name,x+24+sw,y+5,color,false);g.pose().popMatrix();
 }}
}
