package cn.paletale.companion;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
/** Native 1.21.11 drag selection; compatibility implementation supplies it on 1.21.8. */
final class ChatEditBox extends EditBox {
 ChatEditBox(Font font,int x,int y,int width,int height,Component label){super(font,x,y,width,height,label);}
}
