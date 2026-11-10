package cn.paletale.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Consumer;
import static cn.paletale.companion.MarketTheme.*;

/** All six crops stay visible: two half-width tables, three crops each, with quality symbols as column headings. */
final class OfficialQuotaPanel {
    private static int GREEN() { return MarketTheme.c(0xFF2F6B44); } private static int RED() { return MarketTheme.c(0xFF9E4942); }
    private static final String[] STARS = { "★✰✰", "★★✰", "★★★" };
    private String category = "农作物", account = "";
    private long refreshAt;
    private LocalDate date;
    private Map<String, LedgerOfficial.Quota> quotas = Map.of();
    void buttons(Consumer<Button> add, int x, int y, int w, int h, Runnable rebuild) {
        int tx = x;
        for (String value : List.of("农作物", "矿物", "钓鱼")) {
            int size = PaleFont.get().width(value) + 14;
            add.accept(new Action(value, tx, y + 15, size, 14, category.equals(value), null, b -> { category = value; rebuild.run(); })); tx += size + 4;
        }
        var navigate = new Action("导航", x + (w - 58) / 2, y + h - 16, 58, 16, false, "compass", button -> {
            var shop = CityNavigation.shop(category);
            Navigation.start(new Navigation.Target(shop.name(), shop.blockX(), shop.blockY(), shop.blockZ(), "主城商店", List.of(new int[]{ shop.blockX(), shop.blockY(), shop.blockZ() }), CityNavigation.WORLD));
            rebuild.run();
        });
        navigate.setTooltip(net.minecraft.client.gui.components.Tooltip.create(net.minecraft.network.chat.Component.literal("前往主城的" + category + "收购 NPC")));
        add.accept(navigate);
    }
    void category(int filter) { if (filter >= 1 && filter <= 3) category = List.of("农作物", "矿物", "钓鱼").get(filter - 1); }
    private void refresh() {
        long now = System.currentTimeMillis(); String current = LedgerStore.account(); LocalDate today = LedgerOfficial.quotaDay(now);
        if (now < refreshAt && current.equals(account) && today.equals(date)) return;
        refreshAt = now + 1000; account = current; date = today;
        Map<String, LedgerOfficial.Quota> result = new LinkedHashMap<>();
        for (var quota : LedgerOfficial.quotas(LedgerStore.all(), LedgerStore.official(), account, date)) result.put(quota.key(), quota);
        quotas = result;
    }
    void render(GuiGraphics g, int x, int y, int w, int h) {
        refresh();
        g.hLine(x, x + w, y - 3, LINE);
        fit(g, "每日商店上限（北京时间零点刷新）", x + 2, y + 1, w - 4, INK);
        if (category.equals("农作物")) {
            List<String> names = List.of("大豆", "玉米", "茄子", "蘑菇", "水稻", "水晶番茄");
            int half = (w - 10) / 2, nameWidth = Math.max(38, Math.min(64, half / 3)), colWidth = (half - nameWidth) / 3;
            for (int side = 0; side < 2; side++) for (int star = 0; star < 3; star++) fit(g, STARS[star], x + side * (half + 10) + nameWidth + star * colWidth, y + (h < 100 ? 27 : 31), colWidth - 2, MUTED);
            for (int i = 0; i < names.size(); i++) {
                String name = names.get(i); int cx = x + i / 3 * (half + 10), ry = y + (h < 100 ? 38 : 44) + i % 3 * (h < 100 ? 11 : 13);
                OfficialCropIcons.draw(g, name + " · 1星", cx + 1, ry - 1, 11);
                fit(g, name, cx + 14, ry, nameWidth - 15, INK);
                for (int star = 1; star <= 3; star++) draw(g, quotas.get(name + " · " + star + "星"), cx + nameWidth + (star - 1) * colWidth, ry, colWidth - 2);
            }
        } else {
            List<String> names = category.equals("矿物") ? List.of("金锭", "铁锭") : List.of("钓鱼");
            for (int i = 0; i < names.size(); i++) {
                String name = names.get(i); int ry = y + 35 + i * 14;
                boolean mineral = category.equals("矿物");
                if (mineral) ArchiveModels.INSTANCE.draw(g, new net.minecraft.world.item.ItemStack(name.equals("金锭") ? net.minecraft.world.item.Items.GOLD_INGOT : net.minecraft.world.item.Items.IRON_INGOT), x + 1, ry - 1, 11);
                fit(g, name, x + (mineral ? 14 : 2), ry, mineral ? 58 : 70, INK);
                draw(g, quotas.get(name), x + 82, ry, w - 84);
            }
        }
    }
    private static void draw(GuiGraphics g, LedgerOfficial.Quota quota, int x, int y, int w) {
        if (quota == null) return;
        fit(g, String.format("%,.0f/%,.0f", quota.used(), quota.limit()) + (quota.key().equals("钓鱼") ? " 云币" : ""), x, y, w, quota.full() ? GREEN() : RED());
    }
    /** Keep every quantity visible on small windows instead of replacing the last digits with an ellipsis. */
    private static void fit(GuiGraphics g, String value, int x, int y, int width, int colour) {
        var font = PaleFont.get();
        float scale = Math.min(1f, width / (float) Math.max(1, font.width(value)));
        g.pose().pushMatrix(); g.pose().translate(x, y); g.pose().scale(scale, scale);
        g.drawString(font, value, 0, 0, colour, false); g.pose().popMatrix();
    }
}
