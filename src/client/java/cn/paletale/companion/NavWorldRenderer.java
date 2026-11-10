package cn.paletale.companion;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;

/**
 * Navigation drawn in the world itself (Minecraft 1.21.11; the older variant leaves it out): the blocks of the target (the sign and chest of a shop) lit up through walls with the game's gizmo outlines.
 */
final class NavWorldRenderer {
    static final boolean SUPPORTED = true;
    private static final int OUTLINE = 0xFFFFFFFF, FILL = 0x38FFFFFF;
    private NavWorldRenderer() {}

    static void register() {
        WorldRenderEvents.END_EXTRACTION.register(NavWorldRenderer::highlight);
    }

    /** The sign and chest of the shop being navigated to, outlined and tinted through the walls. */
    private static void highlight(WorldExtractionContext context) {
        var client = Minecraft.getInstance();
        var goal = Navigation.target();
        if (goal == null || goal.marks().isEmpty() || client.player == null || client.level == null || !Navigation.inTargetWorld(client)) return;
        // A loaded NPC already has the native outline; only unloaded/coordinate destinations need the fallback marker.
        if(CityNavigation.WORLD.equals(goal.world())&&CityNpcScan.hasHighlight())return;
        try (var gizmos = client.levelRenderer.collectPerFrameGizmos()) {
            if(CityNavigation.WORLD.equals(goal.world())) {
                var plan=Navigation.currentPlan();if(plan==null)return;var step=plan.guide();
                var at=new net.minecraft.world.phys.Vec3(step.x()+.5,Navigation.targetHeight(client,goal,step)+1,step.z()+.5);
                Gizmos.point(at,OUTLINE,6).setAlwaysOnTop();return;
            }
            for (int[] mark : goal.marks()) Gizmos.cuboid(new BlockPos(mark[0], mark[1], mark[2]), GizmoStyle.strokeAndFill(OUTLINE, 3.0f, FILL)).setAlwaysOnTop();
        } catch (RuntimeException ignored) { /* a frame without a gizmo collector simply has no outline */ }
    }
}
