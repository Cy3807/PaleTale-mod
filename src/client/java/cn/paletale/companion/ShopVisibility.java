package cn.paletale.companion;
import net.minecraft.client.Minecraft;
/** Compatibility commands explain where listing is managed; the mod never mutates visibility. */
final class ShopVisibility {
 static void hide(Minecraft client){website(client);}
 static void show(Minecraft client){website(client);}
 private static void website(Minecraft client){if(client.player!=null)client.player.displayClientMessage(CommandFeedback.text("请在网站 &fSMC → 我的商店 &7管理上下线。"),false);}
}
