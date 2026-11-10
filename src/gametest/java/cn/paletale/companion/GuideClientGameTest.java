package cn.paletale.companion;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** The welcome guide (-PguideOnly): screenshots at a common and a small window size, and closing it marks it as seen. No production account or data. */
final class GuideClientGameTest {
    private GuideClientGameTest() {}
    private static void require(boolean ok, String label) { if (!ok) throw new AssertionError(label); }

    static void run(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> { PaleTaleClient.useSite(SiteConfig.LOCAL); ModSettings.setGuideShown(false); ModSettings.setUiScale(1); });
            for (int[] size : new int[][] { { 1920, 1080, 3 }, { 1280, 720, 2 }, { 854, 480, 2 } }) {
                context.runOnClient(client -> { client.getWindow().setWindowed(size[0], size[1]); client.options.guiScale().set(size[2]); client.resizeDisplay(); client.setScreen(new GuideScreen(null, "H")); });
                context.waitTicks(10);
                context.takeScreenshot("guide-" + size[0] + "x" + size[1]);
            }
            context.runOnClient(client -> { client.getWindow().setWindowed(1920, 1080); client.options.guiScale().set(3); client.resizeDisplay(); client.setScreen(new PaleSettingsScreen(null)); });
            context.waitTicks(10);
            context.takeScreenshot("settings-1920x1080");
            context.clickScreenButton("H");
            context.waitTicks(3);
            context.takeScreenshot("settings-listening");
            context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_J);
            context.waitTicks(3);
            require(context.computeOnClient(client -> PaleTaleClient.OPEN.getTranslatedKeyMessage().getString().equals("J")), "A key chosen in the settings is applied");
            context.runOnClient(client -> { PaleTaleClient.OPEN.setKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_H)); net.minecraft.client.KeyMapping.resetMapping(); client.setScreen(new GuideScreen(null, "H")); });
            context.waitTicks(5);
            context.clickScreenButton("知道了");
            context.waitTicks(5);
            context.runOnClient(client -> { ModSettings.markArchiveOpened(); client.setScreen(null); });
            require(context.computeOnClient(client -> ModSettings.archiveOpened()), "Opening the main window is remembered");
            context.runOnClient(client -> { PaleTaleClient.useSite(SiteConfig.MAIN); require(PaleTaleClient.nextSite().equals(SiteConfig.BACKUP), "Main switches to backup"); PaleTaleClient.useSite(SiteConfig.BACKUP); require(PaleTaleClient.nextSite().equals(SiteConfig.MAIN), "Backup switches back to main, never to the local test site"); PaleTaleClient.useSite(SiteConfig.LOCAL); });
            require(context.computeOnClient(client -> client.screen == null && ModSettings.guideShown()), "Closing the guide marks it as seen");
            for (int[] size : new int[][] { { 1920, 1080, 3 }, { 1280, 720, 2 } }) {
                context.runOnClient(client -> { client.getWindow().setWindowed(size[0], size[1]); client.options.guiScale().set(size[2]); client.resizeDisplay(); client.setScreen(ArchiveNavigation.open(null, PaleTaleClient.MARKET, ArchiveNavigation.Tab.PLAYERS)); });
                context.waitTicks(30);
                context.takeScreenshot("nav-" + size[0]);
            }
            // Cut names in the shop table show in full on hover.
            var table = new WorldShopTable(10, 60, 700, true, "");
            var buttons = new java.util.ArrayList<net.minecraft.client.gui.components.Button>();
            var row = com.google.gson.JsonParser.parseString("{\"itemName\":\"圆石\",\"owner\":\"一个非常非常长的玩家名字ABCDEFGH\",\"mode\":\"sell\",\"sellPrice\":1,\"quantity\":2,\"x\":1,\"y\":2,\"z\":3,\"lands\":[{\"id\":\"a\",\"name\":\"一个特别特别长的领地名称用来测试\",\"nation\":\"n\"}],\"routes\":[{\"key\":\"r\",\"name\":\"某传送点\"}]}").getAsJsonObject();
            context.runOnClient(client -> table.controls(buttons::add, PaleTaleClient.MARKET, java.util.List.of(row), 0, 1, r -> {}, r -> {}, r -> {}, l -> {}, r -> {}));
            require(buttons.stream().filter(b -> b.getMessage().getString().contains("特别长") || b.getMessage().getString().contains("非常长")).count() == 2, "Long owner and land buttons exist");
            // Ad products grouped by the owner's categories: coloured bands, the first one folded.
            String products = java.util.stream.IntStream.range(0, 8).mapToObj(i -> "{\"name\":\"商品" + i + "\",\"description\":\"\",\"price\":\"" + (i + 1) * 100 + " 云币\",\"state\":\"active\",\"tags\":[\"已认证\"],\"categories\":[" + (i < 3 ? "\"玩偶头\"" : i < 5 ? "\"地图画\",\"大幅\"" : i < 6 ? "\"地图画\",\"小幅\"" : "") + "]}").collect(java.util.stream.Collectors.joining(","));
            var grouped = MarketApi.parse("{\"version\":1,\"page\":1,\"pageSize\":12,\"total\":1,\"hasMore\":false,\"ads\":[{\"code\":\"123456\",\"title\":\"分类测试\",\"summary\":\"简介\",\"state\":\"active\",\"tags\":[\"map_art\"],\"author\":{\"name\":\"店主\",\"mcName\":\"店主\",\"avatarPath\":\"\",\"playerId\":1},\"products\":[" + products + "],\"locations\":[],\"updatedAt\":\"\",\"publishedAt\":\"\",\"iconPath\":\"\",\"views\":0,\"favorites\":0,\"body\":\"正文\",\"contact\":{}}]}").ads().get(0);
            context.runOnClient(client -> client.setScreen(new MarketDetailScreen(null, null, PaleTaleClient.MARKET, grouped)));
            context.waitTicks(30);
            context.takeScreenshot("ad-groups");
            context.getInput().setCursorPos(125, 292);
            context.waitTicks(5);
            context.takeScreenshot("ad-hover-author");
            context.getInput().setCursorPos(800, 400);
            context.getInput().scroll(-6);
            context.waitTicks(5);
            context.takeScreenshot("ad-groups-scrolled");
            // The ad detail (needs the local test site on 127.0.0.1:3000): full text on top, summary, contact and category icons on the left, product labels.
            var ad = PaleTaleClient.MARKET.detail("926131").join();
            context.runOnClient(client -> { client.getWindow().setWindowed(1920, 1080); client.options.guiScale().set(3); client.resizeDisplay(); client.setScreen(new MarketDetailScreen(null, null, PaleTaleClient.MARKET, ad)); });
            context.waitTicks(60);
            context.takeScreenshot("ad-detail");
                        System.out.println("PaleTale guide: rendered at three sizes and marked as seen when closed");
        }
    }
}
