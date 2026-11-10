package cn.paletale.companion;

import java.lang.reflect.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import static cn.paletale.companion.ChatTokens.*;

/** Optional, on-demand integration. Never downloads or keeps the addon's map data. */
final class ChatMap {
    record Point(int x,int z) {}
    private ChatMap() {}
    static boolean available(){return FabricLoader.getInstance().isModLoaded("xaeroworldmap");}
    static Point addon(Target target){
        if(!FabricLoader.getInstance().isModLoaded("simmcmapaddon"))return null;
        try{return addon(target,Class.forName("net.townymap.TownyMapMod"));}
        catch(ClassNotFoundException|LinkageError ignored){return null;}
    }
    static Point addon(Target target,Class<?> api){
        try{
            if(target.kind()==Kind.LAND||target.kind()==Kind.NATION){
                String name, nation;
                if(target.kind()==Kind.NATION){nation=target.key();Object data=api.getMethod("nationDetails",String.class).invoke(null,nation);if(data==null)return null;name=(String)data.getClass().getMethod("capitalName").invoke(data);}
                else {String[] key=target.key().split("\\|",2);if(key.length!=2)return null;nation=key[0];name=key[1];}
                if(name==null||name.isBlank())return null;
                Object foundNation=api.getMethod("townNationOf",String.class).invoke(null,name);
                if(!nation.isBlank()&&(foundNation==null||!nation.equalsIgnoreCase(foundNation.toString())))return null;
                List<?> towns=(List<?>)api.getMethod("currentTowns").invoke(null);Point result=null;
                for(Object town:towns){Class<?> type=town.getClass();if(!name.equals(type.getMethod("name").invoke(town)))continue;
                    Point point=new Point((Integer)type.getMethod("centerX").invoke(town),(Integer)type.getMethod("centerZ").invoke(town));
                    if(result!=null&&!result.equals(point))return null;result=point;}
                return bounded(result);
            }
            if(target.kind()==Kind.TRAFFIC&&target.key().startsWith("hub:")){
                Object client=api.getMethod("getApiClient").invoke(null);if(client==null)return null;
                List<?> markers=(List<?>)client.getClass().getMethod("getMapMarkers").invoke(client);Point result=null;
                for(Object marker:markers){Class<?> type=marker.getClass();if(!"PORT".equals(String.valueOf(type.getMethod("kind").invoke(marker)))||!target.name().equals(type.getMethod("name").invoke(marker)))continue;
                    Object port=type.getMethod("port").invoke(marker);String id=port==null?"":String.valueOf(port.getClass().getMethod("id").invoke(port));
                    if(!id.isBlank()&&!target.key().substring(4).equalsIgnoreCase(id))continue;
                    Point point=new Point((Integer)type.getMethod("x").invoke(marker),(Integer)type.getMethod("z").invoke(marker));if(result!=null&&!result.equals(point))return null;result=point;}
                return bounded(result);
            }
        }catch(ReflectiveOperationException|RuntimeException|LinkageError ignored){}
        return null;
    }
    private static Point bounded(Point p){return p!=null&&Math.abs((long)p.x())<=1000000&&Math.abs((long)p.z())<=1000000?p:null;}
    static boolean open(Screen previous,int x,int z){
        if(!available()||bounded(new Point(x,z))==null)return false;
        Minecraft client=Minecraft.getInstance();if(client.player==null||client.getCameraEntity()==null)return false;
        try{
            Class<?> sessionType=Class.forName("xaero.map.WorldMapSession"),mapType=Class.forName("xaero.map.gui.GuiMap");
            Object session=sessionType.getMethod("getCurrentSession").invoke(null);if(session==null||!Boolean.TRUE.equals(sessionType.getMethod("isUsable").invoke(session)))return false;
            Object processor=sessionType.getMethod("getMapProcessor").invoke(session);if(processor==null)return false;
            Constructor<?> constructor=Arrays.stream(mapType.getConstructors()).filter(c->c.getParameterCount()==4&&c.getParameterTypes()[2].isInstance(processor)&&c.getParameterTypes()[3].isInstance(client.getCameraEntity())).findFirst().orElseThrow();
            Object map=constructor.newInstance(null,null,processor,client.getCameraEntity());
            Field cameraX=mapType.getDeclaredField("cameraX"),cameraZ=mapType.getDeclaredField("cameraZ"),reset=mapType.getDeclaredField("shouldResetCameraPos");
            cameraX.setAccessible(true);cameraZ.setAccessible(true);reset.setAccessible(true);
            Field attached=null;try{attached=mapType.getDeclaredField("attachedCamera");attached.setAccessible(true);}catch(NoSuchFieldException olderVersion){}
            client.setScreen((Screen)map);if(attached!=null)attached.setBoolean(null,false);cameraX.setDouble(map,x);cameraZ.setDouble(map,z);reset.setBoolean(map,false);return true;
        }catch(ReflectiveOperationException|RuntimeException|LinkageError failure){if(client.screen!=previous)client.setScreen(previous);return false;}
    }
}
