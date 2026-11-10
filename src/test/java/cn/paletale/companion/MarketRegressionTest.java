package cn.paletale.companion;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static cn.paletale.companion.MarketData.*;

public final class MarketRegressionTest {
    private static int checks;
    public static final String RESPONSE = """
        {"version":1,"page":1,"pageSize":12,"total":1,"hasMore":false,"ads":[{
          "code":"123456","title":"§c测试酒馆","summary":"公开说明","state":"active","tags":["wine"],
          "author":{"name":"店主","mcName":"ShopKeeper"},
          "products":[{"name":"测试酒","description":"长文本\\n第二行","price":"2钻 / 瓶","state":"sold_out"}],
          "locations":[{"name":"主店","coordinates":"12 / 64 / -34","directions":"沿路向北"}],"updatedAt":null}]}
        """;

    public static void main(String[] args) throws Exception {
        YunMonsoonTest.main(new String[0]);
        ArchiveLocksTest.main(new String[0]);
        MonsoonRulesTest.main(new String[0]);
        WorldMergeTest.run();
        FishIndexTest.run();
        CustomDestinationsTest.run();
        ChatAssistRegressionTest.run();
        ChatRulesTest.run();
        UpdateTrustTest.run();
        try{LedgerLogImportTest.run();}catch(Exception e){throw new AssertionError(e);}FavoritePresenceTrackerTest.run();
        SoldOutBatchTest.run();XaeroWaypointsTest.run();ShopTouchPolicyTest.run();SignChangeBatchTest.run();MapAddonTargetsTest.run();ModLogTest.run();HttpCompressionTest.run();NetworkTraceTest.run();try{NetworkRetryTest.run();}catch(Exception failure){throw new AssertionError(failure);}ShopGonePolicyTest.run();ModVersionTest.run();RadialSlotsTest.run();ServerItemCatalogTest.run();NavPlannerRegressionTest.run();WorldCacheRegressionTest.run();try{WorldApiRetryTest.run();}catch(Exception failure){throw new AssertionError(failure);}
        require(ServerAccess.isSimmc("simmc.silkysoftware.com:25565"),"New SIMMC address accepted");
        require(!ServerAccess.isSimmc("simmc.silkysoftware.com.evil.example"),"Suffix lookalike rejected");
        require(PlayerQueryData.balance("Fis3craz","Fis3craz的余额: $2,409,198.09")==2409198.09,"Exact balance with separators");
        require(PlayerQueryData.balance("Other","Fis3craz的余额: $42")==null,"Other player balance ignored");
        require(PlayerQueryData.balance("Fis3craz","<Other> Fis3craz的余额: $42")==null,"Player chat lookalike ignored");
        var observed=PlayerQueryData.menu(java.util.List.of("上次在线: 当前"),"领土（9）",java.util.List.of("- 乌瑞亚王国","- 白渚"));
        require(observed.get("online").getAsBoolean()&&!observed.get("landsComplete").getAsBoolean()&&observed.getAsJsonArray("lands").size()==2,"Menu preserves positive truncated membership evidence");
        var feedback=CommandFeedback.text("商店已&f上线&7。");
        require(feedback.getString().equals("商店已上线。"), "Command feedback strips control markers");
        require(feedback.getSiblings().get(0).getStyle().getColor().getValue()==0xAAAAAA, "Command feedback body is grey");
        require(feedback.getSiblings().get(1).getStyle().getColor().getValue()==0xFFFFFF, "Command feedback emphasis is white");
        Page page = MarketApi.parse(RESPONSE);
        require(page.ads().getFirst().title().equals("测试酒馆"), "Formatting codes are removed");
        require(page.ads().getFirst().products().getFirst().price().equals("2钻 / 瓶"), "Chinese price preserved");
        require(page.ads().getFirst().products().getFirst().description().contains("\n"), "Line breaks preserved");
        require(page.ads().getFirst().updatedAt().isEmpty(), "Null date supported");
        require(stateLabel(page.ads().getFirst().products().getFirst().state()).equals("售罄"), "Sold-out state retained");
        reject(() -> MarketApi.parse(RESPONSE.replace("\"version\":1", "\"version\":2")), "Unknown contract rejected");
        reject(() -> MarketApi.parse(RESPONSE.replace("123456", "../bad")), "Unsafe route rejected");
        reject(() -> MarketApi.parse("<html>unavailable</html>"), "HTML rejected");
        reject(() -> new MarketApi(URI.create("https://evil.example")), "Untrusted host rejected");
        reject(() -> new MarketApi(URI.create("https://preview.paletale.cn@evil.example")), "Userinfo rejected");
        MarketApi api = new MarketApi(MarketApi.PREVIEW);
        require(api.searchUri("酒 & 服务", Category.MODELING, false, 2).getRawQuery().contains("q=%E9%85%92+%26+%E6%9C%8D%E5%8A%A1"), "Query URL encoded");
        require(api.adUri(page.ads().getFirst()).toString().equals("https://preview.paletale.cn/market/123456"), "Website link built from trusted host and code");
        reject(() -> api.searchUri("x".repeat(101), Category.ALL, false, 1), "Long query rejected");
        reject(() -> api.searchUri("", Category.ALL, false, 10001), "Unbounded page rejected");
        require(ServerAccess.isSimmc("play.simmc.cn"), "Canonical SIMMC host accepted");
        require(ServerAccess.isSimmc("PLAY.SIMMC.CN:25565"), "Normalized explicit default port accepted");
        for (String address : new String[]{"other.example", "play.simmc.cn.evil.example", "evil@play.simmc.cn", "play.simmc.cn:25566", "play.simmc.cn/path", "127.0.0.1"})
            require(!ServerAccess.isSimmc(address), "Non-SIMMC connection rejected: " + address);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var publicReads=new java.util.concurrent.atomic.AtomicInteger();var privateReads=new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/api/mcmod/market", exchange -> {
            if(exchange.getRequestURI().getPath().equals("/api/mcmod/market"))publicReads.incrementAndGet();else privateReads.incrementAndGet();
            String query = java.util.Objects.toString(exchange.getRequestURI().getRawQuery(),"");
            String body = query.contains("q=fail") ? "unavailable" : RESPONSE;
            int status = query.contains("q=fail") ? 503 : 200;
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
        });
        server.start();
        try {
            MarketApi local = new MarketApi(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
            require(local.search("酒", Category.ALL, false, 1).get(5, TimeUnit.SECONDS).ads().size() == 1, "Actual HTTP round trip parsed");
            int reads=publicReads.get();
            local.search("酒", Category.ALL, false, 1).get(5,TimeUnit.SECONDS);require(publicReads.get()==reads,"Fresh public advertisements are cached");
            var tags=java.util.EnumSet.noneOf(Category.class);local.search("cached",tags,AdState.ACTIVE,1,42,"random").get(5,TimeUnit.SECONDS);
            require(local.snapshot().peekSearch("cached",tags,AdState.ACTIVE,1,42,"random").ads().size()==1,"API snapshots share site-scoped public cache");
            require(local.peekSearch("other",tags,AdState.ACTIVE,1,42,"random")==null,"Different filters never share cached pages");
            reads=publicReads.get();local.invalidatePublic();local.search("cached",tags,AdState.ACTIVE,1,42,"random").get(5,TimeUnit.SECONDS);require(publicReads.get()==reads+1,"Explicit refresh invalidates public cache");
            local.mine("session-a",1).get(5,TimeUnit.SECONDS);local.mine("session-b",1).get(5,TimeUnit.SECONDS);local.favorites("session-a",1).get(5,TimeUnit.SECONDS);local.favorites("session-a",1).get(5,TimeUnit.SECONDS);
            require(privateReads.get()==4,"Authenticated mine/favorites responses are never shared or cached");
            try {
                java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(local.searchUri("", Category.ALL, false, 1)).build(), HttpBodies.limited(32));
                throw new AssertionError("Oversized response must fail");
            } catch (java.io.IOException expected) { require(true, "HTTP body limit enforced while receiving"); }
            try { local.search("fail", Category.ALL, false, 1).get(5, TimeUnit.SECONDS); throw new AssertionError("503 must fail"); }
            catch (java.util.concurrent.ExecutionException expected) { require(expected.getCause().getMessage().contains("503"), "HTTP failures are not empty results"); }
        } finally { server.stop(0); }
        PrimaryPopulationRegressionTest.run();
        require(HttpBodies.request(URI.create("http://127.0.0.1:3000")).build().version().orElseThrow()==java.net.http.HttpClient.Version.HTTP_1_1,"Local HTTP never attempts unsupported h2c");
        require(HttpBodies.request(URI.create("https://www.paletale.cn")).build().version().isEmpty(),"HTTPS keeps normal protocol negotiation");
        NoticeV2RegressionTest.run();
        ArchiveRegressionTest.run();
        ShopRegressionTest.run();
        LedgerRegressionTest.run();
        LocalConfigRegressionTest.run();
        require(!ServerAccess.recordTransfer("evil.example","198.51.100.8",7777), "An untrusted server cannot authorize another address");
        require(ServerAccess.recordTransfer("play.simmc.cn","198.51.100.8",7777) && ServerAccess.isSimmc("198.51.100.8:7777"), "Actual trusted transfer destinations retain SIMMC login eligibility");
        require(!ServerAccess.isSimmc("198.51.100.8:7778") && !ServerAccess.recordTransfer("play.simmc.cn","http://evil",80), "Trust is bound to the exact transferred host and port");
        CityNavigationRegressionTest.run();
        NpcDiscoveryRegressionTest.run();
        var original=MarketApi.parse(RESPONSE).ads().getFirst();
        var all=new java.util.ArrayList<Ad>();for(int i=0;i<24;i++)all.add(new Ad(String.valueOf(100000+i),original.title(),original.summary(),original.state(),original.tags(),original.author(),original.products(),original.locations(),original.updatedAt(),original.publishedAt(),original.iconPath(),original.views(),original.favorites()));
        for(int visible:new int[]{1,4,5,6,10,18,24}){
          var seen=new java.util.ArrayList<String>();for(int visiblePage=1;visiblePage<=Math.ceil(24.0/visible);visiblePage++){
            int batch=MarketPaging.firstBatch(visiblePage,visible);var first=new Page(batch,12,24,batch==1,all.subList((batch-1)*12,batch*12));
            var window=MarketPaging.window(visiblePage,visible,first,MarketPaging.needsNext(visiblePage,visible,first)?new Page(2,12,24,false,all.subList(12,24)):null);
            if(window.ads().size()!=Math.min(visible,24-(visiblePage-1)*visible))throw new AssertionError("Short intermediate market visiblePage");seen.addAll(window.ads().stream().map(Ad::code).toList());checks++;
          }if(!seen.equals(all.stream().map(Ad::code).toList()))throw new AssertionError("Missing/duplicated ads across batches");checks++;
        }
        System.out.println("PaleTale market regression checks passed: " + checks);
    }

    private static void require(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    private static void reject(Runnable action, String label) {
        try { action.run(); } catch (RuntimeException expected) { checks++; return; }
        throw new AssertionError(label);
    }
}
