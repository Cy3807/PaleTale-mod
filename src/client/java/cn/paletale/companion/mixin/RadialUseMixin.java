package cn.paletale.companion.mixin;

import cn.paletale.companion.RadialWheel;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No attacking, placing or breaking while the command wheel is up, whatever key those are bound to. */
@Mixin(Minecraft.class)
abstract class RadialUseMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void paletale$noAttack(CallbackInfoReturnable<Boolean> callback) { if (RadialWheel.active()) callback.setReturnValue(false); }
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void paletale$noUse(CallbackInfo callback) { if (RadialWheel.active()) callback.cancel(); }
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void paletale$noBreak(boolean breaking, CallbackInfo callback) { if (RadialWheel.active()) callback.cancel(); }
}
