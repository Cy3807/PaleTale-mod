package cn.paletale.companion.mixin;
import cn.paletale.companion.ChatAssist;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ChatComponent.class)
abstract class ChatDisplayMixin {
    @ModifyVariable(method="addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private Component paletale$decorate(Component text){return ChatAssist.decorate(text);}
    @Inject(method="clearMessages",at=@At("HEAD"))
    private void paletale$clear(boolean history,CallbackInfo ci){ChatAssist.clear();}
}
