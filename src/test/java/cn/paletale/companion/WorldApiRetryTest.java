package cn.paletale.companion;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
/** Reads are retried on 5xx, 4xx fails at once, and writes are never retried. */
final class WorldApiRetryTest{
 static void run()throws Exception{
  var hits=new AtomicInteger();var failuresLeft=new AtomicInteger(2);
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/api/mcmod/flaky",exchange->{hits.incrementAndGet();byte[] body="{\"ok\":true}".getBytes();exchange.getResponseHeaders().add("Content-Type","application/json");if(failuresLeft.getAndDecrement()>0){exchange.sendResponseHeaders(503,-1);}else{exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);}exchange.close();});
  server.createContext("/api/mcmod/missing",exchange->{hits.incrementAndGet();exchange.sendResponseHeaders(404,-1);exchange.close();});
  server.start();
  try{
   var site=URI.create("http://127.0.0.1:"+server.getAddress().getPort());var api=new WorldApi();
   var ok=api.call(site,"/api/mcmod/flaky",null,null).get(20,java.util.concurrent.TimeUnit.SECONDS);
   if(!ok.get("ok").getAsBoolean()||hits.get()!=3)throw new AssertionError("Two 503 answers are retried and the third succeeds, hits="+hits.get());
   hits.set(0);boolean failed=false;try{api.call(site,"/api/mcmod/missing",null,null).get(20,java.util.concurrent.TimeUnit.SECONDS);}catch(java.util.concurrent.ExecutionException expected){failed=true;}
   if(!failed||hits.get()!=1)throw new AssertionError("A 404 is not retried, hits="+hits.get());
   hits.set(0);failuresLeft.set(5);boolean writeFailed=false;try{api.call(site,"/api/mcmod/flaky",null,new com.google.gson.JsonObject()).get(20,java.util.concurrent.TimeUnit.SECONDS);}catch(java.util.concurrent.ExecutionException expected){writeFailed=true;}
   if(!writeFailed||hits.get()!=1)throw new AssertionError("A POST is never retried, hits="+hits.get());
  }finally{server.stop(0);}
  System.out.println("World API retry checks passed");
 }
}
