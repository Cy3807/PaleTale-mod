package cn.paletale.companion;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Fixed b44f35f8 server-produced examples, plus explicitly synthetic long-ID/other/failure extensions. Loopback only. */
public final class NoticeFixture implements AutoCloseable {
    public final HttpServer server;
    private final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();
    private final JsonArray cases;
    public final List<String> requests=new CopyOnWriteArrayList<>();
    public final AtomicInteger expireNext=new AtomicInteger();
    public volatile int getStatus,writeStatus;
    public volatile boolean legacy,partial,missing;
    public volatile boolean legacyCount=true;
    public volatile long unread=38;
    private final Set<Long> read=ConcurrentHashMap.newKeySet();
    private int revision;
    public static final long OFFSET=4_000_000_000L;
    public NoticeFixture() throws Exception {
        String source;
        try(var stream=NoticeFixture.class.getResourceAsStream("/notifications-examples-v2.json")){source=stream==null?Files.readString(Path.of("src/gametest/resources/notifications-examples-v2.json")):new String(stream.readAllBytes(),StandardCharsets.UTF_8);}
        cases=JsonParser.parseString(source).getAsJsonObject().getAsJsonArray("cases");
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(workers);
        server.createContext("/api/mcmod/notifications",exchange->{
            String path=exchange.getRequestURI().getPath(),query=URLDecoder.decode(Objects.toString(exchange.getRequestURI().getRawQuery(),""),StandardCharsets.UTF_8),body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            requests.add(exchange.getRequestMethod()+" "+path+"?"+query+" "+body);
            int status=200;JsonObject response;
            if(exchange.getRequestMethod().equals("POST")){
                if(writeStatus!=0){status=writeStatus;response=error(status==503?"UNCONFIRMED":"LIMITED");}
                else {
                    var input=JsonParser.parseString(body).getAsJsonObject();var confirmed=new JsonArray();boolean all=input.has("all");
                    if(all)unread=2;else {var ids=input.getAsJsonArray("ids");int maximum=partial?1:ids.size();for(int i=0;i<maximum;i++){long id=ids.get(i).getAsLong();confirmed.add(id);if(read.add(id))unread=Math.max(0,unread-1);}}
                    revision++;response=new JsonObject();response.addProperty("ok",true);
                    if(query.contains("v=2")){response.addProperty("version",2);response.add("confirmedIds",confirmed);response.addProperty("unreadTotal",unread);response.addProperty("restart",true);if(all)response.addProperty("all",true);if(path.endsWith("delete"))response.addProperty("deletedCount",confirmed.size());}
                }
            }else if(getStatus!=0){status=getStatus;response=error("UNAVAILABLE");}
            else if(expireNext.getAndUpdate(value->Math.max(0,value-1))>0){status=410;response=error("SNAPSHOT_EXPIRED");response.addProperty("restart",true);}
            else if(legacy){response=example(0);if(query.contains("all=1")){response.addProperty("page",query.contains("page=2")?2:1);response.addProperty("pageSize",8);response.addProperty("total",4);}if(legacyCount)response.addProperty("unreadTotal",38);else response.remove("unreadTotal");}
            else if(query.contains("view=unread")){
                response=example(1);var items=new JsonArray();for(int i=0;i<20;i++){var item=example(9).getAsJsonArray("items").get(0).getAsJsonObject().deepCopy();item.addProperty("id",OFFSET+219-i);items.add(item);}
                response.add("items",items);response.addProperty("hasMore",true);response.addProperty("unreadTotal",unread);
            }else if(query.contains("view=group&")){
                String cursor=query.substring(query.indexOf("cursor=")+7);
                response=example(cursor.equals(next(9))?10:9);
                if(cursor.equals(next(10))){response=example(10);var items=new JsonArray();for(int i=203;i>=199;i--){var row=response.getAsJsonArray("items").get(0).getAsJsonObject().deepCopy();row.addProperty("id",i);items.add(row);}response.add("items",items);response.add("nextCursor",JsonNull.INSTANCE);}
                if(!cursor.equals(example(7).getAsJsonArray("groups").get(0).getAsJsonObject().get("detailsCursor").getAsString())&&!cursor.equals(next(9))&&!cursor.equals(next(10))){
                    JsonObject group=null;for(var value:example(7).getAsJsonArray("groups")){var candidate=value.getAsJsonObject();if(cursor.equals(candidate.get("detailsCursor").getAsString()))group=candidate;}
                    if(group!=null){var values=new JsonArray();var row=example(9).getAsJsonArray("items").get(0).getAsJsonObject().deepCopy();
                        boolean other=group.get("key").getAsString().equals("ad:13"),hidden=group.get("key").getAsString().equals("ad:12");
                        response.addProperty("groupKey",other?"notification:"+(OFFSET+300):group.get("key").getAsString());response.addProperty("count",1);response.add("nextCursor",JsonNull.INSTANCE);
                        row.addProperty("id",other?300:group.get("latestId").getAsLong());row.addProperty("targetType",other?"other":"ad");row.addProperty("available",!other&&!hidden);row.add("adId",other?JsonNull.INSTANCE:group.get("adId"));row.add("targetId",other?JsonNull.INSTANCE:group.get("targetId"));row.addProperty("adCode",other||hidden?"":group.get("adCode").getAsString());row.addProperty("adTitle",other||hidden?"":group.get("title").getAsString());row.addProperty("detail",other?"独立系统通知仍然保留":hidden?"原广告不可见，通知正文仍可阅读":"测试广告变动");values.add(row);response.add("items",values);
                    }
                }
                shiftItems(response);
                if(missing){var row=response.getAsJsonArray("items").remove(0);response.getAsJsonArray("missingIds").add(row.getAsJsonObject().get("id"));}
                response.addProperty("unreadTotal",unread);response.addProperty("snapshot","fixture-r"+revision);
            }else{
                String cursor=query.contains("cursor=")?query.substring(query.indexOf("cursor=")+7):null;response=example(cursor==null?7:8);
                if(cursor==null){var groups=response.getAsJsonArray("groups");var other=groups.get(6).getAsJsonObject();other.addProperty("key","notification:"+(OFFSET+300));other.addProperty("latestId",300);other.add("adId",JsonNull.INSTANCE);other.addProperty("targetType","other");other.add("targetId",JsonNull.INSTANCE);other.addProperty("available",false);other.addProperty("title","");other.addProperty("adCode","");other.addProperty("latestDetail","独立系统通知仍然保留");
                    var unavailable=groups.get(7).getAsJsonObject();unavailable.addProperty("available",false);unavailable.addProperty("title","");unavailable.addProperty("adCode","");unavailable.addProperty("latestDetail","原广告不可见，通知正文仍可阅读");}
                else if(cursor.equals(next(8))){var groups=new JsonArray();for(int id=3;id>=2;id--){var row=example(8).getAsJsonArray("groups").get(0).getAsJsonObject().deepCopy();row.addProperty("key","ad:"+id);row.addProperty("adId",id);row.addProperty("targetId",id);row.addProperty("adCode","10000"+id);row.addProperty("title","广告"+id);row.addProperty("latestId",99+id);row.addProperty("detailsCursor","fixture-ad"+id);groups.add(row);}response.add("groups",groups);response.add("nextCursor",JsonNull.INSTANCE);}
                for(var group:response.getAsJsonArray("groups")){var row=group.getAsJsonObject();row.addProperty("latestId",row.get("latestId").getAsLong()+OFFSET);if(row.get("key").getAsString().equals("ad:1"))row.addProperty("unreadAtSnapshot",21-read.stream().filter(id->id>=OFFSET+199&&id<=OFFSET+219).count());}
                response.addProperty("unreadTotal",unread);response.addProperty("snapshot","fixture-r"+revision);
            }
            byte[] bytes=response.toString().getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}
        });
        server.start();
    }
    private void shiftItems(JsonObject response){for(var value:response.getAsJsonArray("items")){var row=value.getAsJsonObject();long id=row.get("id").getAsLong()+OFFSET;row.addProperty("id",id);row.addProperty("read",read.contains(id));}}
    private String next(int index){return example(index).get("nextCursor").getAsString();}
    private JsonObject example(int index){return cases.get(index).getAsJsonObject().getAsJsonObject("response").getAsJsonObject("body").deepCopy();}
    private static JsonObject error(String code){var value=new JsonObject();value.addProperty("code",code);return value;}
    public URI site(){return URI.create("http://127.0.0.1:"+server.getAddress().getPort());}
    @Override public void close(){server.stop(0);workers.close();}
}
