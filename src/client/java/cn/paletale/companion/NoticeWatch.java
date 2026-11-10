package cn.paletale.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.Set;

/** Polls for updates to favourited ads once a minute while logged in and shows them as game toasts. */
final class NoticeWatch {
    private static final long POLL_MS = 60_000;
    private static final String[] KIND = { "price", "价格变动", "state", "状态变动", "published", "新发布", "effect_approved", "酒效果审核通过", "effect_rejected", "酒效果审核未通过", "item_approved", "地图画审核通过", "item_rejected", "地图画审核未通过", "review_wine", "待审核酒效果", "review_map", "待审核地图画" };
    private final NoticeV2Api api;
    /** Ids already toasted for the signed-in account; kept on disk so a restart or relog never pops the same unread notice again. */
    private final Set<Long> shown = new java.util.LinkedHashSet<>();
    private String shownAccount = "";
    /** Unread notifications seen at the last poll; drives the red dot on the bell. */
    private static volatile long unreadCount;
    private static long revision;
    private static volatile int shopPreviewCount = -1;
    private static volatile long shopPreviewAt;
    static int shopPreviewCount() { return System.currentTimeMillis() - shopPreviewAt < 90000 ? shopPreviewCount : -1; }
    private static boolean exactCount;
    static boolean countExact() { return exactCount; }
    static long unreadCount() { return unreadCount; }
    static void updateUnread(long count) { shopPreviewCount = -1; exactCount = count >= 0; unreadCount = exactCount ? count : Math.max(1, unreadCount); revision++; }
    static void acceptPreview(NoticeV2Api.Preview preview) {
        if (preview.unreadTotal() >= 0) updateUnread(preview.unreadTotal());
        else { exactCount = false; if (!preview.items().isEmpty() && unreadCount == 0) unreadCount = 1; revision++; }
        shopPreviewCount = (int) preview.items().stream().filter(item -> !item.read() && item.targetType().equals("ad")).count();
        if (shopPreviewCount == 0 && preview.hasMore()) shopPreviewCount = -1;
        shopPreviewAt = System.currentTimeMillis();
    }
    private long next;
    private boolean polling, wasLoggedIn;
    NoticeWatch(NoticeApi api) { this.api = new NoticeV2Api(api::site); }

    void tick(Minecraft client) {
        boolean loggedIn = PaleTaleClient.ACCOUNT.loggedIn();
        if (!loggedIn) { if (wasLoggedIn) { shown.clear(); shownAccount = ""; updateUnread(0); } wasLoggedIn = false; return; }
        long now = System.currentTimeMillis();
        if (!wasLoggedIn) { wasLoggedIn = true; next = now + 5_000; }
        if (!PaleTaleClient.ACCOUNT.label().equals(shownAccount) && !PaleTaleClient.ACCOUNT.label().equals("验证中…")) { shownAccount = PaleTaleClient.ACCOUNT.label(); shown.clear(); shown.addAll(loadShown(shownAccount)); }
        String token = PaleTaleClient.ACCOUNT.token();
        if (polling || token == null || now < next || client.level == null) return;
        polling = true; next = now + POLL_MS;
        long observedRevision = revision;
        api.unread(token).whenComplete((preview, failure) -> client.execute(() -> {
            polling = false;
            if (failure != null) ModLog.failed("获取通知", failure);
            if (failure != null || !PaleTaleClient.ACCOUNT.loggedIn() || !token.equals(PaleTaleClient.ACCOUNT.token()) || observedRevision != revision) return;
            acceptPreview(preview);
            var notices = preview.items();
            List<NoticeV2Api.Item> fresh = notices.stream().filter(n -> shown.add(n.id())).toList();
            if (!fresh.isEmpty()) saveShown(shownAccount, shown);
            // One toast per ad (its several changes summarised), at most three ads, then one summary toast.
            java.util.Map<String, List<NoticeV2Api.Item>> byAd = new java.util.LinkedHashMap<>();
            for (NoticeV2Api.Item notice : fresh) byAd.computeIfAbsent(notice.targetType().equals("ad") && notice.adId() != null ? "ad:" + notice.adId() : notice.targetType().equals("ad") && !notice.adCode().isEmpty() ? "legacy-ad:" + notice.adCode() : "notification:" + notice.id(), key -> new java.util.ArrayList<>()).add(notice);
            while (shown.size() > 512) shown.remove(shown.iterator().next());
            int shownAds = 0;
            for (List<NoticeV2Api.Item> group : byAd.values()) if (shownAds++ < 3) toast(client, group);
            if (byAd.size() > 3) client.getToastManager().addToast(SystemToast.multiline(client, new SystemToast.SystemToastId(),
                    Component.literal("PaleTale · 收藏广告"), Component.literal("另有 " + (byAd.size() - 3) + " 组通知预览，请打开通知页查看。")));
            // They stay unread (the bell keeps its badge) until the player explicitly marks them read.
        }));
    }
    /**
     * Title: "Paletale · 广告价格变动 · <ad title>". Body: "广告主：<game ID>", then per change the product name and what changed.
     * Several changes to one ad share one toast (two shown, "等 N 项" for the rest).
     */
    private static void toast(Minecraft client, List<NoticeV2Api.Item> group) {
        NoticeV2Api.Item first = group.get(0);
        if (!first.targetType().equals("ad") || !first.available()) {
            // A wine-effect review result: "Paletale · 酒效果审核通过 · <wine>" with the outcome as the body.
            client.getToastManager().addToast(SystemToast.multiline(client, new SystemToast.SystemToastId(),
                    Component.literal("Paletale · " + label(first) + " · " + first.adTitle()), Component.literal(first.change().isBlank() ? first.detail() : first.change())));
            return;
        }
        StringBuilder body = new StringBuilder();
        String owner = !first.adOwnerId().isBlank() ? first.adOwnerId() : first.adOwner();
        if (!owner.isBlank()) body.append("广告主：").append(owner);
        for (int i = 0; i < Math.min(2, group.size()); i++) {
            NoticeV2Api.Item notice = group.get(i);
            if (!notice.product().isBlank()) body.append(body.isEmpty() ? "" : "\n").append(notice.product());
            body.append(body.isEmpty() ? "" : "\n").append(notice.change().isBlank() ? notice.detail() : notice.change());
        }
        if (group.size() > 2) body.append("\n等 ").append(group.size()).append(" 项变动");
        client.getToastManager().addToast(SystemToast.multiline(client, new SystemToast.SystemToastId(),
                Component.literal("Paletale · 广告" + label(first) + " · " + first.adTitle()), Component.literal(body.toString())));
    }
    private static java.nio.file.Path shownFile() { return ModPaths.config().resolve("notices-toasted.json"); }
    private static com.google.gson.JsonObject readShown() {
        try { return com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(shownFile())).getAsJsonObject(); }
        catch (Exception missing) { return new com.google.gson.JsonObject(); }
    }
    private static Set<Long> loadShown(String account) {
        Set<Long> ids = new java.util.LinkedHashSet<>();
        try { var rows = readShown().getAsJsonArray(account); if (rows != null) for (var row : rows) ids.add(row.getAsLong()); }
        catch (RuntimeException broken) { ids.clear(); }
        return ids;
    }
    private static void saveShown(String account, Set<Long> ids) {
        if (account.isEmpty()) return;
        try {
            var all = readShown();
            var rows = new com.google.gson.JsonArray();
            ids.forEach(rows::add);
            all.add(account, rows);
            var file = shownFile();
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, all.toString());
        } catch (java.io.IOException | RuntimeException ignored) { /* Worst case the notice toasts again after a restart. */ }
    }
    private static String label(NoticeV2Api.Item notice) {
        for (int i = 0; i + 1 < KIND.length; i += 2) if (KIND[i].equals(notice.kind())) return KIND[i + 1];
        return "通知";
    }
}
