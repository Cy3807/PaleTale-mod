package cn.paletale.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import java.time.LocalDate;

/** Once a day, a few seconds after joining a server, a toast with yesterday's ledger: income, spending, net and the best seller. */
final class DailySummary {
    private static long showAt;
    private DailySummary() {}

    static void register() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> showAt = System.currentTimeMillis() + 8000);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(DailySummary::tick);
    }
    private static void tick(Minecraft client) {
        if (showAt == 0 || System.currentTimeMillis() < showAt || client.level == null) return;
        showAt = 0;
        if (!ModSettings.dailySummary() || !LedgerStore.enabled()) return;
        LocalDate today = LocalDate.now(LedgerStore.BEIJING);
        if (today.toString().equals(ModSettings.lastDaily())) return;
        ModSettings.setLastDaily(today.toString());
        var summary = LedgerDaily.summarize(LedgerStore.visible(), today.minusDays(1), LedgerStore.BEIJING);
        if (summary == null) return;
        String body = "收入 " + String.format("%,.2f", summary.income()) + " · 支出 " + String.format("%,.2f", summary.spent()) + " · 净 " + (summary.net() >= 0 ? "+" : "-") + String.format("%,.2f", Math.abs(summary.net()))
                + (summary.topItem().isEmpty() ? "" : "\n卖得最多：" + summary.topItem() + " ×" + summary.topQuantity());
        client.getToastManager().addToast(SystemToast.multiline(client, new SystemToast.SystemToastId(), Component.literal("Paletale · 昨日小结（" + summary.count() + " 笔）"), Component.literal(body)));
    }
}
