package cn.paletale.companion;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.*;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.*;

/** Public display metadata only. Cached models remain usable offline; no auth or binding writes. */
final class ServerItemCatalogSync {
 private static final int MAX_BYTES=4*1024*1024;
 private static final long FRESH_MS=3600_000L,RETRY_MS=60_000L;
 private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"paletale-item-catalog");t.setDaemon(true);return t;});
 private static final HttpClient HTTP=HttpBodies.CLIENT;
 private static URI current;private static long generation,nextRefresh;private static CompletableFuture<Void> inFlight;
 static synchronized CompletableFuture<Void> refresh(URI site,Path cacheRoot){
  if(!MarketApi.isAllowedSite(site))return CompletableFuture.completedFuture(null);
  if(!site.equals(current)){current=site;generation++;nextRefresh=0;inFlight=null;ServerItemCatalog.clearPublished();}
  if(inFlight!=null)return inFlight;
  if(System.currentTimeMillis()<nextRefresh)return CompletableFuture.completedFuture(null);
  long run=generation;nextRefresh=System.currentTimeMillis()+FRESH_MS;
  var future=CompletableFuture.runAsync(()->{try{loadNames(site,cacheRoot,run);}catch(RuntimeException namesUnavailable){/* sameName stays empty until a later refresh */}load(site,cacheRoot,run);},WORKER);inFlight=future;
  future.whenComplete((ignored,error)->{synchronized(ServerItemCatalogSync.class){if(run==generation){inFlight=null;if(error!=null)nextRefresh=System.currentTimeMillis()+RETRY_MS;}}});return future.exceptionally(error->null);
 }
 private static void load(URI site,Path root,long run){
  Path file;
  try{String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(site.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));file=root.resolve(hash+"-v25.json");}
  catch(Exception error){throw new CompletionException(error);}
  try{
   if(Files.isRegularFile(file)&&Files.size(file)<=MAX_BYTES){var cached=ServerItemCatalog.decode(Files.readAllBytes(file));apply(run,cached);if(System.currentTimeMillis()-Files.getLastModifiedTime(file).toMillis()<FRESH_MS)return;}
  }catch(Exception invalid){/* A corrupt cache never replaces the bundled catalog. */}
  try{
   var response=HTTP.send(HttpBodies.request(site.resolve("/smc-items/v25/catalog-v25.json")).timeout(Duration.ofSeconds(10)).GET().build(),HttpBodies.limited(MAX_BYTES));
   if(response.statusCode()!=200||!response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"))throw new IllegalArgumentException("Catalog unavailable");
   byte[] bytes=response.body();var snapshot=ServerItemCatalog.decode(bytes);if(!apply(run,snapshot))return;
   try{Files.createDirectories(root);Path temp=Files.createTempFile(root,"catalog-",".tmp");try{Files.write(temp,bytes);try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}}
   catch(Exception diskUnavailable){/* Network result remains valid in memory. */}
  }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new CompletionException(interrupted);}catch(Exception offline){throw new CompletionException(offline);}
 }
 private static final int NAMES_MAX=2*1024*1024;
 private static String hashOf(URI site)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(site.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
 /** The known item names (used to match decorated shop-sign names). Downloaded once and cached like the catalog; the jar ships no copy. */
 private static void loadNames(URI site,Path root,long run){
  Path file;
  try{file=root.resolve(hashOf(site)+"-names-v1.json");}catch(Exception error){throw new CompletionException(error);}
  try{
   if(Files.isRegularFile(file)&&Files.size(file)<=NAMES_MAX){var cached=ServerItemCatalog.decodeNames(Files.readAllBytes(file));applyNames(run,cached);if(System.currentTimeMillis()-Files.getLastModifiedTime(file).toMillis()<FRESH_MS)return;}
  }catch(Exception invalid){/* A corrupt cache is ignored and downloaded again. */}
  try{
   var response=HTTP.send(HttpBodies.request(site.resolve("/smc-items/v25/known-names-v1.json")).timeout(Duration.ofSeconds(10)).GET().build(),HttpBodies.limited(NAMES_MAX));
   if(response.statusCode()!=200||!response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"))throw new IllegalArgumentException("Names unavailable");
   byte[] bytes=response.body();var known=ServerItemCatalog.decodeNames(bytes);if(!applyNames(run,known))return;
   try{Files.createDirectories(root);Path temp=Files.createTempFile(root,"names-",".tmp");try{Files.write(temp,bytes);try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}}
   catch(Exception diskUnavailable){/* The download stays valid in memory. */}
  }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new CompletionException(interrupted);}catch(Exception offline){throw new CompletionException(offline);}
 }
 private static synchronized boolean applyNames(long run,java.util.List<String> known){if(run!=generation)return false;ServerItemCatalog.applyNames(known);return true;}
 private static synchronized boolean apply(long run,ServerItemCatalog.Snapshot snapshot){if(run!=generation)return false;ServerItemCatalog.apply(snapshot);return true;}
 static synchronized void resetForTest(){generation++;current=null;nextRefresh=0;inFlight=null;ServerItemCatalog.clearPublished();}
}
