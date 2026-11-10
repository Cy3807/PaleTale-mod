package cn.paletale.companion;
import com.google.gson.Gson;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
/** Bounded optional snapshots. All disk work runs on one worker; no credentials are serialized. */
final class DiskPages {
 private static final Gson JSON=new Gson();
 private static final ThreadPoolExecutor IO=new ThreadPoolExecutor(1,1,10,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),r->{var t=new Thread(r,"paletale-page-cache");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 static {IO.allowCoreThreadTimeOut(true);}
 private static Path root(){return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("paletale/cache/pages-v1");}
 static String owner(java.net.URI site){var c=PaleTaleClient.ACCOUNT.citizen();return PaleTaleClient.ACCOUNT.token()==null||c==null?null:"private:"+site+":"+c.playerId()+":"+c.uuid();}
 private static String hash(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private static Path file(String scope,String key){return root().resolve(hash(scope)).resolve(hash(key)+".json");}
 private static <T> CompletableFuture<T> task(Callable<T> action){var f=new CompletableFuture<T>();try{IO.execute(()->{try{f.complete(action.call());}catch(Exception ignored){f.complete(null);}});}catch(RejectedExecutionException ignored){f.complete(null);}return f;}
 static <T> CompletableFuture<T> read(String scope,String key,Class<T> type){if(scope==null)return CompletableFuture.completedFuture(null);return task(()->{var p=file(scope,key);if(!Files.isRegularFile(p)||Files.size(p)>2_097_152||System.currentTimeMillis()-Files.getLastModifiedTime(p).toMillis()>30L*86400000)return null;return JSON.fromJson(Files.readString(p),type);});}
 static void write(String scope,String key,Object value){if(scope==null)return;task(()->{String data=JSON.toJson(value);if(data.length()>1_000_000)return null;var p=file(scope,key);Files.createDirectories(p.getParent());var temp=Files.createTempFile(p.getParent(),"page-",".tmp");try{Files.writeString(temp,data);try{Files.move(temp,p,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,p,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}prune();return null;});}
 static void clear(String scope){if(scope==null)return;task(()->{var dir=root().resolve(hash(scope));if(Files.isDirectory(dir))try(var stream=Files.list(dir)){for(var p:stream.toList())Files.deleteIfExists(p);}return null;});}
 private static void prune()throws Exception{var files=new ArrayList<Path>();try(var stream=Files.walk(root(),2)){stream.filter(Files::isRegularFile).limit(1024).forEach(files::add);}files.sort(Comparator.comparingLong((Path p)->{try{return Files.getLastModifiedTime(p).toMillis();}catch(Exception e){return 0;}}).reversed());long bytes=0;for(int i=0;i<files.size();i++){var p=files.get(i);bytes+=Files.size(p);if(i>=128||bytes>32L*1024*1024)Files.deleteIfExists(p);}}
}
