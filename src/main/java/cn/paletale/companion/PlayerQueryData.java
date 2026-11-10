package cn.paletale.companion;
import com.google.gson.*;
import java.util.*;
import java.util.regex.Pattern;
/** Only recognised clock/land lore and an exact target's system balance message are projected. */
public final class PlayerQueryData {
 private PlayerQueryData(){}
 public static JsonObject menu(List<String> activity,String landTitle,List<String> lore){
  var out=new JsonObject();String joined=String.join("\n",activity);
  String last=match(joined,"上次在线\\s*[:：]\\s*([^\\n]+)"),offline=match(joined,"离线自\\s*[:：]\\s*([^\\n]+)");
  if(last.isEmpty()&&offline.isEmpty())throw new IllegalArgumentException("Unknown clock");
  if(!last.isEmpty())out.addProperty("lastSeen",last);else out.add("lastSeen",JsonNull.INSTANCE);
  boolean online=last.matches("当前|现在|在线");out.addProperty("online",online);if(online||offline.isEmpty())out.add("offlineFor",JsonNull.INSTANCE);else out.addProperty("offlineFor",offline);
  var lands=new JsonArray();for(String line:lore){String clean=LedgerParser.clean(line).strip();if(clean.startsWith("-")&&!clean.contains("...")&&!clean.contains("…")){String name=clean.substring(1).strip();if(!name.isEmpty()&&name.length()<=160)lands.add(name);}}
  String count=match(LedgerParser.clean(landTitle),"[（(]\\s*(\\d+)\\s*[)）]");
  if(!count.isEmpty()){out.add("lands",lands);out.addProperty("landsComplete",Integer.parseInt(count)==lands.size());}
  return out;
 }
 private static String match(String value,String expression){var m=Pattern.compile(expression).matcher(LedgerParser.clean(value));return m.find()?m.group(1).strip():"";}
 public static Double balance(String name,String raw){
  if(name==null||!name.matches("[A-Za-z0-9_]{1,16}"))return null;
  var m=Pattern.compile("^\\s*"+Pattern.quote(name)+"\\s*的余额\\s*[:：]\\s*\\$?(-?[\\d,]+(?:\\.\\d{1,2})?)\\s*$",Pattern.CASE_INSENSITIVE).matcher(LedgerParser.clean(raw));
  if(!m.matches())return null;try{double value=Double.parseDouble(m.group(1).replace(",",""));return Double.isFinite(value)&&Math.abs(value)<=1e15?value:null;}catch(NumberFormatException e){return null;}
 }
}
