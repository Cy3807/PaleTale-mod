package cn.paletale.companion.mixin;
import cn.paletale.companion.ChatAssist;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(ChatScreen.class)
abstract class ChatInputMixin {
    @Inject(method="init",at=@At("TAIL"))
    private void paletale$opened(CallbackInfo ci){ChatAssist.opened();}
    @Inject(method="removed",at=@At("HEAD"))
    private void paletale$closed(CallbackInfo ci){ChatAssist.closed();}
    @Inject(method="mouseClicked",at=@At("HEAD"),cancellable=true)
    private void paletaleClick(net.minecraft.client.input.MouseButtonEvent event,boolean twice,CallbackInfoReturnable<Boolean> ci){if(ChatAssist.click((ChatScreen)(Object)this,event.x(),event.y(),event.button()))ci.setReturnValue(true);}
    @Inject(method="keyPressed",at=@At("HEAD"),cancellable=true)
    private void paletaleKey(net.minecraft.client.input.KeyEvent event,CallbackInfoReturnable<Boolean> ci){if(ChatAssist.escape(event.key()))ci.setReturnValue(true);}
    @Inject(method="mouseScrolled",at=@At("HEAD"),cancellable=true)
    private void paletale$scroll(double x,double y,double horizontal,double vertical,CallbackInfoReturnable<Boolean> ci){if(ChatAssist.scroll())ci.setReturnValue(true);}
    @Inject(method="render",at=@At("TAIL"))
    private void paletale$render(GuiGraphics graphics,int x,int y,float delta,CallbackInfo ci){ChatAssist.render(graphics,x,y);}
}
