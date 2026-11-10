package cn.paletale.companion;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

final class ArchiveRegressionTest {
    private static int checks;
    private static final String ROW = """
        {"kind":"items","id":1,"name":"樱花之酒","subtitle":"拥有者 · TestPlayer","description":"公开说明",
        "category":"wine","badge":"3D 酒","hasModel":true,"imagePath":"/api/mcmod/items/1/image",
        "owner":{"playerId":12,"name":"玩家别名","mcName":"TestPlayer","verified":true,"avatarPath":null}}
        """;
    private static final String PAGE = "{\"version\":1,\"kind\":\"items\",\"page\":1,\"pageSize\":24,\"total\":1,\"hasMore\":false,\"entries\":[" + ROW + "]}";
    static void run() throws Exception {
        var page = ArchiveApi.page(JsonParser.parseString(PAGE).getAsJsonObject());
        require(page.entries().size() == 1 && page.total() == 1, "Archive contract parsed");
        var row = page.entries().getFirst();
        require(row.owner().playerId() == 12 && row.owner().verified(), "Formal ownership preserved");
        require(row.imagePath().equals("/api/mcmod/items/1/image"), "Only site image path returned");
        var api = new ArchiveApi(new MarketApi(MarketApi.PREVIEW));
        require(api.website(row).toString().equals("https://preview.paletale.cn/items/1"), "Website URL built from canonical kind and ID");
        reject(() -> ArchiveApi.page(JsonParser.parseString(PAGE.replace("\"version\":1", "\"version\":2")).getAsJsonObject()), "Unknown archive protocol rejected");
        reject(() -> ArchiveApi.page(JsonParser.parseString(PAGE.replace("\"id\":1,", "\"id\":4294967297,")).getAsJsonObject()), "Integer overflow cannot select a different record");
        reject(() -> ArchiveApi.page(JsonParser.parseString(PAGE.replace("\"kind\":\"items\"", "\"kind\":\"private\"")).getAsJsonObject()), "Unknown archive kind rejected");
        var nativePage = JsonParser.parseString(PAGE).getAsJsonObject();
        var nativeRow = nativePage.getAsJsonArray("entries").get(0).getAsJsonObject();
        nativeRow.add("nativeAppearance", JsonParser.parseString("{\"itemModel\":\"smc:food/test\",\"customModelData\":30009}"));
        require(ArchiveApi.page(nativePage).entries().getFirst().nativeAppearance().customModelData() == 30009, "Both native item components preserved");
        for (String invalid : new String[]{"{\"itemModel\":\"smc:../private\"}", "{\"customModelData\":16777217}", "{\"customModelData\":1.5}"}) {
            nativeRow.add("nativeAppearance", JsonParser.parseString(invalid));
            reject(() -> ArchiveApi.page(nativePage), "Invalid model component rejected");
        }
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/mcmod/items", exchange -> {
            boolean detail = exchange.getRequestURI().getPath().endsWith("/1");
            String body = detail ? "{\"version\":1,\"entry\":" + ROW + ",\"ownedTotal\":0,\"ownedItems\":[],\"adsTotal\":0,\"ads\":[]}" : PAGE;
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type", "application/json");exchange.sendResponseHeaders(200, bytes.length);
            try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
        });server.start();
        try {
            var local = new ArchiveApi(new MarketApi(URI.create("http://127.0.0.1:" + server.getAddress().getPort())));
            require(local.search(ArchiveData.Kind.ITEMS, "樱花 & 酒", "wine", "3d", 12, "id", 1).get(5, TimeUnit.SECONDS).total() == 1, "Actual HTTP list query works");
            require(local.detail(ArchiveData.Kind.ITEMS, 1).get(5, TimeUnit.SECONDS).entry().id() == 1, "Actual HTTP independent detail works");
        } finally { server.stop(0); }
        System.out.println("PaleTale archive regression checks passed: " + checks);
    }
    private static void require(boolean value, String label) { checks++;if (!value) throw new AssertionError(label); }
    private static void reject(Runnable call, String label) { try { call.run(); } catch (RuntimeException expected) { checks++;return; }throw new AssertionError(label); }
}
