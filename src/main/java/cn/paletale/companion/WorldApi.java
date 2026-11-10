package cn.paletale.companion;
import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
/** Same-site world-shop and presence calls; no Minecraft access token is ever sent. */
public final class WorldApi {
 private final HttpClient http;private WorldApi(HttpClient client){http=client;}
 public WorldApi(){this(HttpBodies.CLIENT);} public static WorldApi control(){return new WorldApi(HttpBodies.CONTROL);}
 public static final class Failure extends IllegalStateException {private final int status;Failure(int status){super("SMC request failed");this.status=status;}public int status(){return status;}}
 public static String problem(Throwable error){while(error.getCause()!=null&&(error instanceof java.util.concurrent.CompletionException||error instanceof java.util.concurrent.ExecutionException))error=error.getCause();if(error instanceof Failure failure)return switch(failure.status()){case 401->"登录已过期，请重新登录后刷新。";case 429->"操作较频繁，请稍后刷新。";default->"收藏暂时无法读取，请稍后刷新重试。";};return "网络暂时无法连接，请稍后刷新重试。";}
 /** Reads (no body) are retried twice on a network error, timeout, 429 or 5xx; 4xx answers and every write fail at once. */
 public CompletableFuture<JsonObject> call(URI site,String path,String token,JsonObject body){
  return body==null?withRetry(()->once(site,path,token,null),2,400):once(site,path,token,body);
 }
 private static CompletableFuture<JsonObject> withRetry(java.util.function.Supplier<CompletableFuture<JsonObject>> attempt,int retries,long delayMs){
  return attempt.get().handle((value,error)->{
   if(error==null)return CompletableFuture.completedFuture(value);
   Throwable cause=error;while(cause.getCause()!=null&&(cause instanceof java.util.concurrent.CompletionException||cause instanceof java.util.concurrent.ExecutionException))cause=cause.getCause();
   boolean again=retries>0&&!(cause instanceof IllegalArgumentException)&&(!(cause instanceof Failure failure)||failure.status()==429||failure.status()>=500);
   if(!again)return CompletableFuture.<JsonObject>failedFuture(cause);
   return CompletableFuture.supplyAsync(()->null,CompletableFuture.delayedExecutor(delayMs,java.util.concurrent.TimeUnit.MILLISECONDS)).thenCompose(ignored->withRetry(attempt,retries-1,delayMs*3));
  }).thenCompose(java.util.function.Function.identity());
 }
 private CompletableFuture<JsonObject> once(URI site,String path,String token,JsonObject body){
  if(!MarketApi.isAllowedSite(site)||!path.startsWith("/api/mcmod/"))return CompletableFuture.failedFuture(new IllegalArgumentException("Unsupported site"));
  var builder=HttpBodies.request(site.resolve(path)).timeout(Duration.ofSeconds(body==null?10:15)).header("Accept","application/json");
  if(token!=null)builder.header("Authorization","Bearer "+token);
  if(body==null)builder.GET();else builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()));
  return http.sendAsync(builder.build(),HttpBodies.limited(1048576)).thenApply(response->{
   if(response.statusCode()!=200)throw new Failure(response.statusCode());
   if(response.body().length>1048576||!response.headers().firstValue("Content-Type").orElse("").contains("application/json"))throw new IllegalStateException("SMC世界暂时无法同步，请稍后重试。");
   return JsonParser.parseString(new String(response.body(),StandardCharsets.UTF_8)).getAsJsonObject();
  });
 }
}
