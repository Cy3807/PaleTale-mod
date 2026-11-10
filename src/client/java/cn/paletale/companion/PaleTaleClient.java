package cn.paletale.companion;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class PaleTaleClient implements ClientModInitializer {
    static final ModAuthApi AUTH = new ModAuthApi(SiteConfig.current());
    public static final ClientAccount ACCOUNT = new ClientAccount(AUTH);
    public static final ShopApi SHOPS = new ShopApi(SiteConfig.current());
    static final ShopWatch WATCH = new ShopWatch(SHOPS);
    static final NoticeApi NOTICE_API = new NoticeApi(SiteConfig.current());
    static final NoticeWatch NOTICES = new NoticeWatch(NOTICE_API);
    static final MarketApi MARKET = new MarketApi(SiteConfig.current());
    static final FavoriteApi FAVORITES = new FavoriteApi(MARKET);
    static final FavoritePresenceWatch FAVORITE_PRESENCE = new FavoritePresenceWatch();
    public static void rosterChanged(net.minecraft.client.multiplayer.ClientPacketListener source,java.util.UUID id){FAVORITE_PRESENCE.rosterChanged(source,id);LocalRoster.changed(source,id);}
    static FavoriteApi favoritesFor(MarketApi api) { return api == MARKET ? FAVORITES : new FavoriteApi(api); }
    private final PresenceWatch presence = new PresenceWatch();
    private final WorldShopWatch worldShops = new WorldShopWatch();
    private final WorldPrewarm prewarm = new WorldPrewarm();
    /** PaleTale's keys (also changeable in PaleTale's own settings). */
    static KeyMapping OPEN, NAV_CANCEL, NAV_TELEPORT, RADIAL;
    private boolean openRequested;
    private static boolean guideRequested;
    private int guideTicks;
    /** Puts the last lines of the sign-in log on the clipboard and says where the file is. */
    static void copyLoginLog() {
        var client = net.minecraft.client.Minecraft.getInstance(); String tail = LoginDebug.tail(300);
        client.keyboardHandler.setClipboard("PaleTale 登录记录\n" + (tail.isEmpty() ? "（暂无记录）" : tail));
        RadialWheel.toast(client, "登录记录已复制到剪贴板。文件：" + (LoginDebug.path() == null ? "?" : LoginDebug.path().toString()));
    }
    /** Copies the network summary (a few lines that fit a chat message) and writes it beside the raw log. */
    static void copyNetworkLog() {
        var client = net.minecraft.client.Minecraft.getInstance();
        String summary = NetworkTrace.summary();
        var file = NetworkTrace.writeSummary();
        client.keyboardHandler.setClipboard(summary);
        RadialWheel.toast(client, "网络诊断已复制到剪贴板。" + (file == null ? "" : " 汇总文件：" + file));
    }
    /** Opens the paletale folder, where network-debug.log and login-debug.log live. */
    static void openNetworkLog() {
        var client = net.minecraft.client.Minecraft.getInstance();
        var file = NetworkTrace.path();
        if (file == null) return;
        try {
            java.nio.file.Files.createDirectories(file.getParent());
            net.minecraft.util.Util.getPlatform().openFile(file.getParent().toFile());
        } catch (java.io.IOException | RuntimeException failed) {
            client.keyboardHandler.setClipboard(file.toString());
            RadialWheel.toast(client, "无法自动打开，路径已复制：" + file);
        }
    }
    /** Opens the welcome guide on the next tick (from the settings or /pt guide). */
    static void requestGuide() { guideRequested = true; }
    private boolean navRequested;
    /** Earliest time the forced registration screen may reappear after being closed. */
    static long registerReopenAt;
    private boolean wasInWorld;
    private final MarketApi api = MARKET;

    /** Points every API at another entry of the same server; the login session stays valid, cached pages and pictures are dropped. */
    static void retargetAll(java.net.URI site) {
        AUTH.retarget(site); SHOPS.retarget(site); NOTICE_API.retarget(site); MARKET.retarget(site);
        // Both entries serve the same pictures and pages, so the caches (keyed by path only) stay warm across a switch.
    }
    /** Switches to the given entry for good: applied now and remembered in the config file. */
    static void useSite(java.net.URI site) {
        ChatAssist.clear();ChatDirectory.clear();
        if (SiteConfig.current().equals(site)) return;
        if (SiteConfig.local() != site.equals(SiteConfig.LOCAL)) {
            ACCOUNT.changeSite();WATCH.clear();NoticeWatch.updateUnread(0);ArchiveScreen.clearSession();MarketImages.INSTANCE.clear();
        }
        NetworkTrace.note("切换站点：" + SiteConfig.label(SiteConfig.current()) + " -> " + SiteConfig.label(site));
        LoginDebug.log("site switch " + SiteConfig.current() + " -> " + site);
        retargetAll(site);SiteConfig.save(site);
    }
    static java.net.URI otherSite() { return SiteConfig.backup() || SiteConfig.local() ? SiteConfig.MAIN : SiteConfig.BACKUP; }
    /** The settings button switches between the main site and the backup entry only; the local test site is a developer setting (-Dpaletale.site=local). */
    static java.net.URI nextSite() { return otherSite(); }

    @Override
    public void onInitializeClient() {
        var loader = net.fabricmc.loader.api.FabricLoader.getInstance();
        HttpBodies.identify(loader.getModContainer("paletale").map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse(""),
                loader.getModContainer("minecraft").map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse(""));
        ModSettings.inferArchiveOpened();
        // Every HTTP exchange is timed into paletale/network-debug.log; the first line names build, machine and site so a pasted log explains itself.
        NetworkTrace.use(ModPaths.config().resolveSibling("network-debug.log"));
        NetworkTrace.begin(ModUpdate.installed(), net.minecraft.SharedConstants.getCurrentVersion().name(),
                System.getProperty("java.version"), System.getProperty("os.name") + " " + System.getProperty("os.version"),
                SiteConfig.label(SiteConfig.current()));
        MarketImages.INSTANCE.prune();
        // Unreachable official domain: every API moves to the mainland mirror; caches keyed by the old site are dropped.
        SiteConfig.probe(PaleTaleClient::retargetAll);
        ServerAccess.trust(ModSettings.extraServers());
        ServerEntriesSync.refresh(SiteConfig.current(), ModPaths.config().resolveSibling("cache").resolve("server-entries-v1.json"));
        LoginDebug.use(ModPaths.config().resolveSibling("login-debug.log"));
        LoginDebug.log("session start: PaleTale " + ModUpdate.installed() + ", Minecraft " + net.minecraft.SharedConstants.getCurrentVersion().name() + ", Java " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + "), " + System.getProperty("os.name") + " " + System.getProperty("os.version") + ", site " + SiteConfig.current() + ", mods " + net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods().size());
        ModUpdate.begin();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client)->LocalRoster.disconnected(handler));
        LedgerWatch.register();
        PrivateChatStore.register();
        FavoritePresenceHud.register();
        PaleScoreboard.register();
        PlayerQuery.register();
        LandHere.register();
        WineTooltips.register();
        DailySummary.register();
        Navigation.register();
        RadialWheel.register();
        LandSpawns.register();
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("paletale", "market"));
        OPEN = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.paletale.market",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, category));
        // Both unbound until the player chooses a key (Options > Controls).
        NAV_CANCEL = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.paletale.nav_cancel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        NAV_TELEPORT = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.paletale.nav_teleport", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        // Unbound until the player picks a key (Options > Controls).
        FishLookup.register(KeyBindingHelper.registerKeyBinding(new KeyMapping("key.paletale.fish_archive", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category)));
        // Hold to show the command wheel, let go to send the hovered slot's command.
        RADIAL = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.paletale.radial", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, category));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> {
            for (String name : new String[]{ "paletale", "pt" }) dispatcher.register(ClientCommandManager.literal(name)
                    .executes(context -> { openRequested = true; return 1; })
                    .then(ClientCommandManager.literal("guide").executes(context -> { requestGuide(); return 1; }))
                    // /pt loginlog copies the sign-in record to the clipboard (for a bug report).
                    .then(ClientCommandManager.literal("loginlog").executes(context -> { copyLoginLog(); return 1; }))
                    // /pt netlog copies the network summary; /pt netlog open shows the folder with the full log.
                    .then(ClientCommandManager.literal("netlog").executes(context -> { copyNetworkLog(); return 1; })
                            .then(ClientCommandManager.literal("open").executes(context -> { openNetworkLog(); return 1; })))
                    .then(ClientCommandManager.literal("yes").executes(context -> { ShopVisibility.show(net.minecraft.client.Minecraft.getInstance()); return 1; }))
                    .then(ClientCommandManager.literal("no").executes(context -> { ShopVisibility.hide(net.minecraft.client.Minecraft.getInstance()); return 1; }))
                    // /pt nav <x> <z> starts guidance to a coordinate; /pt nav off stops it.
                    .then(ClientCommandManager.literal("nav").executes(context -> { navRequested = true; return 1; })
                            .then(ClientCommandManager.literal("world").then(ClientCommandManager.argument("target",com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(context -> {
                                String[] parts=com.mojang.brigadier.arguments.StringArgumentType.getString(context,"target").trim().split("\\s+");
                                try {
                                    if(ExpeditionMenu.active())throw new IllegalStateException("远征服务器不提供一键返回主城或主世界；请手动返回后再导航。" );
                                    if(parts.length!=4||!java.util.List.of(CityNavigation.MAIN_WORLD,CityNavigation.WORLD).contains(parts[0]))throw new IllegalArgumentException();
                                    int x=Integer.parseInt(parts[1]),z=Integer.parseInt(parts[3]);Integer y=parts[2].equals("~")?null:Integer.valueOf(parts[2]);
                                    if(Math.abs((long)x)>1000000||Math.abs((long)z)>1000000||y!=null&&Math.abs((long)y)>2048)throw new IllegalArgumentException();
                                    Navigation.start(new Navigation.Target("箱子商店",x,y,z,"箱子商店",y==null?java.util.List.of():java.util.List.of(new int[]{x,y,z}),parts[0]));
                                } catch(IllegalStateException blocked){context.getSource().sendFeedback(CommandFeedback.text(blocked.getMessage()));}
                                catch(IllegalArgumentException invalid){context.getSource().sendFeedback(CommandFeedback.text("用法：&f/pt nav world <世界> <x> <y> <z>"));}
                                return 1;
                            })))
                            .then(ClientCommandManager.literal("expedition").then(ClientCommandManager.argument("target",com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(context -> {
                                String[] parts=com.mojang.brigadier.arguments.StringArgumentType.getString(context,"target").trim().split("\\s+");
                                try {
                                    if(parts.length!=4)throw new IllegalArgumentException();
                                    ExpeditionWorld destination=ExpeditionWorld.byId(parts[0]);if(destination==null)throw new IllegalArgumentException();
                                    int x=Integer.parseInt(parts[1]),z=Integer.parseInt(parts[3]);Integer y=parts[2].equals("~")?null:Integer.valueOf(parts[2]);
                                    if(Math.abs((long)x)>1000000||Math.abs((long)z)>1000000||y!=null&&Math.abs((long)y)>2048)throw new IllegalArgumentException();
                                    ExpeditionMenu.begin(destination,x,y,z);
                                } catch(IllegalArgumentException invalid){context.getSource().sendFeedback(CommandFeedback.text("用法：&f/pt nav expedition <nether|flesh|dungeon|end> <x> <y> <z>"));}
                                return 1;
                            })))
                            .then(ClientCommandManager.literal("why").executes(context -> { for (String line : Navigation.why(net.minecraft.client.Minecraft.getInstance())) context.getSource().sendFeedback(CommandFeedback.text(line)); return 1; }))
                            .then(ClientCommandManager.literal("off").executes(context -> { Navigation.cancel(); context.getSource().sendFeedback(CommandFeedback.text("导航已&f取消&7。")); return 1; }))
                            .then(ClientCommandManager.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-1_000_000, 1_000_000))
                                    .then(ClientCommandManager.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-1_000_000, 1_000_000)).executes(context -> {
                                        int x = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "x"), z = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "z");
                                        var client=net.minecraft.client.Minecraft.getInstance();String server=ExpeditionMenu.activeServer();String world=Navigation.world(client);
                                        Navigation.start(new Navigation.Target("坐标 " + x + ", " + z, x, null, z, "", java.util.List.of(),world.isEmpty()?CityNavigation.MAIN_WORLD:world,server==null?"":server));
                                        return 1;
                                    }))))
                    // /pt net: switch between the main site and the backup entry, back and forth.
                    .then(ClientCommandManager.literal("net").executes(context -> {
                        java.net.URI next = otherSite();
                        useSite(next);
                        context.getSource().sendFeedback(CommandFeedback.text("已切换：&f" + SiteConfig.label(next)));
                        return 1;
                    })));
        });
        // A shop sign the server just changed (by anyone): the site hears about it within seconds.
        SignUpdates.listen(pos -> worldShops.signChanged(net.minecraft.client.Minecraft.getInstance(), pos));
        // A shop sign was clicked or paid for: the site hears about the new stock now, not at the next scan.
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) for (String line : message.getString().split("\\R")) worldShops.chat(net.minecraft.client.Minecraft.getInstance(), line);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ChatAssist.tick(client);
            MonsoonRulesSync.refresh(SiteConfig.current(), client.gameDirectory.toPath().resolve("paletale/cache/monsoon-rules"));
            FishIndexSync.refresh(SiteConfig.current(), client.gameDirectory.toPath().resolve("paletale/cache/fish-index"));
            FAVORITE_PRESENCE.tick(client);
            ACCOUNT.tick(client);
            presence.tick(client);
            worldShops.tick(client);
            prewarm.tick(client);
            WATCH.tick(client);
            NOTICES.tick(client);
            if (wasInWorld && client.level == null) { MarketImages.INSTANCE.clear(); ArchiveModels.INSTANCE.clear(); ArchiveScreen.clearSession(); }
            wasInWorld = client.level != null;
            while (OPEN.consumeClick()) openRequested = true;
            while (NAV_CANCEL.consumeClick()) Navigation.cancel();
            while (NAV_TELEPORT.consumeClick()) Navigation.runTeleportCommand();
            while (RADIAL.consumeClick()) RadialWheel.open();
            FishLookup.tick(client);
            RadialWheel.tick(client);
            // An unregistered Minecraft account must register: the screen returns every few seconds while nothing else is open.
            if (ACCOUNT.takeRegisterPrompt() && client.player != null && !(client.screen instanceof RegisterScreen)) client.setScreen(new RegisterScreen(null));
            else if (ACCOUNT.mustRegister() && client.player != null && client.screen == null && System.currentTimeMillis() >= registerReopenAt) client.setScreen(new RegisterScreen(null));
            // The welcome guide, once: on SimMC, a few seconds after joining, signed in or at least not waiting to register, nothing else open.
            if (client.screen == null && !ModSettings.guideShown() && !ModSettings.archiveOpened() && ACCOUNT.canLogin(client) && !ACCOUNT.mustRegister() && !ACCOUNT.busy() && ++guideTicks > 160) { guideTicks = 0; client.setScreen(new GuideScreen(null, OPEN.getTranslatedKeyMessage().getString())); }
            if (guideRequested) { guideRequested = false; if (client.player != null) client.setScreen(new GuideScreen(client.screen, OPEN.getTranslatedKeyMessage().getString())); }
            if (openRequested) {
                openRequested = false;
                if (client.player != null) ACCOUNT.autoLogin(client);
                if (client.player != null) client.setScreen(ACCOUNT.mustRegister() ? new RegisterScreen(null) : ArchiveNavigation.openLast(client.screen, api));
            }
            if (navRequested) {
                navRequested = false;
                if (client.player != null) client.setScreen(ArchiveNavigation.open(null, api, ArchiveNavigation.Tab.NAVIGATION));
            }
        });
    }
}
