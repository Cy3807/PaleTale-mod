package cn.paletale.companion.mixin;

import cn.paletale.companion.RadialWheel;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While the command wheel is held the mouse moves its selection instead of turning the camera, and button presses go to the wheel, not the game (movement keys are untouched). */
@Mixin(MouseHandler.class)
abstract class RadialMouseMixin {
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void paletale$wheel(double time, CallbackInfo info) {
        if (!RadialWheel.active()) return;
        accumulatedDX = 0; accumulatedDY = 0;
        info.cancel();
    }
    /** Only presses are taken: a release of a button pressed before the wheel opened must still reach the game, or the key would stay stuck down. */
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void paletale$press(long window, MouseButtonInfo button, int action, CallbackInfo info) {
        if (action != 1 || !RadialWheel.active()) return;
        RadialWheel.press(button.button());
        info.cancel();
    }
}
