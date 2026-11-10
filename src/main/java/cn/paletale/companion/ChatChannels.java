package cn.paletale.companion;
import java.util.Set;
public final class ChatChannels {
 public static String kind(PrivateChat.Entry e){String kind=e.channelKind().startsWith("favorite-")?e.channelKind().substring(9):e.channelKind();return kind.equals("end")||kind.equals("nether")?"global":kind;}
 public static boolean accepts(Set<String> selected,PrivateChat.Entry e){boolean channel=selected.stream().filter(s->!s.startsWith("origin:")).findAny().isEmpty()||selected.contains(kind(e));boolean origin=selected.stream().filter(s->s.startsWith("origin:")).findAny().isEmpty()||selected.contains("origin:"+e.originKind());return channel&&origin;}
 private ChatChannels(){}
}
