package cn.paletale.companion.mixin;

import cn.paletale.companion.CityNpcScan;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Client-only outline; does not mutate server entity flags or interfere with other glowing entities. */
@Mixin(Entity.class)
abstract class NavigationEntityMixin {
    @Inject(method="isCurrentlyGlowing",at=@At("HEAD"),cancellable=true)
    private void paletale$glow(CallbackInfoReturnable<Boolean> result) { if(CityNpcScan.highlighted((Entity)(Object)this))result.setReturnValue(true); }
    @Inject(method="getTeamColor",at=@At("HEAD"),cancellable=true)
    private void paletale$colour(CallbackInfoReturnable<Integer> result) { if(CityNpcScan.highlighted((Entity)(Object)this))result.setReturnValue(0xFFFFFF); }
}
