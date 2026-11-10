package cn.paletale.companion.mixin;
import cn.paletale.companion.PlayerQuery;
import cn.paletale.companion.LandMenu;
import cn.paletale.companion.ExpeditionMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPacketListener.class)
abstract class PlayerQueryCloseMixin {
 @Inject(method="handleCommandSuggestions",at=@At("HEAD"),cancellable=true)
 private void paletale$chatSuggestions(net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket packet,CallbackInfo info){if(Minecraft.getInstance().isSameThread()&&cn.paletale.companion.ChatCompletionPackets.receive(packet))info.cancel();}

 @Inject(method="handleContainerContent",at=@At("TAIL"))
 private void paletale$landContents(ClientboundContainerSetContentPacket packet,CallbackInfo info){if(Minecraft.getInstance().isSameThread()){LandMenu.receivedContents(packet.containerId());cn.paletale.companion.ChatChannelIcons.contents(packet.containerId());ExpeditionMenu.receivedContents(packet.containerId());}}
 @Inject(method="handleContainerSetSlot",at=@At("TAIL"))
 private void paletale$expeditionSlot(ClientboundContainerSetSlotPacket packet,CallbackInfo info){if(Minecraft.getInstance().isSameThread())ExpeditionMenu.receivedContents(packet.getContainerId());}
 @Inject(method="handleContainerClose",at=@At("HEAD"),cancellable=true)
 private void paletale$queryClose(ClientboundContainerClosePacket packet,CallbackInfo info){if(cn.paletale.companion.LandMenuGuard.userActive())return;if(Minecraft.getInstance().isSameThread()&&(cn.paletale.companion.ChatChannelIcons.closing(packet.getContainerId())||PlayerQuery.closing(packet.getContainerId())||cn.paletale.companion.LandHere.closing(packet.getContainerId())||LandMenu.closing(packet.getContainerId())||cn.paletale.companion.LandMenuGuard.closing(packet.getContainerId())||ExpeditionMenu.closing(packet.getContainerId())))info.cancel();}
}
