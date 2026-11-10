package cn.paletale.companion;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Favourites of the logged-in player (items, ads, players); every call carries the short mod session token. */
public final class FavoriteApi {
    public record State(boolean favorited, int favorites) {}
    private final MarketApi base;
    private final HttpClient http = HttpBodies.CLIENT;
    public FavoriteApi(MarketApi base) { this.base = base; }

    public CompletableFuture<State> state(String token, String type, int id) {
        return call("?type=" + checked(type) + "&id=" + id, token, null).thenApply(FavoriteApi::state);
    }
    public CompletableFuture<State> set(String token, String type, int id, boolean on) {
        JsonObject body = new JsonObject(); body.addProperty("type", checked(type)); body.addProperty("id", id); body.addProperty("on", on);
        return call("", token, body).thenApply(FavoriteApi::state);
    }
    /** Opening a page in the mod counts as one view (once per viewer per day on the site); fire-and-forget. */
    public void view(String token, String type, int id) {
        JsonObject body = new JsonObject(); body.addProperty("type", checked(type)); body.addProperty("id", id); send(token, body);
    }
    public void viewAd(String token, String code) {
        JsonObject body = new JsonObject(); body.addProperty("type", "ad"); body.addProperty("code", checkedCode(code)); send(token, body);
    }
    private void send(String token, JsonObject body) {
        HttpRequest.Builder request = HttpBodies.request(base.site().resolve("/api/mcmod/view")).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (token != null) request.header("Authorization", "Bearer " + token);
        http.sendAsync(request.build(), HttpResponse.BodyHandlers.discarding()).exceptionally(failure -> null);
    }
    /** Changes the logged-in player's own website password (the current one is checked by the site). Completes with the site's message. */
    public CompletableFuture<String> changePassword(String token, String current, String next) {
        JsonObject body = new JsonObject(); body.addProperty("current", current); body.addProperty("next", next);
        HttpRequest request = HttpBodies.request(base.site().resolve("/api/mcmod/account/password")).timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json").header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        return http.sendAsync(request, HttpBodies.text()).thenApply(response -> {
            if (!response.headers().firstValue("Content-Type").orElse("").contains("application/json") || response.body().length() > 65_536) throw new IllegalStateException("修改密码接口暂时不可用。");
            JsonObject data = JsonParser.parseString(response.body()).getAsJsonObject();
            String message = data.has("message") ? MarketData.text(data.get("message").getAsString()) : "";
            if (response.statusCode() != 200) throw new IllegalStateException(message.isEmpty() ? "修改失败。" : message);
            return message.isEmpty() ? "密码已更新。" : message;
        });
    }
    /** Ads are addressed by their public six-digit code. */
    public CompletableFuture<State> adState(String token, String code) {
        return call("?type=ad&code=" + checkedCode(code), token, null).thenApply(FavoriteApi::state);
    }
    public CompletableFuture<State> setAd(String token, String code, boolean on) {
        JsonObject body = new JsonObject(); body.addProperty("type", "ad"); body.addProperty("code", checkedCode(code)); body.addProperty("on", on);
        return call("", token, body).thenApply(FavoriteApi::state);
    }
    private static String checkedCode(String code) {
        if (code == null || !code.matches("[1-9][0-9]{5}")) throw new IllegalArgumentException("Invalid market code");
        return code;
    }
    private static String checked(String type) {
        if (!java.util.Set.of("item", "ad", "player").contains(type)) throw new IllegalArgumentException("Unknown favourite type");
        return type;
    }
    private static State state(JsonObject data) { return new State(data.get("favorited").getAsBoolean(), Math.max(0, data.get("favorites").getAsInt())); }
    private CompletableFuture<JsonObject> call(String query, String token, JsonObject body) {
        HttpRequest.Builder request = HttpBodies.request(base.site().resolve("/api/mcmod/favorites" + query)).timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json").header("Authorization", "Bearer " + token);
        if (body == null) request.GET(); else request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        return http.sendAsync(request.build(), HttpBodies.text()).thenApply(response -> {
            if (!response.headers().firstValue("Content-Type").orElse("").contains("application/json") || response.body().length() > 65_536)
                throw new IllegalStateException("收藏接口暂时不可用（HTTP " + response.statusCode() + "）。如刚更新过网站，请稍后重试。");
            JsonObject data = JsonParser.parseString(response.body()).getAsJsonObject();
            if (response.statusCode() != 200) throw new IllegalStateException(data.has("message") ? MarketData.text(data.get("message").getAsString()) : "收藏操作失败。");
            return data;
        });
    }
}
