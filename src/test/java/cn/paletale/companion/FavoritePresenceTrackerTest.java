package cn.paletale.companion;
import java.util.*;
final class FavoritePresenceTrackerTest {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static void run(){var t=new FavoritePresenceTracker();var all=Map.of("a","Alice","b","Bob");
  check(t.accept(all,Map.of("a","Alice")).isEmpty(),"first snapshot silent");
  check(t.accept(all,Map.of("a","Alice","b","Bob")).equals(List.of(new FavoritePresenceTracker.Change("b","Bob",true))),"online transition");
  check(t.accept(null,null).isEmpty(),"unavailable does not emit offline");
  check(t.accept(all,Map.of("b","Bob")).equals(List.of(new FavoritePresenceTracker.Change("a","Alice",false))),"offline transition after complete snapshot");
  check(t.accept(Map.of("a","Alice"),Map.of()).isEmpty(),"unfavorite is not offline");
  check(t.accept(all,Map.of("b","Bob")).isEmpty(),"newly favorited already-online player is silent");
  t.reset();check(t.accept(Map.of("a","OldName"),Map.of("a","CurrentName")).isEmpty(),"renamed player baseline");check(t.accept(Map.of("a","OldName"),Map.of()).equals(List.of(new FavoritePresenceTracker.Change("a","CurrentName",false))),"offline uses latest visible profile name");
  t.reset();check(t.accept(all,Map.of()).isEmpty(),"reconnect establishes baseline");
  var many=Map.of("a","A","b","B","c","C","d","D");t.reset();t.accept(many,many);
  check(t.accept(many,Map.of()).isEmpty(),"bulk disappearance silently rebaselines");
  check(t.accept(many,many).isEmpty(),"bulk return does not announce everyone");
  check(t.accept(many,Map.of("a","A","b","B","c","C")).size()==1,"individual offline still alerts");
  check(t.accept(many,many).size()==1,"individual online still alerts");
 }
}
