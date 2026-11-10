package cn.paletale.companion;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
/** Command feedback uses grey body text and explicit white emphasis only. */
final class CommandFeedback {
 private CommandFeedback(){}
 static Component text(String message){
  var result=Component.empty();var color=ChatFormatting.GRAY;int start=0;
  for(int i=0;i+1<message.length();i++)if((message.charAt(i)=='&'||message.charAt(i)=='§')&&(message.charAt(i+1)=='f'||message.charAt(i+1)=='7')){
   if(i>start)result.append(Component.literal(message.substring(start,i)).withStyle(color));color=message.charAt(i+1)=='f'?ChatFormatting.WHITE:ChatFormatting.GRAY;i++;start=i+1;
  }
  if(start<message.length())result.append(Component.literal(message.substring(start)).withStyle(color));
  return result;
 }
}
