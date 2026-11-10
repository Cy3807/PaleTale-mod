package cn.paletale.companion;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import java.util.*;
import java.util.concurrent.*;
/** One loaded chunk per tick and 12 sign copies per tick; parsing/JSON/HTTP run off the client thread. */
final class WorldShopWatch {
 private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(task->{var t=new Thread(task,"paletale-world-shops");t.setDaemon(true);return t;});
 private final WorldApi api=new WorldApi();
 private final Map<String,ShopSigns.Reading> sent=new LinkedHashMap<>();
 private final Map<String,Long> sentAt=new HashMap<>();
 private String token;private Object level;
 private long next; private volatile long generation;private boolean working;
 private int chunk,cx,cz,cursor;private List<BlockPos> positions;private int chunkX,chunkZ;
 private List<ShopScanner.Sign> samples;
 private final ShopTouchPolicy touches=new ShopTouchPolicy();
 private final SignChangeBatch changedSigns=new SignChangeBatch();
 private final ShopGonePolicy gone=new ShopGonePolicy();private long nearAt;private int nearX,nearZ;
 /** A chest shop was clicked or paid for: read that sign again at once (and once more a moment later) instead of waiting for the next scan, so the site learns about a sold-out shop in seconds. */
 void chat(Minecraft client,String raw){
  var kind=ShopTouchPolicy.classify(raw);
  if(kind==ShopTouchPolicy.Kind.NONE||token==null||client.level==null||client.player==null||!ShopScanner.world(client).equals(CityNavigation.MAIN_WORLD))return;
  var spot=touches.choose(kind,signInSight(client),System.currentTimeMillis());
  if(spot==null)return;
  for(long delay:ShopTouchPolicy.delays(kind)){
   if(delay<=0)read(client,spot);
   else CompletableFuture.delayedExecutor(delay,TimeUnit.MILLISECONDS).execute(()->client.execute(()->read(client,spot)));
  }
 }
 private static ShopTouchPolicy.Spot signInSight(Minecraft client){
  if(!(client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)||hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK)return null;
  var pos=hit.getBlockPos();
  return client.level.getBlockEntity(pos) instanceof SignBlockEntity?new ShopTouchPolicy.Spot(pos.getX(),pos.getY(),pos.getZ()):null;
 }
 private void read(Minecraft client,ShopTouchPolicy.Spot spot){
  if(!touches.mayRead(spot,System.currentTimeMillis()))return;
  readAndReport(client,List.of(spot));
 }
 /** The signs the server has just changed arrive as update packets (see SignUpdateMixin); they are read together a few seconds later. */
 void signChanged(Minecraft client,BlockPos pos){
  if(token==null||client.level==null||level!=client.level||!ShopScanner.world(client).equals(CityNavigation.MAIN_WORLD))return;
  if(!(client.level.getBlockEntity(pos) instanceof SignBlockEntity))return;
  changedSigns.add(new ShopTouchPolicy.Spot(pos.getX(),pos.getY(),pos.getZ()),System.currentTimeMillis());
 }
 private void flushChangedSigns(Minecraft client){
  var batch=changedSigns.take(System.currentTimeMillis());
  if(!batch.isEmpty())readAndReport(client,batch);
 }
 /** Reads these signs as they are now and reports those that differ from what was last reported (one request for all of them). */
 private void readAndReport(Minecraft client,List<ShopTouchPolicy.Spot> spots){
  if(token==null||client.level==null||level!=client.level||!ShopScanner.world(client).equals(CityNavigation.MAIN_WORLD))return;
  long now=System.currentTimeMillis();
  List<ShopSigns.Reading> changed=new ArrayList<>();
  for(var spot:spots){
   var pos=new BlockPos(spot.x(),spot.y(),spot.z());
   if(!(client.level.getBlockEntity(pos) instanceof SignBlockEntity sign))continue;
   List<String> lines=new ArrayList<>(4);for(var line:sign.getFrontText().getMessages(false))lines.add(line.getString());
   var parsed=ShopSigns.parse(ShopScanner.world(client),pos.getX(),pos.getY(),pos.getZ(),lines);
   if(parsed.isEmpty())continue;
   var row=parsed.get();if(!row.owner().matches("[A-Za-z0-9_]{2,32}"))continue;
   var old=sent.get(row.key());
   if(old!=null&&old.sameTrade(row)&&now-sentAt.getOrDefault(row.key(),0L)<=300000)continue;
   changed.add(row);if(changed.size()>=60)break;
  }
  if(changed.isEmpty())return;
  String session=token;long run=generation;
  var body=new JsonObject();body.addProperty("observedAt",java.time.Instant.ofEpochMilli(HttpBodies.now()).toString());
  var shops=ShopSigns.jsonList(changed);for(var shop:shops)shop.getAsJsonObject().remove("world");body.add("shops",shops);
  api.call(SiteConfig.current(),"/api/mcmod/world/shops",session,body).whenComplete((result,error)->client.execute(()->{
   if(error!=null)ModLog.failed("世界商店上报",error);
   if(error!=null||run!=generation)return;
   for(var row:changed){sent.put(row.key(),row);sentAt.put(row.key(),now);}
   expectShops(changed);
  }));
 }
 void tick(Minecraft client){
  String session=PaleTaleClient.ACCOUNT.canLogin(client)?PaleTaleClient.ACCOUNT.token():null;
  if(!Objects.equals(session,token)||level!=client.level){generation++;token=session;level=client.level;working=false;samples=null;positions=null;sent.clear();sentAt.clear();changedSigns.clear();gone.clear();nearAt=0;next=System.currentTimeMillis()+5000;}
  flushChangedSigns(client);
  if(token==null||working||client.level==null||!ShopScanner.world(client).equals(CityNavigation.MAIN_WORLD))return;
  if(samples==null){if(System.currentTimeMillis()<next)return;samples=new ArrayList<>();chunk=0;cx=client.player.blockPosition().getX()>>4;cz=client.player.blockPosition().getZ()>>4;loadNear(client);}
  if(positions==null){
   if(chunk>=49||samples.size()>=400){finish(client);return;}
   chunkX=cx+chunk%7-3;chunkZ=cz+chunk/7-3;chunk++;
   var loaded=client.level.getChunkSource().getChunkNow(chunkX,chunkZ);
   if(loaded==null)return;
   positions=new ArrayList<>(loaded.getBlockEntities().keySet());cursor=0;
  }
  var loaded=client.level.getChunkSource().getChunkNow(chunkX,chunkZ);if(loaded==null){positions=null;return;}
  long began=System.nanoTime();int count=0;
  while(cursor<positions.size()&&count++<12&&(count==1||System.nanoTime()-began<500000)&&samples.size()<400){
   var pos=positions.get(cursor++);if(client.player.position().distanceToSqr(pos.getX(),pos.getY(),pos.getZ())>48*48)continue;
   if(!(loaded.getBlockEntities().get(pos) instanceof SignBlockEntity sign))continue;
   List<String> lines=new ArrayList<>(4);for(var line:sign.getFrontText().getMessages(false))lines.add(line.getString());
   samples.add(new ShopScanner.Sign(ShopScanner.world(client),pos.getX(),pos.getY(),pos.getZ(),lines,0,ShopScanner.chestDirOf(sign)));
  }
  if(cursor>=positions.size()||samples.size()>=400)positions=null;
 }

 private void expectShops(List<ShopSigns.Reading> rows){gone.expect(rows.stream().map(row->new ShopGonePolicy.Spot(row.x(),row.y(),row.z())).toList());}
 /** Asks the site which shops it still lists around the player (at most once a minute, and only after moving), so shops pulled down while nobody watched can be noticed. */
 private void loadNear(Minecraft client){
  long now=System.currentTimeMillis();int x=client.player.blockPosition().getX(),z=client.player.blockPosition().getZ();
  if(now-nearAt<60000||nearAt!=0&&Math.abs(x-nearX)<24&&Math.abs(z-nearZ)<24&&now-nearAt<600000)return;
  nearAt=now;nearX=x;nearZ=z;long run=generation;
  api.call(SiteConfig.current(),"/api/mcmod/world/shops?x="+x+"&z="+z,token,null).whenComplete((result,error)->client.execute(()->{
   if(error!=null||run!=generation||result==null||!result.has("shops")||!result.get("shops").isJsonArray())return;
   List<ShopGonePolicy.Spot> spots=new ArrayList<>();
   for(var el:result.getAsJsonArray("shops")){if(!el.isJsonObject())continue;var o=el.getAsJsonObject();try{spots.add(new ShopGonePolicy.Spot(o.get("x").getAsInt(),o.get("y").getAsInt(),o.get("z").getAsInt()));}catch(RuntimeException ignored){}if(spots.size()>=600)break;}
   gone.expect(spots);
  }));
 }
 /** Judged on the live world: only blocks in loaded chunks close to the player count, and a sign that is still there but no longer a shop counts as gone. */
 private List<ShopGonePolicy.Spot> goneNow(Minecraft client,long now){
  if(client.level==null||client.player==null||!ShopScanner.world(client).equals(CityNavigation.MAIN_WORLD))return List.of();
  var level=client.level;double px=client.player.getX(),pz=client.player.getZ();String world=ShopScanner.world(client);
  return gone.observe(spot->Math.abs(spot.x()+0.5-px)<=40&&Math.abs(spot.z()+0.5-pz)<=40&&level.getChunkSource().getChunkNow(spot.x()>>4,spot.z()>>4)!=null,
   spot->{
    if(!(level.getBlockEntity(new BlockPos(spot.x(),spot.y(),spot.z())) instanceof SignBlockEntity sign))return false;
    List<String> lines=new ArrayList<>(4);for(var line:sign.getFrontText().getMessages(false))lines.add(line.getString());
    var parsed=ShopSigns.parse(world,spot.x(),spot.y(),spot.z(),lines);
    return parsed.isPresent()&&parsed.get().owner().matches("[A-Za-z0-9_]{2,32}");
   },now);
 }
 private void finish(Minecraft client){
  var snapshot=List.copyOf(samples);samples=null;positions=null;working=true;
  var goneSpots=goneNow(client,System.currentTimeMillis());
  long run=generation,now=System.currentTimeMillis();String session=token;var before=Map.copyOf(sent);var times=Map.copyOf(sentAt);var site=SiteConfig.current();
  CompletableFuture.supplyAsync(()->{
   List<ShopSigns.Reading> changed=new ArrayList<>();
   for(var sign:snapshot){var parsed=ShopSigns.parse(sign.world(),sign.x(),sign.y(),sign.z(),sign.raw());if(parsed.isEmpty())continue;var row=parsed.get();if(!row.owner().matches("[A-Za-z0-9_]{2,32}"))continue;var old=before.get(row.key());if(old==null||!old.sameTrade(row)||now-times.getOrDefault(row.key(),0L)>300000)changed.add(row);if(changed.size()>=60)break;}
   return changed;
  },WORKER).thenCompose(changed->{
   if(run!=generation)return CompletableFuture.<List<ShopSigns.Reading>>failedFuture(new CancellationException());
   if(changed.isEmpty()&&goneSpots.isEmpty())return CompletableFuture.completedFuture(changed);
   var body=new JsonObject();body.addProperty("observedAt",java.time.Instant.ofEpochMilli(HttpBodies.now()).toString());var shops=ShopSigns.jsonList(changed);for(var shop:shops)shop.getAsJsonObject().remove("world");body.add("shops",shops);
   if(!goneSpots.isEmpty()){var list=new com.google.gson.JsonArray();for(var spot:goneSpots){var o=new JsonObject();o.addProperty("x",spot.x());o.addProperty("y",spot.y());o.addProperty("z",spot.z());list.add(o);}body.add("gone",list);}
   return api.call(site,"/api/mcmod/world/shops",session,body).thenApply(result->changed);
  }).whenComplete((changed,error)->client.execute(()->{
   if(run!=generation)return;working=false;next=System.currentTimeMillis()+(error==null?30000:120000);
   if(error==null){for(var row:changed){sent.put(row.key(),row);sentAt.put(row.key(),now);}expectShops(changed);}else gone.failed(goneSpots);
   while(sent.size()>4000){String first=sent.keySet().iterator().next();sent.remove(first);sentAt.remove(first);}
  }));
 }
}
