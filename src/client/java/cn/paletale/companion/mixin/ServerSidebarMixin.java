package cn.paletale.companion.mixin;
import cn.paletale.companion.PaleScoreboard;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The server's sidebar is drawn inside PaleTale's own panel when merging is on, so vanilla must not draw it a second time. */
@Mixin(Gui.class)
abstract class ServerSidebarMixin {
    @Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void paletale$mergedSidebar(GuiGraphics graphics, DeltaTracker tracker, CallbackInfo info) {
        if (PaleScoreboard.mergesServerSidebar()) info.cancel();
    }
}
