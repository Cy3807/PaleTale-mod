package cn.paletale.companion;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import static cn.paletale.companion.MarketTheme.*;

/** One ledger entry in full: every field and the original server line it came from. */
final class LedgerDetailScreen extends WindowScreen {
    private final Screen parent;
    private final LedgerEntry entry;
    LedgerDetailScreen(Screen parent, LedgerEntry entry) { super(Component.literal("账本记录")); this.parent = parent; this.entry = entry; }

    @Override protected void init() {
        fitWindow();
        int w = Math.min(380, width - 40), x = (width - w) / 2;
        addRenderableWidget(new Action("返回", x, height - 34, 70, 18, false, null, b -> onClose()));
    }
    @Override protected void windowBackground(GuiGraphics g, int x, int y, float delta) { g.fill(0, 0, width, height, PAPER); }
    @Override protected void renderWindow(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderWindow(g, mouseX, mouseY, delta);
        int w = Math.min(380, width - 40), x = (width - w) / 2, y = 20;
        text(g, "账本记录 · " + entry.kind().label, x, y, w, INK);
        List<String> rows = new ArrayList<>();
        rows.add("时间：" + LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(entry.time()), LedgerStore.BEIJING).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        if (entry.kind() != LedgerEntry.Kind.SOLD_OUT) rows.add("金额：" + (entry.signed() >= 0 ? "+" : "-") + String.format("%,.2f", entry.amount()) + " 云币" + (entry.quantity() > 0 && (entry.kind().group().equals("shop") || entry.kind().group().equals("official")) ? "（单价 " + String.format("%,.2f", entry.unitPrice()) + "）" : ""));
        if (entry.kind().group().equals("official")) {
            rows.add("来源：官方 · " + entry.kind().label.replace("官方", ""));
            if (entry.kind() == LedgerEntry.Kind.OFFICIAL_PURCHASE && entry.item().isEmpty()) rows.add("购买提示未提供物品名称与数量，仅记录支出金额。");
            else if (entry.quantity() == 0 && entry.kind() != LedgerEntry.Kind.OFFICIAL_FISH && entry.kind() != LedgerEntry.Kind.OFFICIAL_PURCHASE) rows.add("数量未确认：总金额无法唯一确定星级与件数。");
        }
        if (!entry.counterparty().isEmpty()) rows.add("对方：" + entry.counterparty());
        if (!entry.item().isEmpty()) rows.add("物品：" + entry.item() + (entry.quantity() > 0 ? " ×" + entry.quantity() : ""));
        if (entry.x() != null) rows.add("位置：" + entry.x() + ", " + entry.y() + ", " + entry.z());
        if (!entry.adCode().isEmpty()) rows.add("关联广告：" + entry.adCode() + " · " + entry.adTitle() + (entry.productName().isBlank() ? "" : " · " + entry.productName()));
        if (entry.kind() == LedgerEntry.Kind.SOLD_OUT) { var pace = LedgerSales.of(LedgerStore.visible(), entry, System.currentTimeMillis()); rows.add("销售速度：" + (pace == null ? "账本里还没有这个商品的销售记录" : pace.describe())); }
        rows.add("账号：" + (entry.accountName().isEmpty() ? "未记录" : entry.accountName()) + (entry.account().isEmpty() ? "" : "（" + entry.account().substring(0, Math.min(8, entry.account().length())) + "…）"));
        int line = y + 18;
        for (String row : rows) { for (var part : font.split(Component.literal(row), w)) { g.drawString(font, part, x, line, INK, false); line += 12; } line += 2; }
        line += 4;
        g.drawString(font, "服务器原文：", x, line, MUTED, false); line += 13;
        for (var part : font.split(Component.literal(entry.raw().isEmpty() ? "（这条记录没有保存原文）" : entry.raw()), w)) { g.drawString(font, part, x, line, MUTED, false); line += 12; }
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
