package xaero.hud.minimap.waypoint.set;
import java.util.ArrayList;
import java.util.List;
import xaero.common.minimap.waypoints.Waypoint;
/** Test stub of Xaero's class of the same name; remembers how waypoints were added. */
public final class WaypointSet {
    public final List<Waypoint> list = new ArrayList<>();
    public final List<Boolean> addedOnTop = new ArrayList<>();
    public Iterable<Waypoint> getWaypoints() { return list; }
    public void add(Waypoint waypoint, boolean top) { list.add(waypoint); addedOnTop.add(top); }
    public void add(Waypoint waypoint) { add(waypoint, true); }
    public void remove(Waypoint waypoint) { list.remove(waypoint); }
    public Waypoint remove(int index) { return list.remove(index); }
}
