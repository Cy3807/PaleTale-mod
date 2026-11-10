package cn.paletale.companion;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
/** Client-thread bounded background command queue, shared with chat's rolling wire limit. */
final class ClientCommandQueue {
 private record Job(String command,Object connection,long expires,BooleanSupplier valid,Runnable sent){}
 private static final ArrayDeque<Job> queue=new ArrayDeque<>();private static final CommandPacing pacing=new CommandPacing();private static Object connection;private static long nextBackground;
 private static long now(){return System.nanoTime()/1_000_000L;}
 private static void scope(){var current=Minecraft.getInstance().getConnection();if(current!=connection){queue.clear();pacing.clear();nextBackground=0;connection=current;}}
 static void background(String command,BooleanSupplier valid,Runnable sent){scope();if(connection==null||queue.size()>=16)return;if(queue.stream().anyMatch(j->j.command().equals(command)&&j.connection()==connection))return;queue.addLast(new Job(command,connection,now()+10000,valid,sent));}
 static void tick(){scope();if(LandMenuGuard.userActive())return;long now=now();queue.removeIf(j->j.connection()!=connection||now>j.expires()||!j.valid().getAsBoolean());if(queue.isEmpty()||now<nextBackground||!pacing.acquire(now))return;var job=queue.removeFirst();LandMenuGuard.sent(job.command());Minecraft.getInstance().getConnection().sendCommand(job.command());job.sent().run();nextBackground=now()+1200;}
 static boolean chat(String command){scope();long now=now();if(connection==null||!pacing.acquire(now))return false;LandMenuGuard.sent(command);Minecraft.getInstance().getConnection().sendCommand(command);nextBackground=Math.max(nextBackground,now+1200);return true;}
 /** A command the player asked for (not armed against menus: whatever it opens is meant to be seen); still counted against the shared rate limit. */
 static boolean direct(String command){scope();long now=now();if(connection==null||!pacing.acquire(now))return false;Minecraft.getInstance().getConnection().sendCommand(command);nextBackground=Math.max(nextBackground,now+1200);return true;}
}
