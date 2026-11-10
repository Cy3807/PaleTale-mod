package cn.paletale.companion;
import com.google.gson.JsonObject;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
/** Public catalog snapshots only. Site-scoped, bounded, single-flight, hourly refresh; no session or private owner responses. A failed refresh falls back to the last snapshot (kept for twelve hours) instead of an error. */
public final class WorldCatalogCache {
 public static final long REFRESH_MS=3600_000, KEEP_MS=12*REFRESH_MS, CAPACITY=256;
 private static final class Entry{JsonObject data;long at;CompletableFuture<JsonObject> pending;}
 private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>(16,.75f,true);private final LongSupplier clock;
 public WorldCatalogCache(){this(System::currentTimeMillis);}WorldCatalogCache(LongSupplier clock){this.clock=clock;}
 private String key(URI site,String key){return site.toString()+"|"+key;}
 public synchronized JsonObject peek(URI site,String key){var entry=entries.get(key(site,key));return entry!=null&&entry.data!=null&&clock.getAsLong()-entry.at<KEEP_MS?entry.data.deepCopy():null;}
 public synchronized long refreshIn(URI site,String key){var entry=entries.get(key(site,key));return entry==null||entry.data==null?0:Math.max(0,REFRESH_MS-(clock.getAsLong()-entry.at));}
 public synchronized void invalidate(URI site){entries.keySet().removeIf(key->key.startsWith(site.toString()+"|"));}
 public CompletableFuture<JsonObject> load(URI site,String key,Supplier<CompletableFuture<JsonObject>> loader){
  Entry held;CompletableFuture<JsonObject> task;
  synchronized(this){String id=key(site,key);held=entries.get(id);if(held!=null&&held.data!=null&&clock.getAsLong()-held.at<REFRESH_MS)return CompletableFuture.completedFuture(held.data.deepCopy());if(held!=null&&held.pending!=null)return held.pending.thenApply(JsonObject::deepCopy);
   if(held==null){if(entries.size()>=CAPACITY){var removable=entries.entrySet().stream().filter(e->e.getValue().pending==null).map(Map.Entry::getKey).findFirst();if(removable.isEmpty())return CompletableFuture.failedFuture(new IllegalStateException("目录繁忙"));entries.remove(removable.get());}held=new Entry();entries.put(id,held);}task=new CompletableFuture<>();held.pending=task;
  }
  Entry target=held;try{loader.get().whenComplete((data,error)->{synchronized(this){target.pending=null;if(error==null){target.data=data.deepCopy();target.at=clock.getAsLong();}}if(error==null)task.complete(data.deepCopy());else{JsonObject stale;synchronized(this){stale=target.data!=null&&clock.getAsLong()-target.at<KEEP_MS?target.data.deepCopy():null;}if(stale!=null)task.complete(stale);else task.completeExceptionally(error);}});}catch(RuntimeException error){synchronized(this){target.pending=null;}task.completeExceptionally(error);}return task.thenApply(JsonObject::deepCopy);
 }
}
