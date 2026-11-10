package cn.paletale.companion;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** A separate animated layer lets HUD caches retain the rest of the interface. */
final class FavoritePresenceHud {
 static final Identifier ID=Identifier.fromNamespaceAndPath("paletale","favorite_presence");
 private static Field rendering,currentElement,config,elements;
 private static Method uncached,disableCaching;
 private static boolean checked,failed;
 private FavoritePresenceHud(){}
 static void register(){HudElementRegistry.addLast(ID,(graphics,tracker)->{uncacheAnimation();PaleTaleClient.FAVORITE_PRESENCE.render(graphics);});net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client,screen,w,h)->{if(screen instanceof net.minecraft.client.gui.screens.PauseScreen)net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((s,g,x,y,d)->PaleTaleClient.FAVORITE_PRESENCE.renderOnPause(g));});}
 /** Optional Gnetum API: changes this layer's AUTO default in memory, never writes settings. */
 static void uncacheAnimation(){
  if(failed)return;
  if(!checked){checked=true;if(!FabricLoader.getInstance().isModLoaded("gnetum"))return;
   try{var api=Class.forName("me.decce.gnetum.Gnetum");rendering=api.getField("rendering");currentElement=api.getField("currentElement");config=api.getField("config");elements=Class.forName("me.decce.gnetum.GnetumConfig").getField("map");uncached=Class.forName("me.decce.gnetum.CachedElement").getMethod("isUncached");disableCaching=api.getMethod("disableCachingForCurrentElement",String.class);}
   catch(ReflectiveOperationException|LinkageError ex){failed=true;org.slf4j.LoggerFactory.getLogger("PaleTale").warn("Gnetum animated HUD compatibility unavailable",ex);return;}
  }
  if(disableCaching==null)return;
  try{if(!rendering.getBoolean(null))return;Object element=currentElement.get(null);if(element!=null&&((java.util.Map<?,?>)elements.get(config.get(null))).get(ID.toString())==element&&!((Boolean)uncached.invoke(element)))disableCaching.invoke(null,"PaleTale favorite presence uses per-frame animation");}
  catch(ReflectiveOperationException|LinkageError ex){failed=true;org.slf4j.LoggerFactory.getLogger("PaleTale").warn("Gnetum animated HUD compatibility disabled",ex);}
 }
}
