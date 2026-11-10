package cn.paletale.companion.mixin;

import cn.paletale.companion.SignUpdates;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The server sends a block entity update when a shop sign's text changes (sold out, restocked, repriced): the shop watcher reads that sign again right away. */
@Mixin(ClientPacketListener.class)
abstract class SignUpdateMixin {
    @Inject(method = "handleBlockEntityData", at = @At("TAIL"))
    private void paletale$signUpdated(ClientboundBlockEntityDataPacket packet, CallbackInfo ignored) {
        // The packet is handled twice (network thread, then game thread); only the game thread has applied it.
        if (Minecraft.getInstance().isSameThread()) SignUpdates.changed(packet.getPos());
    }
}
