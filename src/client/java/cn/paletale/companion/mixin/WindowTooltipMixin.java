package cn.paletale.companion.mixin;

import cn.paletale.companion.WindowTooltips;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tooltips of widgets inside PaleTale's scaled windows: convert the position from window to screen coordinates. */
@Mixin(GuiGraphics.class)
public abstract class WindowTooltipMixin {
    @Inject(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;IIZ)V", at = @At("HEAD"), cancellable = true)
    private void paletale$window(Font font, List<FormattedCharSequence> lines, ClientTooltipPositioner positioner, int x, int y, boolean focused, CallbackInfo ci) {
        if (!WindowTooltips.pending()) return;
        WindowTooltips.converting(true);
        try { ((GuiGraphics) (Object) this).setTooltipForNextFrame(font, lines, WindowTooltips.convert(positioner), x, y, focused); }
        finally { WindowTooltips.converting(false); }
        ci.cancel();
    }
}
