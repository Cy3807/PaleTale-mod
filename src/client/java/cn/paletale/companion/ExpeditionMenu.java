package cn.paletale.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import java.util.Locale;

/** Silently follows /m → expedition entry → an exact server label, then resumes navigation after Bungee swaps the client world. */
public final class ExpeditionMenu {
    private enum Phase { IDLE, ROOT, WORLD, TRANSFER }
    private static Phase phase=Phase.IDLE;
    private static ExpeditionWorld requested,active;
    private static int x,z; private static Integer y;
    private static long startedAt,clickedAt; private static int waited,menuId=-1;
    private static Screen previous; private static AbstractContainerScreen<?> hidden;
    private static Object connection; private static Object originLevel;
    private static boolean contentReceived;
    private static int recentlyClosedId=-1;private static long recentlyClosedUntil;private static Object recentlyClosedConnection;private static Screen recentlyClosedScreen;
    private ExpeditionMenu() {}

    static void register(){net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(ExpeditionMenu::tick);}
    public static String activeServer(){return active==null?null:active.id();}
    public static boolean active(){return active!=null;}

    public static boolean begin(ExpeditionWorld destination,int targetX,Integer targetY,int targetZ){
        var client=Minecraft.getInstance();
        if(phase!=Phase.IDLE||client.player==null||client.getConnection()==null||client.player.containerMenu!=client.player.inventoryMenu||LandMenu.busy()||PlayerQuery.busy()){
            if(client.player!=null)client.player.displayClientMessage(Component.literal("请先关闭当前容器，再开始远征导航。"),false);
            return false;
        }
        if(client.screen instanceof net.minecraft.client.gui.screens.ChatScreen)client.setScreen(null);
        if(client.screen!=null){client.player.displayClientMessage(Component.literal("请先关闭当前界面，再开始远征导航。"),false);return false;}
        requested=destination;x=targetX;y=targetY;z=targetZ;startedAt=System.currentTimeMillis();waited=0;menuId=-1;contentReceived=false;
        previous=null;connection=client.getConnection();originLevel=client.level;hidden=null;phase=Phase.ROOT;
        client.getConnection().sendCommand("m");
        client.player.displayClientMessage(Component.literal("正在静默选择远征世界："+destination.menuName()),false);
        return true;
    }

    /** Called before a server container screen is allowed to render. */
    public static boolean opening(Screen screen){
        var client=Minecraft.getInstance();
        if((phase!=Phase.ROOT&&phase!=Phase.WORLD)||client.player==null||client.getConnection()!=connection||!(screen instanceof AbstractContainerScreen<?> candidate)||client.player.containerMenu!=candidate.getMenu())return false;
        hidden=candidate;menuId=candidate.getMenu().containerId;waited=0;contentReceived=false;return true;
    }
    public static void receivedContents(int id){if((phase==Phase.ROOT||phase==Phase.WORLD)&&hidden!=null&&id==menuId)contentReceived=true;}
    public static boolean closing(int id){
        var client=Minecraft.getInstance();
        if((phase==Phase.ROOT||phase==Phase.WORLD)&&hidden!=null&&id==menuId&&client.getConnection()==connection)return true;
        return id==recentlyClosedId&&System.currentTimeMillis()<recentlyClosedUntil&&client.getConnection()==recentlyClosedConnection&&(client.screen==recentlyClosedScreen||client.player!=null&&client.player.containerMenu.containerId!=id);
    }

    private static void tick(Minecraft client){
        if(phase==Phase.IDLE)return;
        if(client.player==null||client.getConnection()!=connection){fail(client,"远征导航已取消。");return;}
        if(client.screen!=previous){abortSilently(client,"远征菜单选择已取消；菜单不会显示。");return;}
        long now=System.currentTimeMillis();
        if(phase==Phase.TRANSFER){
            if(client.level!=originLevel){
                active=requested;String dimension=client.level==null?CityNavigation.MAIN_WORLD:client.level.dimension().identifier().toString();
                var target=new Navigation.Target("坐标 "+x+", "+z,x,y,z,"远征",java.util.List.of(),dimension,requested.id());
                reset();Navigation.start(target);return;
            }
            if(now-clickedAt>30000)fail(client,"没有检测到切服完成，请在远征入口手动选择目标世界后重试。");
            return;
        }
        if(now-startedAt>12000){abortSilently(client,"远征菜单响应超时，已关闭菜单。");return;}
        if(hidden==null)return;
        if(client.player.containerMenu!=hidden.getMenu()){hidden=null;menuId=-1;return;}
        if(++waited<2||!contentReceived)return;
        contentReceived=false;waited=0;
        var menu=hidden.getMenu();int containerSlots=Math.max(0,menu.slots.size()-36);
        for(int i=0;i<containerSlots;i++){
            ItemStack stack=menu.slots.get(i).getItem();if(stack.isEmpty())continue;
            String label=clean(stack.getHoverName());var lore=stack.get(DataComponents.LORE);if(lore!=null)for(var line:lore.lines())label+=" "+clean(line);
            String normalized=normalize(label);boolean matched=phase==Phase.ROOT?normalized.contains(normalize("远征入口")):requested.matchesMenu(label);
            if(!matched)continue;
            if(client.gameMode==null){abortSilently(client,"无法操作远征菜单，已关闭菜单。");return;}
            client.gameMode.handleInventoryMouseClick(menu.containerId,i,0,ClickType.PICKUP,client.player);
            if(phase==Phase.ROOT){phase=Phase.WORLD;startedAt=now;return;}
            phase=Phase.TRANSFER;clickedAt=now;hidden=null;menuId=-1;return;
        }
        if(phase==Phase.ROOT){abortSilently(client,"未找到“远征入口”，已关闭菜单。");return;}
        if(phase==Phase.WORLD){abortSilently(client,"未找到目标远征世界，已关闭菜单。");}
    }

    private static String clean(Component component){return component.getString().replaceAll("§.","").replaceAll("[\\p{Cntrl}\\p{Co}\\p{Cf}\\p{Zs}]","").strip();}
    private static String normalize(String value){return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");}
    private static void abortSilently(Minecraft client,String message){
        if(client.player!=null&&client.getConnection()==connection&&client.player.containerMenu!=client.player.inventoryMenu){
            int id=client.player.containerMenu.containerId;recentlyClosedId=id;recentlyClosedUntil=System.currentTimeMillis()+5000;recentlyClosedConnection=connection;recentlyClosedScreen=previous;
            client.getConnection().send(new ServerboundContainerClosePacket(id));client.player.containerMenu=client.player.inventoryMenu;
            if(client.screen instanceof AbstractContainerScreen<?>)client.setScreen(null);
        }
        reset();if(client.player!=null)client.player.displayClientMessage(Component.literal(message),false);
    }
    private static void fail(Minecraft client,String message){abortSilently(client,message);}
    private static void reset(){phase=Phase.IDLE;requested=null;previous=null;hidden=null;connection=null;originLevel=null;menuId=-1;contentReceived=false;}
    static void disconnected(){active=null;reset();}
}
