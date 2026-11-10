package cn.paletale.companion.mixin;
import cn.paletale.companion.PlayerQuery;
import cn.paletale.companion.LandMenu;
import cn.paletale.companion.ExpeditionMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
abstract class PlayerQueryMixin {
 @Inject(method="setScreen",at=@At("HEAD"),cancellable=true)
 private void paletale$queryScreen(Screen screen,CallbackInfo info){if(cn.paletale.companion.LandMenuGuard.playerOwnsMenu())return;if(cn.paletale.companion.ChatChannelIcons.opening(screen)||PlayerQuery.opening(screen)||cn.paletale.companion.LandHere.opening(screen)||LandMenu.opening(screen)||cn.paletale.companion.LandMenuGuard.opening(screen)||ExpeditionMenu.opening(screen))info.cancel();}
}
