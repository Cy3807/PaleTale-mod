package cn.paletale.companion;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.item.Items;
import java.util.*;
import java.util.concurrent.CompletableFuture;
/** One bounded, user-requested query. It never clicks the menu, and preserves the PaleTale screen. */
public final class PlayerQuery {
 private static CompletableFuture<JsonObject> pending;private static String name;private static Screen original;private static Object connection;
 private static long hiddenUntil;private static AbstractContainerScreen<?> menu;private static int menuId=-1;private static long deadline,nextStart,nextCommand;private static JsonObject data;private static boolean sentBalance,balanceQueued;
 private PlayerQuery(){}
 static void register(){net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(PlayerQuery::tick);net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->!message(message.getString()));}
 static boolean busy(){return pending!=null;}
 static CompletableFuture<JsonObject> start(Minecraft client,String target){
  long now=System.currentTimeMillis();
  if(pending!=null||ChatChannelIcons.busy()||LandMenu.busy()||now<nextStart||!target.matches("[A-Za-z0-9_]{1,16}")||!PaleTaleClient.ACCOUNT.canLogin(client)||client.player.containerMenu!=client.player.inventoryMenu)return CompletableFuture.failedFuture(new IllegalStateException("请稍后再刷新，并关闭其他容器。"));
  LandMenuGuard.readStarted();pending=new CompletableFuture<>();name=target;original=client.screen;connection=client.getConnection();menu=null;menuId=-1;data=null;sentBalance=false;balanceQueued=false;deadline=now+20000;nextStart=now+10000;nextCommand=now+2000;
  ClientCommandQueue.background("l player "+target,()->pending!=null&&target.equals(name)&&client.getConnection()==connection&&client.screen==original,()->{deadline=System.currentTimeMillis()+20000;nextCommand=System.currentTimeMillis()+2000;});return pending;
 }
 /** Called before vanilla displays an incoming menu. Only this exact target's player menu is concealed. */
 public static boolean opening(Screen screen){
  var client=Minecraft.getInstance();if(pending==null||client.getConnection()!=connection||client.screen!=original||System.currentTimeMillis()>deadline)return false;
  if(screen instanceof AbstractContainerScreen<?> candidate&&LedgerParser.clean(candidate.getTitle().getString()).strip().equalsIgnoreCase("玩家 "+name)){
   menu=candidate;menuId=candidate.getMenu().containerId;hiddenUntil=System.currentTimeMillis()+25000;return true;
  }
  return false;
 }
 public static boolean closing(int id){return menuId==id&&System.currentTimeMillis()<hiddenUntil&&Minecraft.getInstance().getConnection()==connection&&Minecraft.getInstance().screen==original;}
 private static void closeMenu(Minecraft client){hiddenUntil=Math.min(hiddenUntil,System.currentTimeMillis()+5000);if(menuId>=0&&client.player!=null&&client.getConnection()==connection){client.getConnection().send(new ServerboundContainerClosePacket(menuId));if(client.player.containerMenu.containerId==menuId)client.player.containerMenu=client.player.inventoryMenu;}menu=null;}
 private static void tick(Minecraft client){
  if(pending==null)return;long now=System.currentTimeMillis();
  if(client.getConnection()!=connection||client.screen!=original||client.player==null||now>deadline){fail(client,"查询未完成，请稍后重试。");return;}
  if(data==null&&menu!=null){
   List<String> clock=null,lands=List.of();String title="";
   for(var slot:menu.getMenu().slots){if(slot.container==client.player.getInventory())continue;var stack=slot.getItem();var lore=stack.get(DataComponents.LORE);if(lore==null)continue;var lines=lore.lines().stream().map(line->line.getString()).toList();if(stack.is(Items.CLOCK))clock=lines;if(LedgerParser.clean(stack.getHoverName().getString()).matches("领土\\s*[（(].*")){lands=lines;title=stack.getHoverName().getString();}}
   if(clock!=null&&!title.isEmpty())try{data=PlayerQueryData.menu(clock,title,lands);closeMenu(client);}catch(IllegalArgumentException ignored){}
  }
  if(data!=null&&!sentBalance&&!balanceQueued&&now>=nextCommand){balanceQueued=true;String target=name;ClientCommandQueue.background("bal "+target,()->pending!=null&&target.equals(name)&&client.getConnection()==connection&&client.screen==original,()->sentBalance=true);}
 }
 private static boolean message(String raw){
  if(pending==null||!sentBalance||Minecraft.getInstance().getConnection()!=connection)return false;
  Double balance=PlayerQueryData.balance(name,raw);if(balance==null)return false;
  data.addProperty("balance",balance);data.addProperty("observedAt",java.time.Instant.ofEpochMilli(HttpBodies.now()).toString());var result=pending;pending=null;nextStart=Math.max(nextStart,System.currentTimeMillis()+3000);result.complete(data.deepCopy());return true;
 }
 private static void fail(Minecraft client,String message){closeMenu(client);var result=pending;pending=null;nextStart=Math.max(nextStart,System.currentTimeMillis()+5000);result.completeExceptionally(new IllegalStateException(message));}
}
