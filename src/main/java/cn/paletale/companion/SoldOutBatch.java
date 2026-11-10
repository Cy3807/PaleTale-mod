package cn.paletale.companion;
import java.util.*;
/** Coalesces a burst and counts distinct shops, never the number of repeated server lines. */
final class SoldOutBatch {
 private final Set<String> shops=new HashSet<>();private String scope="";private long windowAt,firstPending,lastChange;private boolean pending;
 void offer(String scope,String shop,long now){if(!this.scope.equals(scope)||now-windowAt>=30000){reset();this.scope=scope;windowAt=now;}if(!shops.add(shop))return;if(!pending)firstPending=now;pending=true;lastChange=now;}
 int drain(long now){if(!pending||now-lastChange<1200&&now-firstPending<5000)return 0;pending=false;return shops.size();}
 void reset(){shops.clear();scope="";pending=false;windowAt=0;}
}
