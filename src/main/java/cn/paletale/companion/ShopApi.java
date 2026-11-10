package cn.paletale.companion;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Bound chest-shop calls; every call carries the short mod session token, never a Minecraft token. */
public final class ShopApi {
    public record Position(String world, int x, int y, int z) { String key() { return world + "|" + x + "|" + y + "|" + z; } }
    /** A shop this player bound: its sign, the chest behind it (when known) and the ad product it feeds. */
    public record MyShop(Position sign, Position chest, String adCode, String adTitle, int productIndex, String productName, String itemName) {}
    /** An ad product whose bound chest shop belongs to a given owner and item (the ledger links a purchase to the seller's ad). */
    public record AdLink(String adCode, String adTitle, int productIndex, String productName, int adId) {}
    private volatile URI site;
    public void retarget(URI next) { if (MarketApi.isAllowedSite(next)) site = next; }
    private final HttpClient http = HttpBodies.CLIENT;
    public ShopApi(URI site) { this.site = site; }

    public CompletableFuture<Integer> bind(String token, String code, int productIndex, List<ShopSigns.Reading> shops) {
        return bind(token,code,productIndex,"",shops);
    }
    public CompletableFuture<Integer> bind(String token,String code,int productIndex,String productKey,List<ShopSigns.Reading> shops){
        JsonObject body = new JsonObject(); body.addProperty("code", code); body.addProperty("productIndex", productIndex); if(!productKey.isEmpty())body.addProperty("productKey",productKey);body.add("shops", ShopSigns.jsonList(shops));
        return call("bind", "POST", token, body).thenApply(data -> data.get("bound").getAsInt());
    }
    public CompletableFuture<Integer> unbind(String token, String code, List<Position> positions) {
        JsonObject body = new JsonObject(); body.addProperty("code", code);
        JsonArray list = new JsonArray();
        for (Position p : positions) { JsonObject row = new JsonObject(); row.addProperty("world", p.world()); row.addProperty("x", p.x()); row.addProperty("y", p.y()); row.addProperty("z", p.z()); list.add(row); }
        body.add("positions", list);
        return call("unbind", "POST", token, body).thenApply(data -> data.get("unbound").getAsInt());
    }
    public CompletableFuture<Integer> report(String token, List<ShopSigns.Reading> shops) {
        JsonObject body = new JsonObject(); body.add("shops", ShopSigns.jsonList(shops));
        return call("report", "POST", token, body).thenApply(data -> data.get("updated").getAsInt());
    }
    public CompletableFuture<List<Position>> mine(String token) {
        return mineDetailed(token).thenApply(shops -> shops.stream().map(MyShop::sign).toList());
    }
    public CompletableFuture<List<MyShop>> mineDetailed(String token) {
        return call("mine", "GET", token, null).thenApply(data -> {
            List<MyShop> shops = new ArrayList<>();
            for (var element : data.getAsJsonArray("shops")) {
                JsonObject row = element.getAsJsonObject();
                String world = row.get("world").getAsString();
                Position sign = new Position(world, row.get("x").getAsInt(), row.get("y").getAsInt(), row.get("z").getAsInt());
                int[] at = ShopSigns.chestAt(sign.x(), sign.y(), sign.z(), text(row, "chestDir"));
                Position chest = at == null ? null : new Position(world, at[0], at[1], at[2]);
                shops.add(new MyShop(sign, chest, text(row, "adCode"), text(row, "adTitle"), row.has("productIndex") ? row.get("productIndex").getAsInt() : 0, text(row, "productName"), text(row, "itemName")));
            }
            return List.copyOf(shops);
        });
    }
    public CompletableFuture<List<AdLink>> lookup(String token, String owner, String item) {
        String query = "lookup?owner=" + java.net.URLEncoder.encode(owner, StandardCharsets.UTF_8) + "&item=" + java.net.URLEncoder.encode(item, StandardCharsets.UTF_8);
        return call(query, "GET", token, null).thenApply(data -> {
            List<AdLink> ads = new ArrayList<>();
            if (data.has("ads")) for (var element : data.getAsJsonArray("ads")) {
                JsonObject row = element.getAsJsonObject();
                ads.add(new AdLink(text(row, "adCode"), text(row, "adTitle"), row.has("productIndex") ? row.get("productIndex").getAsInt() : 0, text(row, "productName"), row.has("adId") ? row.get("adId").getAsInt() : 0));
            }
            return List.copyOf(ads);
        });
    }
    private static String text(JsonObject row, String key) { return row.has(key) && row.get(key).isJsonPrimitive() ? row.get(key).getAsString() : ""; }
    private CompletableFuture<JsonObject> call(String path, String method, String token, JsonObject body) {
        HttpRequest.Builder request = HttpBodies.request(site.resolve("/api/mcmod/shops/" + path)).timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json").header("Authorization", "Bearer " + token);
        if (body == null) request.GET(); else request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        return http.sendAsync(request.build(), HttpBodies.text()).thenApply(response -> {
            if (!response.headers().firstValue("Content-Type").orElse("").contains("application/json") || response.body().length() > 262_144)
                throw new IllegalStateException("箱子商店接口暂时不可用。");
            JsonObject data = JsonParser.parseString(response.body()).getAsJsonObject();
            if (response.statusCode() != 200) throw new IllegalStateException(data.has("message") ? MarketData.text(data.get("message").getAsString()) : "箱子商店操作失败。");
            return data;
        });
    }
}
