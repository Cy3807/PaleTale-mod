package cn.paletale.companion;

import net.minecraft.client.Minecraft;

/** A few seconds after joining, reads the default SMC catalog pages into the cache so the first visit to the screen is instant. */
final class WorldPrewarm {
    private static final long DELAY_MS = 8_000;
    private static final String PREFIX = "/api/mcmod/world/catalog?q=&item=&category=&mode=&searchType=all&sort=quantity&page=";
    private final WorldApi api = new WorldApi();
    private long joinedAt;
    private boolean done;

    void tick(Minecraft client) {
        // Leaving a world draws the next random ad order, so the following join (or rejoin) starts reshuffled and stays stable inside it.
        if (client.level == null) { if (joinedAt != 0) MarketScreen.clearSession(); joinedAt = 0; done = false; return; }
        long now = System.currentTimeMillis();
        if (joinedAt == 0) joinedAt = now;
        if (done || now - joinedAt < DELAY_MS) return;
        done = true;
        var site = PaleTaleClient.MARKET.site();
        for (int page = 1; page <= 2; page++) WorldPages.batch(api, site, PREFIX + page).exceptionally(ignored -> null);
    }
}
