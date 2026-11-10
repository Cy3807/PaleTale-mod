package cn.paletale.companion;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import static cn.paletale.companion.MarketData.*;
import static cn.paletale.companion.MarketTheme.*;

/** Binds nearby chest-shop signs to one ad product. Nothing is sent until the player confirms. */
final class ShopBindScreen extends WindowScreen {
    private final MarketScreen list;
    private final Screen parent;
    private final Ad ad;
    private final int productIndex;
    private final Product product;
    private List<ShopScanner.Found> shops = List.of();
    private final Set<String> bound = new HashSet<>();
    /** Readings of the bound shops: saved data first, then what this screen binds or unbinds; used for the consistency rules and out-of-range rows. */
    private final java.util.Map<String, ShopSigns.Reading> boundReadings = new java.util.LinkedHashMap<>();
    private int radius = 32, offset, rowsPerPage, left, panelWidth;
    private boolean scanned, busy;
    private String message = "";
    private boolean changed;

    ShopBindScreen(Screen parent, MarketScreen list, Ad ad, int productIndex) {
        super(Component.literal("绑定箱子商店"));
        this.parent = parent; this.list = list; this.ad = ad; this.productIndex = productIndex; this.product = ad.products().get(productIndex);
        for (Shop shop : product.shops()) { bound.add(shop.reading().key()); boundReadings.put(shop.reading().key(), shop.reading()); }
    }
    /** The sign's item must be the linked item (archive or vanilla) or, without a link, the product itself. */
    private boolean matches(ShopSigns.Reading reading) {
        String item = norm(reading.itemName());
        if (item.isEmpty()) return false;
        boolean vanilla = !product.vanillaItem().isEmpty();
        java.util.List<String> names = new java.util.ArrayList<>();
        if (vanilla) { names.add(product.vanillaItemName()); names.add(product.vanillaItem()); }
        else if (!product.itemName().isBlank()) names.add(product.itemName());
        if (names.stream().allMatch(name -> norm(name).isEmpty())) names = java.util.List.of(product.name());
        for (String name : names) {
            String target = norm(name);
            if (target.isEmpty()) continue;
            if (item.equals(target)) return true;
            // Vanilla names must match exactly; custom names may be decorated, so a contained name of two or more characters counts.
            if (!vanilla && ServerItemCatalog.sameName(reading.itemName(),name)) return true;
        }
        return false;
    }
    /** Unlinked products take any sign (matching ones are only pre-selected); linked ones need the item's name. */
    private boolean allowed(ShopSigns.Reading reading) { return (product.itemId() <= 0 && product.vanillaItem().isEmpty()) || matches(reading); }
    private static String norm(String value) { return value == null ? "" : java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC).replaceAll("[\\s_]+", "").toLowerCase(Locale.ROOT); }
    private void scan() {
        // Signs that fit this product come first; the nearest-first order is kept inside each group.
        var found = new java.util.ArrayList<>(ShopScanner.shops(minecraft, radius));
        // Already bound shops stay on top even when they are outside the scan range (shown from the saved data); then matching signs.
        var inRange = new HashSet<String>(); found.forEach(shop -> inRange.add(shop.reading().key()));
        for (var saved : boundReadings.values())
            if (!inRange.contains(saved.key())) found.add(new ShopScanner.Found(saved, List.of(), Double.NaN));
        found.sort(java.util.Comparator.comparing((ShopScanner.Found shop) -> bound.contains(shop.reading().key()) ? 0 : matches(shop.reading()) ? 1 : 2)
                .thenComparingDouble(shop -> Double.isNaN(shop.distance()) ? Double.MAX_VALUE : shop.distance()));
        shops = found;
        offset = 0; scanned = true;
    }
    @Override protected void init() {
        fitWindow();
        panelWidth = Math.min(760, width - 20); left = (width - panelWidth) / 2;
        rowsPerPage = Math.max(2, (height - 120) / 28);
        if (!scanned) scan();
        int row = 0, total = shops.size();
        offset = Math.max(0, Math.min(offset, Math.max(0, total - rowsPerPage)));
        for (int i = offset; i < Math.min(shops.size(), offset + rowsPerPage); i++, row++) {
            var found = shops.get(i);
            addRenderableWidget(new RowButton(found, left, 58 + row * 28, panelWidth, 26));
        }
        int buttons = 4, gap = 4, w = (panelWidth - gap * (buttons - 1)) / buttons, y = height - 28;
        addRenderableWidget(new Action("返回", left, y, w, 19, false, null, b -> onClose()));
        addRenderableWidget(new Action("扫描：" + radius + "格", left + (w + gap), y, w, 19, false, null, b -> { radius = radius == 16 ? 32 : radius == 32 ? 64 : 16; scan(); rebuildWidgets(); }));
        var auto = addRenderableWidget(new Action("一键绑定符合要求的", left + 2 * (w + gap), y, w, 19, true, null, b -> bindAllFitting()));
        auto.active = !busy;
        var clear = addRenderableWidget(new Action("取消全部绑定", left + 3 * (w + gap), y, w, 19, false, null, b -> send(false, List.copyOf(boundReadings.values()))));
        clear.active = !busy && !boundReadings.isEmpty();
    }
    /**
     * Binds every nearby sign that fits this product and agrees with what is already bound (same item, same direction). With nothing bound yet
     * the largest fitting group (item + direction) is taken, nearest signs first.
     */
    private void bindAllFitting() {
        if (busy) return;
        var candidates = new ArrayList<ShopSigns.Reading>();
        for (var shop : shops) { var r = shop.reading(); if (!Double.isNaN(shop.distance()) && !bound.contains(r.key()) && allowed(r) && matches(r)) candidates.add(r); }
        ShopSigns.Reading reference = boundReadings.values().stream().findFirst().orElse(null);
        if (reference == null && !candidates.isEmpty()) {
            var groups = new java.util.LinkedHashMap<String, Integer>();
            for (var r : candidates) groups.merge(norm(r.itemName()) + "|" + r.mode(), 1, Integer::sum);
            String best = groups.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).get().getKey();
            reference = candidates.stream().filter(r -> (norm(r.itemName()) + "|" + r.mode()).equals(best)).findFirst().get();
        }
        final ShopSigns.Reading base = reference;
        var fitting = candidates.stream().limit(60).filter(r -> base != null && norm(r.itemName()).equals(norm(base.itemName())) && r.mode() == base.mode()).toList();
        if (fitting.isEmpty()) { message = "附近没有可绑定的符合要求的箱子商店。"; rebuildWidgets(); return; }
        send(true, fitting);
    }
    /** One click binds a sign, another click on it unbinds again. */
    private void toggle(ShopSigns.Reading reading) {
        if (busy) return;
        if (bound.contains(reading.key())) { send(false, List.of(reading)); return; }
        if (!allowed(reading)) {
            message = "该商品已关联物品「" + (product.vanillaItem().isEmpty() ? product.itemName() : product.vanillaItemName()) + "」，只能绑定告示牌物品名一致的箱子商店。";
            rebuildWidgets(); return;
        }
        for (var other : boundReadings.values()) {
            // All shops of one product sell (or all buy) the same item; different owners are fine.
            if (!norm(other.itemName()).equals(norm(reading.itemName()))) { message = "同一商品只能绑定物品名相同的箱子商店（不同店主可以）。"; rebuildWidgets(); return; }
            if (other.mode() != reading.mode()) { message = "同一商品不能同时绑定出售和收购的箱子商店。"; rebuildWidgets(); return; }
        }
        send(true, List.of(reading));
    }
    private void send(boolean doBind, List<ShopSigns.Reading> picked) {
        String token = PaleTaleClient.ACCOUNT.token();
        if (token == null) { message = "请先登录正版账号（进入 SIMMC 后会自动登录）。"; rebuildWidgets(); return; }
        if (picked.isEmpty() || busy) return;
        busy = true; message = doBind ? "正在绑定…" : "正在取消绑定…"; rebuildWidgets();
        var call = doBind ? PaleTaleClient.SHOPS.bind(token, ad.code(), productIndex, product.bindingKey(), picked)
                : PaleTaleClient.SHOPS.unbind(token, ad.code(), picked.stream().map(r -> new ShopApi.Position(r.world(), r.x(), r.y(), r.z())).toList());
        call.whenComplete((count, failure) -> minecraft.execute(() -> {
            busy = false;
            if (failure != null) {
                Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                message = cause instanceof IllegalStateException ? cause.getMessage() : "网络连接失败，请重试。";
                if (minecraft.screen == this) rebuildWidgets(); return;
            }
            PaleTaleClient.WATCH.refresh(minecraft);
            for (var r : picked) { if (doBind) { bound.add(r.key()); boundReadings.put(r.key(), r); } else { bound.remove(r.key()); boundReadings.remove(r.key()); } }
            scan();
            message = doBind ? "已绑定 " + count + " 个。" : "已取消绑定 " + count + " 个。";
            changed = true;
            rebuildWidgets();
        }));
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        offset = Math.max(0, offset + (vertical > 0 ? -2 : vertical < 0 ? 2 : 0)); rebuildWidgets(); return true;
    }
    @Override protected void windowBackground(GuiGraphics g, int x, int y, float delta) { g.fill(0, 0, width, height, PAPER); line(g, left, 38, panelWidth); }
    @Override protected void renderWindow(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderWindow(g, mouseX, mouseY, delta);
        text(g, "绑定箱子商店 · " + product.name(), left, 14, panelWidth, INK);
        text(g, "附近 " + shops.size() + " 个可识别箱子商店 · 已绑定 " + bound.size() + " 个 · 点一下绑定，再点一下取消绑定，库存会合计", left, 44, panelWidth, MUTED);
        if (shops.isEmpty()) text(g, "附近没有识别到箱子商店告示牌。请靠近商店，必要时调大扫描范围。", left + 4, 66, panelWidth - 8, MUTED);
        if(message.isEmpty()&&!product.bindingWarning().isEmpty())text(g,product.bindingWarning(),left,height-53,panelWidth,MarketTheme.c(0xFF9E4942));
        text(g, message.isEmpty() ? "规则：B/买/出售=商店出售，S/卖/收购=商店收购；同一商品只能绑定同物品名、同方向（出售或收购）的箱子；仅广告作者与协助者可绑定" : message, left, height - 42, panelWidth, message.isEmpty() ? MUTED : MarketTheme.c(0xFF9E4942));
    }
    /** Return to the same detail; the detail re-reads the server after a successful edit. */
    @Override public void onClose() {
        if (busy) return;
        minecraft.setScreen(parent);
        if (changed && parent instanceof MarketDetailScreen detail) detail.refreshAfterBinding();
    }
    @Override public boolean isPauseScreen() { return false; }

    private final class RowButton extends Button {
        private final ShopScanner.Found found;
        RowButton(ShopScanner.Found found, int x, int y, int width, int height) {
            super(x, y, width, height, Component.literal("绑定／取消绑定 " + found.reading().itemName()), b -> ShopBindScreen.this.toggle(found.reading()), DEFAULT_NARRATION);
            this.found = found;
        }
        @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            var r = found.reading();
            int x = getX(), y = getY();
            g.fill(x, y, x + getWidth(), y + getHeight(), isHoveredOrFocused() ? MarketTheme.c(0xFFECEFE8) : MarketTheme.c(0xFFFAFAF7));
            g.renderOutline(x, y, getWidth(), getHeight(), LINE);
            boolean on = bound.contains(r.key());
            g.renderOutline(x + 6, y + 8, 10, 10, INK);
            if (on) g.fill(x + 8, y + 10, x + 14, y + 16, INK);
            text(g, r.itemName() + " · " + ShopSigns.modeLabel(r.mode()) + " ×" + r.quantity() + (matches(r) ? " · 匹配本商品" : ""), x + 24, y + 4, getWidth() - 100, matches(r) ? ACCENT : INK);
            text(g, r.owner() + " · " + ShopSigns.priceLabel(r) + " · " + r.x() + "," + r.y() + "," + r.z() + " · " + (Double.isNaN(found.distance()) ? "不在扫描范围" : Math.round(found.distance()) + "格"), x + 24, y + 15, getWidth() - 100, MUTED);
            if (!allowed(r)) text(g, "物品不符", x + getWidth() - 60, y + 9, 54, MarketTheme.c(0xFF9E4942));
            else if (bound.contains(r.key())) text(g, "已绑定", x + getWidth() - 60, y + 9, 54, ACCENT);
        }
    }
}
