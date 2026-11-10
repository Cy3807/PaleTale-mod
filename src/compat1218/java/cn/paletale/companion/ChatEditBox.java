package cn.paletale.companion;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
/** 1.21.8 EditBox has click/keyboard selection but no drag-selection implementation. */
final class ChatEditBox extends EditBox {
 private final Font textFont;private double dragStartX;private int dragStartCursor;
 ChatEditBox(Font font,int x,int y,int width,int height,Component label){super(font,x,y,width,height,label);textFont=font;}
 @Override public void onClick(double x,double y){super.onClick(x,y);dragStartX=x;dragStartCursor=getCursorPosition();}
 @Override protected void onDrag(double x,double y,double dx,double dy){String value=getValue();int anchor=Math.min(dragStartCursor,value.length());double pixel=textFont.width(value.substring(0,anchor))+x-dragStartX;int cursor=textFont.plainSubstrByWidth(value,Math.max(0,(int)Math.round(pixel))).length();moveCursorTo(cursor,true);}
}
