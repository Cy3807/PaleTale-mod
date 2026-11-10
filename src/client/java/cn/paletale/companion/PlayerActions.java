package cn.paletale.companion;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.minecraft.client.Minecraft;

/** What the chat header offers for the player of a conversation besides transferring: favouriting them and inviting them to a team. */
final class PlayerActions {
    private PlayerActions() {}

    /** The team invite as a command, "%s" standing for the player; the server's own wording can be put in paletale.properties as teamInviteCommand. */
    static String inviteCommand(String player) { return ModSettings.teamInviteCommand().replace("%s", player); }

    static void invite(Minecraft client, String player) {
        if (!player.matches("[A-Za-z0-9_]{1,16}")) return;
        RadialWheel.send(client, inviteCommand(player));
    }

    /** Favourites the player by the site's own favourites (as the SMC world page does), or takes them off the list again. */
    static void toggleFavorite(Minecraft client, String player, Runnable changed) {
        String token = PaleTaleClient.ACCOUNT.token();
        if (token == null) { RadialWheel.toast(client, "请先登录 PaleTale 账号后再收藏。"); return; }
        var market = PaleTaleClient.MARKET; var api = new WorldApi(); var site = market.site(); long account = FavoritePresenceWatch.currentAccountId();
        api.call(site, "/api/mcmod/world/favorites?name=" + java.net.URLEncoder.encode(player, StandardCharsets.UTF_8), token, null).whenComplete((state, failure) -> client.execute(() -> {
            if (failure != null || !state.has("uuid") || !state.has("favorited") || !WorldShopTable.value(state, "uuid").matches("[a-f0-9]{32}")) { RadialWheel.toast(client, "暂时无法收藏这位玩家（要求是已核实的正版玩家）。"); return; }
            boolean on = !state.get("favorited").getAsBoolean();
            var body = new JsonObject(); body.add("uuid", state.get("uuid")); body.add("name", state.get("name")); body.addProperty("on", on);
            api.call(site, "/api/mcmod/world/favorites", token, body).whenComplete((saved, problem) -> client.execute(() -> {
                if (problem != null) { RadialWheel.toast(client, "收藏没有保存，请稍后重试。"); return; }
                DiskPages.clear(DiskPages.owner(site)); WorldEntityScreen.clearFavoriteCache();
                if (Objects.equals(token, PaleTaleClient.ACCOUNT.token())) PaleTaleClient.FAVORITE_PRESENCE.changed(token, site, account, body.get("uuid").getAsString(), body.get("name").getAsString(), on);
                changed.run();
            }));
        }));
    }
}
