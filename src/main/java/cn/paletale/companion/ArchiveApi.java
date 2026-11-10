package cn.paletale.companion;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static cn.paletale.companion.ArchiveData.*;

public final class ArchiveApi {
    private final URI site;
    private final HttpClient http = HttpBodies.CLIENT;
    public ArchiveApi(MarketApi api) { site = api.site(); }
    public CompletableFuture<Page> search(Kind kind, String query, String type, String model, int owner, String sort, int page) {
        return search(kind, query, type, model, owner, sort, page, 0);
    }
    /** seed > 0 selects a stable shuffled order shared by every page of one browsing session. */
    public CompletableFuture<Page> search(Kind kind, String query, String type, String model, int owner, String sort, int page, int seed) {
        return search(kind, query, type, model, owner, sort, page, seed, 24);
    }
    public CompletableFuture<Page> search(Kind kind, String query, String type, String model, int owner, String sort, int page, int seed, int size) {
        return search(kind, query, type, model, owner, sort, page, seed, size, false, null, null);
    }
    /**
     * size: entries per page (4–48). favorites: only the player's favourites (needs the token). online: players only, the undashed UUIDs
     * this client currently sees on its server (null = no online filter).
     */
    public CompletableFuture<Page> search(Kind kind, String query, String type, String model, int owner, String sort, int page, int seed, int size,
                                          boolean favorites, java.util.Collection<String> online, String token) {
        return search(kind, query, type, model, owner, sort, page, seed, size, favorites, online, token, "");
    }
    /** effect: a wine effect key or "unknown" (wines without a recorded effect); "" = no effect filter. */
    public CompletableFuture<Page> search(Kind kind, String query, String type, String model, int owner, String sort, int page, int seed, int size,
                                          boolean favorites, java.util.Collection<String> online, String token, String effect) {
        return search(kind, query, type, model, owner, sort, page, seed, size, favorites, online, token, effect, "");
    }
    /** tags: fish website tags, comma separated (all must match); "" = no tag filter. */
    public CompletableFuture<Page> search(Kind kind, String query, String type, String model, int owner, String sort, int page, int seed, int size,
                                          boolean favorites, java.util.Collection<String> online, String token, String effect, String tags) {
        if (!effect.isEmpty() && java.util.Arrays.stream(WINE_EFFECTS).noneMatch(e -> e[0].equals(effect))) throw new IllegalArgumentException("Invalid effect");
        if (query.length() > 100 || page < 1 || page > 10000 || seed < 0 || seed > 999_999_999 || size < 4 || size > 48) throw new IllegalArgumentException("Invalid search");
        String params = "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&page=" + page + "&sort=" + sort + (seed > 0 ? "&seed=" + seed : "") + "&size=" + size;
        if (kind == Kind.ITEMS) params += (type.isBlank() ? "" : "&type=" + type) + (model.isBlank() ? "" : "&model=" + model) + (owner > 0 ? "&owner=" + owner : "") + (effect.isEmpty() ? "" : "&effect=" + effect) + (tags.isEmpty() || !type.equals("fish") ? "" : "&tag=" + URLEncoder.encode(tags, StandardCharsets.UTF_8));
        if (favorites) params += "&favorites=1";
        if (online != null && kind == Kind.PLAYERS) params += "&uuids=" + String.join(",", online.stream().filter(u -> u.matches("[a-f0-9]{32}")).limit(300).toList());
        return request("/api/mcmod/" + kind.id + params, favorites ? token : null, ArchiveApi::page);
    }
    /** Every public player's undashed UUID (lower case), so the mod can pick the registered ones out of its tab list. */
    public CompletableFuture<java.util.Set<String>> registeredPlayers() {
        return request("/api/mcmod/players/uuids", data -> {
            version(data);
            java.util.Set<String> uuids = new java.util.HashSet<>();
            for (JsonElement value : data.getAsJsonArray("uuids")) {
                String uuid = value.getAsString();
                if (uuid.matches("[a-f0-9]{32}") && uuids.size() < 20_000) uuids.add(uuid);
            }
            return java.util.Set.copyOf(uuids);
        });
    }
    public CompletableFuture<Detail> detail(Kind kind, int id) { return detail(kind, id, null); }
    /** With the short mod session token the player detail also carries the QQ number (signed-in players only, like the website). */
    public CompletableFuture<Detail> detail(Kind kind, int id, String token) {
        if (id < 1) throw new IllegalArgumentException("Invalid archive id");
        return request("/api/mcmod/" + kind.id + "/" + id, token, root -> {
            version(root);
            Entry entry = entry(root.getAsJsonObject("entry"));
            if (entry.id() != id || !entry.kind().equals(kind.id)) throw new IllegalStateException("档案身份不一致。");
            List<Entry> owned = new ArrayList<>();
            for (JsonElement value : root.getAsJsonArray("ownedItems")) owned.add(entry(value.getAsJsonObject()));
            if (owned.size() > 12) throw new IllegalStateException("档案数据过多。");
            List<Ad> ads = new ArrayList<>();
            for (JsonElement value : root.getAsJsonArray("ads")) {
                JsonObject ad = value.getAsJsonObject();
                String code = text(ad, "code", 6);
                if (!code.matches("[1-9][0-9]{5}")) throw new IllegalStateException("广告编号不正确。");
                ads.add(new Ad(code, text(ad, "title", 100), text(ad, "state", 20)));
            }
            if (ads.size() > 12) throw new IllegalStateException("档案数据过多。");
            String qq = root.has("qq") && !root.get("qq").isJsonNull() ? text(root, "qq", 12) : "";
            if (!qq.isEmpty() && !qq.matches("[0-9]{5,12}")) throw new IllegalStateException("QQ号格式不正确。");
            List<Member> members = new ArrayList<>();
            if (root.has("members")) for (JsonElement value : root.getAsJsonArray("members")) {
                JsonObject row = value.getAsJsonObject();
                if (members.size() >= 100) throw new IllegalStateException("档案数据过多。");
                members.add(new Member(row.get("playerId").getAsInt(), text(row, "name", 100), text(row, "mcName", 100), row.get("leader").getAsBoolean(), text(row, "avatarPath", 200)));
            }
            String joinUrl = root.has("joinUrl") && !root.get("joinUrl").isJsonNull() ? text(root, "joinUrl", 800) : "";
            if (!joinUrl.isEmpty() && !joinUrl.matches("https://(?:qun|jq|qm)\\.qq\\.com/[^\\s]*")) joinUrl = "";
            boolean qqHidden = root.has("qqHidden") && !root.get("qqHidden").isJsonNull() && root.get("qqHidden").getAsBoolean();
            return new Detail(entry, qq, qqHidden, joinUrl, root.get("ownedTotal").getAsInt(), List.copyOf(owned), root.get("adsTotal").getAsInt(), List.copyOf(ads), List.copyOf(members), fishCode(root), fishLines(root));
        });
    }
    /** Navigation commands are only ever the two forms the mod itself defines; anything else from the network is dropped. */
    static final java.util.regex.Pattern FISH_COMMAND = java.util.regex.Pattern.compile("/pt nav (?:world minecraft:overworld|expedition (?:nether|flesh|dungeon|end)) -?\\d{1,7} (?:~|-?\\d{1,4}) -?\\d{1,7}");
    static String fishCode(JsonObject root) {
        String code = root.has("fishing") && root.get("fishing").isJsonObject() && root.getAsJsonObject("fishing").has("code") ? text(root.getAsJsonObject("fishing"), "code", 40) : "";
        return code.matches("[a-z]{1,10}-[0-9-]{1,16}") ? code : "";
    }
    static List<FishLine> fishLines(JsonObject root) {
        List<FishLine> lines = new ArrayList<>();
        if (root.has("fishing") && root.get("fishing").isJsonObject()) for (JsonElement value : root.getAsJsonObject("fishing").getAsJsonArray("lines")) {
            if (lines.size() >= 8) break;
            JsonObject row = value.getAsJsonObject();
            List<String> icons = new ArrayList<>();
            for (JsonElement icon : row.getAsJsonArray("icons")) {
                String id = icon.getAsString();
                if (icons.size() < 3 && id.matches("minecraft:[a-z0-9_]{1,40}")) icons.add(id);
            }
            String command = row.has("command") && !row.get("command").isJsonNull() ? text(row, "command", 80) : "";
            lines.add(new FishLine(text(row, "text", 200), List.copyOf(icons), FISH_COMMAND.matcher(command).matches() ? command : ""));
        }
        return List.copyOf(lines);
    }
    public URI website(Entry entry) {
        return site.resolve((entry.kind().equals("items") ? "/items/" : entry.kind().equals("orgs") ? "/orgs/" : "/citizens/") + entry.id());
    }
    private <T> CompletableFuture<T> request(String path, java.util.function.Function<JsonObject, T> parser) { return request(path, null, parser); }
    private <T> CompletableFuture<T> request(String path, String token, java.util.function.Function<JsonObject, T> parser) {
        HttpRequest.Builder builder = HttpBodies.request(site.resolve(path)).timeout(Duration.ofSeconds(20)).header("Accept", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        HttpRequest request = builder.GET().build();
        var exchange = http.sendAsync(request, HttpBodies.limited(1024 * 1024));
        CompletableFuture<T> parsed = exchange.thenApply(response -> {
            if (!response.headers().firstValue("Content-Type").orElse("").contains("application/json")) throw new IllegalStateException("档案接口暂时不可用。");
            JsonObject data = JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();
            if (response.statusCode() != 200) throw new IllegalStateException(data.has("message") ? text(data, "message", 300) : "公开档案暂时无法读取。");
            return parser.apply(data);
        });
        parsed.whenComplete((value, failure) -> { if (parsed.isCancelled()) exchange.cancel(true); });
        return parsed;
    }
    public static Page page(JsonObject data) {
        version(data);
        int page = data.get("page").getAsInt(), size = data.get("pageSize").getAsInt(), total = data.get("total").getAsInt();
        if (page < 1 || page > 10000 || size < 4 || size > 48 || total < 0) throw new IllegalStateException("档案分页格式不正确。");
        List<Entry> entries = new ArrayList<>();
        for (JsonElement element : data.getAsJsonArray("entries")) entries.add(entry(element.getAsJsonObject()));
        if (entries.size() > size) throw new IllegalStateException("档案分页过大。");
        return new Page(page, size, total, data.get("hasMore").getAsBoolean(), List.copyOf(entries));
    }
    private static void version(JsonObject root) { if (root.get("version").getAsInt() != 1) throw new IllegalStateException("请更新模组以读取档案。"); }
    private static Entry entry(JsonObject row) {
        String kind = text(row, "kind", 12);
        long rawId = row.get("id").getAsLong();
        if (!List.of("items", "players", "orgs").contains(kind) || rawId < 1 || rawId > Integer.MAX_VALUE) throw new IllegalStateException("档案编号不正确。");
        int id = (int) rawId;
        Owner owner = null;
        if (row.has("owner") && !row.get("owner").isJsonNull()) {
            JsonObject person = row.getAsJsonObject("owner");
            Integer playerId = person.has("playerId") && !person.get("playerId").isJsonNull() ? person.get("playerId").getAsInt() : null;
            if (playerId != null && playerId < 1) throw new IllegalStateException("玩家编号不正确。");
            owner = new Owner(playerId, text(person, "name", 200), text(person, "mcName", 100), person.get("verified").getAsBoolean(), text(person, "avatarPath", 200));
        }
        NativeAppearance appearance = kind.equals("items") ? appearance(row) : null;
        return new Entry(kind, id, text(row, "name", 300), text(row, "subtitle", 300), text(row, "description", 20000),
                text(row, "category", 20), text(row, "badge", 100), row.get("hasModel").getAsBoolean(), text(row, "imagePath", 200), owner, appearance,
                counter(row, "views"), counter(row, "favorites"),
                row.has("effect") && row.get("effect").isJsonPrimitive() && java.util.Arrays.stream(WINE_EFFECTS).anyMatch(e -> e[0].equals(row.get("effect").getAsString())) ? row.get("effect").getAsString() : "",
                text(row, "effectText", 60),
                row.has("brand") && row.get("brand").isJsonPrimitive() ? row.get("brand").getAsString().substring(0, Math.min(2000, row.get("brand").getAsString().length())) : "",
                text(row, "facts", 200));
    }
    static int counter(JsonObject row, String key) {
        return row.has(key) && row.get(key).isJsonPrimitive() ? (int) Math.max(0, Math.min(Integer.MAX_VALUE, row.get(key).getAsLong())) : -1;
    }
    public static NativeAppearance appearance(JsonObject row) {
        NativeAppearance appearance = null;
        if (row.has("nativeAppearance") && !row.get("nativeAppearance").isJsonNull()) {
            JsonObject nativeData = row.getAsJsonObject("nativeAppearance");
            String itemModel = text(nativeData, "itemModel", 200);
            if (!itemModel.isEmpty() && (!itemModel.matches("[a-z0-9_.-]+:[a-z0-9_.-]+(?:/[a-z0-9_.-]+)*")
                    || java.util.Arrays.stream(itemModel.split(":")[1].split("/")).anyMatch(part -> part.equals(".") || part.equals(".."))))
                throw new IllegalStateException("物品模型标识不正确。");
            Integer customId = null;
            if (nativeData.has("customModelData") && !nativeData.get("customModelData").isJsonNull()) {
                double value = nativeData.get("customModelData").getAsDouble();
                if (!Double.isFinite(value) || value < 0 || value > 16_777_216 || value != Math.rint(value)) throw new IllegalStateException("物品模型编号不正确。");
                customId = (int) value;
            }
            if (!itemModel.isEmpty() || customId != null) appearance = new NativeAppearance(itemModel, customId);
        }
        return appearance;
    }
    private static String text(JsonObject row, String key, int maximum) {
        JsonElement value = row.get(key);
        if (value == null || value.isJsonNull()) return "";
        String text = value.getAsString();
        if (text.length() > maximum) throw new IllegalStateException("档案字段超出范围。");
        return MarketData.text(text);
    }
}
