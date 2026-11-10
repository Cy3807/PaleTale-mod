package cn.paletale.companion.mixin;
import cn.paletale.companion.PresenceWatch;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerTabOverlay.class)
public abstract class TabPopulationMixin {
 @Inject(method={"setHeader","setFooter"},at=@At("TAIL"))
 private void paletale$populationUpdate(Component value,CallbackInfo ci){PresenceWatch.tabUpdated();}
}
