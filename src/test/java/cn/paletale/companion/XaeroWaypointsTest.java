package cn.paletale.companion;

import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.module.HudModule;

/** The Xaero waypoint of the navigation destination, against stand-ins for Xaero's classes (see src/test/java/xaero). */
final class XaeroWaypointsTest {
    private XaeroWaypointsTest() {}
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    static void run() {
        var session = new MinimapSession();
        HudModule.session = session;
        XaeroWaypoints.useXaero();
        WaypointSet set = session.manager.world.set;

        XaeroWaypoints.show("导航 · 港口 安眠港", 10, 64, 20, true);
        require(set.list.size() == 1, "A destination creates one waypoint");
        Waypoint made = set.list.getFirst();
        require(made.x == 10 && made.y == 64 && made.z == 20 && made.name.equals("导航 · 港口 安眠港") && made.symbol.equals("导"), "Waypoint carries the destination and its name");
        require(made.temporary && made.yIncluded && made.color == WaypointColor.AQUA && made.purpose == WaypointPurpose.NORMAL, "Waypoint is temporary (never saved), with height, in one fixed colour");
        require(set.addedOnTop.getFirst(), "New waypoint goes to the top of Xaero's list");

        XaeroWaypoints.show("导航 · 港口 安眠港", 10, 64, 20, true);
        require(set.list.size() == 1 && XaeroWaypoints.shown(), "Showing the same destination again changes nothing");

        XaeroWaypoints.show("导航 · 白港", 30, 70, 40, false);
        require(set.list.size() == 1 && set.list.getFirst().name.equals("导航 · 白港") && !set.list.getFirst().yIncluded, "A new destination replaces the old waypoint (height ignored when unknown)");

        XaeroWaypoints.hide();
        require(set.list.isEmpty() && !XaeroWaypoints.shown(), "Ending the navigation removes the waypoint");
        XaeroWaypoints.hide();

        // Xaero switched to another world's list (another dimension, then back): the waypoint is re-created in the list that is showing now.
        XaeroWaypoints.show("导航 · 白港", 30, 70, 40, true);
        var other = new WaypointSet();
        session.manager.world.set = other;
        XaeroWaypoints.show("导航 · 白港", 30, 70, 40, true);
        require(set.list.isEmpty() && other.list.size() == 1, "A waypoint left in an old list is removed and re-created in the current one");

        // The player deleted it in Xaero's own menu.
        other.list.clear();
        XaeroWaypoints.show("导航 · 白港", 30, 70, 40, true);
        require(other.list.size() == 1, "A waypoint the player deleted comes back while the navigation lasts");
        XaeroWaypoints.hide();

        // Xaero loaded but has no session yet: nothing happens, and a later try works.
        HudModule.session = null;
        XaeroWaypoints.show("导航 · 白港", 1, 2, 3, true);
        require(!XaeroWaypoints.shown(), "No Xaero session yet: no waypoint, no failure");
        HudModule.session = session;
        XaeroWaypoints.show("导航 · 白港", 1, 2, 3, true);
        require(XaeroWaypoints.shown() && other.list.size() == 1, "Once Xaero has a session the waypoint appears");
        XaeroWaypoints.hide();

        // A bridge that fails or finds nothing must never break navigation.
        XaeroWaypoints.useBridge((name, symbol, x, y, z, yIncluded) -> { throw new IllegalStateException("Xaero changed"); });
        XaeroWaypoints.show("导航 · X", 1, 2, 3, true);
        require(!XaeroWaypoints.shown(), "A failing Xaero lookup is swallowed");
        XaeroWaypoints.useBridge((name, symbol, x, y, z, yIncluded) -> null);
        XaeroWaypoints.show("导航 · X", 1, 2, 3, true);
        require(!XaeroWaypoints.shown(), "Without Xaero there is no waypoint");
        XaeroWaypoints.useXaero();
        XaeroWaypoints.hide();
    }
}
