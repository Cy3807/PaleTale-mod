package cn.paletale.companion;
import com.google.gson.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
/** Fill a screen of players from complete fixed 24-row API batches, including their boundary. */
final class WorldRosterPages {
 static String key(String prefix,int page,int size,boolean scope){return "roster:"+prefix+"|"+page+"|"+size+"|"+scope;}
 static JsonArray rows(JsonObject root,boolean scope){return scope?root.getAsJsonObject("scope").getAsJsonArray("players"):root.getAsJsonArray("players");}
 static int total(JsonObject root,boolean scope){return scope?root.getAsJsonObject("scope").get("playerCount").getAsInt():root.get("total").getAsInt();}
 static CompletableFuture<JsonObject> load(WorldApi api,URI site,String prefix,int page,int size,boolean scope){return WorldPages.CACHE.load(site,key(prefix,page,size,scope),()->{
  int offset=(page-1)*size,first=offset/24+1;
  return WorldPages.batch(api,site,prefix+first).thenCompose(head->{int count=total(head,scope),end=Math.min(count,offset+size),last=Math.max(first,Math.max(offset,end-1)/24+1);var requests=new ArrayList<CompletableFuture<JsonObject>>();requests.add(CompletableFuture.completedFuture(head));for(int batch=first+1;batch<=last;batch++)requests.add(WorldPages.batch(api,site,prefix+batch));return CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).thenApply(done->{var merged=new JsonArray();for(var task:requests){var response=task.join();if(total(response,scope)!=count)throw new IllegalStateException("名单已变化");for(var row:rows(response,scope))merged.add(row);}int skip=offset-(first-1)*24,n=Math.max(0,end-offset);if(skip+n>merged.size()&&n>0)throw new IllegalStateException("名单不完整");var visible=new JsonArray();for(int i=skip;i<skip+n;i++)visible.add(merged.get(i));var result=head.deepCopy();if(scope)result.getAsJsonObject("scope").add("players",visible);else result.add("players",visible);return result;});});
 });}
}
