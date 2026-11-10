package cn.paletale.companion;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import static cn.paletale.companion.MarketTheme.*;

/**
 * Paletale's own settings, opened from the gear at the top right of every window. Sorted into pages down the left side (the key bindings at the bottom of that column); each page is
 * a list of rows with the name and a plain explanation on the left and the control on the right, scrolling with the mouse wheel when there are more rows than fit. Everything is
 * changed on the page itself. Keys: click, then press the new key; Esc or Backspace clears the binding.
 */
final class PaleSettingsScreen extends WindowScreen {
    private enum Page { GENERAL("常规"), SCOREBOARD("计分板"), SOCIAL("社交"), CHAT("聊天辅助"), NAVIGATION("导航"), WHEEL("快捷轮盘"), LEDGER("账本"), KEYS("按键");
        final String label; Page(String label) { this.label = label; } }
    private static final int NAV_W = 92, CONTROL_W = 112, ROW_FULL = 40, ROW_TIGHT = 34;
    private static Page last = Page.GENERAL;
    private record Spec(String label, String help, String control, boolean on, Button.OnPress press) {}
    private record Shown(int y, String label, String help) {}
    private final Screen parent;
    private final List<Spec> specs = new ArrayList<>();
    private final List<Shown> shown = new ArrayList<>();
    private int panelX, panelW, panelTop, rowH = ROW_FULL, perPage, scroll;
    /** The key waiting for a new binding, or null. */
    private KeyMapping listening;

    PaleSettingsScreen(Screen parent) { super(Component.literal("Paletale 设置")); this.parent = parent; }

    private static String onOff(boolean on) { return on ? "开" : "关"; }

    private void row(String label, String help, String control, boolean on, Button.OnPress press) { specs.add(new Spec(label, help, control, on, press)); }
    private void toggle(String label, String help, boolean on, Runnable flip) { row(label, help, onOff(on), on, b -> { flip.run(); rebuildWidgets(); }); }
    private void keyRow(String label, KeyMapping key) {
        if (key == null) return;
        String text = key == listening ? "> 按下新按键 <" : key.isUnbound() ? "未设置" : key.getTranslatedKeyMessage().getString();
        row(label, "点击后按下新按键，Esc 清除。", text, key == listening, b -> { listening = key; rebuildWidgets(); });
    }

    @Override protected void init() {
        fitWindow();
        specs.clear(); shown.clear();
        addRenderableWidget(new Action("返回", width - 86, 10, 64, 16, false, null, b -> onClose()));
        // The pages down the left side; the key bindings sit at the bottom of the column.
        int y = 42;
        for (Page page : Page.values()) {
            if (page == Page.KEYS) continue;
            addRenderableWidget(new Action(page.label, 16, y, NAV_W, 20, page == last, null, b -> select(page)));
            y += 24;
        }
        addRenderableWidget(new Action(Page.KEYS.label, 16, height - 44, NAV_W, 20, last == Page.KEYS, null, b -> select(Page.KEYS)));
        panelX = 16 + NAV_W + 22; panelW = width - panelX - 16; panelTop = 62;
        build();
        // A page with more rows than fit is tighter and scrolls with the wheel.
        int visible = height - panelTop - 26;
        rowH = specs.size() * ROW_FULL <= visible ? ROW_FULL : ROW_TIGHT;
        perPage = Math.max(1, visible / rowH);
        scroll = Math.max(0, Math.min(scroll, specs.size() - perPage));
        for (int i = scroll; i < Math.min(specs.size(), scroll + perPage); i++) {
            var spec = specs.get(i); int rowY = panelTop + (i - scroll) * rowH;
            shown.add(new Shown(rowY, spec.label(), spec.help()));
            addRenderableWidget(new Action(spec.control(), panelX + panelW - CONTROL_W, rowY + Math.max(2, (rowH - 22) / 2), CONTROL_W, 18, spec.on(), null, spec.press()));
        }
    }

    private void select(Page page) { last = page; listening = null; scroll = 0; rebuildWidgets(); }

    private void build() {
        switch (last) {
            case GENERAL -> {
                row("站点", "切换主站和备用站。官网连不上时选备用站，或在聊天栏输入 /pt net。", SiteConfig.label(SiteConfig.current()), false, b -> { PaleTaleClient.useSite(PaleTaleClient.nextSite()); rebuildWidgets(); });
                if (PaleFont.supported()) row("界面字体", "模组窗口里文字的字体，只影响模组窗口，不影响聊天和游戏本身。", ModSettings.paleFont() ? "苍白宋体" : "MC 像素字", false, b -> { ModSettings.setUiFont(ModSettings.paleFont() ? "minecraft" : "serif"); rebuildWidgets(); });
                toggle("每日小结", "每天进服后弹出昨日的收入、支出和卖得最多的商品（按北京时间）。", ModSettings.dailySummary(), () -> ModSettings.setDailySummary(!ModSettings.dailySummary()));
                toggle("酒效果提示", "在背包里悬停已登记的酒时，显示它的效果和等级。", ModSettings.wineTooltip(), () -> ModSettings.setWineTooltip(!ModSettings.wineTooltip()));
                row("登录诊断", "登录出问题时，把登录过程的记录复制下来发给开发者（不含密码和令牌）。", "复制记录", false, b -> PaleTaleClient.copyLoginLog());
                row("网络诊断", "统计最近的请求耗时、超时与失败分类并复制，用来排查不同网络下的连接问题（不含搜索词、令牌或密码）。", "复制汇总", false, b -> PaleTaleClient.copyNetworkLog());
                row("新手引导", "重新看一遍苍白档案的入门引导。", "打开", false, b -> { minecraft.setScreen(null); PaleTaleClient.requestGuide(); });
            }
            case SCOREBOARD -> {
                String[] keys = {"enabled", "merge", "monsoon", "favorites", "social"};
                String[] names = {"计分板", "合并服务器计分板", "季风", "星标", "社交"};
                String[] help = {"显示 PaleTale 计分板。", "服务器自带的右侧计分板并入 PaleTale 面板，不再互相遮挡；关闭后两块各自显示。", "显示 Yun 历季风日期和港口。", "显示星标玩家在线数及商店动态。", "显示社交会话未读消息数。"};
                for (int i = 0; i < keys.length; i++) { String key = keys[i]; boolean on = ModSettings.scoreboard(key); toggle(names[i], help[i], on, () -> ModSettings.setScoreboard(key, !on)); }
            }
            case SOCIAL -> {
                toggle("自动收藏", "在别人的箱子商店买东西时，如果店主有公开广告，就自动收藏它；之后改价、脱销或补货都会通知你。", ModSettings.autoFavorite(), () -> ModSettings.setAutoFavorite(!ModSettings.autoFavorite()));
                toggle("收藏玩家上下线", "需登录；收藏名单本地缓存，上下线由客户端玩家列表检测，首次读取展示当前在线收藏。", ModSettings.favoritePresence(), () -> ModSettings.setFavoritePresence(!ModSettings.favoritePresence()));
                toggle("收藏提示音效", "收藏玩家提示出现时播放音效。", ModSettings.favoriteSound(), () -> ModSettings.setFavoriteSound(!ModSettings.favoriteSound()));
            }
            case CHAT -> {
                String[] keys = { "chatCoordinates", "chatPlaces", "chatPlayers", "chatLinks", "chatItems", "chatMenu", "chatMentions", "chatDoNotDisturb" };
                String[] labels = { "坐标快捷导航", "传送点 / 领地关联", "玩家页面关联", "聊天链接", "物品档案关联", "右键交互菜单", "@提及高亮", "免打扰" };
                String[] help = { "识别 X Z 或 X Y Z，点击导航到主世界坐标。", "点击名称查看 SMC 页面；右键可导航。", "点击玩家名查看 SMC 玩家页，不添加下划线。", "打开完整 HTTP / HTTPS 链接，支持显示折行。", "点击公开物品档案名称打开详情。",
                        "复制完整消息；特殊词条可私聊、预填转账或查看地图。", "聊天中提到你时显示绿色并提示；提到其他在线玩家时显示蓝色。", "开启后 @提及 仍然高亮，但不再响铃。" };
                boolean[] flags = { ModSettings.chatCoordinates(), ModSettings.chatPlaces(), ModSettings.chatPlayers(), ModSettings.chatLinks(), ModSettings.chatItems(), ModSettings.chatMenu(), ModSettings.chatMentions(), ModSettings.chatDoNotDisturb() };
                for (int i = 0; i < keys.length; i++) { String key = keys[i]; boolean on = flags[i]; toggle(labels[i], help[i], on, () -> ModSettings.setChat(key, !on)); }
                var kinds = new ChatTokens.Kind[]{ ChatTokens.Kind.PLAYER, ChatTokens.Kind.LAND, ChatTokens.Kind.NATION, ChatTokens.Kind.TRAFFIC };
                String[] names = { "点击玩家", "点击领地", "点击国家", "点击传送点" };
                for (int i = 0; i < kinds.length; i++) {
                    var kind = kinds[i]; var choices = ChatAssist.choices(kind); String now = ChatAssist.defaultOperation(kind);
                    row(names[i], "聊天里点击这类名称时的默认动作，点击依次切换；右键仍可打开完整菜单。", ChatAssist.choiceLabel(now), false, b -> { ModSettings.setChatClickAction(kind.name(), choices.get((choices.indexOf(now) + 1) % choices.size())); rebuildWidgets(); });
                }
            }
            case NAVIGATION -> {
                toggle("导航读取领地", "导航时参考你的领地列表，可用回领地传送的路线会算进去。", ModSettings.readLands(), () -> ModSettings.setReadLands(!ModSettings.readLands()));
                toggle("Xaero 路径点", "装了 Xaero 小地图时，导航目的地会同步成一个临时路径点；导航结束后自动删除。", ModSettings.xaeroWaypoint(), () -> ModSettings.setXaeroWaypoint(!ModSettings.xaeroWaypoint()));
                row("导航粒子", "导航时空中指向下一步的粒子线的样式，点击依次切换；选「关闭」只保留顶部的指南针条。", NavParticles.current().label(), false, b -> { ModSettings.setNavParticle(NavParticles.next().key()); rebuildWidgets(); });
            }
            case WHEEL -> {
                row("打开方式", "长按：按住呼出，松开选中。点击：按一下呼出并保持，左键选中，右键关闭。", ModSettings.radialClick() ? "点击" : "长按", false, b -> { ModSettings.setRadialClick(!ModSettings.radialClick()); rebuildWidgets(); });
                row("编辑轮盘", "改每个格子的名称、指令和图标，也可以绑定某个领地或玩家。", "编辑…", false, b -> minecraft.setScreen(new RadialEditScreen(this, 0)));
                keyRow("快捷轮盘按键", PaleTaleClient.RADIAL);
            }
            case LEDGER -> {
                row("账号范围", "只显示当前账号时，换账号玩不会混账；改为显示全部账号，可看到文件里所有账号的记录（包括别人分享给你的账本）。", LedgerStore.separateAccounts() ? "当前账号" : "全部账号", false, b -> { LedgerStore.setSeparateAccounts(!LedgerStore.separateAccounts()); rebuildWidgets(); });
                row("保留期限", "超过期限的记录会自动删除（每日备份仍保留 7 天）。默认 3 个月。", LedgerStore.retentionLabel(LedgerStore.retentionDays()), false, b -> {
                    int current = LedgerStore.retentionDays(), next = LedgerStore.RETENTION_CHOICES[0];
                    for (int i = 0; i < LedgerStore.RETENTION_CHOICES.length; i++) if (LedgerStore.RETENTION_CHOICES[i] == current) next = LedgerStore.RETENTION_CHOICES[(i + 1) % LedgerStore.RETENTION_CHOICES.length];
                    LedgerStore.setRetentionDays(next); rebuildWidgets();
                });
                row("账本文件", "账本只保存在本机，每天一份备份放在 backups 文件夹（保留 7 天）；分享账本把 ledger.json 发给对方即可。", "打开文件夹", false, b -> {
                    try { java.nio.file.Files.createDirectories(LedgerStore.folder()); net.minecraft.util.Util.getPlatform().openFile(LedgerStore.folder().toFile()); }
                    catch (java.io.IOException | RuntimeException failed) { minecraft.keyboardHandler.setClipboard(LedgerStore.folder().toString()); RadialWheel.toast(minecraft, "无法自动打开，路径已复制：" + LedgerStore.folder()); }
                });
            }
            case KEYS -> {
                keyRow("打开苍白档案", PaleTaleClient.OPEN);
                keyRow("取消导航", PaleTaleClient.NAV_CANCEL);
                keyRow("导航：下一步传送", PaleTaleClient.NAV_TELEPORT);
                keyRow("快捷轮盘", PaleTaleClient.RADIAL);
            }
        }
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (listening != null) {
            KeyMapping key = listening;
            listening = null;
            // Esc and Backspace both clear the binding; any other key becomes the binding.
            key.setKey(event.key() == 256 || event.key() == 259 ? InputConstants.UNKNOWN : InputConstants.Type.KEYSYM.getOrCreate(event.key()));
            KeyMapping.resetMapping();
            minecraft.options.save();
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (specs.size() <= perPage) return super.mouseScrolled(x, y, horizontal, vertical);
        int next = Math.max(0, Math.min(specs.size() - perPage, scroll - (int) Math.signum(vertical)));
        if (next != scroll) { scroll = next; rebuildWidgets(); }
        return true;
    }

    @Override protected void windowBackground(GuiGraphics g, int x, int y, float delta) { g.fill(0, 0, width, height, PAPER); }
    @Override protected void renderWindow(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderWindow(g, mouseX, mouseY, delta);
        text(g, "Paletale 设置", 16, 14, 200, INK);
        g.fill(16 + NAV_W + 10, 42, 16 + NAV_W + 11, height - 24, LINE);
        text(g, last.label, panelX, 44, panelW, INK);
        int helpW = panelW - CONTROL_W - 16;
        for (Shown row : shown) {
            g.drawString(font, font.plainSubstrByWidth(row.label(), helpW), panelX, row.y() + 2, INK, false);
            var lines = font.split(Component.literal(row.help()), helpW);
            for (int i = 0; i < lines.size() && i < (rowH >= ROW_FULL - 2 ? 2 : 1); i++) g.drawString(font, lines.get(i), panelX, row.y() + 16 + i * 11, MUTED, false);
            g.hLine(panelX, panelX + panelW, row.y() + rowH - 3, LINE);
        }
        if (specs.size() > perPage) {
            int track = perPage * rowH, thumb = Math.max(12, track * perPage / specs.size()), at = panelTop + (track - thumb) * scroll / Math.max(1, specs.size() - perPage);
            g.fill(panelX + panelW + 6, panelTop, panelX + panelW + 7, panelTop + track, LINE); g.fill(panelX + panelW + 5, at, panelX + panelW + 8, at + thumb, ACCENT);
        }
    }
    @Override public void onClose() { if (listening != null) { listening = null; rebuildWidgets(); return; } minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
