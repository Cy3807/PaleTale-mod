package cn.paletale.companion;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Mirrors the navigation destination as a temporary waypoint in Xaero's Minimap (and so on Xaero's World Map): created when a destination is set,
 * removed when the navigation ends. Xaero is never a dependency: its classes are looked up by name, so the mod works the same without it (or when a
 * future Xaero release changes these names, in which case this simply does nothing). Temporary waypoints are not saved by Xaero, so nothing is left
 * behind after a crash. Call from the client thread only; Xaero's waypoint lists are not thread-safe.
 */
final class XaeroWaypoints {
    /** A waypoint that was added, so that it can be checked and removed again. */
    interface Handle { boolean present() throws Exception; void remove() throws Exception; }
    interface Bridge {
        Handle add(String name, String symbol, int x, int y, int z, boolean yIncluded) throws Exception;
        /** Same with a named Xaero waypoint colour (GREEN, GOLD, …); a bridge that has no colours ignores it. */
        default Handle add(String name, String symbol, int x, int y, int z, boolean yIncluded, String color) throws Exception { return add(name, symbol, x, y, z, yIncluded); }
    }

    private static Bridge bridge = new Reflective();
    private static Handle handle;
    private static String key = "";
    private XaeroWaypoints() {}

    /** Shows the waypoint; a no-op while the same one is already there, a replacement when the destination changed or Xaero reloaded its world. */
    static synchronized void show(String name, int x, int y, int z, boolean yIncluded) {
        String next = name + '|' + x + '|' + y + '|' + z + '|' + yIncluded;
        if (handle != null && next.equals(key) && present(handle)) return;
        hide();
        try {
            Handle created = bridge.add(name, "导", x, y, z, yIncluded);
            if (created != null) { handle = created; key = next; }
        } catch (Throwable ignored) { /* Xaero missing or changed: navigation works without the waypoint. */ }
    }

    static synchronized void hide() {
        Handle old = handle;
        handle = null; key = "";
        if (old != null) try { old.remove(); } catch (Throwable ignored) { /* Already gone (the player deleted it, or the world was unloaded). */ }
    }

    static synchronized boolean shown() { return handle != null; }

    /** A waypoint of its own (the pins of the SMC world pages), independent of the navigation waypoint: the caller keeps the handle and removes it. Null when Xaero is missing or has no world yet. */
    static synchronized Handle pin(String name, String symbol, int x, int y, int z, boolean yIncluded, String color) {
        try { return bridge.add(name, symbol, x, y, z, yIncluded, color); } catch (Throwable ignored) { return null; }
    }

    private static boolean present(Handle value) {
        try { return value.present(); } catch (Throwable failure) { return false; }
    }

    /** Tests only: use this bridge instead of looking Xaero up. */
    static synchronized void useBridge(Bridge replacement) { hide(); bridge = replacement; }
    static synchronized void useXaero() { hide(); bridge = new Reflective(); }

    /** The real thing: Xaero's Minimap classes reached by name (the same calls its own temporary-waypoint feature makes). */
    static final class Reflective implements Bridge {
        private boolean unavailable;

        @Override public Handle add(String name, String symbol, int x, int y, int z, boolean yIncluded) throws Exception { return add(name, symbol, x, y, z, yIncluded, "AQUA"); }

        @Override public Handle add(String name, String symbol, int x, int y, int z, boolean yIncluded, String color) throws Exception {
            if (unavailable) return null;
            Object set;
            try { set = currentSet(); }
            catch (ClassNotFoundException missing) { unavailable = true; return null; }
            catch (NoSuchMethodException | NoSuchFieldException changed) { unavailable = true; ModLog.failed("Xaero 路径点", "Xaero 小地图版本不兼容，已停用路径点同步"); return null; }
            if (set == null) return null; // Xaero is there but has no world yet; the next try may find one.
            Class<?> waypointClass = Class.forName("xaero.common.minimap.waypoints.Waypoint");
            Class<?> colorClass = Class.forName("xaero.hud.minimap.waypoint.WaypointColor");
            Class<?> purposeClass = Class.forName("xaero.hud.minimap.waypoint.WaypointPurpose");
            Constructor<?> constructor = waypointClass.getConstructor(int.class, int.class, int.class, String.class, String.class, colorClass, purposeClass, boolean.class, boolean.class);
            Object waypoint = constructor.newInstance(x, y, z, name, symbol, color(colorClass, color), constant(purposeClass, "NORMAL"), true, yIncluded);
            Method add = set.getClass().getMethod("add", waypointClass, boolean.class);
            add.invoke(set, waypoint, true);
            Method removeMethod = set.getClass().getMethod("remove", waypointClass);
            Method listMethod = set.getClass().getMethod("getWaypoints");
            return new Handle() {
                @Override public boolean present() throws Exception {
                    if (currentSet() != set) return false;
                    for (Object each : (Iterable<?>) listMethod.invoke(set)) if (each == waypoint) return true;
                    return false;
                }
                @Override public void remove() throws Exception { removeMethod.invoke(set, waypoint); }
            };
        }

        /** The waypoint list of the world Xaero is showing now, or null when it has none yet. */
        private static Object currentSet() throws Exception {
            Class<?> modules = Class.forName("xaero.hud.minimap.BuiltInHudModules");
            Object module = modules.getField("MINIMAP").get(null);
            Object session = module.getClass().getMethod("getCurrentSession").invoke(module);
            if (session == null) return null;
            Object manager = session.getClass().getMethod("getWorldManager").invoke(session);
            Object world = manager == null ? null : manager.getClass().getMethod("getCurrentWorld").invoke(manager);
            return world == null ? null : world.getClass().getMethod("getCurrentWaypointSet").invoke(world);
        }

        private static Object color(Class<?> type, String name) { try { return constant(type, name); } catch (IllegalArgumentException unknown) { return constant(type, "AQUA"); } }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static Object constant(Class<?> type, String name) { return Enum.valueOf((Class) type, name); }
    }
}
