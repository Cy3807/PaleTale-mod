package cn.paletale.companion;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

/** Frame timing with the wheel closed and open (level 0 and a ring of heads): the frames are counted by a HUD layer that runs once per rendered frame. */
final class RadialPerfClientTest {
    private static volatile String phase = "";
    private static final java.util.Map<String, List<Long>> GAPS = new java.util.LinkedHashMap<>();
    private static long last;

    static void run(ClientGameTestContext context) {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("paletale", "perf_probe"), (g, tracker) -> {
            long now = System.nanoTime();
            if (!phase.isEmpty() && last != 0) GAPS.computeIfAbsent(phase, k -> new ArrayList<>()).add(now - last);
            last = now;
        });
        context.runOnClient(c -> { c.options.enableVsync().set(false); c.options.framerateLimit().set(260); c.options.pauseOnLostFocus = false; });
        ModSettings.setRadialClick(true);
        context.waitTicks(100);
        measure(context, "A 轮盘关闭", () -> { });
        measure(context, "B 打开主轮盘(首次)", () -> { RadialWheel.open(); if (!RadialWheel.active()) force(); });
        measure(context, "C 主轮盘并指向槽位", () -> { var window = net.minecraft.client.Minecraft.getInstance().getWindow(); org.lwjgl.glfw.GLFW.glfwSetCursorPos(window.handle(), window.getScreenWidth() / 2.0, window.getScreenHeight() / 2.0 - 40); });
        measure(context, "D 玩家头像环", () -> { RadialWheel.showPlayer("Notch"); });
        measure(context, "E 头像环继续", () -> { });
        context.runOnClient(c -> { if (RadialWheel.active()) RadialWheel.press(1); });
        measure(context, "F 关闭后", () -> { });
        ModSettings.setRadialClick(false);
        for (var e : GAPS.entrySet()) {
            var v = new ArrayList<>(e.getValue()); java.util.Collections.sort(v);
            double total = v.stream().mapToLong(Long::longValue).sum() / 1e9;
            System.out.printf("PERF %-24s frames=%d fps=%.1f median=%.2fms p99=%.2fms max=%.2fms%n", e.getKey(), v.size(), v.size() / total, v.get(v.size() / 2) / 1e6, v.get((int) (v.size() * .99)) / 1e6, v.get(v.size() - 1) / 1e6);
        }
        System.out.println("PERF wheel active at end of D: see above");
    }

    /** open() refuses in the test client (no focused window / no key held): set the wheel's own state the way open() does. */
    private static void force() {
        try {
            for (var f : new String[]{ "active", "needRelease" }) { var field = RadialWheel.class.getDeclaredField(f); field.setAccessible(true); field.setBoolean(null, f.equals("active")); }
            for (var f : new String[]{ "since", "lastInput" }) { var field = RadialWheel.class.getDeclaredField(f); field.setAccessible(true); field.setLong(null, System.currentTimeMillis() + 3_600_000L); }
            RadialArt.reset(); RadialArt.warm(RadialSlots.COUNT);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void measure(ClientGameTestContext context, String name, Runnable setup) {
        context.runOnClient(c -> { setup.run(); phase = ""; last = 0; });
        phase = name;
        context.waitTicks(80);
        phase = "";
        context.runOnClient(c -> System.out.println("PERF active after " + name + ": " + RadialWheel.active()));
    }
}
