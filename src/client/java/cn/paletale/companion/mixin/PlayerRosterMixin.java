package cn.paletale.companion.mixin;
import cn.paletale.companion.PaleTaleClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Observe already-processed vanilla player-list packets; never send a packet. */
@Mixin(ClientPacketListener.class)
abstract class PlayerRosterMixin {
 @Inject(method="handlePlayerInfoUpdate",at=@At("TAIL"))
 private void paletale$updated(ClientboundPlayerInfoUpdatePacket packet,CallbackInfo ci){
  var actions=packet.actions();if(!actions.contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)&&!actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED))return;
  for(var entry:packet.entries())PaleTaleClient.rosterChanged((ClientPacketListener)(Object)this,entry.profileId());
 }
 @Inject(method="handlePlayerInfoRemove",at=@At("TAIL"))
 private void paletale$removed(ClientboundPlayerInfoRemovePacket packet,CallbackInfo ci){for(var id:packet.profileIds())PaleTaleClient.rosterChanged((ClientPacketListener)(Object)this,id);}
}
