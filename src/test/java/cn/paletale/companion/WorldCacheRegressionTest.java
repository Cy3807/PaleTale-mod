package cn.paletale.companion;
import com.google.gson.JsonObject;
import java.net.URI;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
final class WorldCacheRegressionTest{
 static void require(boolean ok,String label){if(!ok)throw new AssertionError(label);}
 static void run(){
  var stamp=new AtomicLong(1000);var calls=new AtomicInteger();var cache=new WorldCatalogCache(stamp::get);var site=URI.create("https://www.paletale.cn");var other=URI.create("http://localhost:3000");var pending=new CompletableFuture<JsonObject>();
  var first=cache.load(site,"items",()->{calls.incrementAndGet();return pending;});var second=cache.load(site,"items",()->{calls.incrementAndGet();return CompletableFuture.failedFuture(new Error());});require(calls.get()==1,"Concurrent readers share one request");var value=new JsonObject();value.addProperty("count",7);pending.complete(value);first.join().addProperty("count",999);require(second.join().get("count").getAsInt()==7&&cache.peek(site,"items").get("count").getAsInt()==7,"Readers cannot mutate cached data");
  cache.load(site,"items",()->{throw new AssertionError("fresh cache must not fetch");}).join();require(cache.peek(other,"items")==null,"Backends cannot share snapshots");stamp.addAndGet(1800000);require(cache.refreshIn(site,"items")==1800000,"Re-entry keeps original hourly deadline");cache.load(site,"items",()->{throw new AssertionError("Half-hour return must stay warm");}).join();stamp.addAndGet(1800001);var refresh=new CompletableFuture<JsonObject>();cache.load(site,"items",()->refresh);require(cache.peek(site,"items")!=null,"Stale snapshot remains visible during refresh");var served=cache.load(site,"items",()->{throw new AssertionError("joins the running refresh");});refresh.completeExceptionally(new IllegalStateException("offline"));require(served.join().get("count").getAsInt()==7,"A failed refresh serves the last snapshot instead of an error");require(cache.peek(site,"items").get("count").getAsInt()==7,"Failed refresh keeps the last snapshot");cache.invalidate(site);require(cache.peek(site,"items")==null,"Owner visibility mutations invalidate public views");
  cache.load(site,"items",()->CompletableFuture.completedFuture(value)).join();stamp.addAndGet(12*3600_000L+1);require(cache.peek(site,"items")==null,"Old snapshots expire");
  System.out.println("World catalog cache regression checks passed");
 }
}
