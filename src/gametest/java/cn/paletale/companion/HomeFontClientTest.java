package cn.paletale.companion;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** The start page, the bundled UI font and its switch in the settings (-PhomeOnly). Screenshots show both fonts; no production account or data. */
final class HomeFontClientTest {
    private HomeFontClientTest() {}
    /** A bare window with lines of symbols: the bundled font has none of them, so each must come from the game's own font. */
    private static final class SymbolScreen extends WindowScreen {
        SymbolScreen() { super(net.minecraft.network.chat.Component.literal("symbols")); }
        @Override protected void init() { fitWindow(); }
        @Override protected void renderWindow(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float delta) {
            super.renderWindow(g, mouseX, mouseY, delta);
            String[] lines = { "吉凶未卜 〖吉凶〗「引号」《书名》（括号）…… ——", "★ ☆ ♥ ♪ ☺ ✔ ✘ ⚔ ❤ ✦ ✧ ❖", "→ ← ↑ ↓ ⇒ ▲ ▼ ◆ ◇ ● ○ ■ □ ▯", "ABC abc 0123456789 !?@#$%&*()", "圆石 酒 菜 地图画 SMC世界 账本", "排序：浏览量 浏 览 瑄 琬 翀 燚 淼 垚", "♻ 刷新 ⇄ 随机" };
            for (int i = 0; i < lines.length; i++) g.drawString(font, lines[i], 12, 12 + i * 14, 0xFF343735, false);
        }
        @Override protected void windowBackground(net.minecraft.client.gui.GuiGraphics g, int x, int y, float d) { g.fill(0, 0, width, height, 0xFFF5F4F0); }
    }

    private static Object fontOf(Object screen) {
        try { var field = net.minecraft.client.gui.screens.Screen.class.getDeclaredField("font"); field.setAccessible(true); return field.get(screen); } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void staticField(Class<?> type, String name, Object value) {
        try { var field = type.getDeclaredField(name); field.setAccessible(true); field.set(null, value); } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void require(boolean ok, String label) { if (!ok) throw new AssertionError(label); }

    static void run(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> { PaleTaleClient.useSite(SiteConfig.LOCAL); ModSettings.setGuideShown(true); ModSettings.setUiScale(1); client.getWindow().setWindowed(1920, 1080); client.options.guiScale().set(3); client.resizeDisplay(); });
            // H after a client start opens the start page; later opens go back to the last tab.
            context.runOnClient(client -> { ArchiveNavigation.resetHomeForTest(); client.setScreen(ArchiveNavigation.openLast(null, PaleTaleClient.MARKET)); });
            require(context.computeOnClient(client -> client.screen instanceof HomeScreen), "The first open after a start is the start page");
            context.runOnClient(client -> ModSettings.setUiFont("serif"));
            // A few plaza ads at the bottom (fixed here: the test site has none).
            context.runOnClient(client -> staticField(HomeScreen.class, "featuredAt", System.currentTimeMillis()));
            context.runOnClient(client -> staticField(HomeScreen.class, "featured", MarketApi.parse("{\"version\":1,\"page\":1,\"pageSize\":12,\"total\":6,\"hasMore\":false,\"ads\":[{\"code\":\"123450\",\"title\":\"苍白测试酒馆 · 商店与服务\",\"summary\":\"这里的内容仅用于客户端测试。\",\"state\":\"active\",\"tags\":[\"map_art\"],\"author\":{\"name\":\"店主\",\"mcName\":\"ShopKeeper\",\"avatarPath\":\"\",\"playerId\":1},\"products\":[],\"locations\":[],\"updatedAt\":\"\",\"publishedAt\":\"\",\"iconPath\":\"\",\"views\":0,\"favorites\":0,\"body\":\"正文\",\"contact\":{}},{\"code\":\"123451\",\"title\":\"圆石批发店\",\"summary\":\"长期收购并出售各类建材\",\"state\":\"active\",\"tags\":[\"map_art\"],\"author\":{\"name\":\"店主\",\"mcName\":\"ShopKeeper\",\"avatarPath\":\"\",\"playerId\":1},\"products\":[],\"locations\":[],\"updatedAt\":\"\",\"publishedAt\":\"\",\"iconPath\":\"\",\"views\":0,\"favorites\":0,\"body\":\"正文\",\"contact\":{}},{\"code\":\"123452\",\"title\":\"模型制作\",\"summary\":\"接各类建筑模型委托\",\"state\":\"active\",\"tags\":[\"map_art\"],\"author\":{\"name\":\"店主\",\"mcName\":\"ShopKeeper\",\"avatarPath\":\"\",\"playerId\":1},\"products\":[],\"locations\":[],\"updatedAt\":\"\",\"publishedAt\":\"\",\"iconPath\":\"\",\"views\":0,\"favorites\":0,\"body\":\"正文\",\"contact\":{}},{\"code\":\"123453\",\"title\":\"收购钻石\",\"summary\":\"高价收钻石和绿宝石\",\"state\":\"active\",\"tags\":[\"map_art\"],\"author\":{\"name\":\"店主\",\"mcName\":\"ShopKeeper\",\"avatarPath\":\"\",\"playerId\":1},\"products\":[],\"locations\":[],\"updatedAt\":\"\",\"publishedAt\":\"\",\"iconPath\":\"\",\"views\":0,\"favorites\":0,\"body\":\"正文\",\"contact\":{}},{\"code\":\"123454\",\"title\":\"星河酒馆\",\"summary\":\"自酿果酒，附带效果\",\"state\":\"active\",\"tags\":[\"map_art\"],\"author\":{\"name\":\"店主\",\"mcName\":\"ShopKeeper\",\"avatarPath\":\"\",\"playerId\":1},\"products\":[],\"locations\":[],\"updatedAt\":\"\",\"publishedAt\":\"\",\"iconPath\":\"\",\"views\":0,\"favorites\":0,\"body\":\"正文\",\"contact\":{}},{\"code\":\"123455\",\"title\":\"地图画工坊\",\"summary\":\"噪点地图画制作与投影\",\"state\":\"active\",\"tags\":[\"map_art\"],\"author\":{\"name\":\"店主\",\"mcName\":\"ShopKeeper\",\"avatarPath\":\"\",\"playerId\":1},\"products\":[],\"locations\":[],\"updatedAt\":\"\",\"publishedAt\":\"\",\"iconPath\":\"\",\"views\":0,\"favorites\":0,\"body\":\"正文\",\"contact\":{}}]}").ads()));
            context.runOnClient(client -> client.setScreen(new HomeScreen(null, PaleTaleClient.MARKET)));
            context.waitTicks(20);
            require(context.computeOnClient(client -> client.screen instanceof HomeScreen home && fontOf(home) != client.font), "The window uses the bundled font");
            context.takeScreenshot("home-pale-font");
            // A long ad title shows its full name in a tooltip right beside the pointer (the window is scaled and shifted on screen).
            context.getInput().setCursorPos(300, 805);
            context.waitTicks(40);
            context.takeScreenshot("home-title-tooltip");
            context.getInput().setCursorPos(5, 5);
            // Signed in: the account button at the top right is just the player's head (replaced here by a fixed path; no network).
            context.runOnClient(client -> MarketTheme.Action.accountAvatar = () -> "/api/players/00000000000000000000000000000001/avatar");
            context.runOnClient(client -> client.setScreen(new HomeScreen(null, PaleTaleClient.MARKET)));
            context.waitTicks(15);
            context.takeScreenshot("home-signed-in");
            context.runOnClient(client -> client.setScreen(ArchiveNavigation.open(null, PaleTaleClient.MARKET, ArchiveNavigation.Tab.ITEMS)));
            context.waitTicks(20);
            context.takeScreenshot("archive-signed-in");
            context.runOnClient(client -> MarketTheme.Action.accountAvatar = () -> ArchiveNavigation.avatarOf(PaleTaleClient.ACCOUNT.citizen()));
            context.runOnClient(client -> client.setScreen(new SymbolScreen()));
            context.waitTicks(10);
            context.takeScreenshot("symbols-serif");
            context.runOnClient(client -> ModSettings.setUiFont("serif"));
            context.runOnClient(client -> client.setScreen(ArchiveNavigation.openLast(null, PaleTaleClient.MARKET)));
            require(context.computeOnClient(client -> !(client.screen instanceof HomeScreen)), "The second open goes back to the last tab");
            context.waitTicks(30);
            context.takeScreenshot("archive-pale-font");
            // The plaza at the common 1080p / GUI 3 size: two rows of small cover cards fit (the ads come from whichever site is configured).
            context.runOnClient(client -> client.setScreen(new MarketScreen(null, PaleTaleClient.MARKET)));
            context.waitTicks(80);
            context.takeScreenshot("plaza-1080p");
            // The same plaza at UI 75%: the window is larger in layout units, so three rows fit.
            context.runOnClient(client -> { ModSettings.setUiScale(.75); client.setScreen(new MarketScreen(null, PaleTaleClient.MARKET)); });
            context.waitTicks(80);
            context.takeScreenshot("plaza-ui75");
            context.runOnClient(client -> { ModSettings.setUiScale(1); client.setScreen(new MarketScreen(null, PaleTaleClient.MARKET)); });
            context.waitTicks(60);
            // A long summary of a second-row card: the tooltip sits beside the pointer (clamped inside the window), not far above it.
            context.getInput().setCursorPos(820, 868);
            context.waitTicks(40);
            context.takeScreenshot("plaza-tooltip-second-row");
            context.getInput().setCursorPos(5, 5);
            // The ad page: the navigation button says where it goes.
            context.runOnClient(client -> client.setScreen(new MarketDetailScreen(null, null, PaleTaleClient.MARKET, MarketApi.parse("{\"version\":1,\"page\":1,\"pageSize\":12,\"total\":1,\"hasMore\":false,\"ads\":[{\"code\":\"123456\",\"title\":\"灰原港\",\"summary\":\"收购所有血肉世界的产物\",\"state\":\"active\",\"tags\":[\"wholesale\"],\"author\":{\"name\":\"白泽\",\"mcName\":\"tao_kou_zi_Baize\",\"avatarPath\":\"\",\"playerId\":1},\"products\":[],\"locations\":[{\"name\":\"灰原港\",\"coordinates\":\"100 / 64 / -200\",\"directions\":\"\"}],\"updatedAt\":\"2026-10-30\",\"publishedAt\":\"\",\"iconPath\":\"\",\"views\":0,\"favorites\":1,\"body\":\"正文\",\"contact\":{}}]}").ads().get(0))));
            context.waitTicks(30);
            context.takeScreenshot("ad-detail-navigation");
            context.runOnClient(client -> client.setScreen(new PaleSettingsScreen(null)));
            context.waitTicks(10);
            context.takeScreenshot("settings-pale-font");
            context.clickScreenButton("导航粒子：金光");
            context.waitTicks(3);
            require(context.computeOnClient(client -> ModSettings.navParticle().equals("red")), "The particle button cycles to the next look");
            context.takeScreenshot("settings-particle");
            context.clickScreenButton("界面字体：苍白宋体");
            context.waitTicks(5);
            require(context.computeOnClient(client -> !ModSettings.paleFont() && fontOf(client.screen) == client.font), "Switching back gives the game's font");
            context.takeScreenshot("settings-minecraft-font");
            context.runOnClient(client -> client.setScreen(new HomeScreen(null, PaleTaleClient.MARKET)));
            context.waitTicks(10);
            context.takeScreenshot("home-minecraft-font");
            context.runOnClient(client -> { ModSettings.setUiFont("serif"); ModSettings.setNavParticle("gold"); PaleTaleClient.useSite(SiteConfig.MAIN); client.setScreen(new net.minecraft.client.gui.screens.TitleScreen()); });
        }
    }
}
