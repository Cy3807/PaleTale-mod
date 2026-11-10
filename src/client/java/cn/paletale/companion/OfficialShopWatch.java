package cn.paletale.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import java.util.HashMap;
import java.util.Map;

/** Reads only menu slots already sent by the server; never clicks or sells anything. */
final class OfficialShopWatch {
    private static long nextScan;
    private static Object lastScreen;
    private static String lastAccount = "";
    private static final Map<Integer, String> OBSERVED = new HashMap<>();
    private OfficialShopWatch() {}
    static Map<String, String> names() {
        Map<String, String> result = new HashMap<>();
        String account = LedgerStore.account();
        for (var reading : LedgerStore.official()) if (reading.account().equals(account) && !reading.name().isEmpty()) result.merge(reading.name(), reading.product(), (a, b) -> a.equals(b) ? a : "");
        result.values().removeIf(String::isEmpty);
        return result;
    }
    static void fishing(String raw, long now) { LedgerOfficial.fishing(LedgerStore.account(), raw, now).ifPresent(LedgerStore::official); }
    static void tick(Minecraft client) {
        long now = System.currentTimeMillis();
        if (client.player == null || !(client.screen instanceof AbstractContainerScreen<?> screen)) { lastScreen = null; OBSERVED.clear(); return; }
        if (now < nextScan) return;
        String account = LedgerStore.account();
        if (lastScreen != screen || !lastAccount.equals(account)) { lastScreen = screen; lastAccount = account; OBSERVED.clear(); }
        nextScan = now + 250;
        for (var slot : screen.getMenu().slots) {
            if (slot.container == client.player.getInventory()) continue;
            var stack = slot.getItem(); var lore = stack.get(DataComponents.LORE);
            if (stack.isEmpty() || lore == null) { OBSERVED.remove(slot.index); continue; }
            LedgerOfficial.tooltip(account, stack.getHoverName().getString(), lore.lines().stream().map(line -> line.getString()).toList(), now).ifPresent(reading -> {
                String signature = reading.name() + "|" + reading.product() + "|" + reading.used() + "|" + reading.limit();
                // An unchanged open menu must not acquire a fresh timestamp at midnight and carry yesterday's quota forward.
                if (!signature.equals(OBSERVED.put(slot.index, signature))) LedgerStore.official(reading);
            });
        }
    }
}
