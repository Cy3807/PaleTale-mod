package cn.paletale.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import java.util.function.Consumer;
import static cn.paletale.companion.MarketTheme.*;

final class ArchiveNavigation {
    enum Tab { ITEMS, PLAYERS, ORGS, MARKET, WORLD, LEDGER, NAVIGATION, FAVORITES, NOTIFICATIONS, PRIVATE_CHAT, HOME }
    private static Tab selected = Tab.MARKET;
    /** The screen the player was in before opening PaleTale (usually none): Esc from a top-level tab returns there, however many lists were opened since. */
    private static Screen root;
    static Screen open(Screen parent, MarketApi api, Tab tab) {
        selected = tab;
        if (!(parent instanceof WindowScreen)) root = parent;
        ModSettings.markArchiveOpened();
        if(tab==Tab.PRIVATE_CHAT)return new PrivateChatScreen(root,api);
        if(tab==Tab.FAVORITES)return new FavoritesHub(root,api);
        if(tab==Tab.NOTIFICATIONS)return new NotificationsScreen(root,PaleTaleClient.NOTICE_API);
        if (tab == Tab.WORLD) return new WorldScreen(parent, api);
        if (tab == Tab.NAVIGATION) return new NavigationHubScreen(parent, api);
        if (tab == Tab.LEDGER) return new LedgerScreen(parent, api);
        return tab == Tab.MARKET ? new MarketScreen(parent, api) : new ArchiveScreen(parent, api, tab == Tab.ITEMS ? ArchiveData.Kind.ITEMS : tab == Tab.ORGS ? ArchiveData.Kind.ORGS : ArchiveData.Kind.PLAYERS, 0);
    }
    /** The start page comes first after every client start; after that H reopens wherever the player last was. */
    private static boolean homeShown;
    static Screen openLast(Screen parent, MarketApi api) { if (!homeShown || selected == Tab.HOME) return home(parent, api); return open(parent, api, selected); }
    static Screen home(Screen parent, MarketApi api) {
        homeShown = true; selected = Tab.HOME;
        if (!(parent instanceof WindowScreen)) root = parent;
        ModSettings.markArchiveOpened();
        return new HomeScreen(root, api);
    }
    /** From the start page into a tab. */
    static void go(Tab tab) { Minecraft.getInstance().setScreen(open(root, PaleTaleClient.MARKET, tab)); }
    static void resetHomeForTest() { homeShown = false; }
    static Screen owned(Screen parent, MarketApi api, int player) { selected = Tab.ITEMS; return new ArchiveScreen(parent, api, ArchiveData.Kind.ITEMS, player); }
    static Screen ads(Screen parent, MarketApi api, int player) { selected = Tab.MARKET; return new MarketScreen(parent, api, "ID:" + player, MarketData.AdState.ALL); }
    static final java.net.URI QQ_GROUP = java.net.URI.create("https://qm.qq.com/q/3ll80iLCzK");
    static String avatarOf(ModAuthApi.Citizen citizen) {
        return citizen != null && citizen.uuid() != null && citizen.uuid().matches("[a-fA-F0-9]{32}") ? "/api/players/" + citizen.uuid() + "/avatar" : null;
    }
    /** Buttons are only as wide as their text (plus padding) with a small gap, so a click never lands on a neighbour's label. */
    static void tabs(Consumer<Button> add, Screen parent, MarketApi api, Tab tab, int left, int width) {
        var font = PaleFont.get();
        boolean compact = width < 600;
        int pad = compact ? 8 : 12, gap = compact ? 1 : 3;
        int logo = width < 520 ? 0 : compact ? 70 : 108;
        int x = left + (logo == 0 ? 4 : logo);
        Tab[] tabs = { Tab.ITEMS, Tab.PLAYERS, Tab.ORGS, Tab.MARKET, Tab.WORLD, Tab.LEDGER, Tab.PRIVATE_CHAT, Tab.NAVIGATION };
        // 【档案 [物品][玩家][组织]】 is one frame (it only groups, it cannot be clicked) holding three small buttons; the rest are single buttons.
        // The small buttons show 物品 / 玩家 / 组织 (the frame says 档案) but keep the full accessible names.
        String[] fullNames = { "物品档案", "玩家档案", "组织档案", "广场", "SMC世界", "账本", "社交", "导航" };
        String[] names = { "物品", "玩家", "组织", "广场", "SMC世界", "账本", "社交", "导航" };
        String frameLabel = "档案";
        String groupLabel = "加入官方群";
        int groupWidth = 22;
        int accountWidth = 40;
        int tabRoom = left + width - accountWidth - 6 - 22 - 50 - groupWidth - gap - x;
        int frameInner = 6, frameLabelWidth = font.width(frameLabel) + 6;
        if (navWidth(font, names, frameLabelWidth, pad, gap) > tabRoom) { names[3] = "广告"; names[4] = "SMC"; }
        if (navWidth(font, names, frameLabelWidth, pad, gap) > tabRoom) { pad = Math.max(8, pad - 4); gap = 1; }
        if (width < 400||navWidth(font,names,frameLabelWidth,pad,gap)>tabRoom) { pad=8;gap=0;names = new String[]{"物", "人", "组", "广告", "SMC", "账", "聊", "导"}; frameLabel = "档"; frameLabelWidth = font.width(frameLabel) + 6; }
        if(navWidth(font,names,frameLabelWidth,pad,gap)>tabRoom&&logo>0){logo=0;x=left+4;}
        int frameWidth = frameInner * 2 + frameLabelWidth + java.util.Arrays.stream(names).limit(3).mapToInt(font::width).sum() + 3 * (pad + gap) - gap;
        add.accept(new GroupFrame(frameLabel, x, 10, frameWidth, 24));
        int innerX = x + frameInner + frameLabelWidth;
        x += frameWidth + gap + 4;
        for (int i = 0; i < tabs.length; i++) {
            Tab target = tabs[i];
            boolean inFrame = i < 3;
            int w = font.width(names[i]) + pad;
            int bx = inFrame ? innerX : x;
            var button = new Action(fullNames[i], bx, 13, w, 18, target == tab, null, ignored -> {
                if (target != tab) Minecraft.getInstance().setScreen(open(root, api, target));
            });
            button.setDisplayLabel(names[i]);
            if (!names[i].equals(fullNames[i]) && !inFrame) button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(net.minecraft.network.chat.Component.literal(fullNames[i])));
            add.accept(button);
            if (inFrame) innerX += w + gap; else x += w + gap;
        }
        if (logo > 0) add.accept(new LogoLink(left + 3, compact ? 8 : 7, compact ? 62 : 82, compact ? 21 : 28));
        int cursor = left + width - accountWidth - 6 - 22;
        add.accept(new ThemeToggle(left + width, 13));
        add.accept(new Bell(cursor - 22, 13)); cursor -= 26;
        var favorites=new Action("★",cursor-22,13,22,18,tab==Tab.FAVORITES,null,b->Minecraft.getInstance().setScreen(open(root,api,Tab.FAVORITES)));favorites.setTooltip(net.minecraft.client.gui.components.Tooltip.create(net.minecraft.network.chat.Component.literal("我的收藏")));add.accept(favorites);cursor-=24;
        Action group = new Action(groupLabel, cursor - groupWidth, 13, groupWidth, 18, false, null,
                button -> net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLink(Minecraft.getInstance().screen, QQ_GROUP).onPress(button));
        group.setDisplayLabel("?");
        group.setTooltip(net.minecraft.client.gui.components.Tooltip.create(net.minecraft.network.chat.Component.literal("点击加入\n群号 966062728")));
        add.accept(group);
    }
    static void brand(GuiGraphics g, int left, int width) {
        if (width < 520) return;
        if (width < 600) asset(g, "wordmark", left + 3, 8, 62, 21, 256, 86);
        else asset(g, "wordmark", left + 3, 7, 82, 28, 256, 86);
    }

    /** Width of the whole tab row (frame, small buttons and single buttons) for the given label sizes. */
    private static int navWidth(net.minecraft.client.gui.Font font, String[] names, int frameLabelWidth, int pad, int gap) {
        int inside = java.util.Arrays.stream(names).limit(3).mapToInt(font::width).sum() + 3 * (pad + gap);
        int outside = java.util.Arrays.stream(names).skip(3).mapToInt(font::width).sum() + 5 * (pad + gap);
        return 12 + frameLabelWidth + inside + outside + 4;
    }

    /** The frame around 档案's small buttons: it only draws (outline and label) and takes no clicks, so the buttons inside stay the only targets. */
    static final class GroupFrame extends Button {
        private final String label;
        GroupFrame(String label, int x, int y, int width, int height) {
            super(x, y, width, height, net.minecraft.network.chat.Component.empty(), b -> {}, DEFAULT_NARRATION);
            this.label = label; active = false;
        }
        @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            g.renderOutline(getX(), getY(), getWidth(), getHeight(), LINE);
            g.drawString(PaleFont.get(), label, getX() + 6, getY() + (getHeight() - 8) / 2, INK, false);
        }
        @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) { return false; }
        @Override public boolean isMouseOver(double mouseX, double mouseY) { return false; }
    }

    /** Light/dark switch right beside the account head; it follows the head as that button changes width. */
    private static final class ThemeToggle extends Button {
        private final int right;
        ThemeToggle(int right, int y) {
            super(right - 24 - 2 - 20, y, 20, 18, net.minecraft.network.chat.Component.literal("切换主题"), button -> {
                var client = Minecraft.getInstance();
                ModSettings.setDarkTheme(!ModSettings.darkTheme());
                MarketTheme.refresh();
                var screen = client.screen;
                if (screen != null) {
                    for (var child : screen.children()) if (child instanceof net.minecraft.client.gui.components.EditBox box) box.setTextColor(INK);
                    screen.resize(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
                }
            }, DEFAULT_NARRATION);
            this.right = right;
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(net.minecraft.network.chat.Component.literal("切换暗色 / 亮色")));
        }
        @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            String head = Action.accountAvatar.get();
            setX(right - (head != null && !head.isEmpty() ? 24 : 40) - 2 - 20);
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), isHoveredOrFocused() ? MarketTheme.c(0xFFE2E8DE) : PAPER);
            String mark = ModSettings.darkTheme() ? "☾" : "☀";
            var font = PaleFont.get();
            g.drawString(font, mark, getX() + (getWidth() - font.width(mark)) / 2, getY() + (getHeight() - 8) / 2, INK, false);
        }
    }

    /** The bell that opens the notification screen. */
    private static final class Bell extends Button {
        Bell(int x, int y) {
            super(x, y, 22, 18, net.minecraft.network.chat.Component.literal("通知"), button -> {
                var screen = Minecraft.getInstance().screen;
                if (!PaleTaleClient.ACCOUNT.loggedIn()) { PaleTaleClient.ACCOUNT.start(Minecraft.getInstance()); return; }
                Minecraft.getInstance().setScreen(open(root,PaleTaleClient.MARKET,Tab.NOTIFICATIONS));
            }, DEFAULT_NARRATION);
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(net.minecraft.network.chat.Component.literal(PaleTaleClient.ACCOUNT.loggedIn() ? "我的通知" : "登录后查看通知")));
        }
        @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            long unread = NoticeWatch.unreadCount();
            // Unread notifications tint the whole bell button red and show their count.
            if (unread > 0) g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), isHoveredOrFocused() ? MarketTheme.c(0xFFF3C9C4) : MarketTheme.c(0xFFF8DDD9));
            else if (isHoveredOrFocused()) g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), MarketTheme.c(0xFFEAECE5));
            asset(g, "bell", getX() + 5, getY() + 4, 12, 12, 24, 24);
            if (unread > 0) {
                var font = PaleFont.get();
                String count = !NoticeWatch.countExact() ? "!" : unread > 99 ? "99+" : String.valueOf(unread);
                int badgeWidth = Math.max(9, font.width(count) + 4), badgeX = getX() + getWidth() - badgeWidth + 2, badgeY = getY() - 3;
                g.fill(badgeX, badgeY, badgeX + badgeWidth, badgeY + 9, MarketTheme.c(0xFFB3392F));
                g.drawString(font, count, badgeX + (badgeWidth - font.width(count)) / 2 + 1, badgeY + 1, 0xFFFFFFFF, false);
            }
        }
    }
    /** Invisible button over the logo. */
    private static final class LogoLink extends Button {
        LogoLink(int x, int y, int width, int height) {
            super(x, y, width, height, net.minecraft.network.chat.Component.literal("开始页"),
                    button -> { if (!(Minecraft.getInstance().screen instanceof HomeScreen)) Minecraft.getInstance().setScreen(home(root, PaleTaleClient.MARKET)); }, DEFAULT_NARRATION);
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(net.minecraft.network.chat.Component.literal("回到开始页")));
        }
        @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) { }
    }

    /** Always-visible installed version; hovering explains where to download the mod safely. */
    private static final class VersionLabel extends Button {
        private final String label;
        VersionLabel(int x, int y, int width, int height, String label) {
            super(x, y, width, height, net.minecraft.network.chat.Component.literal(label), button -> { }, DEFAULT_NARRATION);
            this.label = label; this.active = true;
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(net.minecraft.network.chat.Component.literal("请到官方 QQ 群下载模组，\n避免第三方注入漏洞！")));
        }
        @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) { text(g, label, getX() + 2, getY() + 5, getWidth() - 4, MUTED); }
    }
}
