package cn.paletale.companion;
import com.google.gson.*;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import static cn.paletale.companion.MarketTheme.*;
/** Private state belongs only to one name/token pair; it never enters the public world cache. */
final class WorldFavoriteControl {
 private final WorldEntityScreen parent;private final MarketApi market;private final String name;private final java.util.function.Supplier<String> tokens;private final WorldApi api=new WorldApi();private String token,error="";private boolean busy,started;private int generation;private JsonObject state;
 WorldFavoriteControl(WorldEntityScreen parent,MarketApi market,String name){this(parent,market,name,PaleTaleClient.ACCOUNT::token);}
 WorldFavoriteControl(WorldEntityScreen parent,MarketApi market,String name,java.util.function.Supplier<String> tokens){this.parent=parent;this.market=market;this.name=name;this.tokens=tokens;}
 void reset(){generation++;token=null;state=null;busy=false;started=false;error="";}
 void tick(){if(!Objects.equals(token,tokens.get())){reset();parent.rebuildWidgets();}}
 Action button(int x,int y){ensure();var button=new Action(token==null?"登录后收藏":busy?"核对中…":state==null?"收藏不可用":state.get("favorited").getAsBoolean()?"★ 已收藏":"☆ 收藏",x,y,88,18,state!=null&&state.get("favorited").getAsBoolean(),null,b->toggle());button.active=token!=null&&!busy&&state!=null;button.setTooltip(Tooltip.create(Component.literal(error.isEmpty()?"按正版UUID收藏此SMC玩家":error)));return button;}
 private void ensure(){String actor=tokens.get();if(!Objects.equals(token,actor)){reset();token=actor;}if(token==null||started)return;started=true;busy=true;int run=++generation;String held=token;api.call(market.site(),"/api/mcmod/world/favorites?name="+java.net.URLEncoder.encode(name,java.nio.charset.StandardCharsets.UTF_8),held,null).whenComplete((value,failure)->Minecraft.getInstance().execute(()->{if(run!=generation||!Objects.equals(held,tokens.get()))return;busy=false;if(failure!=null)error="收藏尚未就绪，或无法核实正版身份与服务器记录。";else if(value.has("uuid")&&WorldShopTable.value(value,"uuid").matches("[a-f0-9]{32}")&&value.has("favorited"))state=value;else error="收藏信息暂不可用。";if(Minecraft.getInstance().screen==parent)parent.rebuildWidgets();}));}
 private void toggle(){if(state==null||busy||token==null)return;busy=true;error="";int run=++generation;String actor=token;boolean on=!state.get("favorited").getAsBoolean();var body=new JsonObject();body.add("uuid",state.get("uuid"));body.add("name",state.get("name"));body.addProperty("on",on);
  var requestSite=market.site();long requestAccount=FavoritePresenceWatch.currentAccountId();api.call(requestSite,"/api/mcmod/world/favorites",actor,body).whenComplete((value,failure)->Minecraft.getInstance().execute(()->{if(failure==null){DiskPages.clear(DiskPages.owner(requestSite));WorldEntityScreen.clearFavoriteCache();}if(failure==null)PaleTaleClient.FAVORITE_PRESENCE.changed(actor,requestSite,requestAccount,body.get("uuid").getAsString(),body.get("name").getAsString(),on);if(run!=generation||!Objects.equals(actor,tokens.get()))return;busy=false;if(failure!=null)error="收藏未保存，请刷新正版身份或等待站点更新后重试。";else state.addProperty("favorited",on);if(Minecraft.getInstance().screen==parent)parent.rebuildWidgets();}));parent.rebuildWidgets();
 }
}
