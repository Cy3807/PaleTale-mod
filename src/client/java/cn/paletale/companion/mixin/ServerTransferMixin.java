package cn.paletale.companion.mixin;

import cn.paletale.companion.ServerAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
abstract class ServerTransferMixin {
    @Shadow @Final protected ServerData serverData;
    @Inject(method="handleTransfer",at=@At("HEAD"))
    private void paletale$transfer(ClientboundTransferPacket packet, CallbackInfo ignored) {
        if(Minecraft.getInstance().isSameThread()&&serverData!=null)ServerAccess.recordTransfer(serverData.ip,packet.host(),packet.port());
    }
}
