package cn.paletale.companion;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

/** A shop sign is reported gone only after repeated, well-spaced misses where the block can really be seen. */
final class ShopGonePolicyTest {
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static void run(){
  var a=new ShopGonePolicy.Spot(1,64,2);var b=new ShopGonePolicy.Spot(5,64,5);
  var policy=new ShopGonePolicy();policy.expect(List.of(a,b));
  Set<ShopGonePolicy.Spot> there=new HashSet<>(List.of(b));
  check(policy.observe(s->true,there::contains,0).isEmpty(),"first miss alone is not enough");
  check(policy.observe(s->true,there::contains,30_000).isEmpty(),"second miss within a minute is not enough");
  var gone=policy.observe(s->true,there::contains,61_000);
  check(gone.equals(List.of(a)),"missing across a full minute is reported, a present sign is not");
  check(policy.observe(s->true,there::contains,100_000).isEmpty(),"not repeated right away");
  check(policy.observe(s->true,there::contains,61_000+600_000).equals(List.of(a)),"repeated after ten minutes");
  // not visible (chunk unloaded / far): the streak is dropped, nothing is reported
  var p2=new ShopGonePolicy();p2.expect(List.of(a));
  p2.observe(s->true,s->false,0);p2.observe(s->false,s->false,40_000);
  check(p2.observe(s->true,s->false,70_000).isEmpty(),"an unseen scan restarts the count");
  check(p2.observe(s->true,s->false,140_000).equals(List.of(a)),"then two visible misses a minute apart report");
  // sighting resets and allows a fresh report later
  var p3=new ShopGonePolicy();p3.expect(List.of(a));
  p3.observe(s->true,s->false,0);p3.observe(s->true,s->true,30_000);
  check(p3.observe(s->true,s->false,70_000).isEmpty(),"a sighting starts over");
  var p4=new ShopGonePolicy();p4.expect(List.of(a));p4.observe(s->true,s->false,0);
  var first=p4.observe(s->true,s->false,61_000);p4.failed(first);
  check(p4.observe(s->true,s->false,62_000).equals(List.of(a)),"a failed report is retried at the next scan");
  var many=new ShopGonePolicy();var spots=new java.util.ArrayList<ShopGonePolicy.Spot>();for(int i=0;i<50;i++)spots.add(new ShopGonePolicy.Spot(i,64,0));many.expect(spots);
  many.observe(s->true,s->false,0);
  check(many.observe(s->true,s->false,61_000).size()==30,"at most 30 per report");
  System.out.println("Shop gone policy regression: passed");
 }
}
