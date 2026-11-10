package cn.paletale.companion;
import com.google.gson.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
/** A screen-sized window over fixed 24-row API batches. At most three bounded reads, no short intermediate pages. */
final class WorldPages {
 static final WorldCatalogCache CACHE=new WorldCatalogCache();
 static String key(String prefix,String field,int page,int size){return "view:"+prefix+"|"+field+"|"+page+"|"+size;}
 static JsonObject peek(URI site,String prefix,String field,int page,int size){return CACHE.peek(site,key(prefix,field,page,size));}
 static CompletableFuture<JsonObject> batch(WorldApi api,URI site,String path){return CACHE.load(site,"batch:"+path,()->api.call(site,path,null,null));}
 static CompletableFuture<JsonObject> load(WorldApi api,URI site,String prefix,String field,int page,int size){return CACHE.load(site,key(prefix,field,page,size),()->read(api,site,prefix,field,page,size));}

 static long refreshIn(URI site,String prefix,int page,int size,int total){int start=(page-1)*size,first=start/24+1,last=Math.max(first,(Math.max(start,Math.min(total,start+size)-1))/24+1);long left=WorldCatalogCache.REFRESH_MS;for(int batch=first;batch<=last;batch++)left=Math.min(left,CACHE.refreshIn(site,"batch:"+prefix+batch));return left;}
 static CompletableFuture<JsonObject> read(WorldApi api,URI site,String prefix,String field,int page,int size){
  int offset=(page-1)*size,first=offset/24+1;
  return batch(api,site,prefix+first).thenCompose(head->{
   String count=field.equals("items")?"itemTotal":"total";int total=head.get(count).getAsInt(),end=Math.min(total,offset+size),last=Math.max(first,(Math.max(offset,end-1))/24+1);
   var requests=new ArrayList<CompletableFuture<JsonObject>>();requests.add(CompletableFuture.completedFuture(head));for(int batch=first+1;batch<=last;batch++)requests.add(batch(api,site,prefix+batch));
   return CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).thenApply(done->{var merged=new JsonArray();for(var request:requests){var result=request.join();if(result.get(count).getAsInt()!=total)throw new IllegalStateException("目录已变化");for(var row:result.getAsJsonArray(field))merged.add(row);}int skip=offset-(first-1)*24,n=Math.max(0,end-offset);if(n>0&&skip+n>merged.size())throw new IllegalStateException("目录不完整");var rows=new JsonArray();for(int i=skip;i<skip+n;i++)rows.add(merged.get(i));var result=head.deepCopy();result.add(field,rows);return result;});
  });
 }
}
