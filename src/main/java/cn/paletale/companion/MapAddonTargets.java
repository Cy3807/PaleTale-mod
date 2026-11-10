package cn.paletale.companion;

import java.lang.reflect.Method;

/**
 * Follows the navigation target of the SimMC map addon (simmcmapaddon, net.townymap): when the player sets a route there, PaleTale navigates to the same
 * point; when that route ends, the navigation it started ends too. The addon is never a dependency: its public TownyMapMod.activeNavigationTarget() is
 * looked up by name, and without the addon (or after it renames that method) this does nothing. Call from the client thread only.
 */
final class MapAddonTargets {
    /** The addon's current target: a label and a block column (the addon has no height). */
    record Point(String label, int x, int z) {}
    interface Source { Point current() throws Exception; default void stop() throws Exception {} }
    /** What the navigation should do after a poll. */
    enum Change { NONE, START, STOP }

    private Source source;
    private Point followed;
    private boolean unavailable;

    MapAddonTargets(Source source) { this.source = source; }

    /** The player cancelled PaleTale's navigation to the addon's target: end the addon's route as well (its own stop: route, travel plan and waypoint). */
    void stopAddon() {
        if (unavailable || followed == null) return;
        followed = null;
        try { source.stop(); } catch (Throwable failure) { ModLog.failed("地图扩展联动", "无法同步取消地图扩展的导航"); }
    }

    /** The point to navigate to after {@link Change#START}. */
    Point followed() { return followed; }

    /**
     * START when the addon shows a target that differs from the last one followed (so a PaleTale navigation the player stopped or reached is not restarted
     * while the addon still shows the same target); STOP when the addon's target is gone and {@code stillOurs} says PaleTale is still navigating there.
     */
    Change poll(boolean stillOurs) {
        if (unavailable) return Change.NONE;
        Point now;
        try { now = source.current(); }
        catch (ClassNotFoundException missing) { unavailable = true; return Change.NONE; }
        catch (NoSuchMethodException changed) { unavailable = true; ModLog.failed("地图扩展联动", "地图扩展版本不兼容，已停用跟随导航"); return Change.NONE; }
        catch (Throwable failure) { return Change.NONE; }
        if (now == null) {
            if (followed == null) return Change.NONE;
            followed = null;
            return stillOurs ? Change.STOP : Change.NONE;
        }
        if (now.equals(followed)) return Change.NONE;
        followed = now;
        return Change.START;
    }

    /** The real addon, reached by name. */
    static final class Reflective implements Source {
        private Method target, label, x, z, stop;

        @Override public void stop() throws Exception {
            if (stop == null) stop = Class.forName("net.townymap.TownyMapMod").getMethod("stopXaeroRoute");
            stop.invoke(null);
        }

        @Override public Point current() throws Exception {
            if (target == null) target = Class.forName("net.townymap.TownyMapMod").getMethod("activeNavigationTarget");
            return point(target.invoke(null));
        }

        /** Reads one of the addon's MapJumpTarget records (label, x, z). */
        Point point(Object value) throws Exception {
            if (value == null) return null;
            if (label == null) { Class<?> type = value.getClass(); label = type.getMethod("label"); x = type.getMethod("x"); z = type.getMethod("z"); }
            Object name = label.invoke(value);
            return new Point(name == null ? "" : String.valueOf(name).strip(), (Integer) x.invoke(value), (Integer) z.invoke(value));
        }
    }
}
