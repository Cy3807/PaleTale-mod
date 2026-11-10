package xaero.common.minimap.waypoints;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
/** Test stub of Xaero's class of the same name (the constructor Paletale uses). */
public class Waypoint {
    public final int x, y, z; public final String name, symbol; public final WaypointColor color; public final WaypointPurpose purpose; public final boolean temporary, yIncluded;
    public Waypoint(int x, int y, int z, String name, String symbol, WaypointColor color, WaypointPurpose purpose, boolean temporary, boolean yIncluded) {
        this.x = x; this.y = y; this.z = z; this.name = name; this.symbol = symbol; this.color = color; this.purpose = purpose; this.temporary = temporary; this.yIncluded = yIncluded;
    }
}
