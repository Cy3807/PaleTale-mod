package cn.paletale.companion;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import java.util.*;
/** Bounded, name-only projection of player-list packets already received for this connection. */
final class LocalRoster {
 record Player(String name,UUID uuid){}
 private static long revision,built=-1;private static Object connection;private static List<Player> snapshot=List.of();private static Set<String> names=Set.of();private static Set<UUID> ids=Set.of();private static boolean truncated;
 private LocalRoster(){}
 static void changed(Object source,UUID id){var current=Minecraft.getInstance().getConnection();if(source!=current||current==null||id==null)return;if(connection!=current||built<0){revision++;return;}var info=current.getPlayerInfo(id);boolean present=info!=null&&current.getListedOnlinePlayers().contains(info);if(ids.contains(id)!=present)revision++;}
 static long revision(){return revision;}
 static void disconnected(Object source){if(source==connection){connection=null;snapshot=List.of();ids=Set.of();names=Set.of();truncated=false;built=-1;revision++;}}
 static List<Player> players(Minecraft client){
  var current=client.getConnection();if(current==null){connection=null;snapshot=List.of();ids=Set.of();names=Set.of();truncated=false;built=-1;return snapshot;}
  if(connection==current&&built==revision)return snapshot;
  var rows=new ArrayList<Player>();var seen=new HashSet<UUID>();boolean capped=false;for(var info:current.getListedOnlinePlayers()){
   if(info==null||info.getProfile()==null||info.getProfile().id()==null||info.getProfile().name()==null)continue;
   String name=info.getProfile().name();UUID id=info.getProfile().id();if(!name.matches("[A-Za-z0-9_]{1,16}")||!seen.add(id))continue;
   if(rows.size()>=5000){capped=true;break;}rows.add(new Player(name,id));
  }
  rows.sort(Comparator.comparing(Player::name,String.CASE_INSENSITIVE_ORDER).thenComparing(Player::name).thenComparing(p->p.uuid().toString()));connection=current;built=revision;snapshot=List.copyOf(rows);ids=Set.copyOf(snapshot.stream().map(Player::uuid).toList());names=Set.copyOf(snapshot.stream().map(p->p.name().toLowerCase(Locale.ROOT)).toList());truncated=capped;return snapshot;
 }
 static Set<String> onlineNames(Minecraft client){players(client);return names;}
 static boolean online(String name){players(Minecraft.getInstance());return names.contains(name.toLowerCase(Locale.ROOT));}
 static boolean truncated(){return truncated;}
 static boolean trustedServer(Minecraft client){return client!=null&&client.level!=null&&!client.hasSingleplayerServer()&&client.getCurrentServer()!=null&&ServerAccess.isSimmc(client.getCurrentServer().ip);}
 // Tab may include multiple servers, so counts alone cannot establish main-server membership.
 static boolean primary(Minecraft client){return false;}
 static JsonObject page(Minecraft client,int requested,int size){return page(players(client),requested,size);}
 static JsonObject page(List<Player> rows,int requested,int size){int n=Math.max(1,size),pages=Math.max(1,(rows.size()+n-1)/n),page=Math.max(1,Math.min(requested,pages));var result=new JsonObject();result.addProperty("version",1);result.addProperty("total",rows.size());result.addProperty("page",page);var visible=new JsonArray();for(int i=(page-1)*n;i<Math.min(rows.size(),page*n);i++){var row=new JsonObject();row.addProperty("name",rows.get(i).name());row.addProperty("uuid",rows.get(i).uuid().toString());row.addProperty("eligible",true);visible.add(row);}result.add("players",visible);return result;}
}
