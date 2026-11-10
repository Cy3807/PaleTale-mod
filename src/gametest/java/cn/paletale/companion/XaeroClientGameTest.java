package cn.paletale.companion;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** With the real Xaero's Minimap loaded (-PxaeroDir): a destination creates a temporary waypoint in Xaero's own list, and ending the navigation removes it. No production account or data. */
final class XaeroClientGameTest {
    private XaeroClientGameTest() {}
    private static void require(boolean ok, String label) { if (!ok) throw new AssertionError(label); }

    /** Names (and details) of the waypoints in the list Xaero is showing, read the way Xaero's own classes expose them. */
    private static List<String> waypoints() {
        try {
            Object module = Class.forName("xaero.hud.minimap.BuiltInHudModules").getField("MINIMAP").get(null);
            Object session = module.getClass().getMethod("getCurrentSession").invoke(module);
            if (session == null) return null;
            Object manager = session.getClass().getMethod("getWorldManager").invoke(session);
            Object world = manager.getClass().getMethod("getCurrentWorld").invoke(manager);
            if (world == null) return null;
            Object set = world.getClass().getMethod("getCurrentWaypointSet").invoke(world);
            Method list = set.getClass().getMethod("getWaypoints");
            List<String> out = new ArrayList<>();
            for (Object w : (Iterable<?>) list.invoke(set)) {
                Class<?> c = w.getClass();
                out.add(c.getMethod("getName").invoke(w) + "|" + c.getMethod("getX").invoke(w) + "," + c.getMethod("getY").invoke(w) + "," + c.getMethod("getZ").invoke(w)
                        + "|temporary=" + c.getMethod("isTemporary").invoke(w) + "|symbol=" + c.getMethod("getSymbol").invoke(w));
            }
            return out;
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static List<String> now(ClientGameTestContext context) { return context.computeOnClient(client -> waypoints()); }

    static void run(ClientGameTestContext context) {
        require(Boolean.getBoolean("paletale.test.xaero"), "Run with -PxaeroDir=<folder with the Xaero jars>");
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> { PaleTaleClient.useSite(SiteConfig.LOCAL); ModSettings.setReadLands(false); Navigation.usePoints(NavPoints.EMPTY); });
            // Xaero builds its minimap world a moment after joining.
            context.waitFor(client -> waypoints() != null, 600);
            require(now(context).stream().noneMatch(w -> w.startsWith("导航")), "No navigation waypoint before a destination exists");
            int[] spot = new int[2];
            context.runOnClient(client -> {
                spot[0] = client.player.blockPosition().getX() + 500; spot[1] = client.player.blockPosition().getZ() + 40;
                Navigation.start(new Navigation.Target("测试目的地", spot[0], 70, spot[1], "", java.util.List.of()));
            });
            context.waitFor(client -> waypoints().stream().anyMatch(w -> w.startsWith("导航 · 测试目的地")), 200);
            var made = now(context).stream().filter(w -> w.startsWith("导航")).toList();
            require(made.size() == 1 && made.getFirst().equals("导航 · 测试目的地|" + spot[0] + ",70," + spot[1] + "|temporary=true|symbol=导"), "Real Xaero holds one temporary waypoint at the destination: " + made);
            context.waitTicks(60);
            require(now(context).stream().filter(w -> w.startsWith("导航")).count() == 1, "The waypoint is kept, not duplicated, while navigating");

            context.runOnClient(client -> Navigation.start(new Navigation.Target("另一处", spot[0] + 10, 71, spot[1], "", java.util.List.of())));
            context.waitFor(client -> waypoints().stream().anyMatch(w -> w.startsWith("导航 · 另一处")), 200);
            require(now(context).stream().filter(w -> w.startsWith("导航")).count() == 1, "A new destination replaces the old waypoint");

            context.runOnClient(client -> ModSettings.setXaeroWaypoint(false));
            context.waitFor(client -> waypoints().stream().noneMatch(w -> w.startsWith("导航")), 200);
            context.runOnClient(client -> ModSettings.setXaeroWaypoint(true));
            context.waitFor(client -> waypoints().stream().anyMatch(w -> w.startsWith("导航 · 另一处")), 200);

            context.runOnClient(client -> Navigation.clear());
            context.waitFor(client -> waypoints().stream().noneMatch(w -> w.startsWith("导航")), 200);
            System.out.println("PaleTale Xaero waypoint: created, kept, replaced, switched off and removed in real Xaero Minimap");
        }
    }
}
