package cn.paletale.companion;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * With the real SimMC map addon and Xaero loaded (-PmapAddonDir): the game starts with both mods, PaleTale finds the addon's route target method and reads its
 * target record. The addon only routes on the SimMC server itself, so following a real route is checked in game there, not here.
 */
final class MapAddonClientGameTest {
    private MapAddonClientGameTest() {}
    private static void require(boolean ok, String label) { if (!ok) throw new AssertionError(label); }

    static void run(ClientGameTestContext context) {
        require(Boolean.getBoolean("paletale.test.mapAddon"), "Run with -PmapAddonDir=<folder with the addon and Xaero jars>");
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> { PaleTaleClient.useSite(SiteConfig.LOCAL); ModSettings.setReadLands(false); Navigation.usePoints(NavPoints.EMPTY); });
            context.waitTicks(40);
            var source = new MapAddonTargets.Reflective();
            Object[] read = context.computeOnClient(client -> {
                try {
                    Object none = source.current();
                    Object record = Class.forName("net.townymap.model.MapJumpTarget").getConstructor(String.class, int.class, int.class).newInstance(" 白港 ", 120, -340);
                    return new Object[] { none, source.point(record) };
                } catch (Exception failure) { throw new AssertionError(failure); }
            });
            require(read[0] == null, "Off the SimMC server the addon has no route, and asking for it does not fail");
            require(new MapAddonTargets.Point("白港", 120, -340).equals(read[1]), "The addon's target record is read as label and column: " + read[1]);
            context.waitTicks(40);
            require(context.computeOnClient(client -> Navigation.target() == null), "No navigation starts by itself beside the addon");
            System.out.println("PaleTale map addon: loaded beside SimMC map addon and Xaero, route target method found and read");
        }
    }
}
