package cn.paletale.companion;
import java.util.*;
/** Diffs complete snapshots only. Login/reconnect and newly-added favourites establish a silent baseline. */
public final class FavoritePresenceTracker {
 public record Change(String id,String name,boolean online) {}
 private Map<String,String> favorites,online;
 public void reset(){favorites=null;online=null;}
 boolean ready(){return online!=null;}
 boolean isOnline(String uuid){return online!=null&&online.containsKey(uuid);}
 private static boolean bulk(int changed,int population){return changed>=3 && changed*2>=population;}
 public List<Change> accept(Map<String,String> currentFavorites,Map<String,String> currentOnline){
  if(currentFavorites==null||currentOnline==null||!currentFavorites.keySet().containsAll(currentOnline.keySet()))return List.of();
  var events=new ArrayList<Change>();if(favorites!=null&&online!=null){
   var joined=new ArrayList<Change>();var left=new ArrayList<Change>();
   for(var e:currentOnline.entrySet())if(favorites.containsKey(e.getKey())&&currentFavorites.containsKey(e.getKey())&&!online.containsKey(e.getKey()))joined.add(new Change(e.getKey(),e.getValue(),true));
   for(var e:online.entrySet())if(favorites.containsKey(e.getKey())&&currentFavorites.containsKey(e.getKey())&&!currentOnline.containsKey(e.getKey()))left.add(new Change(e.getKey(),e.getValue(),false));
   // A large roster wipe/repopulation is not reliable evidence of individual transitions.
   if(!bulk(joined.size(),currentOnline.size()))events.addAll(joined);
   if(!bulk(left.size(),online.size()))events.addAll(left);
  }
  favorites=Map.copyOf(currentFavorites);online=Map.copyOf(currentOnline);return List.copyOf(events);
 }
}
