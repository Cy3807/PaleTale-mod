package cn.paletale.companion;

import net.minecraft.client.Minecraft;
import java.util.HashSet;
import java.util.Set;

/** Feeds the ledger from the server's system messages and boss-bar titles. Only messages the server itself sent are read, never other players' chat. */
final class LedgerWatch {
    private static String lastText = ""; private static long lastAt;
    private static final Set<String> SEEN_BOSS = new HashSet<>();
    /** The last few lines (with their time), to find who sent a transfer whose confirmation line does not say. */
    private static final java.util.ArrayDeque<String[]> RECENT = new java.util.ArrayDeque<>();
    private static String pendingTransferId = ""; private static long pendingTransferAt;
    private static long nextBossScan;
    /** Ads already favourited automatically this session, so one purchase run does not repeat the call. */
    private static final Set<Integer> AUTO_FAVORITED = new HashSet<>();
    private LedgerWatch() {}

    static void register() {
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            // The action bar message arrives once as a packet (the client redraws it); the 400 ms repeat guard covers servers that resend it.
            LandSpawns.onMessage(message.getString());
            handle(message.getString(), overlay);
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(LedgerWatch::tick);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> LedgerStore.flushNow());
    }
    private static String me() { var user = Minecraft.getInstance().getUser(); return user == null ? "" : user.getName(); }

    private static final LedgerShopTalk TALK = new LedgerShopTalk();
    private static final TipDedup TIPS = new TipDedup();

    /** One message may hold several lines (the server's shop blocks); each line is handled on its own. */
    static void handle(String raw, boolean overlay) {
        if (!LedgerStore.enabled() || raw == null) return;
        if (LedgerOfficial.fishing(LedgerStore.account(), raw, System.currentTimeMillis()).isPresent()) { handleLine(raw, overlay); return; }
        for (String line : raw.split("\\R")) handleLine(line, overlay);
    }

    private static void handleLine(String raw, boolean overlay) {
        long now = System.currentTimeMillis();
        String text = LedgerParser.clean(raw);
        if (text.isEmpty()) return;
        var official = LedgerOfficial.parse(raw, now, OfficialShopWatch.names());
        // A repeated chat line is a real repeat (rapid tips may look identical); only action-bar resends are damped.
        boolean tipLine = text.contains("打赏");
        if (official.isEmpty() && overlay && text.equals(lastText) && now - lastAt < (tipLine ? 100 : 400)) return;
        lastText = text; lastAt = now;
        // A line shortly after a received transfer may name its sender.
        if (!pendingTransferId.isEmpty() && now - pendingTransferAt < 3000) {
            var source = LedgerParser.transferSource(text, me());
            if (source.isPresent()) { LedgerStore.setCounterparty(pendingTransferId, source.get()); pendingTransferId = ""; }
        }
        OfficialShopWatch.fishing(raw, now);
        var parsed = official.isPresent() ? official.map(e -> e.withRaw(text)) : LedgerParser.parse(text, me(), now);
        if (parsed.isPresent()) {
            var entry = parsed.get();
            if (entry.kind() == LedgerEntry.Kind.TRANSFER_IN) {
                for (String[] earlier : RECENT) if (now - Long.parseLong(earlier[0]) < 3000) {
                    var source = LedgerParser.transferSource(earlier[1], me());
                    if (source.isPresent()) { entry = entry.withCounterparty(source.get()); break; }
                }
                pendingTransferId = entry.id(); pendingTransferAt = now;
            }
            if (TipDedup.isTip(entry) && TIPS.duplicate(entry, false, now)) { RECENT.addLast(new String[]{ String.valueOf(now), text }); while (RECENT.size() > 6) RECENT.removeFirst(); return; }
            LedgerStore.add(entry);
            if (entry.kind() == LedgerEntry.Kind.SOLD_OUT) soldOutToast(LedgerStore.autoLink(entry));
        } else {
            // Trading in someone else's shop: remember the shop block, record the result line whenever it comes.
            var talk = TALK.feed(text, now);
            if (talk.isPresent()) { LedgerStore.add(talk.get()); linkSellerAd(talk.get()); }
            else if (LedgerParser.looksFinancial(text)) LedgerStore.addUnmatched(text);
        }
        RECENT.addLast(new String[]{ String.valueOf(now), text });
        while (RECENT.size() > 6) RECENT.removeFirst();
    }

    private static final SoldOutBatch SOLD_OUT=new SoldOutBatch();
    private static final net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId SOLD_OUT_ID=new net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId();
    private static void soldOutToast(LedgerEntry entry) {
        var client=Minecraft.getInstance();String server=client.getCurrentServer()==null?"local":client.getCurrentServer().ip;
        String scope=LedgerStore.account()+"|"+server+"|"+(client.level==null?"":client.level.dimension().identifier());
        String shop=entry.x()==null?entry.item():entry.x()+"|"+entry.y()+"|"+entry.z();SOLD_OUT.offer(scope,shop,System.currentTimeMillis());
    }
    private static void soldOutTick(Minecraft client){
        if(client.level==null){SOLD_OUT.reset();net.minecraft.client.gui.components.toasts.SystemToast.forceHide(client.getToastManager(),SOLD_OUT_ID);return;}
        int count=SOLD_OUT.drain(System.currentTimeMillis());if(count>0)net.minecraft.client.gui.components.toasts.SystemToast.addOrUpdate(client.getToastManager(),SOLD_OUT_ID,
            net.minecraft.network.chat.Component.literal("Paletale · 商店脱销"),net.minecraft.network.chat.Component.literal("近期有 "+count+" 家商店脱销了。"));
    }

    /** A purchase from (or a sale into) someone else's shop: look the owner's ad up on the site and link it when it is unambiguous. */
    private static void linkSellerAd(LedgerEntry entry) {
        String token = PaleTaleClient.ACCOUNT.token();
        if (token == null || entry.counterparty().isEmpty() || entry.item().isEmpty()) return;
        PaleTaleClient.SHOPS.lookup(token, entry.counterparty(), entry.item()).whenComplete((ads, failure) -> {
            if (failure != null || ads == null || ads.isEmpty()) return;
            var first = ads.get(0);
            for (var other : ads) if (!other.adCode().equals(first.adCode())) return; // two different ads: leave it unlinked
            if (ModSettings.autoFavorite() && entry.kind() == LedgerEntry.Kind.SHOP_PURCHASE && first.adId() > 0 && AUTO_FAVORITED.add(first.adId()))
                PaleTaleClient.FAVORITES.set(token, "ad", first.adId(), true).exceptionally(error -> { AUTO_FAVORITED.remove(first.adId()); return null; });
            Minecraft.getInstance().execute(() -> LedgerStore.setAd(entry.id(), first.adCode(), first.adTitle(), first.productName()));
        });
    }

    private static void tick(Minecraft client) {
        LedgerStore.tick();soldOutTick(client);
        OfficialShopWatch.tick(client);
        long now = System.currentTimeMillis();
        if (client.level == null || now < nextBossScan) return;
        nextBossScan = now + 500;
        Set<String> present = new HashSet<>();
        for (String title : BossTexts.titles(client)) {
            String text = LedgerParser.clean(title);
            present.add(text);
            if (!SEEN_BOSS.add(text) || !LedgerStore.enabled()) continue;
            LedgerParser.parse(text, me(), now).ifPresent(e -> { if (!TipDedup.isTip(e) || !TIPS.duplicate(e, true, now)) LedgerStore.add(e); });
        }
        SEEN_BOSS.retainAll(present); // a tip shown again later (after the bar went away) counts again
    }
}
