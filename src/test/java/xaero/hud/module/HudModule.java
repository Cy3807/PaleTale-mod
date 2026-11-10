package xaero.hud.module;
/** Test stub with the shape of Xaero's class of the same name (only what Paletale reads by reflection). */
public final class HudModule<S> {
    public static Object session;
    @SuppressWarnings("unchecked") public S getCurrentSession() { return (S) session; }
}
