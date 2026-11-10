package cn.paletale.companion;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import static cn.paletale.companion.MarketTheme.*;

/**
 * The welcome guide: shown once, the first time the player is on SimMC with the mod installed (a few seconds after joining, when no other screen is open),
 * and again from the settings or /pt guide. Closing it in any way counts as seen.
 */
final class GuideScreen extends WindowScreen {
    /** One line of the guide: what it is, and how to reach it. */
    private record Entry(net.minecraft.world.item.Item icon, String title, String how) {}
    private final Screen parent;
    private final String openKey;

    GuideScreen(Screen parent, String openKey) { super(Component.literal("PaleTale 新手引导")); this.parent = parent; this.openKey = openKey; }

    private java.util.List<Entry> entries() {
        return java.util.List.of(
                new Entry(net.minecraft.world.item.Items.NAME_TAG, "账号", "进服几秒后会用你的正版账号自动登录网站；还没有网站账号时，会弹出窗口让你设置密码完成注册。"),
                new Entry(net.minecraft.world.item.Items.WRITABLE_BOOK, "苍白档案", "按 " + openKey + " 打开：物品、玩家、组织、广场和 SMC 世界的商店与价格，都能在游戏里直接查。"),
                new Entry(net.minecraft.world.item.Items.COMPASS, "导航", "在档案里点「导航」，或输入 /pt nav x z。会自动配合 Xaero 小地图和 SimMC 地图扩展。"),
                new Entry(net.minecraft.world.item.Items.CHEST, "箱子商店", "走到商店附近会自动读取告示牌，库存和价格几秒内同步到网站。在「广告」里可以把自己的商店绑定到广告。"),
                new Entry(net.minecraft.world.item.Items.BELL, "通知与账本", "收藏的广告改价、补货会在右上角铃铛提醒；收支会自动记进「账本」，每天进服时弹出昨日小结。"),
                new Entry(net.minecraft.world.item.Items.COMPARATOR, "设置", "每个窗口右上角的齿轮是设置，可以关掉不需要的功能，也能在这里重新打开本引导。官网连不上时输入 /pt net 切换备用站。"));
    }

    @Override protected void init() {
        fitWindow();
        int w = Math.min(420, width - 32), x = (width - w) / 2;
        addRenderableWidget(new Action("打开苍白档案", x + w - 196, height - 34, 108, 18, true, null, b -> {
            seen(); minecraft.setScreen(ArchiveNavigation.openLast(null, PaleTaleClient.MARKET));
        }));
        addRenderableWidget(new Action("知道了", x + w - 80, height - 34, 80, 18, false, null, b -> onClose()));
    }

    @Override protected void renderWindow(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderWindow(g, mouseX, mouseY, delta);
        int w = Math.min(420, width - 32), x = (width - w) / 2, y = 14;
        text(g, "欢迎使用 PaleTale · 苍白档案", x, y, w, INK);
        y += 13;
        for (var line : font.split(Component.literal("在游戏里直接查物品、玩家、商店和价格。下面是几个常用的入口："), w)) { g.drawString(font, line, x, y, MUTED, false); y += 10; }
        y += 6;
        g.fill(x, y, x + w, y + 1, LINE);
        y += 8;
        int room = height - 44 - y, each = Math.max(26, Math.min(36, room / entries().size()));
        for (var entry : entries()) {
            if (entry.icon() == net.minecraft.world.item.Items.COMPASS) compass(g, x, y + 1, 12);
            else item(g, new net.minecraft.world.item.ItemStack(entry.icon()), x, y + 1, 12);
            text(g, entry.title(), x + 18, y + 2, 70, ACCENT);
            int lineY = y + 2;
            for (var line : font.split(Component.literal(entry.how()), w - 92)) {
                if (lineY + 9 > y + each - 2) break;
                g.drawString(font, line, x + 92, lineY, INK, false); lineY += 10;
            }
            y += each;
        }
        g.drawString(font, "随时可在设置里重新查看。", x, height - 29, MUTED, false);
    }

    private static void seen() { ModSettings.setGuideShown(true); }
    @Override public void onClose() { seen(); minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
