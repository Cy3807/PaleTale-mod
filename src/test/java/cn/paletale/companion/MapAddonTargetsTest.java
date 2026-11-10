package cn.paletale.companion;

import cn.paletale.companion.MapAddonTargets.Change;
import cn.paletale.companion.MapAddonTargets.Point;

/** PaleTale follows the SimMC map addon's route target without fighting the player's own choices. */
final class MapAddonTargetsTest {
    private MapAddonTargetsTest() {}
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    static void run() {
        Point[] shown = { null };
        var follow = new MapAddonTargets(() -> shown[0]);
        require(follow.poll(false) == Change.NONE, "No route in the addon, nothing to do");
        shown[0] = new Point("白港", 100, -200);
        require(follow.poll(false) == Change.START && follow.followed().equals(shown[0]), "A new route in the addon starts the same navigation");
        require(follow.poll(true) == Change.NONE, "The same route is not started again");
        require(follow.poll(false) == Change.NONE, "A navigation the player stopped or reached is not restarted while the addon shows the same route");
        shown[0] = new Point("安眠港", 5, 6);
        require(follow.poll(false) == Change.START, "A changed route (the next leg, or a new target) is followed");
        shown[0] = null;
        require(follow.poll(true) == Change.STOP && follow.followed() == null, "The addon's route ending ends PaleTale's navigation to it");
        shown[0] = new Point("安眠港", 5, 6);
        require(follow.poll(false) == Change.START, "The same route set again later is followed again");
        shown[0] = null;
        require(follow.poll(false) == Change.NONE, "A navigation the player replaced with another target is left alone");

        int[] stops = { 0 };
        Point[] route = { new Point("白港", 1, 2) };
        var cancel = new MapAddonTargets(new MapAddonTargets.Source() {
            @Override public Point current() { return route[0]; }
            @Override public void stop() { stops[0]++; route[0] = null; }
        });
        require(cancel.poll(false) == Change.START, "Following the addon's route");
        cancel.stopAddon();
        require(stops[0] == 1, "Cancelling PaleTale's navigation stops the addon's route");
        require(cancel.poll(false) == Change.NONE && cancel.poll(false) == Change.NONE, "Nothing restarts after the cancel");
        cancel.stopAddon();
        require(stops[0] == 1, "With no followed route there is nothing to stop");

        var missing = new MapAddonTargets(() -> { throw new ClassNotFoundException("net.townymap.TownyMapMod"); });
        require(missing.poll(false) == Change.NONE && missing.poll(false) == Change.NONE, "Without the addon nothing happens");
        int[] calls = { 0 };
        var broken = new MapAddonTargets(() -> { calls[0]++; throw new IllegalStateException("not ready"); });
        broken.poll(false); broken.poll(false);
        require(calls[0] == 2, "A passing failure is tried again on the next poll");
        int[] lookups = { 0 };
        var gone = new MapAddonTargets(() -> { lookups[0]++; throw new NoSuchMethodException("activeNavigationTarget"); });
        gone.poll(false); gone.poll(false);
        require(lookups[0] == 1, "A renamed method is looked up once, not every poll");
    }
}
