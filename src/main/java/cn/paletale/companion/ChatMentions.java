package cn.paletale.companion;
import java.util.*;
import java.util.regex.Pattern;
/** Player chat lines (public, land, nation) and where they mention a given player; whispers and system text never match. */
public final class ChatMentions {
 public record Line(String channel,String scope,String sender,int bodyStart){}
 public record Span(int start,int end){}
 public record Hit(Line line,List<Span> spans){}
 private static final Pattern RP=Pattern.compile("^\\[RP\\]\\s+(.{1,160}?)\\s+说\\s+(.*)$");
 private static final Pattern TOKEN=Pattern.compile("(?<![A-Za-z0-9_])@?[A-Za-z0-9_]{1,16}(?![A-Za-z0-9_])");
 private static final Pattern NAME=Pattern.compile("[A-Za-z0-9_]{1,16}");
 /** Channel is global, land or nation; scope is the land/nation name or the public marker. */
 public static Optional<Line> line(String plain){
  if(plain==null||plain.length()>4096)return Optional.empty();
  var rules=ChatRules.current();String trimmed=plain.stripLeading();int lead=plain.length()-trimmed.length();var m=rules.global().matcher(trimmed);if(m.matches())return Optional.of(new Line("global",m.group(2),m.group(3),m.start(4)+lead));
  var rp=RP.matcher(trimmed);if(rp.matches())return Optional.of(new Line("rp","RP",rp.group(1).strip(),rp.start(2)+lead));
  for(int type=0;type<2;type++){var group=(type==0?rules.nation():PrivateChat.LAND).matcher(plain.stripLeading());if(!group.matches())continue;String scope=group.group(1).strip();if(!PrivateChat.scopeValid(scope)||rules.reservedScope(scope))return Optional.empty();int shift=plain.length()-plain.stripLeading().length();return Optional.of(new Line(type==0?"nation":"land",scope,group.group(2),group.start(3)+shift));}
  return Optional.empty();
 }
 /** Spans cover the name plus an optional leading @; matched as a whole word. */
 public static Optional<Hit> find(String plain,String me){
  if(me==null||!NAME.matcher(me).matches())return Optional.empty();
  var parsed=line(plain);if(parsed.isEmpty())return Optional.empty();
  var spans=new ArrayList<Span>();var m=Pattern.compile("(?<![A-Za-z0-9_])@?"+Pattern.quote(me)+"(?![A-Za-z0-9_])",Pattern.CASE_INSENSITIVE).matcher(plain);m.region(parsed.get().bodyStart(),plain.length());
  while(m.find()&&spans.size()<16)spans.add(new Span(m.start(),m.end()));
  return spans.isEmpty()?Optional.empty():Optional.of(new Hit(parsed.get(),List.copyOf(spans)));
 }
 public record NamedSpan(int start,int end,String name,boolean self){}
 /** Detect self and online player IDs only inside recognised chat bodies. */
 public static List<NamedSpan> findPlayers(String plain,String me,Set<String> online){
  if(me==null||!NAME.matcher(me).matches())return List.of();
  var parsed=line(plain);if(parsed.isEmpty())return List.of();
  Set<String> allowed=online==null?Set.of():online;
  var matches=new ArrayList<NamedSpan>();var m=TOKEN.matcher(plain);m.region(parsed.get().bodyStart(),plain.length());
  while(m.find()&&matches.size()<16){String token=m.group(),name=token.startsWith("@")?token.substring(1):token;boolean self=name.equalsIgnoreCase(me);if(!self&&!allowed.contains(name.toLowerCase(Locale.ROOT)))continue;String canonical=self?me:name;matches.add(new NamedSpan(m.start(),m.end(),canonical,self));}
  return List.copyOf(matches);
 }
 private ChatMentions(){}
}
