package cn.paletale.companion;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import static cn.paletale.companion.ChatTokens.*;
final class CountryClientTest {
 static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
 static Object field(Object o,String name){return WorldClientGameTest.field(o,name);}
 static Object staticField(Class<?> type,String name){try{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(null);}catch(Exception e){throw new AssertionError(e);}}
 static int[] countryPoint(net.minecraft.client.Minecraft c){for(int y=c.screen.height-42;y>Math.max(5,c.screen.height-200);y--)for(int x=4;x<Math.min(c.screen.width-5,400);x+=2){var style=ChatAssist.hit(x,y);if(style!=null&&style.isUnderlined())return new int[]{x,y};}throw new AssertionError("country not underlined");}
 static void click(ChatScreen screen,double x,double y,int button){screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(x,y,new net.minecraft.client.input.MouseButtonInfo(button,0)),false);}
 static void run(ClientGameTestContext context)throws Exception{
  var calls=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/api/mcmod/chat/names",ex->{calls.incrementAndGet();String q=URLDecoder.decode(Objects.toString(ex.getRequestURI().getRawQuery(),""),StandardCharsets.UTF_8);int code=q.equals("capital=测试国家")?200:404;String body=code==200?"{\"version\":1,\"nation\":\"测试国家\",\"capital\":\"首都城区\",\"x\":4,\"z\":2}":"{}";byte[] bytes=body.getBytes(StandardCharsets.UTF_8);ex.getResponseHeaders().set("Content-Type","application/json");ex.sendResponseHeaders(code,bytes.length);try(var out=ex.getResponseBody()){out.write(bytes);}});server.start();URI site=URI.create("http://127.0.0.1:"+server.getAddress().getPort());
  try(var world=context.worldBuilder().create()){
   // Let connection lifecycle listeners observe the new world before starting an async query.
   context.waitTicks(5);
   context.runOnClient(c->{ModSettings.setGuideShown(true);ModSettings.setReadLands(false);SiteConfig.save(site);ChatAssist.useIndex(new Index(List.of(new Target(Kind.LAND,"测试领地","测试国家|测试领地",null,null,null)),List.of("测试国家")));c.gui.getChat().clearMessages(true);c.setScreen(new ChatScreen("",false));c.gui.getChat().addMessage(Component.literal("前往测试国家"));var p=countryPoint(c);click((ChatScreen)c.screen,p[0],p[1],0);check(c.screen instanceof WorldEntityScreen&&field(c.screen,"type").equals("nation")&&field(c.screen,"key").equals("测试国家"),"country click opens its SMC world page");c.setScreen(new ChatScreen("",false));c.gui.getChat().clearMessages(true);c.gui.getChat().addMessage(Component.literal("前往测试国家"));p=countryPoint(c);click((ChatScreen)c.screen,p[0],p[1],1);Object menu=staticField(ChatAssist.class,"menu");int x=(Integer)field(menu,"x"),y=(Integer)field(menu,"y");var rows=(List<?>)field(menu,"rows");int nav=-1;for(int i=0;i<rows.size();i++)if("快捷导航".equals(field(rows.get(i),"label")))nav=i;check(nav>=0,"country menu offers navigation");click((ChatScreen)c.screen,x+8,y+nav*18+8,0);});
   context.waitFor(c->Navigation.target()!=null&&Navigation.target().x()==4&&Navigation.target().z()==2,400);
   context.runOnClient(c->{var target=Navigation.target();check(target.world().equals(CityNavigation.MAIN_WORLD)&&target.name().contains("首都城区"),"country route uses exact capital geometric centre");check(calls.get()==1,"one on-demand capital request");Navigation.clear();SiteConfig.save(SiteConfig.LOCAL);ModSettings.setReadLands(true);c.setScreen(new net.minecraft.client.gui.screens.TitleScreen());});
  }finally{server.stop(0);}
 }
}
