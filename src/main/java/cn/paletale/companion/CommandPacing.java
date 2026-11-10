package cn.paletale.companion;
import java.util.ArrayDeque;
/** Rolling window, independent of wall-clock changes. */
public final class CommandPacing {
 private final ArrayDeque<Long> sent=new ArrayDeque<>();
 public boolean acquire(long now){while(!sent.isEmpty()&&now-sent.peekFirst()>=1000)sent.removeFirst();if(sent.size()>=3)return false;sent.addLast(now);return true;}
 public void clear(){sent.clear();}
}
