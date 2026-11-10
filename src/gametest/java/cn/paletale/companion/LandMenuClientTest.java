package cn.paletale.companion;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
final class LandMenuClientTest {
 static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
 static void set(String field,Object value){try{var f=LandMenu.class.getDeclaredField(field);f.setAccessible(true);f.set(null,value);}catch(Exception e){throw new AssertionError(e);}}
 static void tick(net.minecraft.client.Minecraft client){try{var method=LandMenu.class.getDeclaredMethod("tick",net.minecraft.client.Minecraft.class);method.setAccessible(true);method.invoke(null,client);}catch(Exception e){throw new AssertionError(e);}}
 static void prepare(net.minecraft.client.Minecraft client,AtomicReference<List<LandEntries.Entry>> result){set("reading",true);set("sent",true);set("startedAt",System.currentTimeMillis());set("waited",0);set("previous",client.screen);set("connection",client.getConnection());set("hidden",null);set("hiddenClosedByServer",false);set("contentReceived",false);set("menuId",-1);set("done",(java.util.function.Consumer<List<LandEntries.Entry>>)result::set);}
 static void contents(net.minecraft.client.Minecraft c,ChestMenu menu){var items=new ArrayList<ItemStack>();for(var slot:menu.slots)items.add(slot.getItem().copy());c.getConnection().handleContainerContent(new ClientboundContainerSetContentPacket(menu.containerId,0,items,ItemStack.EMPTY));}
 static void run(ClientGameTestContext context){
  try(var world=context.worldBuilder().create()){
   context.runOnClient(c->{c.setScreen(new ChatScreen("keep draft",false));var original=c.screen;var result=new AtomicReference<List<LandEntries.Entry>>();prepare(c,result);
    var menu=ChestMenu.threeRows(51,c.player.getInventory());var item=new ItemStack(Items.PAPER);item.set(DataComponents.CUSTOM_NAME,Component.literal("测试领地"));item.set(DataComponents.LORE,new ItemLore(List.of(Component.literal("你拥有这个领土"),Component.literal("国家：测试国家"))));menu.getSlot(0).set(item);c.player.containerMenu=menu;
    var candidate=new ContainerScreen(menu,c.player.getInventory(),Component.literal("领地菜单"));c.setScreen(candidate);check(c.screen==original,"land menu never replaces original screen");tick(c);tick(c);check(c.screen==original&&result.get()==null,"menu remains hidden while content packets are delayed");contents(c,menu);tick(c);check(c.screen==original&&result.get()!=null&&result.get().size()==1&&result.get().getFirst().name().equals("测试领地"),"owned land read without displaying menu");check(c.player.containerMenu==c.player.inventoryMenu,"server container closed and local inventory restored");c.getConnection().handleContainerClose(new ClientboundContainerClosePacket(51));check(c.screen==original,"delayed server close does not close original screen");
    result.set(null);prepare(c,result);var other=ChestMenu.threeRows(52,c.player.getInventory());c.player.containerMenu=other;var otherScreen=new ContainerScreen(other,c.player.getInventory(),Component.literal("普通箱子"));c.setScreen(otherScreen);check(c.screen==original,"candidate held before inspection");contents(c,other);tick(c);tick(c);check(c.screen==otherScreen&&c.player.containerMenu==other&&result.get()!=null&&result.get().isEmpty(),"unrelated chest is shown and never closed");
    var refused=new AtomicReference<List<LandEntries.Entry>>();LandMenu.read(refused::set);check(refused.get()!=null&&refused.get().isEmpty()&&c.screen==otherScreen&&c.player.containerMenu==other,"existing chest is never interrupted by a new land read");
    c.player.containerMenu=c.player.inventoryMenu;c.setScreen(original);result.set(null);prepare(c,result);var remoteClosed=ChestMenu.threeRows(54,c.player.getInventory());c.player.containerMenu=remoteClosed;c.setScreen(new ContainerScreen(remoteClosed,c.player.getInventory(),Component.literal("其他容器")));c.getConnection().handleContainerClose(new ClientboundContainerClosePacket(54));tick(c);tick(c);check(c.screen==original&&c.player.containerMenu==c.player.inventoryMenu,"already-closed unrelated container is not reopened");
    result.set(null);prepare(c,result);set("startedAt",System.currentTimeMillis()-5000);tick(c);check(c.screen==original&&result.get()!=null,"timeout preserves the original screen");
    result.set(null);prepare(c,result);var empty=ChestMenu.threeRows(53,c.player.getInventory());c.player.containerMenu=empty;c.setScreen(new ContainerScreen(empty,c.player.getInventory(),Component.literal("我的领土")));contents(c,empty);tick(c);tick(c);check(c.screen==original&&result.get()!=null&&result.get().isEmpty(),"recognisable empty land menu remains hidden");check(LandMenu.recentlyRead(),"successful empty land list is cached without repeated commands");set("lastSession",new Object());tick(c);check(!LandMenu.recentlyRead()&&LandMenu.lastEntries().isEmpty(),"session switch clears cached territories");
    // Chat navigation: the command is out, then chat closes before the server answers; the menu must still stay hidden.
    result.set(null);c.setScreen(original);prepare(c,result);c.setScreen(null);var late=ChestMenu.threeRows(55,c.player.getInventory());var lateItem=new ItemStack(Items.PAPER);lateItem.set(DataComponents.CUSTOM_NAME,Component.literal("晚到领地"));lateItem.set(DataComponents.LORE,new ItemLore(List.of(Component.literal("你拥有这个领土"))));late.getSlot(0).set(lateItem);c.player.containerMenu=late;
    c.setScreen(new ContainerScreen(late,c.player.getInventory(),Component.literal("领地菜单")));check(c.screen==null,"land menu answering after the screen changed is still hidden");contents(c,late);tick(c);tick(c);check(c.screen==null&&result.get()!=null&&result.get().size()==1&&c.player.containerMenu==c.player.inventoryMenu,"late land menu is read and closed without ever showing");
    result.set(null);prepare(c,result);set("sent",false);c.setScreen(original);tick(c);check(result.get()!=null&&result.get().isEmpty(),"read abandoned before the command was sent when the screen changes");
    var stranger=ChestMenu.threeRows(56,c.player.getInventory());c.player.containerMenu=stranger;var strangerScreen=new ContainerScreen(stranger,c.player.getInventory(),Component.literal("普通箱子"));c.setScreen(strangerScreen);check(c.screen==strangerScreen,"nothing is hidden when no command was sent");c.player.containerMenu=c.player.inventoryMenu;c.setScreen(null);
    c.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
   });
  }catch(Exception e){throw new AssertionError(e);}
 }
}
