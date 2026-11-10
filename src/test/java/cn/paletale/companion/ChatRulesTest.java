package cn.paletale.companion;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Chat wording as downloadable data: the website file equals the built-in rules, bad files are rejected, traffic is one 304. */
final class ChatRulesTest {
 static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 private static final List<String> LINES=List.of("国家 测试国 | ￼Example » nation test","[地狱远征] [G] [红] Example: hi","[交易] [称号] Example » sell","[末地] Example : x","[地牢裂隙] [G] Example : y","Example 悄悄的对 我 说: 你好","我 悄悄地对 Cy3807 说: 嗨","隐秘的声音 » 公告内容","全服 | ￼Example » reserved","[G] Stranger : hello");
 private static String fingerprint(){var out=new StringBuilder();for(String line:LINES){out.append(PrivateChat.parse(line,"Tester",1000,"a")).append('|').append(PrivateChat.favoriteGlobal(line,Set.of("Example"),1000,"a")).append('|').append(PrivateChat.official(line,1000,"a")).append('|').append(ChatMentions.find(line.replace("Example","Zed")+" Tester","Tester")).append('\n');}return out.toString();}
 private static JsonObject published()throws Exception{return JsonParser.parseString(Files.readString(Path.of("../public/mcmod/chat-rules-v1.json"))).getAsJsonObject();}
 static void run(){
  if(!Files.exists(Path.of("../public/mcmod/chat-rules-v1.json"))){System.out.println("Skipped: needs the website-published chat-rules-v1.json (only present in the original monorepo)");return;}
  HttpServer server=null;
  try{
   ChatRules.resetForTest();String builtIn=fingerprint();
   byte[] file=Files.readAllBytes(Path.of("../public/mcmod/chat-rules-v1.json"));require(file.length<ChatRules.MAX_BYTES/2,"Rules file stays tiny");
   ChatRules.use(ChatRules.decode(file));require(fingerprint().equals(builtIn),"Published rules behave exactly like the built-in rules");ChatRules.resetForTest();
   var changed=published();changed.addProperty("nation","王国");changed.addProperty("version",2);
   ChatRules.use(ChatRules.decode(changed.toString().getBytes(StandardCharsets.UTF_8)));
   require(PrivateChat.parse("王国 测试国 | ￼Example » hi","Tester",1000,"a").orElseThrow().channelKind().equals("nation")&&PrivateChat.parse("国家 测试国 | ￼Example » hi","Tester",1000,"a").isEmpty(),"A downloaded wording change takes effect without a new mod");ChatRules.resetForTest();
   for(String bad:List.of("{}","[]","not json","{\"kind\":\"chat-rules\",\"version\":0}")){try{ChatRules.decode(bad.getBytes(StandardCharsets.UTF_8));throw new AssertionError("Rejected: "+bad);}catch(IllegalArgumentException expected){}}
   var regex=published();regex.addProperty("nation","国.*)(");ChatRules.use(ChatRules.decode(regex.toString().getBytes(StandardCharsets.UTF_8)));require(PrivateChat.parse("国.*)( 测试国 | ￼Example » hi","Tester",1000,"a").isPresent()&&PrivateChat.parse("国家 测试国 | ￼Example » hi","Tester",1000,"a").isEmpty(),"Words are literals, never regular expressions");ChatRules.resetForTest();
   var longWord=published();longWord.addProperty("nation","x".repeat(17));var control=published();control.addProperty("say","说\n");var pipe=published();pipe.getAsJsonArray("officialSenders").add("a|b");var undeclared=published();undeclared.addProperty("tradeMarker","拍卖");var many=published();var list=new JsonArray();for(int i=0;i<17;i++)list.add("w"+i);many.add("reservedScopes",list);
   for(var invalid:List.of(longWord,control,pipe,undeclared,many)){try{ChatRules.decode(invalid.toString().getBytes(StandardCharsets.UTF_8));throw new AssertionError("Rejected: "+invalid);}catch(IllegalArgumentException expected){}}
   try{ChatRules.decode(new byte[ChatRules.MAX_BYTES+1]);throw new AssertionError("Oversized rejected");}catch(IllegalArgumentException expected){}
   var calls=new AtomicInteger();var conditional=new AtomicInteger();var mode=new String[]{"ok"};
   server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/mcmod/chat-rules-v1.json",exchange->{calls.incrementAndGet();byte[] body=mode[0].equals("ok")?changed.toString().getBytes(StandardCharsets.UTF_8):"{broken".getBytes(StandardCharsets.UTF_8);String tag="W/\"rules-v2\"";
    if(mode[0].equals("ok")&&tag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))){conditional.incrementAndGet();exchange.sendResponseHeaders(304,-1);exchange.close();return;}
    exchange.getResponseHeaders().set("Content-Type","application/json");exchange.getResponseHeaders().set("ETag",tag);exchange.sendResponseHeaders(200,body.length);try(var out=exchange.getResponseBody()){out.write(body);}});server.start();
   var site=URI.create("http://127.0.0.1:"+server.getAddress().getPort());Path cache=Files.createTempDirectory("paletale-chat-rules-");
   ChatRulesSync.resetForTest();ChatRulesSync.refresh(site,cache).get(15,TimeUnit.SECONDS);require(ChatRules.current().version()==2&&calls.get()==1,"First launch downloads once and applies");
   ChatRulesSync.refresh(site,cache).get(15,TimeUnit.SECONDS);require(calls.get()==1,"Rebuilding screens never asks again within the interval");
   ChatRulesSync.resetForTest();ChatRulesSync.refresh(site,cache).get(15,TimeUnit.SECONDS);require(calls.get()==2&&conditional.get()==1&&ChatRules.current().version()==2,"Next launch sends If-None-Match, gets 304 with no body and keeps the cached rules");
   mode[0]="broken";ChatRulesSync.resetForTest();ChatRulesSync.refresh(site,cache).get(15,TimeUnit.SECONDS);require(ChatRules.current().version()==2,"Invalid download keeps the cached rules");
   server.stop(0);server=null;ChatRulesSync.resetForTest();ChatRulesSync.refresh(site,cache).get(15,TimeUnit.SECONDS);require(ChatRules.current().version()==2,"Offline launch uses the disk cache");
   try(var files=Files.list(cache)){for(var f:files.toList())Files.writeString(f,"{bad");}ChatRulesSync.resetForTest();ChatRulesSync.refresh(site,cache).get(15,TimeUnit.SECONDS);require(ChatRules.current().version()==1&&fingerprint().equals(builtIn),"Corrupt cache and no network fall back to the built-in rules");
   System.out.println("PaleTale chat rules checks passed");
  }catch(Exception error){throw new AssertionError(error);}finally{if(server!=null)server.stop(0);ChatRulesSync.resetForTest();}
 }
}
