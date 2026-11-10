package cn.paletale.companion;

import com.google.gson.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Notification v2 is parsed independently; an HTTP error can never trigger legacy fallback. */
public final class NoticeV2Api {
    public record Item(long id, String kind, String adCode, String adTitle, String adOwner, String adOwnerId,
                       long itemId, String product, String change, String detail, boolean read, String createdAt,
                       Long adId, String targetType, Long targetId, boolean available) {}
    public record Group(String key, Long adId, String targetType, Long targetId, boolean available, String adCode,
                        String title, String latestKind, String latestDetail, long count, long unreadAtSnapshot,
                        long latestId, String latestAt, String detailsCursor) {}
    public record Preview(boolean v2, long unreadTotal, List<Item> items, boolean hasMore) {}
    /** unreadTotal=-1 only for a legacy server that has no authoritative count. */
    public record Page(boolean v2, String mode, String snapshot, long total, long totalGroups, long unreadAtSnapshot,
                       long unreadTotal, int pageSize, List<Group> groups, List<Item> items, List<Long> missingIds,
                       String nextCursor, String groupKey, long count, int legacyPage) {}
    public record Mutation(boolean v2, List<Long> confirmedIds, long unreadTotal, boolean all) {}
    /** A request waiting for the player's yes or no (an organization invitation, a helper appointment). */
    public record Action(long id, String type, String detail, String createdAt, String expiresAt) {}
    public static final class Failure extends IllegalStateException {
        public final int status;
        public final String code;
        Failure(int status, String code, String message) { super(message); this.status=status;this.code=code; }
    }
    private final Supplier<URI> site;
    private static final HttpClient HTTP = HttpBodies.CLIENT;
    private volatile URI legacyAt;
    public NoticeV2Api(Supplier<URI> site) { this.site=site; }
    public CompletableFuture<Preview> unread(String token) {
        return call("?v=2&view=unread",token,null).thenApply(data -> {
            if (isV2(data)) {
                mode(data,"unread");return new Preview(true,number(data,"unreadTotal"),items(data,true),data.get("hasMore").getAsBoolean());
            }
            old(data);legacyAt=site.get();
            return new Preview(false,optionalCount(data),items(data,false),false);
        });
    }
    public CompletableFuture<Page> groups(String token, String cursor) {
        if (Objects.equals(legacyAt,site.get())) return legacyPage(token,1);
        return call("?v=2&view=groups"+cursor(cursor),token,null).thenCompose(data -> {
            if (isV2(data)) return CompletableFuture.completedFuture(page(data,"groups"));
            old(data);legacyAt=site.get();return legacyPage(token,1);
        });
    }
    public CompletableFuture<Page> details(String token, String cursor) {
        if (cursor==null || cursor.isBlank()) throw new IllegalArgumentException("Missing detail cursor");
        return call("?v=2&view=group"+cursor(cursor),token,null).thenApply(data -> page(data,"group"));
    }
    public CompletableFuture<Page> legacyPage(String token,int page) {
        if(page<1||page>1000)throw new IllegalArgumentException("Invalid page");
        return call("?all=1&page="+page,token,null).thenApply(data->{
            old(data);int size=(int)number(data,"pageSize");if(size<1||size>20)throw invalid();
            return new Page(false,"legacy","",number(data,"total"),0,0,optionalCount(data),size,List.of(),items(data,false),List.of(),null,"",0,(int)number(data,"page"));
        });
    }
    public CompletableFuture<Mutation> mutate(String token, List<Long> ids, boolean delete, boolean all, boolean v2) {
        if(all&&!delete||all&&!ids.isEmpty()||!all&&(ids.isEmpty()||ids.size()>(delete?100:50)))throw new IllegalArgumentException("Invalid notification selection");
        JsonObject body=new JsonObject();
        if(all)body.addProperty("all",true);else {JsonArray values=new JsonArray();for(long id: new LinkedHashSet<>(ids)){positive(id);values.add(id);}body.add("ids",values);}
        return call((delete?"/delete":"/read")+(v2?"?v=2":""),token,body).thenApply(data->{
            if(!data.has("ok")||!data.get("ok").getAsBoolean())throw invalid();
            if(!v2)return new Mutation(false,List.of(),-1,all);
            if(!isV2(data)||!data.has("restart")||!data.get("restart").getAsBoolean())throw invalid();
            List<Long> confirmed=all?List.of():ids(data,"confirmedIds");
            if(!ids.containsAll(confirmed)||new HashSet<>(confirmed).size()!=confirmed.size())throw invalid();
            if(all&&(!data.has("all")||!data.get("all").getAsBoolean()))throw invalid();
            return new Mutation(true,confirmed,number(data,"unreadTotal"),all);
        });
    }
    /** Pending yes/no requests, newest first; an older server that has no such endpoint just answers with a failure. */
    public CompletableFuture<List<Action>> actions(String token) {
        return call("/actions",token,null).thenApply(data->{
            List<Action> result=new ArrayList<>();JsonArray rows=data.getAsJsonArray("items");if(rows==null||rows.size()>50)throw invalid();
            for(var value:rows){var row=value.getAsJsonObject();result.add(new Action(id(row,"id"),string(row,"type"),string(row,"detail"),string(row,"createdAt"),string(row,"expiresAt")));}
            return List.copyOf(result);
        });
    }
    /** Answers one request; the result is the status the server settled on (accepted, declined, expired, cancelled or missing). */
    public CompletableFuture<String> respond(String token, long id, boolean accept) {
        positive(id);JsonObject body=new JsonObject();body.addProperty("id",id);body.addProperty("accept",accept);
        return call("/actions",token,body).thenApply(data->{
            String status=string(data,"status");
            if(!List.of("accepted","declined","expired","cancelled","missing").contains(status))throw invalid();
            return status;
        });
    }
    private static String cursor(String value){return value==null?"":"&cursor="+URLEncoder.encode(value,StandardCharsets.UTF_8);}
    private CompletableFuture<JsonObject> call(String path,String token,JsonObject body) {
        var request=HttpBodies.request(site.get().resolve("/api/mcmod/notifications"+path)).timeout(Duration.ofSeconds(12)).header("Accept","application/json").header("Authorization","Bearer "+token);
        if(body==null)request.GET();else request.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        return HTTP.sendAsync(request.build(),HttpBodies.limited(262144)).thenApply(response->{
            String text=new String(response.body(),StandardCharsets.UTF_8);
            if(text.length()>65536||!response.headers().firstValue("Content-Type").orElse("").contains("application/json"))throw invalid();
            JsonObject data;
            try {data=JsonParser.parseString(text).getAsJsonObject();}catch(RuntimeException bad){throw invalid();}
            int status=response.statusCode();
            if(status!=200)throw new Failure(status,string(data,"code"),switch(status){
                case 401->"模组会话已失效，请重新登录后重试。";
                case 410->"通知列表已过期，请刷新通知。";
                case 429->"通知请求过于频繁，请稍后重试。";
                case 413->"通知数量超出上限，请先在网站整理后重试。";
                case 503->body==null?"通知暂时无法读取，保留上次结果，请稍后刷新。":"操作结果尚未确认，可能已生效，请刷新核对或重试。";
                default->"通知接口暂时不可用，请重试。";});
            return data;
        });
    }
    static Page page(JsonObject data,String mode) {
        if(!isV2(data))throw invalid();mode(data,mode);
        List<Group> groups=new ArrayList<>();
        if(mode.equals("groups"))for(var value:data.getAsJsonArray("groups")){
            var row=value.getAsJsonObject();groups.add(new Group(required(row,"key"),nullableId(row,"adId"),target(row),nullableId(row,"targetId"),row.get("available").getAsBoolean(),string(row,"adCode"),string(row,"title"),string(row,"latestKind"),string(row,"latestDetail"),number(row,"count"),number(row,"unreadAtSnapshot"),id(row,"latestId"),string(row,"latestAt"),required(row,"detailsCursor")));
        }
        if(groups.size()>8)throw invalid();
        int size=(int)number(data,"pageSize");if(size!=8)throw invalid();
        return new Page(true,mode,required(data,"snapshot"),number(data,"total"),number(data,"totalGroups"),number(data,"unreadAtSnapshot"),number(data,"unreadTotal"),size,List.copyOf(groups),mode.equals("group")?items(data,true):List.of(),mode.equals("group")?ids(data,"missingIds"):List.of(),nullableString(data,"nextCursor"),mode.equals("group")?required(data,"groupKey"):"",mode.equals("group")?number(data,"count"):0,0);
    }
    private static List<Item> items(JsonObject data,boolean v2){
        List<Item> result=new ArrayList<>();JsonArray rows=data.getAsJsonArray("items");if(rows.size()>20)throw invalid();
        for(var value:rows){var row=value.getAsJsonObject();String code=string(row,"adCode");long item=row.has("itemId")?number(row,"itemId"):0;
            // Legacy targets may also be unavailable; retain notification text rather than dropping the row.
            result.add(new Item(id(row,"id"),string(row,"kind"),code,string(row,"adTitle"),string(row,"adOwner"),string(row,"adOwnerId"),item,string(row,"product"),string(row,"change"),string(row,"detail"),row.has("read")&&row.get("read").getAsBoolean(),string(row,"createdAt"),v2?nullableId(row,"adId"):null,v2?target(row):item>0?"item":code.matches("[1-9][0-9]{5}")?"ad":"other",v2?nullableId(row,"targetId"):null,v2?row.get("available").getAsBoolean():item>0||code.matches("[1-9][0-9]{5}")));
        }return List.copyOf(result);
    }
    private static boolean isV2(JsonObject data){return data.has("version")&&data.get("version").isJsonPrimitive()&&number(data,"version")==2;}
    private static void old(JsonObject data){if(data.has("version")||!data.has("items")||!data.get("items").isJsonArray())throw invalid();}
    private static void mode(JsonObject data,String expected){if(!expected.equals(string(data,"mode")))throw invalid();}
    private static String target(JsonObject data){String value=required(data,"targetType");if(!List.of("ad","item","other").contains(value))throw invalid();return value;}
    private static long optionalCount(JsonObject data){return data.has("unreadTotal")?number(data,"unreadTotal"):-1;}
    private static String string(JsonObject data,String key){return data.has(key)&&!data.get(key).isJsonNull()?MarketData.text(data.get(key).getAsString()):"";}
    private static String required(JsonObject data,String key){String result=string(data,key);if(result.isBlank())throw invalid();return result;}
    private static String nullableString(JsonObject data,String key){return data.get(key).isJsonNull()?null:required(data,key);}
    private static long number(JsonObject data,String key){try {long value=data.get(key).getAsBigDecimal().longValueExact();if(value<0||value>9007199254740991L)throw invalid();return value;}catch(RuntimeException bad){throw invalid();}}
    private static long id(JsonObject data,String key){long value=number(data,key);positive(value);return value;}
    private static Long nullableId(JsonObject data,String key){return data.get(key).isJsonNull()?null:id(data,key);}
    private static void positive(long value){if(value<1||value>9007199254740991L)throw invalid();}
    private static List<Long> ids(JsonObject data,String key){List<Long> result=new ArrayList<>();for(var value:data.getAsJsonArray(key)){long id;try{id=value.getAsBigDecimal().longValueExact();}catch(RuntimeException bad){throw invalid();}positive(id);result.add(id);}return List.copyOf(result);}
    private static Failure invalid(){return new Failure(0,"INVALID_RESPONSE","通知响应不完整，请刷新后重试。");}
}
