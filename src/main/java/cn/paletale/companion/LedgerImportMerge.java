package cn.paletale.companion;
import java.util.*;
/**
 * Conservative deduplication for imported ledger rows. An incoming row matches an existing row of
 * the same account and raw text within one second; identical repeats (for example several tips of
 * the same amount from one player in the same second) are matched one-for-one instead of all being
 * dropped after the first.
 */
public final class LedgerImportMerge {
 private LedgerImportMerge(){}
 private static String legacyKey(LedgerEntry e,long second){return e.account()+"\0"+e.kind()+"\0"+second+"\0"+e.currency()+"\0"+e.amount()+"\0"+e.counterparty()+"\0"+e.item()+"\0"+e.quantity()+"\0"+e.x()+"\0"+e.y()+"\0"+e.z();}
 private static final class SeenTime { final long at; boolean used; SeenTime(long at){this.at=at;} }
 public static List<LedgerEntry> additions(List<LedgerEntry> existing,List<LedgerEntry> incoming,String account,int capacity){
  var legacy=new HashSet<String>();
  var rawTimes=new HashMap<String,List<SeenTime>>();
  for(var e:existing){
   if(e.raw().isEmpty())legacy.add(legacyKey(e,e.time()/1000));
   else rawTimes.computeIfAbsent(e.account()+"\0"+e.raw(),k->new ArrayList<>()).add(new SeenTime(e.time()));
  }
  var added=new ArrayList<LedgerEntry>();int limit=Math.max(0,capacity-existing.size());
  for(var e:incoming){
   if(!e.recordable()||!account.equals(e.account())||e.raw().isEmpty())continue;
   long second=e.time()/1000;
   if(legacy.contains(legacyKey(e,second-1))||legacy.contains(legacyKey(e,second))||legacy.contains(legacyKey(e,second+1)))continue;
   var times=rawTimes.get(e.account()+"\0"+e.raw());boolean matched=false;
   if(times!=null)for(var seen:times)if(!seen.used&&Math.abs(seen.at-e.time())<=1000){seen.used=true;matched=true;break;}
   if(matched)continue;
   if(added.size()>=limit)break;
   added.add(e);
  }
  return List.copyOf(added);
 }
}
