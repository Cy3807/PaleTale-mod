package cn.paletale.companion;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
/** Per-account heartbeat and server-coordinated population sampling with jitter and bounded retries. */
public final class PresenceWatch {
 private static final WorldApi API=WorldApi.control();
 private static long tabUpdatedAt;
 private long next,generation;
 private boolean busy;
 private String token;
 private java.net.URI activeSite=SiteConfig.current();
 private Object connection;
 private String connectionId=java.util.UUID.randomUUID().toString();
 public static void tabUpdated(){tabUpdatedAt=System.currentTimeMillis();}
 void tick(Minecraft client){
  String current=PaleTaleClient.ACCOUNT.canLogin(client)?PaleTaleClient.ACCOUNT.token():null;
  if(!java.util.Objects.equals(current,token)||client.getConnection()!=connection||!SiteConfig.current().equals(activeSite)){
   if(token!=null){var offline=new JsonObject();offline.addProperty("version",net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("paletale").orElseThrow().getMetadata().getVersion().getFriendlyString());offline.addProperty("world","unknown");offline.addProperty("online",false);offline.addProperty("connection",connectionId);API.call(activeSite,"/api/mcmod/presence",token,offline).exceptionally(error->null);}
   token=current;activeSite=SiteConfig.current();connection=client.getConnection();connectionId=java.util.UUID.randomUUID().toString();generation++;busy=false;next=System.currentTimeMillis()+java.util.concurrent.ThreadLocalRandom.current().nextLong(1000,6000);
  }
  if(token==null||busy||System.currentTimeMillis()<next)return;
  busy=true;long run=generation;String session=token;var site=activeSite;
  var body=new JsonObject();body.addProperty("version",net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("paletale").orElseThrow().getMetadata().getVersion().getFriendlyString());body.addProperty("world",ShopScanner.world(client));body.addProperty("online",true);body.addProperty("connection",connectionId);
  API.call(site,"/api/mcmod/presence",session,body).whenComplete((data,error)->client.execute(()->{
   if(error!=null)ModLog.failed("在线状态同步",error);
   if(run!=generation||!site.equals(SiteConfig.current()))return;busy=false;next=System.currentTimeMillis()+(error==null?30000:20000)+java.util.concurrent.ThreadLocalRandom.current().nextLong(6000);
   if(error!=null||!data.has("lease")||!java.util.Objects.equals(session,PaleTaleClient.ACCOUNT.token())||!PaleTaleClient.ACCOUNT.canLogin(client)||System.currentTimeMillis()-tabUpdatedAt>30000)return;
   var tab=client.gui.getTabList();String text=(tab.header==null?"":tab.header.getString())+"\n"+(tab.footer==null?"":tab.footer.getString());
   var sample=PrimaryPopulation.parse(text);if(sample.isEmpty())return;
   var payload=new JsonObject();payload.addProperty("lease",data.get("lease").getAsString());payload.addProperty("count",sample.get().count());payload.addProperty("evidence",sample.get().evidence());payload.addProperty("sampledAt",java.time.Instant.ofEpochMilli(HttpBodies.now()).toString());
   // Only the labelled population count is reported. Other players' names and UUIDs are never read from the tab list or sent to the site.
   API.call(site,"/api/mcmod/presence",session,payload).exceptionally(failure->null);
  }));
 }
}
