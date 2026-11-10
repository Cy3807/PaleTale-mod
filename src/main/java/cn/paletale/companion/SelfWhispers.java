package cn.paletale.companion;
import java.util.*;
/** Pair opposite server echoes one-for-one; never collapse repeated sends in one direction. */
public final class SelfWhispers {
 public record Decision(boolean drop,PrivateChat.Entry replace){}
 private final ArrayDeque<PrivateChat.Entry> unmatched=new ArrayDeque<>();
 public Decision accept(PrivateChat.Entry e,String me){if(!e.channelKind().equals("private")||!e.peer().equalsIgnoreCase(me))return new Decision(false,null);unmatched.removeIf(p->e.time()-p.time()>2000);for(var it=unmatched.iterator();it.hasNext();){var p=it.next();if(p.outgoing()!=e.outgoing()&&p.text().equals(e.text())&&Math.abs(p.time()-e.time())<=2000){it.remove();return e.outgoing()?new Decision(false,p):new Decision(true,null);}}unmatched.addLast(e);while(unmatched.size()>64)unmatched.removeFirst();return new Decision(false,null);}
 public void clear(){unmatched.clear();}
 public static List<PrivateChat.Entry> repair(List<PrivateChat.Entry> rows,String me){var pairs=new SelfWhispers();var result=new ArrayList<PrivateChat.Entry>();for(var e:rows){var decision=pairs.accept(e,me);if(decision.drop())continue;if(decision.replace()!=null)result.remove(decision.replace());result.add(e);}result.sort(Comparator.comparingLong(PrivateChat.Entry::time));return List.copyOf(result);}
}
