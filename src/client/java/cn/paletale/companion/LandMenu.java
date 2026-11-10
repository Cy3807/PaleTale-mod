package cn.paletale.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Reads the /l menu without displaying it, preserving the current screen and other server containers. */
public final class LandMenu {
    private static boolean reading,sent; private static long startedAt,hiddenUntil; private static int waited,menuId=-1;
    private static Screen previous; private static Object connection; private static AbstractContainerScreen<?> hidden; private static boolean hiddenClosedByServer,contentReceived;
    private static Screen recentlyClosedScreen; private static Object recentlyClosedConnection; private static int recentlyClosedId=-1; private static long recentlyClosedUntil;
    private static Consumer<List<LandEntries.Entry>> done; private static List<LandEntries.Entry> lastEntries=List.of(); private static long lastAt; private static Object lastSession;
    private LandMenu() {}
    static void register(){net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(LandMenu::tick);}
    static List<LandEntries.Entry> lastEntries(){return lastEntries;}
    static boolean recentlyRead(){return lastAt>0&&lastSession==Minecraft.getInstance().getConnection()&&System.currentTimeMillis()-lastAt<3_600_000L;}
    static boolean busy(){return reading;}
    private static long timeout=4000;
    private static java.util.Set<String> lastUnverified=java.util.Set.of();
    /** Entries of the last read that carried no territory description (an icon item that kept its own name and lore): their name may not be the territory's. */
    static java.util.Set<String> lastUnverified(){return lastUnverified;}
    private static java.util.Map<String,net.minecraft.world.item.ItemStack> lastStacks=java.util.Map.of();
    /** The icon of each territory of the last read, by name. */
    static java.util.Map<String,net.minecraft.world.item.ItemStack> lastStacks(){return lastStacks;}
    static void read(Consumer<List<LandEntries.Entry>> then){read(then,4000,false);}
    static void read(Consumer<List<LandEntries.Entry>> then,long timeoutMs){read(then,timeoutMs,false);}
    /** {@code timeoutMs}: how long the server may take to answer once the command has gone out (the command wheel waits longer than the silent background reads). */
    /** {@code urgent}: the player asked for it, so the command goes out at once instead of waiting its turn among the background commands. */
    static void read(Consumer<List<LandEntries.Entry>> then,long timeoutMs,boolean urgent){
        timeout=timeoutMs;
        var client=Minecraft.getInstance();
        if(reading||ChatChannelIcons.busy()||PlayerQuery.busy()||client.player==null||client.getConnection()==null||client.player.containerMenu!=client.player.inventoryMenu||client.screen instanceof AbstractContainerScreen<?>){then.accept(List.of());return;}
        LandMenuGuard.readStarted();reading=true;sent=false;startedAt=System.currentTimeMillis();waited=0;done=then;previous=client.screen;connection=client.getConnection();hidden=null;hiddenClosedByServer=false;contentReceived=false;menuId=-1;
        if(urgent&&ClientCommandQueue.direct("l menu")){startedAt=System.currentTimeMillis();sent=true;}else ClientCommandQueue.background("l menu",()->reading&&client.getConnection()==connection&&client.screen==previous,()->{startedAt=System.currentTimeMillis();sent=true;});
    }
    /**
     * Intercept a candidate before it can render; inspect its slots after server packets arrive. Once the command has
     * gone out the menu is expected whatever screen is showing by then (chat clicks close or change the screen while
     * the server is still answering), so it is hidden regardless of the current screen.
     */
    public static boolean opening(Screen screen){
        var client=Minecraft.getInstance();
        // The same menu offered to the screen again while it is already being read stays hidden too (it must never fall through to another handler that would close it).
        if(reading&&hidden!=null&&screen instanceof AbstractContainerScreen<?> again&&again.getMenu().containerId==menuId&&client.getConnection()==connection){hidden=again;return true;}
        if(!reading||!sent||hidden!=null||client.player==null||client.getConnection()!=connection||System.currentTimeMillis()-startedAt>timeout)return false;
        if(!(screen instanceof AbstractContainerScreen<?> candidate)||client.player.containerMenu!=candidate.getMenu())return false;
        hidden=candidate;menuId=candidate.getMenu().containerId;hiddenUntil=System.currentTimeMillis()+5000;waited=0;return true;
    }
    /** Slot content is applied by vanilla to the hidden menu before this notification. */
    public static void receivedContents(int id){if(reading&&hidden!=null&&id==menuId)contentReceived=true;}
    /** The server may close a concealed menu; never let it close the original visible screen. */
    public static boolean closing(int id){var client=Minecraft.getInstance();long now=System.currentTimeMillis();if(reading&&hidden!=null&&id==menuId&&now<hiddenUntil&&client.getConnection()==connection){hiddenClosedByServer=true;return true;}return id==recentlyClosedId&&now<recentlyClosedUntil&&client.getConnection()==recentlyClosedConnection&&(client.screen==recentlyClosedScreen||client.player!=null&&client.player.containerMenu.containerId!=id);}
    private static boolean titleLooksLikeLands(String title){return LandMenuGuard.looksLikeLands(title);}
    private static void closeHidden(Minecraft client){
        if(hidden!=null&&client.player!=null&&client.getConnection()==connection&&client.player.containerMenu.containerId==menuId){
            recentlyClosedId=menuId;recentlyClosedUntil=System.currentTimeMillis()+5000;recentlyClosedScreen=previous;recentlyClosedConnection=connection;
            if(!hiddenClosedByServer)client.getConnection().send(new ServerboundContainerClosePacket(menuId));client.player.containerMenu=client.player.inventoryMenu;
        }
        hidden=null;menuId=-1;
    }
    private static void why(String reason){ModLog.failed("领地读取："+reason,reason);}
    private static void finish(List<LandEntries.Entry> entries){
        reading=false;
        var callback=done;done=null;previous=null;connection=null;hidden=null;menuId=-1;if(callback!=null)callback.accept(entries);
    }
    private static void tick(Minecraft client){
        if(lastSession!=client.getConnection()){lastSession=client.getConnection();lastEntries=List.of();lastAt=0;}
        if(recentlyClosedId>=0&&(System.currentTimeMillis()>=recentlyClosedUntil||client.getConnection()!=recentlyClosedConnection||client.screen!=recentlyClosedScreen)){recentlyClosedId=-1;recentlyClosedScreen=null;recentlyClosedConnection=null;}
        if(!reading)return;
        if(client.player==null||client.getConnection()!=connection||!sent&&client.screen!=previous){why(client.player==null||client.getConnection()!=connection?"连接已变":"发送前界面已变");closeHidden(client);finish(List.of());return;}
        if(System.currentTimeMillis()-startedAt>timeout){why(sent?"服务器 "+timeout+" 毫秒内没有打开菜单":"命令迟迟没有发出");closeHidden(client);finish(List.of());return;}
        if(hidden==null)return;
        if(client.player.containerMenu.containerId!=menuId){why("菜单被关闭：当前容器 "+client.player.containerMenu.containerId+"，应为 "+menuId+(hiddenClosedByServer?"（服务器关闭）":""));finish(List.of());return;}
        if(++waited<2)return;
        if(!contentReceived&&!hiddenClosedByServer&&waited<20)return;
        List<List<String>> items=new ArrayList<>();java.util.Map<String,net.minecraft.world.item.ItemStack> stacks=new java.util.HashMap<>();var menu=client.player.containerMenu;int slots=Math.max(0,menu.slots.size()-36);
        for(int i=0;i<slots;i++){var stack=menu.slots.get(i).getItem();if(stack.isEmpty())continue;var item=new ArrayList<String>();item.add(clean(stack.getHoverName().getString()));stacks.put(item.get(0),stack.copyWithCount(1));var lore=stack.get(DataComponents.LORE);if(lore!=null)for(var line:lore.lines()){String text=clean(line.getString());if(!text.isEmpty())item.add(text);}items.add(item);}
        List<LandEntries.Entry> entries=LandEntries.parse(items);var loose=new java.util.LinkedHashSet<String>();if(titleLooksLikeLands(hidden.getTitle().getString())){var every=LandEntries.lenient(items);if(every.size()>entries.size()){var described=entries;for(var e:every)if(described.stream().noneMatch(d->d.name().equals(e.name())))loose.add(e.name());entries=LandEntries.merge(entries,every);}}lastUnverified=java.util.Set.copyOf(loose);java.util.Map<String,net.minecraft.world.item.ItemStack> found=new java.util.LinkedHashMap<>();for(var entry:entries){var icon=stacks.get(entry.name());if(icon!=null){found.put(entry.name(),icon);ChatChannelIcons.learn("领地:"+entry.name(),icon);}}lastStacks=found;if(!entries.isEmpty())LandCache.record(entries,found,lastUnverified);
        if(!entries.isEmpty()||titleLooksLikeLands(hidden.getTitle().getString())){lastEntries=List.copyOf(entries);lastAt=System.currentTimeMillis();lastSession=connection;closeHidden(client);finish(entries);return;}
        // Not a recognisable land menu: let vanilla show the untouched container instead of closing it.
        var unrelated=hidden;var original=previous;boolean serverClosed=hiddenClosedByServer;if(serverClosed&&client.player.containerMenu==unrelated.getMenu())client.player.containerMenu=client.player.inventoryMenu;finish(List.of());if(!serverClosed&&(client.screen==original||client.screen==null)&&client.player.containerMenu==unrelated.getMenu())client.setScreen(unrelated);
    }
    private static String clean(String raw){return raw==null?"":raw.replaceAll("§.","").replaceAll("[\\p{Cntrl}\\p{Co}]","").strip();}
}
