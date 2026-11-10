package cn.paletale.companion;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * The command wheel. It is a HUD layer, not a screen: walking, jumping and everything else keep working, the OS mouse cursor is visible, and the camera stays still.
 * Level 0 (the saved slots) exists while the key is held; letting go sends the slot's command or runs its macro. Slots that lead on open level 1, a ring of tiles that stays up
 * without the key: the territories (with a refresh tile at the bottom), or the few things to do with the one territory / player a slot is bound to. On level 1 left click runs the
 * tile under the pointer, right click closes. Left click on level 0 edits the slot, right click closes. Anything that could leave the wheel hanging (a screen, losing focus, leaving
 * the world, 90 idle seconds) closes it, and after a close the key has to be let go before the wheel can open again.
 */
public final class RadialWheel {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("paletale", "radial_wheel");
    private static final double REACH = 30, MIN = 5;
    private static final int MAX_LANDS = 40, ATTEMPTS = 3;
    private static final long ANSWER_MS = 4_000, RETRY_MS = 1_000, IDLE_MS = 90_000;
    /** The server's complaint when a territory is not there (the list is stale), as seen in the first seconds after a teleport command. */
    private static final Pattern GONE = Pattern.compile(".*领土.*(找不到|不存在|未找到|没有找到|无法找到|未知|不是有效).*|.*(找不到|不存在|未找到|没有找到).*领土.*");

    /** One tile of a level-1 ring; {@code keepOpen} tiles (refresh) do not close the wheel. */
    private record Tile(String name, RadialArt.Icon icon, Runnable run, boolean keepOpen) {}
    private static boolean active, suppress, needRelease; private static double px, py; private static int hover = -1, level; private static long since, lastInput;
    private static Tile[] ring = new Tile[0]; private static String title = "";
    private static boolean landRing, loading; private static String landCommand = "l spawn ";
    private static int attempts, ringVersion; private static long retryAt, watchUntil;
    private RadialWheel() {}

    static void register() {
        HudElementRegistry.addLast(ID, (g, tracker) -> render(g));
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay || System.currentTimeMillis() > watchUntil || !GONE.matcher(message.getString().replace("\n", " ")).matches()) return;
            watchUntil = 0;
            var client = Minecraft.getInstance();
            // Something showed the list is out of date: read it again now, without being asked.
            client.execute(() -> { toast(client, "领地列表已过期，正在重新读取。"); if (!LandMenu.busy() && client.screen == null) LandMenu.read(entries -> { }, ANSWER_MS, true); });
        });
    }
    public static boolean active() { return active; }

    static void open() {
        var client = Minecraft.getInstance();
        if (active) {
            // Click mode: a fresh press of the key closes the wheel again (key repeats of the opening press do not count).
            if (level == 0 && ModSettings.radialClick() && !needRelease) close();
            return;
        }
        // Key repeats of the press that closed the wheel arrive as new clicks; only a fresh press (after the key was let go) opens it again.
        if (suppress || client.player == null || client.screen != null) return;
        active = true; needRelease = true; level = 0; px = py = 0; hover = -1; since = lastInput = System.currentTimeMillis();client.mouseHandler.releaseMouse();centerCursor(client);RadialArt.reset(); RadialArt.warm(RadialSlots.COUNT);
    }

    private static void close() { active = false; suppress = true; loading = false; retryAt = 0;restoreMouse(Minecraft.getInstance()); }

    private static void centerCursor(Minecraft client){var window=client.getWindow();org.lwjgl.glfw.GLFW.glfwSetCursorPos(client.getWindow().handle(),window.getScreenWidth()/2.0,window.getScreenHeight()/2.0);}
    private static void restoreMouse(Minecraft client){if(client.player!=null&&client.screen==null&&client.isWindowActive())client.mouseHandler.grabMouse();}

    // ---- level 1: rings of tiles ----
    private static void enter(String name, Tile[] tiles, boolean lands) {
        title = name; ring = tiles; landRing = lands; level = 1; px = py = 0; hover = -1; since = lastInput = System.currentTimeMillis();centerCursor(Minecraft.getInstance());RadialArt.reset(); RadialArt.warm(tiles.length);
    }

    private static Tile action(String name, ItemStack icon, Runnable run) { return new Tile(name, RadialArt.Icon.of(icon), run, false); }

    /** The ring of what to do with one territory or one player, for a slot bound to it. */
    private static boolean enterBound(Minecraft client, RadialSlots.Slot slot, String[] bound) {
        String name = bound[1]; List<Tile> tiles = new ArrayList<>();
        if (bound[0].equals("land")) {
            // Six tiles; the one at the bottom is the territory's own icon and opens /l info. The commands on the bank act on the selected (/l edit) territory, so they select it first.
            tiles.add(action("传送", new ItemStack(Items.ENDER_PEARL), () -> teleport(client, name)));
            tiles.add(action("选中", new ItemStack(Items.OAK_SIGN), () -> send(client, "l edit " + name)));
            tiles.add(action("查询余额", new ItemStack(Items.GOLD_INGOT), () -> selectThen(client, name, () -> send(client, "l balance"))));
            tiles.add(action("领地信息", LandCache.icon(name), () -> send(client, "l info " + name)));
            tiles.add(action("存云币", new ItemStack(Items.EMERALD), () -> selectThen(client, name, () -> client.setScreen(new net.minecraft.client.gui.screens.ChatScreen("/l deposit ", false)))));
            tiles.add(action("取云币", new ItemStack(Items.GOLD_NUGGET), () -> selectThen(client, name, () -> client.setScreen(new net.minecraft.client.gui.screens.ChatScreen("/l withdraw ", false)))));
        } else {
            tiles.add(action("查询", RadialArt.headOf(name), () -> client.setScreen(WorldEntityScreen.playerQuery(PaleTaleClient.MARKET, name))));
            tiles.add(action("余额", new ItemStack(Items.GOLD_INGOT), () -> send(client, "bal " + name)));
            tiles.add(action("私聊", new ItemStack(Items.PAPER), () -> client.setScreen(PrivateChatScreen.conversation(null, PaleTaleClient.MARKET, name))));
            tiles.add(action("队伍邀请", new ItemStack(Items.WHITE_BANNER), () -> PlayerActions.invite(client, name)));
            tiles.add(action("转账", new ItemStack(Items.GOLD_INGOT), () -> client.setScreen(new TransferDialog(null, name))));
        }
        Tile[] padded = new Tile[RadialArt.tiles(tiles.size())];
        for (int i = 0; i < tiles.size(); i++) padded[i] = tiles.get(i);
        enter(slot.label().isEmpty() ? name : slot.label(), padded, false);
        return true;
    }

    private static Runnable pending; private static long pendingAt;
    /** Selects the territory for /lands commands (/l edit), then runs {@code after} once the server has had a moment to take it. */
    private static void selectThen(Minecraft client, String land, Runnable after) {
        send(client, "l edit " + land); pending = after; pendingAt = System.currentTimeMillis() + 450;
    }

    /** The ring of actions the server has as plain commands (/sit, /spin, /crawl, /lay); /crawl is the one at the bottom. */
    private static void enterActions(Minecraft client) {
        Tile[] tiles = new Tile[4];
        tiles[0] = action("坐下", new ItemStack(Items.OAK_STAIRS), () -> send(client, "sit"));
        tiles[1] = action("旋转", new ItemStack(Items.CLOCK), () -> send(client, "spin"));
        tiles[2] = action("趴下", new ItemStack(Items.LEATHER_BOOTS), () -> send(client, "crawl"));
        tiles[3] = action("躺下", new ItemStack(Items.WHITE_BED), () -> send(client, "lay"));
        enter("动作", tiles, false);
    }

    /** Favourite players, those online first. */
    static List<String> favoritePlayers() {
        var names = new ArrayList<>(PaleTaleClient.FAVORITE_PRESENCE.cachedNames());
        names.removeIf(n -> !n.matches("[A-Za-z0-9_]{1,16}"));
        names.sort(java.util.Comparator.comparing((String n) -> !LocalRoster.online(n)).thenComparing(String.CASE_INSENSITIVE_ORDER));
        return names;
    }

    /** The players of the latest private chats, newest first. */
    private static List<String> recentPeers() {
        var latest = new java.util.LinkedHashMap<String, Long>();
        for (var entry : PrivateChatStore.entries()) if (entry.peer().matches("[A-Za-z0-9_]{1,16}")) latest.merge(entry.peer(), entry.time(), Math::max);
        return latest.entrySet().stream().sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed()).map(java.util.Map.Entry::getKey).toList();
    }

    /** The players you transferred to or got transfers from lately, newest first (from the ledger). */
    private static List<String> transferPeers() {
        var latest = new java.util.LinkedHashMap<String, Long>();
        for (var entry : LedgerStore.visible()) {
            boolean transfer = entry.kind() == LedgerEntry.Kind.TRANSFER_OUT || entry.kind() == LedgerEntry.Kind.TRANSFER_IN;
            if (transfer && entry.counterparty().matches("[A-Za-z0-9_]{1,16}")) latest.merge(entry.counterparty(), entry.time(), Math::max);
        }
        return latest.entrySet().stream().sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed()).map(java.util.Map.Entry::getKey).toList();
    }

    /** Who could be invited to a team: favourite players and the latest chats, those online only. */
    private static List<String> partyCandidates() {
        var names = new java.util.LinkedHashSet<String>(favoritePlayers());
        names.addAll(recentPeers());
        return names.stream().filter(LocalRoster::online).toList();
    }

    /** A ring of players (their heads); a tile does {@code pick} with the name and, when {@code keepOpen}, leaves the ring up for the next one. */
    private static boolean enterPeople(Minecraft client, String title, List<String> names, String none, boolean keepOpen, java.util.function.Consumer<String> pick) {
        if (names.isEmpty()) { toast(client, none); return false; }
        var shownNames = names.stream().limit(8).toList();
        Tile[] tiles = new Tile[RadialArt.tiles(shownNames.size())];
        for (int i = 0; i < shownNames.size(); i++) {
            String name = shownNames.get(i);
            tiles[i] = new Tile(name, RadialArt.Icon.sized(RadialArt.headOf(name), 1.5f), () -> pick.accept(name), keepOpen);
        }
        enter(title, tiles, false);
        return true;
    }

    /** The RP teleport points nearest to where the player stands (from the site's map data): a tile teleports there with /warp. */
    private static boolean enterWarps(Minecraft client) {
        var warps = new ArrayList<>(Navigation.points().warps());
        if (warps.isEmpty()) { toast(client, "传送点数据还没有加载，请稍后再试。"); return false; }
        double px = client.player.getX(), pz = client.player.getZ();
        warps.sort(java.util.Comparator.comparingDouble(w -> Math.hypot(w.x() - px, w.z() - pz)));
        var nearest = warps.stream().limit(8).toList();
        Tile[] tiles = new Tile[RadialArt.tiles(nearest.size())];
        for (int i = 0; i < nearest.size(); i++) {
            var warp = nearest.get(i); int distance = (int) Math.hypot(warp.x() - px, warp.z() - pz);
            tiles[i] = action(warp.name() + "（" + distance + " 格）", new ItemStack(Items.ENDER_EYE), () -> send(client, "warp " + warp.name()));
        }
        enter("传送点", tiles, false);
        return true;
    }

    /** The two things to do with a navigation, as two half rings: the upper takes the next teleport step, the lower stops. */
    private static void enterNavigation() {
        Tile[] tiles = new Tile[2];
        tiles[0] = action("一键导航", new ItemStack(Items.COMMAND_BLOCK), Navigation::runTeleportCommand);
        tiles[1] = action("停止导航", new ItemStack(Items.LEVER), () -> { Navigation.cancel(); toast(Minecraft.getInstance(), "导航已取消。"); });
        enter("导航", tiles, false);
    }

    /** Opens the ring of what to do with a player (after the player search chose one); it stays up like any ring of level 1. */
    static void showPlayer(String name) {
        var client = Minecraft.getInstance();
        if (client.player == null) return;
        active = true; needRelease = false; suppress = false; lastInput = System.currentTimeMillis();
        enterBound(client, new RadialSlots.Slot(name, "@player:" + name, ""), new String[]{ "player", name });
    }

    private static void teleport(Minecraft client, String land) {
        send(client, "l spawn " + land); watchUntil = System.currentTimeMillis() + 5000;
    }

    /** Territory tiles from the shared cache: the refresh tile sits at the bottom (the ring is even), territories fill the rest clockwise from the top. */
    private static void buildLandRing() {
        var client = Minecraft.getInstance();
        var names = LandCache.load().stream().limit(MAX_LANDS).toList();
        int n = names.size() + 1; if (n % 2 == 1) n++; n = Math.max(4, n);
        Tile[] tiles = new Tile[n]; int refresh = n / 2, k = 0;
        for (int i = 0; i < n; i++) {
            if (i == refresh) { tiles[i] = new Tile("刷新领地", RadialArt.Icon.of(new ItemStack(Items.CLOCK)), () -> { if (!loading) { attempts = 0; refreshLands(); } }, true); continue; }
            if (k >= names.size()) continue;
            String name = names.get(k++);
            // "我的领地" (no command): a territory opens its own ring of actions on the next level.
            boolean own = landCommand.isEmpty();
            tiles[i] = new Tile(name, RadialArt.Icon.of(LandCache.icon(name)), () -> {
                if (own) enterBound(client, new RadialSlots.Slot(name, "@land:" + name, ""), new String[]{ "land", name });
                else if (landCommand.equals("l spawn ")) teleport(client, name); else send(client, landCommand + name);
            }, own);
        }
        ring = tiles; ringVersion = LandCache.version();
    }

    private static void enterLands(String command) {
        landCommand = command; buildLandRing();
        enter(command.equals("l edit ") ? "选择领地" : command.isEmpty() ? "我的领地" : "传送领地", ring, true);
        // Opens with the saved list; reads only when there is none yet, or when it was saved without icons. Otherwise refreshing is the player's choice.
        if (LandCache.lands().isEmpty() || LandCache.missingIcons()) { attempts = 0; refreshLands(); }
    }

    /** Reads /l again at once (the cache and the ring follow by themselves); an empty answer is asked for again a moment later. */
    private static void refreshLands() {
        attempts++; loading = true;
        LandMenu.read(entries -> {
            if (!entries.isEmpty()) loading = false;
            else if (attempts < ATTEMPTS) retryAt = System.currentTimeMillis() + RETRY_MS;
            else loading = false;
        }, ANSWER_MS, true);
    }

    private static int tilesNow() { return level == 0 ? RadialSlots.COUNT : ring.length; }

    /** Maps the visible OS cursor onto the wheel's direction selector. */
    private static void updatePointer(Minecraft client) {
        var window=client.getWindow();int screenWidth=window.getScreenWidth(),screenHeight=window.getScreenHeight();
        if(screenWidth<=0||screenHeight<=0)return;
        double mouseX=client.mouseHandler.xpos()*window.getGuiScaledWidth()/screenWidth,mouseY=client.mouseHandler.ypos()*window.getGuiScaledHeight()/screenHeight;
        double nextX=mouseX-window.getGuiScaledWidth()/2.0,nextY=mouseY-window.getGuiScaledHeight()/2.0;
        if(Math.abs(nextX-px)>.1||Math.abs(nextY-py)>.1)lastInput=System.currentTimeMillis();
        px=nextX;py=nextY;
        double len = Math.hypot(px, py);
        if (len > REACH) { px *= REACH / len; py *= REACH / len; len = REACH; }
        if (len >= MIN) {
            int at = RadialArt.sectorAt(px, py, tilesNow());
            hover = level == 0 || at < ring.length && ring[at] != null ? at : -1;
        }
    }

    private static boolean held() {
        var key = KeyBindingHelper.getBoundKeyOf(PaleTaleClient.RADIAL);
        if (key.equals(InputConstants.UNKNOWN)) return false;
        long window = Minecraft.getInstance().getWindow().handle();
        return key.getType() == InputConstants.Type.MOUSE ? GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS : GLFW.glfwGetKey(window, key.getValue()) == GLFW.GLFW_PRESS;
    }

    /**
     * A mouse button went down while the wheel is open (the game never sees it). Right closes the wheel without doing anything. Left: on the saved slots it edits the slot under the
     * pointer; on a ring it runs the tile under the pointer.
     */
    public static void press(int button) {
        if (!active) return;
        var client = Minecraft.getInstance();
        if (button == 1) { close(); return; }
        if (button != 0 || hover < 0) return;
        if (level == 0) { chooseSlot(client); return; }
        var tile = ring[hover];
        if (tile == null) return;
        if (!tile.keepOpen()) close();
        tile.run().run();
    }

    /** Left click on the saved slots selects the one under the pointer, as letting go of the key does (editing slots is done in the settings). */
    private static void chooseSlot(Minecraft client) {
        var chosen = hover >= 0 ? RadialMenuScreen.slots().get(hover) : RadialSlots.EMPTY;
        if (chosen.empty()) return;
        if (!run(client, chosen)) close();
    }

    static void toast(Minecraft client, String text) {
        client.getToastManager().addToast(SystemToast.multiline(client, new SystemToast.SystemToastId(), Component.literal("Paletale"), Component.literal(text)));
    }

    /** Sends a command the player asked for. A territory spawn keeps the menu guard armed (no menu may pop up); anything else may show whatever it opens. */
    static void send(Minecraft client, String command) {
        boolean sent;
        if (command.startsWith("l spawn ")) sent = ClientCommandQueue.chat(command);
        else { LandMenuGuard.userSent(); sent = ClientCommandQueue.direct(command); }
        if (!sent) toast(client, "指令发送太快，请稍后重试。");
    }

    /** Runs a slot let go of on level 0; returns true when the wheel stays up (it went on to level 1). */
    private static boolean run(Minecraft client, RadialSlots.Slot slot) {
        String command = slot.command();
        var bound = RadialSlots.bound(command);
        if (bound != null) return enterBound(client, slot, bound);
        if (!RadialSlots.macro(command)) { send(client, command); return false; }
        switch (command) {
            case "@coords" -> {
                var at = client.player.blockPosition(); String text = at.getX() + " " + at.getY() + " " + at.getZ();
                client.keyboardHandler.setClipboard(text); toast(client, "已复制坐标：" + text);
            }
            case "@nav-off" -> { Navigation.cancel(); toast(client, "导航已取消。"); }
            case "@lands" -> { enterLands("l spawn "); return true; }
            case "@landedit" -> { enterLands("l edit "); return true; }
            case "@mylands" -> { enterLands(""); return true; }
            case "@actions" -> { enterActions(client); return true; }
            case "@nav" -> { enterNavigation(); return true; }
            case "@recent" -> { return enterPeople(client, "最近聊天", recentPeers(), "还没有私聊记录。", false, name -> client.setScreen(PrivateChatScreen.conversation(null, PaleTaleClient.MARKET, name))); }
            case "@transfers" -> { return enterPeople(client, "转账对象", transferPeers(), "账本里还没有转账记录。", false, name -> client.setScreen(new TransferDialog(null, name))); }
            case "@party" -> { return enterPeople(client, "组队", partyCandidates(), "没有在线的收藏玩家或最近聊天的玩家。", true, name -> PlayerActions.invite(client, name)); }
            case "@warps" -> { return enterWarps(client); }
            case "@signs" -> client.setScreen(new SignSearchScreen());
            case "@nav-go" -> Navigation.runTeleportCommand();
            // A small dialog asks for the name (Tab completes it from the players online).
            case "@player" -> client.setScreen(new PlayerQueryScreen());
            default -> { /* @cancel, or an unknown macro: nothing */ }
        }
        return false;
    }

    static void tick(Minecraft client) {
        if (suppress && !held()) suppress = false;
        if (pending != null && System.currentTimeMillis() >= pendingAt) { var next = pending; pending = null; if (client.player != null) next.run(); }
        if (!active) return;
        if (client.player == null || client.screen != null || !client.isWindowActive() || System.currentTimeMillis() - lastInput > IDLE_MS) { close(); return; }
        updatePointer(client);
        if (needRelease && !held()) needRelease = false;
        if (level == 1) {
            if (landRing && ringVersion != LandCache.version()) buildLandRing();
            if (retryAt != 0 && System.currentTimeMillis() >= retryAt && !LandMenu.busy()) { retryAt = 0; refreshLands(); }
            return;
        }
        if (ModSettings.radialClick() || held()) return;
        active = false;
        var chosen = hover >= 0 ? RadialMenuScreen.slots().get(hover) : RadialSlots.EMPTY;
        if (!chosen.empty() && run(client, chosen)) active = true;
        else restoreMouse(client);
    }

    private static void render(GuiGraphics g) {
        if (!active) return;
        FavoritePresenceHud.uncacheAnimation();
        double fade = Math.min(1, (System.currentTimeMillis() - since) / 140.0);
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2; var font = PaleFont.get();
        if (level == 1) {
            var icons = new ArrayList<RadialArt.Icon>();
            for (Tile tile : ring) icons.add(tile == null ? RadialArt.Icon.NONE : tile.icon());
            var over = hover >= 0 && hover < ring.length ? ring[hover] : null;
            String name = over != null ? (over.keepOpen() && loading ? "正在刷新…" : over.name()) : landRing && loading ? "正在读取领地…" : landRing && LandCache.lands().isEmpty() ? "没有读取到领地，点下方刷新" : title;
        RadialArt.draw(g, font, cx, cy, ring.length, icons, hover, fade, name);
            return;
        }
        var slots = RadialMenuScreen.slots();
        boolean has = hover >= 0 && !slots.get(hover).empty();
        RadialArt.draw(g, font, cx, cy, RadialSlots.COUNT, RadialArt.slotIcons(slots), hover, fade, has ? slots.get(hover).label() : "快捷轮盘");
    }
}
