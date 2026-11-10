package cn.paletale.companion;

import com.sun.net.httpserver.HttpServer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public final class MarketClientGameTest implements FabricClientGameTest {
    static final String AD = """
        {"code":"123456","title":"苍白测试酒馆 · 商店与服务联动","summary":"这里的内容仅用于客户端测试。",
        "state":"active","tags":["wine"],"iconPath":"/api/mcmod/market/123456/icon",
        "author":{"name":"测试店主","mcName":"ShopKeeper","avatarPath":"/api/players/11111111222233334444555555555555/avatar"},
        "products":[{"name":"测试酒","description":"测试商品说明，支持中文长文和换行。","price":"2钻 / 瓶","state":"available"},
        {"name":"模型制作服务","description":"这是用于验证长列表阅读的服务说明。","price":"面议","state":"accepting"}],
        "locations":[{"name":"主店","coordinates":"12 / 64 / -34","directions":"沿主路向北"}],"updatedAt":"2026-10-16T00:00:00Z"}
        """;

    private static void clickCategory(ClientGameTestContext context,String icon){context.runOnClient(client->{for(int attempt=0;attempt<8;attempt++){var found=client.screen.children().stream().filter(c->c instanceof MarketTheme.Action action&&icon.equals(WorldClientGameTest.field(action,"icon"))).map(c->(net.minecraft.client.gui.components.Button)c).findFirst();if(found.isPresent()){found.get().onPress(new net.minecraft.client.input.KeyEvent(257,0,0));return;}var screen=(WindowScreen)client.screen;screen.mouseScrolled(screen.winX+20*screen.windowScale,screen.winY+180*screen.windowScale,0,-1);}throw new AssertionError("Category not reachable: "+icon);});}

    @Override
    public void runTest(ClientGameTestContext context) {
        if(Boolean.getBoolean("paletale.test.mentionOnly")){MentionSoundClientTest.run(context);return;}
        if(Boolean.getBoolean("paletale.test.favoritePresenceOnly")){boolean sound=ModSettings.favoriteSound();try{ModSettings.setFavoriteSound(false);FavoritePresenceHttpTest.run();}catch(Exception e){throw new AssertionError(e);}finally{ModSettings.setFavoriteSound(sound);}return;}
        if(Boolean.getBoolean("paletale.test.privateChatOnly")){try{PrivateChatClientTest.run(context);}catch(Exception e){throw new AssertionError(e);}return;}
        if(Boolean.getBoolean("paletale.test.marketCardsOnly")){try(var world=context.worldBuilder().create()){MarketPagingClientTest.run(context);context.runOnClient(c->c.setScreen(new net.minecraft.client.gui.screens.TitleScreen()));}catch(Exception e){throw new AssertionError(e);}return;}
        if (Boolean.getBoolean("paletale.test.favoriteUiOnly")) { try(var world=context.worldBuilder().create()){WorldFavoritesClientTest.run(context);WorldInlineFavoritesClientTest.run(context);context.runOnClient(c->c.setScreen(new net.minecraft.client.gui.screens.TitleScreen()));}catch(Exception e){throw new AssertionError(e);}return; }
        if (Boolean.getBoolean("paletale.test.feature102")) { Feature102ClientTest.run(context);return; }
        if (Boolean.getBoolean("paletale.test.beta4")) { ChatAssistClientTest.run(context);try{ChatDirectoryClientTest.run();LandMenuClientTest.run(context);CountryClientTest.run(context);try(var world=context.worldBuilder().create()){WorldSearchClientTest.run(context);}if(Boolean.getBoolean("paletale.test.chatLive"))ChatDirectoryClientTest.runLive();}catch(Exception e){throw new AssertionError(e);}return; }
        if (Boolean.getBoolean("paletale.test.countryOnly")) { try{CountryClientTest.run(context);}catch(Exception e){throw new AssertionError(e);}return; }
        if (Boolean.getBoolean("paletale.test.worldSearchOnly")) { try(var world=context.worldBuilder().create()){WorldSearchClientTest.run(context);context.runOnClient(c->c.setScreen(new net.minecraft.client.gui.screens.TitleScreen()));}catch(Exception e){throw new AssertionError(e);}return; }
        if (Boolean.getBoolean("paletale.test.landOnly")) { LandMenuClientTest.run(context); return; }
        if (Boolean.getBoolean("paletale.test.chatOnly")) { ChatAssistClientTest.run(context); try{ChatDirectoryClientTest.run();if(Boolean.getBoolean("paletale.test.chatLive"))ChatDirectoryClientTest.runLive();}catch(Exception error){throw new AssertionError(error);} return; }
        if (Boolean.getBoolean("paletale.test.homeOnly")) { HomeFontClientTest.run(context); return; }
        if (Boolean.getBoolean("paletale.test.guideOnly")) { GuideClientGameTest.run(context); return; }
        if (Boolean.getBoolean("paletale.test.mapAddon")) { MapAddonClientGameTest.run(context); return; }
        if (Boolean.getBoolean("paletale.test.xaero")) { XaeroClientGameTest.run(context); return; }
        if (Boolean.getBoolean("paletale.test.signOnly")) { SignUpdateClientGameTest.run(context); return; }
        if (Boolean.getBoolean("paletale.test.worldOnly")) { try{ItemMappingClientTest.run(context);}catch(Exception e){throw new AssertionError(e);}try (var world=context.worldBuilder().create()) { WorldInteractionClientTest.run(context);WorldSearchClientTest.run(context);WorldScopePagingClientTest.run(context);WorldFavoritesClientTest.run(context);WorldInlineFavoritesClientTest.run(context);WorldClientGameTest.run(context);WorldDensityClientTest.run(context);WorldSwitchClientTest.run(context);MarketPagingClientTest.run(context);context.runOnClient(c->c.setScreen(new net.minecraft.client.gui.screens.TitleScreen())); } catch(Exception e){throw new AssertionError(e);}return; }
        if (Boolean.getBoolean("paletale.test.noticesOnly")) { try (var world=context.worldBuilder().create()) { NoticeClientGameTest.run(context);context.runOnClient(c->c.setScreen(new net.minecraft.client.gui.screens.TitleScreen())); } catch(Exception e){throw new AssertionError(e);}return; }
        if (Boolean.getBoolean("paletale.test.legacyUiComparison")) { try (var world=context.worldBuilder().create()) { LegacyUiComparisonTest.run(context);context.runOnClient(c->c.setScreen(new net.minecraft.client.gui.screens.TitleScreen())); } catch(Exception e){throw new AssertionError(e);}return; }
        if (Boolean.getBoolean("paletale.test.radialPerf")) { try (var world = context.worldBuilder().create()) { RadialPerfClientTest.run(context); } return; }
        if (Boolean.getBoolean("paletale.test.archiveOnly")) { try (var world = context.worldBuilder().create()) { ArchiveClientGameTest.run(context); context.runOnClient(c -> c.setScreen(new net.minecraft.client.gui.screens.TitleScreen())); } catch (Exception e) { throw new AssertionError(e); } return; }
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            var executor = Executors.newVirtualThreadPerTaskExecutor();
            server.setExecutor(executor);
            server.createContext("/api/mcmod/market", exchange -> {
                if (exchange.getRequestURI().getPath().endsWith("/icon") || exchange.getRequestURI().getPath().endsWith("/cover")) {
                    byte[] bytes;
                    try (var resource = getClass().getResourceAsStream("/assets/paletale/textures/gui/wine.png")) { bytes = resource.readAllBytes(); }
                    exchange.getResponseHeaders().set("Content-Type", "image/png");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
                    return;
                }
                String query = URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
                if (query.contains("q=slow")) {
                    try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
                int status = query.contains("q=fail") ? 503 : 200;
                boolean secondPage = query.contains("page=2");
                boolean category = query.contains("tag=wine");
                String ads = secondPage || category ? AD : String.join(",", java.util.Collections.nCopies(12, AD));
                String body = "{\"version\":1,\"page\":" + (secondPage ? 2 : 1) + ",\"pageSize\":12,\"total\":"
                        + (category ? 1 : 13) + ",\"hasMore\":" + (!secondPage && !category) + ",\"ads\":[" + ads + "]}";
                if (query.contains("q=无结果")) body = "{\"version\":1,\"page\":1,\"pageSize\":12,\"total\":0,\"hasMore\":false,\"ads\":[]}";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
            });
            server.createContext("/api/players/", exchange -> {
                byte[] bytes;
                try (var resource = getClass().getResourceAsStream("/assets/paletale/textures/gui/person.png")) { bytes = resource.readAllBytes(); }
                exchange.getResponseHeaders().set("Content-Type", "image/png"); exchange.sendResponseHeaders(200, bytes.length);
                try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
            });
            server.start();
            try (var world = context.worldBuilder().create()) {
                // Exercise the actual default key, including vanilla mapping conflict checks.
                context.runOnClient(client -> {
                    var binding = net.minecraft.client.KeyMapping.get("key.paletale.market");
                    // Debug mappings such as F3+H share a base key but require the debug modifier.
                    require(java.util.Arrays.stream(client.options.keyMappings).noneMatch(other -> other != binding
                                    && other.getCategory() != net.minecraft.client.KeyMapping.Category.DEBUG && other.same(binding)),
                            "Default shortcut does not conflict with ordinary vanilla controls");
                });
                context.runOnClient(client->{ModSettings.setGuideShown(true);client.setScreen(null);});context.waitTicks(10);
                context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_H);
                context.waitForScreen(HomeScreen.class);
                context.runOnClient(client -> client.setScreen(null));
                // Verify the installed client command really opens the registered screen.
                context.runOnClient(client -> {
                    client.setScreen(new ChatScreen("", false));
                    ((ChatScreen) client.screen).handleChatInput("/paletale", false);
                    client.setScreen(null);
                });
                context.waitForScreen(MarketScreen.class);
                URI local = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
                context.runOnClient(client -> client.setScreen(new MarketScreen(null, new MarketApi(local))));
                await(context);
                context.runOnClient(client -> require(((MarketScreen) client.screen).result().total() == 13, "HTTP result reaches actual screen"));
                context.waitFor(client -> MarketImages.INSTANCE.loadedCount() >= 2, 200);
                context.runOnClient(client -> {
                    require(!PaleTaleClient.ACCOUNT.canLogin(client), "Singleplayer cannot claim to be SIMMC");
                    var button = client.screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                            .map(net.minecraft.client.gui.components.Button.class::cast).filter(b -> b.getMessage().getString().equals("正版账号登录")).findFirst().orElseThrow();
                    require(!button.active, "Login button disabled outside SIMMC");
                });
                context.takeScreenshot("paletale-market-list");
                // The native viewport can show fewer rows than the server's 12-row page.
                for (int i = 0; i < 12; i++) {
                    boolean[] reached = {false};
                    context.runOnClient(client -> reached[0] = ((MarketScreen) client.screen).result().page() == 2);
                    if (reached[0]) break;
                    context.clickScreenButton("下一页");
                    await(context);
                }
                await(context);
                context.runOnClient(client -> require(((MarketScreen) client.screen).result().page() == 2, "Server page navigation works"));
                context.clickScreenButton("上一页");
                await(context);
                clickCategory(context,"wine");
                await(context);
                context.runOnClient(client -> require(((MarketScreen) client.screen).result().total() == 1, "Category control reaches HTTP query"));
                clickCategory(context,"dish");
                await(context);
                context.clickScreenButton("状态：有效");
                await(context);
                context.runOnClient(client -> client.screen.children().stream()
                        .filter(net.minecraft.client.gui.components.Button.class::isInstance)
                        .map(net.minecraft.client.gui.components.Button.class::cast)
                        .filter(button -> button.getMessage().getString().contains("苍白测试酒馆"))
                        .findFirst().orElseThrow().onPress(new net.minecraft.client.input.KeyEvent(257, 0, 0)));
                context.waitForScreen(MarketDetailScreen.class);
                context.takeScreenshot("paletale-market-detail");
                context.clickScreenButton("复制地址");
                context.runOnClient(client -> require(client.keyboardHandler.getClipboard().contains("12 / 64 / -34"), "Address copied to actual clipboard"));
                context.runOnClient(client -> client.screen.mouseScrolled(0, 0, 0, -1));
                context.takeScreenshot("paletale-market-detail-scrolled");
                context.clickScreenButton("返回列表");
                context.waitForScreen(MarketScreen.class);
                context.runOnClient(client -> {
                    client.getWindow().setWindowed(640, 480);
                    edit(client.screen).setValue("测试酒");
                });
                context.waitFor(client -> client.getWindow().getGuiScaledWidth() == 320, 100);
                context.clickScreenButton("搜");
                await(context);
                context.runOnClient(client -> require(edit(client.screen).getValue().equals("测试酒"), "Chinese input survives request and resize"));
                context.takeScreenshot("paletale-market-small");
                search(context, "无结果");
                context.runOnClient(client -> require(((MarketScreen) client.screen).result().total() == 0, "Empty search is distinct from failure"));
                context.takeScreenshot("paletale-market-empty");
                search(context, "fail");
                context.runOnClient(client -> require(!((MarketScreen) client.screen).error().isEmpty(), "Service error visible"));
                context.takeScreenshot("paletale-market-error");
                context.runOnClient(client -> edit(client.screen).setValue("slow"));
                context.clickScreenButton("搜");
                search(context, "无结果");
                long settled = System.nanoTime() + 1_600_000_000L;
                context.waitFor(client -> System.nanoTime() > settled, 200);
                context.runOnClient(client -> require(((MarketScreen) client.screen).result().total() == 0, "New search wins over old request"));
                search(context, "测试酒");
                context.takeScreenshot("paletale-market-recovered");
                if (Boolean.getBoolean("paletale.test.live")) {
                    context.runOnClient(client -> {
                        client.getWindow().setWindowed(1600, 1000);
                        client.options.guiScale().set(2);
                        client.resizeDisplay();
                        MarketImages.INSTANCE.clear();
                        client.setScreen(new MarketScreen(null, new MarketApi(MarketApi.PREVIEW)));
                    });
                    await(context);
                    context.runOnClient(client -> {
                        MarketScreen screen = (MarketScreen) client.screen;
                        require(screen.error().isEmpty() && screen.result() != null && screen.result().total() > 0,
                                "Live preview HTTPS -> database -> native UI has public results");
                        System.out.println("PALETALE_LIVE_MARKET total=" + screen.result().total()
                                + " first=" + screen.result().ads().getFirst().title());
                    });
                    long expectedAssets = context.computeOnClient(client -> {
                        long cards = client.screen.children().stream().filter(MarketCardButton.class::isInstance).count();
                        return ((MarketScreen) client.screen).result().ads().stream().limit(cards)
                                .flatMap(ad -> java.util.stream.Stream.of(ad.coverPath(), ad.author().avatarPath()))
                                .filter(path -> path != null && !path.isBlank()).distinct().count();
                    });
                    context.waitFor(client -> MarketImages.INSTANCE.loadedCount() >= Math.max(1, expectedAssets), 400);
                    context.getInput().setCursorPos(0, 0);
                    context.takeScreenshot("paletale-live-market-list");
                    context.runOnClient(client -> {
                        var ads = ((MarketScreen) client.screen).result().ads();
                        var sample = ads.stream().filter(ad -> ad.title().length() > 4 && !ad.products().isEmpty())
                                .findFirst().orElse(ads.getFirst());
                        edit(client.screen).setValue(sample.title());
                    });
                    context.clickScreenButton("搜");
                    await(context);
                    context.runOnClient(client -> require(((MarketScreen) client.screen).result().total() > 0, "Real Chinese shop search returns data"));
                    context.getInput().setCursorPos(0, 0);
                    context.takeScreenshot("paletale-live-market-search");
                    context.runOnClient(client -> client.screen.children().stream()
                            .filter(net.minecraft.client.gui.components.Button.class::isInstance)
                            .map(net.minecraft.client.gui.components.Button.class::cast)
                            .filter(button -> button.getMessage().getString().startsWith("查看广告："))
                            .findFirst().orElseThrow().onPress(new net.minecraft.client.input.KeyEvent(257, 0, 0)));
                    context.waitForScreen(MarketDetailScreen.class);
                    context.takeScreenshot("paletale-live-market-detail");
                }
                ArchiveClientGameTest.run(context);
                context.runOnClient(client -> client.setScreen(null));
            } finally { server.stop(0); executor.close(); }
        } catch (Exception e) { throw new AssertionError("PaleTale client flow failed", e); }
    }

    private static EditBox edit(net.minecraft.client.gui.screens.Screen screen) {
        return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
    }
    private static void search(ClientGameTestContext context, String query) {
        context.runOnClient(client -> edit(client.screen).setValue(query));
        context.clickScreenButton("搜");
        await(context);
    }
    private static void await(ClientGameTestContext context) {
        context.waitFor(client -> client.screen instanceof MarketScreen screen && !screen.isLoading(), 600);
    }
    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
