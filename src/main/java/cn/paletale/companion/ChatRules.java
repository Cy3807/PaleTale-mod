package cn.paletale.companion;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
/**
 * Server chat wording as data. Downloaded files only supply literal words (never regular expressions): every word is
 * quoted before it is compiled, so a bad or hostile file can neither crash chat parsing nor cause runaway matching,
 * and anything that fails validation leaves the previous rules in place.
 */
public final class ChatRules {
 public static final int MAX_BYTES=16*1024;
 private static final Pattern WORD=Pattern.compile("[^\\p{Cntrl}\\\\|<>]{1,16}");
 private static final String HEAD="(?:\\uFFFC|\\[[A-Za-z0-9_]{1,16} head\\])";
 private final int version;private final String nation,tradeMarker,endMarker,netherMarker,say,self;
 private final List<String> publicMarkers,whispers,reserved,officialSenders;private final Map<String,List<String>> origins;
 private final Pattern nationPattern,envelope,global,official;
 static final ChatRules BUNDLED=defaults();
 private static volatile ChatRules current=BUNDLED;
 public static ChatRules current(){return current;}
 static void use(ChatRules rules){current=rules==null?BUNDLED:rules;}
 static void resetForTest(){current=BUNDLED;}
 private ChatRules(int version,String nation,List<String> publicMarkers,String tradeMarker,String endMarker,String netherMarker,Map<String,List<String>> origins,List<String> whispers,String say,String self,List<String> reserved,List<String> officialSenders){
  this.version=version;this.nation=nation;this.publicMarkers=List.copyOf(publicMarkers);this.tradeMarker=tradeMarker;this.endMarker=endMarker;this.netherMarker=netherMarker;this.origins=origins;this.whispers=List.copyOf(whispers);this.say=say;this.self=self;this.reserved=List.copyOf(reserved);this.officialSenders=List.copyOf(officialSenders);
  nationPattern=Pattern.compile("^"+Pattern.quote(nation)+"\\s+([^|]+?)\\s*\\|\\s*(?:\\uFFFC|\\[[A-Za-z0-9_]{1,16} head\\])?\\s*([A-Za-z0-9_]{1,16})\\s*[»>]\\s?(.*)$");
  String who="("+Pattern.quote(self)+"|[A-Za-z0-9_]{1,16})";
  envelope=Pattern.compile("^\\s*(?:"+HEAD+"\\s*)?"+who+"\\s+(?:"+alternatives(whispers)+")\\s+(?:"+HEAD+"\\s*)?"+who+"\\s+"+Pattern.quote(say)+"[:：]\\s?(.*)$");
  global=Pattern.compile("^([^\\r\\n]{0,160}?)\\[("+alternatives(publicMarkers)+")\\]\\s*[^\\r\\n:：»>]{0,160}?(?<![A-Za-z0-9_])([A-Za-z0-9_]{1,16})(?![A-Za-z0-9_])\\s*[^A-Za-z0-9_\\r\\n:：»>]{0,16}[:：»>]\\s?(.*)$");
  official=Pattern.compile("^("+alternatives(officialSenders)+")\\s*(?:»|>>|>|：|:)\\s*([\\s\\S]+)$");
 }
 private static String alternatives(List<String> words){var out=new StringJoiner("|");for(var word:words)out.add(Pattern.quote(word));return out.toString();}
 private static ChatRules defaults(){
  return new ChatRules(1,"国家",List.of("G","交易","末地","地狱"),"交易","末地","地狱",
   Map.of("nether",List.of("地狱远征","[地狱]"),"dungeon",List.of("地牢裂隙","[地牢]"),"end",List.of("末地")),
   List.of("悄悄地对","悄悄的对"),"说","我",List.of("全球","全服","世界","Global"),List.of("隐秘的声音","隐秘的时间","服务器公告","系统公告","公告"));
 }
 /** Parses and validates a rules document; throws IllegalArgumentException for anything unusual. */
 public static ChatRules decode(byte[] bytes){
  if(bytes==null||bytes.length==0||bytes.length>MAX_BYTES)throw new IllegalArgumentException("Rules size");
  try{
   var root=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
   if(!"chat-rules".equals(text(root,"kind"))||root.get("version").getAsInt()<1)throw new IllegalArgumentException("Rules header");
   var markers=words(root,"publicMarkers",1,12);String trade=word(root,"tradeMarker"),end=word(root,"endMarker"),nether=word(root,"netherMarker");
   for(String marker:List.of(trade,end,nether))if(!markers.contains(marker))throw new IllegalArgumentException("Marker not declared");
   var origins=new LinkedHashMap<String,List<String>>();var originNode=root.getAsJsonObject("origins");
   for(String key:List.of("nether","dungeon","end")){origins.put(key,words(originNode,key,1,8));}
   return new ChatRules(root.get("version").getAsInt(),word(root,"nation"),markers,trade,end,nether,Map.copyOf(origins),words(root,"whisperPhrases",1,6),word(root,"say"),word(root,"self"),words(root,"reservedScopes",1,16),words(root,"officialSenders",1,16));
  }catch(IllegalArgumentException invalid){throw invalid;}catch(RuntimeException broken){throw new IllegalArgumentException("Rules format",broken);}
 }
 private static String text(JsonObject node,String key){var value=node.get(key);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Missing "+key);return value.getAsString();}
 private static String word(JsonObject node,String key){String value=text(node,key);if(!WORD.matcher(value).matches())throw new IllegalArgumentException("Bad word "+key);return value;}
 private static List<String> words(JsonObject node,String key,int min,int max){
  var value=node.get(key);if(value==null||!value.isJsonArray()||value.getAsJsonArray().size()<min||value.getAsJsonArray().size()>max)throw new IllegalArgumentException("Bad list "+key);
  var out=new ArrayList<String>();for(var item:value.getAsJsonArray()){if(!item.isJsonPrimitive()||!item.getAsJsonPrimitive().isString()||!WORD.matcher(item.getAsString()).matches())throw new IllegalArgumentException("Bad word in "+key);if(!out.contains(item.getAsString()))out.add(item.getAsString());}
  return out;
 }
 public int version(){return version;}
 public Pattern nation(){return nationPattern;}
 public Pattern envelope(){return envelope;}
 public Pattern global(){return global;}
 public Pattern official(){return official;}
 public String self(){return self;}
 public String tradeMarker(){return tradeMarker;}
 public boolean reservedScope(String scope){return reserved.contains(scope);}
 /** nether / dungeon / end, or empty; the bracketed prefix keywords are checked first, the marker itself decides the rest. */
 public String origin(String prefix,String marker){
  if(marker.equals(netherMarker)||mentions(prefix,"nether"))return "nether";
  if(mentions(prefix,"dungeon"))return "dungeon";
  return marker.equals(endMarker)||mentions(prefix,"end")?"end":"";
 }
 private boolean mentions(String prefix,String origin){for(String keyword:origins.get(origin))if(prefix.contains(keyword))return true;return false;}
}
