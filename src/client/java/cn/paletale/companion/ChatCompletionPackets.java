package cn.paletale.companion;
import java.util.concurrent.CompletableFuture;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.*;
/** One bounded request, isolated from vanilla's positive transaction IDs. */
public final class ChatCompletionPackets {
 private static final CommandPacing requestPacing=new CommandPacing();private static final java.util.LinkedHashSet<Integer> issued=new java.util.LinkedHashSet<>();private static int serial=-1000000,id;private static long deadline;private static Object connection;private static CompletableFuture<Suggestions> pending;
 static CompletableFuture<Suggestions> request(String text){cancel();if(!requestPacing.acquire(System.nanoTime()/1_000_000L))return Suggestions.empty();var c=Minecraft.getInstance();if(c.getConnection()==null)return CompletableFuture.completedFuture(Suggestions.empty().join());connection=c.getConnection();id=--serial;issued.add(id);while(issued.size()>64)issued.remove(issued.iterator().next());deadline=System.currentTimeMillis()+3000;pending=new CompletableFuture<>();c.getConnection().send(new ServerboundCommandSuggestionPacket(id,text));return pending;}
 public static boolean receive(ClientboundCommandSuggestionsPacket packet){if(!issued.remove(packet.id()))return false;if(pending!=null&&packet.id()==id&&connection==Minecraft.getInstance().getConnection()){var future=pending;pending=null;connection=null;future.complete(packet.toSuggestions());}return true;}
 static void tick(){if(pending!=null&&(System.currentTimeMillis()>deadline||connection!=Minecraft.getInstance().getConnection())){var future=pending;pending=null;connection=null;future.complete(Suggestions.empty().join());}}
 static void cancel(){if(pending!=null)pending.cancel(false);pending=null;connection=null;}
}
