package cn.paletale.companion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static cn.paletale.companion.MarketData.*;

public final class MarketApi {
    public static final URI PREVIEW = URI.create("https://preview.paletale.cn");
    private static final WorldCatalogCache PUBLIC = new WorldCatalogCache();
    private volatile URI site;
    private final HttpClient http;

    public MarketApi(URI site) {this(site,HttpBodies.CLIENT);}
    private MarketApi(URI site,HttpClient http) {
        if (!isAllowedSite(site)) throw new IllegalArgumentException("Unsupported PaleTale site");
        this.site = site;this.http=http;
    }
    public URI site() { return site; }
    public MarketApi snapshot() {return new MarketApi(site,http);}
    /** Switch to another official entry (e.g. the mainland mirror) when the main domain is unreachable. */
    public void retarget(URI next) { if (isAllowedSite(next)) site = next; }

    public static boolean isAllowedSite(URI site) {
        if (site.getUserInfo() != null || site.getQuery() != null || site.getFragment() != null
                || !(site.getPath().isEmpty() || site.getPath().equals("/"))) return false;
        return ("https".equals(site.getScheme()) && site.getPort() == -1
                && List.of("preview.paletale.cn", "www.paletale.cn", "pt.cy3807.com").contains(site.getHost()))
                || ("http".equals(site.getScheme()) && List.of("localhost", "127.0.0.1").contains(site.getHost()));
    }

    public URI searchUri(String query, Category category, boolean includeInactive, int page) {
        if (query.length() > 100 || page < 1 || page > 10000) throw new IllegalArgumentException("搜索条件超出范围");
        return site.resolve("/api/mcmod/market?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + (category == Category.ALL ? "" : "&tag=" + category.id)
                + "&state=" + (includeInactive ? "all" : "active") + "&page=" + page);
    }

    public CompletableFuture<Page> search(String query, Category category, boolean includeInactive, int page) {
        URI uri = searchUri(query, category, includeInactive, page);
        return search(uri);
    }

    public CompletableFuture<Page> search(String query, Set<Category> categories, AdState state, int page) {
        if (query.length() > 100 || page < 1 || page > 10000) throw new IllegalArgumentException("搜索条件超出范围");
        String tags = categories.stream().filter(category -> category != Category.ALL)
                .sorted().map(category -> "&tag=" + category.id).collect(java.util.stream.Collectors.joining());
        return search(site.resolve("/api/mcmod/market?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + tags + "&state=" + state.id + "&page=" + page));
    }

    /** sort: random (with the seed) | views | favorites | updated. */
    public CompletableFuture<Page> search(String query, Set<Category> categories, AdState state, int page, int seed, String sort) {
        return search(publicSearchUri(query,categories,state,page,seed,sort));
    }
    private URI publicSearchUri(String query, Set<Category> categories, AdState state, int page, int seed, String sort) {
        if (!List.of("random","views","favorites","updated").contains(sort)||query.length()>100||page<1||page>10000||seed<1||seed>999_999_999) throw new IllegalArgumentException("搜索条件超出范围");
        String tags=categories.stream().filter(c->c!=Category.ALL).sorted().map(c->"&tag="+c.id).collect(java.util.stream.Collectors.joining());
        return site.resolve("/api/mcmod/market?q="+URLEncoder.encode(query,StandardCharsets.UTF_8)+tags+"&state="+state.id+"&page="+page+(sort.equals("random")?"&seed="+seed:"&sort="+sort));
    }
    public CompletableFuture<Page> search(String query, Set<Category> categories, AdState state, int page, int seed) { return search(query,categories,state,page,seed,"random"); }
    public Page peekSearch(String query, Set<Category> categories, AdState state, int page, int seed, String sort) {
        var cached=PUBLIC.peek(site,publicSearchUri(query,categories,state,page,seed,sort).getRawQuery());return cached==null?null:parse(cached.get("body").getAsString());
    }
    public long refreshIn(String query,Set<Category> categories,AdState state,int page,int seed,String sort){return PUBLIC.refreshIn(site,publicSearchUri(query,categories,state,page,seed,sort).getRawQuery());}
    public void invalidatePublic() { PUBLIC.invalidate(site); }
    /** The logged-in player's favourite ads. */
    public CompletableFuture<Page> favorites(String token, int page) {
        if (page < 1 || page > 100) throw new IllegalArgumentException("页码超出范围");
        return search(site.resolve("/api/mcmod/market/favorites?page=" + page), token);
    }
    /** The logged-in player's own and assisted ads (needs the short mod session token). */
    public CompletableFuture<Page> mine(String token, int page) {
        if (page < 1 || page > 100) throw new IllegalArgumentException("页码超出范围");
        return search(site.resolve("/api/mcmod/market/mine?page=" + page), token);
    }
    private CompletableFuture<Page> search(URI uri) {
        // Details and authenticated endpoints are always checked live.
        if(!uri.getPath().equals("/api/mcmod/market"))return search(uri,null);
        return PUBLIC.load(site,uri.getRawQuery(),()->searchBody(uri,null).thenApply(body->{parse(body);var data=new JsonObject();data.addProperty("body",body);return data;})).thenApply(data->parse(data.get("body").getAsString()));
    }
    private CompletableFuture<Page> search(URI uri, String token) {
        return searchBody(uri,token).thenApply(MarketApi::parse);
    }
    private CompletableFuture<String> searchBody(URI uri,String token) {
        HttpRequest.Builder builder = HttpBodies.request(uri).timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        HttpRequest request = builder.GET().build();
        CompletableFuture<HttpResponse<String>> exchange = http.sendAsync(request, HttpBodies.text());
        CompletableFuture<String> parsed = exchange.thenApply(response -> {
            if (response.statusCode() != 200) throw new IllegalStateException(response.statusCode() == 404
                    ? "搜索接口尚未开放，请稍后重试。" : "搜索服务暂时不可用（HTTP " + response.statusCode() + "），请重试。");
            if (!response.headers().firstValue("Content-Type").orElse("").contains("application/json")) {
                throw new IllegalStateException("网站未返回搜索数据，请稍后重试。");
            }
            return response.body();
        });
        parsed.whenComplete((value, failure) -> {
            if (parsed.isCancelled()) exchange.cancel(true);
        });
        return parsed;
    }

    public CompletableFuture<Ad> detail(String code) {
        if (!code.matches("[1-9][0-9]{5}")) throw new IllegalArgumentException("Invalid market code");
        return search(site.resolve("/api/mcmod/market/" + code)).thenApply(page -> {
            if (page.ads().size() != 1 || !page.ads().get(0).code().equals(code)) throw new IllegalStateException("广告已不可用。");
            return page.ads().get(0);
        });
    }

    public URI adUri(Ad ad) {
        if (!ad.code().matches("[1-9][0-9]{5}")) throw new IllegalArgumentException("Invalid market code");
        return site.resolve("/market/" + ad.code());
    }

    public static Page parse(String body) {
        if (body.length() > 4 * 1024 * 1024) throw new IllegalStateException("搜索数据过大，请缩小搜索范围。");
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.get("version").getAsInt() != 1) throw new IllegalArgumentException("Unsupported version");
            int page = root.get("page").getAsInt(), size = root.get("pageSize").getAsInt(), total = root.get("total").getAsInt();
            if (page < 1 || page > 10000 || size != 12 || total < 0) throw new IllegalArgumentException("Invalid pagination");
            List<Ad> ads = new ArrayList<>();
            for (JsonElement element : array(root, "ads", 12)) {
                JsonObject ad = element.getAsJsonObject();
                String code = string(ad, "code", 6);
                if (!code.matches("[1-9][0-9]{5}")) throw new IllegalArgumentException("Invalid code");
                List<Product> products = new ArrayList<>();
                for (JsonElement entry : array(ad, "products", 50)) {
                    JsonObject p = entry.getAsJsonObject();
                    products.add(new Product(string(p, "name", 100), string(p, "description", 2000),
                            string(p, "price", 120), string(p, "state", 20),
                            p.has("itemId") && !p.get("itemId").isJsonNull() ? Math.max(0, p.get("itemId").getAsInt()) : 0,
                            ArchiveApi.appearance(p), p.has("imagePath") && !p.get("imagePath").isJsonNull() ? string(p, "imagePath", 200) : "",
                            p.has("itemName") && !p.get("itemName").isJsonNull() ? string(p, "itemName", 100) : "",
                            vanilla(p, "vanillaItem", 64), vanilla(p, "vanillaItemName", 100),
                            p.has("itemHasModel") && !p.get("itemHasModel").isJsonNull() && p.get("itemHasModel").getAsBoolean(), shops(p),string(p,"bindingKey",96),string(p,"bindingWarning",200),texts(p,"tags",4,20),texts(p,"categories",3,40)));
                }
                List<Location> locations = new ArrayList<>();
                for (JsonElement entry : array(ad, "locations", 8)) {
                    JsonObject l = entry.getAsJsonObject();
                    locations.add(new Location(string(l, "name", 100), string(l, "coordinates", 120), string(l, "directions", 1000)));
                }
                List<String> tags = new ArrayList<>();
                for (JsonElement tag : array(ad, "tags", 12)) tags.add(text(tag.getAsString()));
                JsonObject author = ad.getAsJsonObject("author");
                ads.add(new Ad(code, string(ad, "title", 80), string(ad, "summary", 200), string(ad, "state", 20),
                        List.copyOf(tags), new Author(string(author, "name", 200), string(author, "mcName", 100), string(author, "avatarPath", 200),
                                author.has("playerId") && !author.get("playerId").isJsonNull() ? Math.max(0, author.get("playerId").getAsInt()) : 0),
                        List.copyOf(products), List.copyOf(locations), string(ad, "updatedAt", 100), string(ad, "publishedAt", 100), string(ad, "iconPath", 3000),
                        ArchiveApi.counter(ad, "views"), ArchiveApi.counter(ad, "favorites"), string(ad, "body", 20000),
                        ad.has("contact") && ad.get("contact").isJsonObject() && string(ad.getAsJsonObject("contact"), "qq", 12).matches("[0-9]{5,12}") ? string(ad.getAsJsonObject("contact"), "qq", 12) : "",
                        ad.has("contact") && ad.get("contact").isJsonObject() ? string(ad.getAsJsonObject("contact"), "note", 300) : ""));
            }
            return new Page(page, size, total, root.get("hasMore").getAsBoolean(), List.copyOf(ads));
        } catch (RuntimeException error) {
            throw new IllegalStateException("搜索数据格式不兼容，请更新模组或稍后重试。", error);
        }
    }

    /** An optional list of short texts (absent in older sites): at most {@code count}, each at most {@code limit} characters. */
    private static List<String> texts(JsonObject object, String key, int count, int limit) {
        if (!object.has(key) || !object.get(key).isJsonArray()) return List.of();
        List<String> out = new ArrayList<>();
        for (JsonElement value : object.getAsJsonArray(key)) { if (out.size() >= count) break; if (value.isJsonPrimitive()) { String text = text(value.getAsString()).strip(); if (!text.isEmpty() && text.length() <= limit) out.add(text); } }
        return List.copyOf(out);
    }

    private static List<Shop> shops(JsonObject product) {
        List<Shop> rows = new ArrayList<>();
        if (!product.has("shops") || product.get("shops").isJsonNull()) return rows;
        for (JsonElement element : array(product, "shops", 64)) {
            JsonObject row = element.getAsJsonObject();
            String mode = string(row, "mode", 4);
            ShopSigns.Mode parsed = mode.equals("buy") ? ShopSigns.Mode.BUY : mode.equals("sell") ? ShopSigns.Mode.SELL : mode.equals("both") ? ShopSigns.Mode.BOTH : null;
            if (parsed == null) throw new IllegalArgumentException("Invalid shop mode");
            rows.add(new Shop(new ShopSigns.Reading(string(row, "world", 64), row.get("x").getAsInt(), row.get("y").getAsInt(), row.get("z").getAsInt(),
                    string(row, "owner", 32), string(row, "itemName", 100), parsed, row.get("quantity").getAsInt(),
                    row.get("buyPrice").isJsonNull() ? null : row.get("buyPrice").getAsDouble(), row.get("sellPrice").isJsonNull() ? null : row.get("sellPrice").getAsDouble()),
                    string(row, "observedAt", 40)));
        }
        return List.copyOf(rows);
    }
    private static String string(JsonObject object, String key, int limit) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) return "";
        String raw = value.getAsString();
        if (raw.length() > limit) throw new IllegalArgumentException("Field too long");
        return text(raw);
    }

    private static JsonArray array(JsonObject object, String key, int limit) {
        JsonArray array = object.getAsJsonArray(key);
        if (array == null || array.size() > limit) throw new IllegalArgumentException("Invalid list");
        return array;
    }

    /** Vanilla item id (lower-case registry path) or display name; anything else is dropped. */
    private static String vanilla(JsonObject p, String key, int max) {
        if (!p.has(key) || p.get(key).isJsonNull()) return "";
        String value = string(p, key, max);
        return !key.equals("vanillaItem") || value.matches("[a-z0-9_]{1,64}") ? value : "";
    }
}
