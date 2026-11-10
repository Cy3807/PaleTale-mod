package cn.paletale.companion;
import com.google.gson.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
/** Read-only display catalog. Names, physical binding identity and visual models are separate. */
final class ServerItemCatalog {
 record Appearance(String model,String baseItem,Integer customModelData){}
 private static final Snapshot EMPTY=new Snapshot(Map.of(),Set.of());
 private static volatile Snapshot published=EMPTY;
 record Snapshot(Map<String,Appearance> items,Set<String> blocked){}
 /** Normalized name key -> display name; null marks a key shared by two different names. Filled from the site's known-names file after the first download; the jar ships no copy. */
 private static volatile Map<String,String> names=Map.of();
 static List<String> decodeNames(byte[] bytes){
  if(bytes.length>2*1024*1024)throw new IllegalArgumentException("Names too large");
  var root=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
  if(root.get("version").getAsInt()!=1)throw new IllegalArgumentException("Unsupported names file");
  var rows=root.getAsJsonArray("names");if(rows.size()>50_000)throw new IllegalArgumentException("Too many names");
  var out=new ArrayList<String>(rows.size());
  for(var value:rows){String name=value.getAsString();if(name.length()>160)throw new IllegalArgumentException("Invalid name");out.add(name);}
  return out;
 }
 static void applyNames(Collection<String> known){
  Map<String,String> map=new HashMap<>();
  for(String name:known){String key=key(name);if(map.containsKey(key)&&!Objects.equals(map.get(key),name))map.put(key,null);else if(!map.containsKey(key))map.put(key,name);}
  names=Collections.unmodifiableMap(map);
 }
 static String key(String name){String normalized=java.text.Normalizer.normalize(name,java.text.Normalizer.Form.NFKC).replaceAll("(?i)§[0-9a-fk-orx]","").replaceFirst("^\\s*【(?:玩偶头|玩偶|商品)】","");StringBuilder out=new StringBuilder();normalized.codePoints().filter(c->Character.isLetterOrDigit(c)||c=='★'||c=='☆'||c=='✰').forEach(out::appendCodePoint);return out.toString().toLowerCase(Locale.ROOT);}
 static boolean sameName(String a,String b){String key=key(a);return !key.isEmpty()&&names.get(key)!=null&&key.equals(key(b));}
 static String displayKey(String name){return java.text.Normalizer.normalize(name,java.text.Normalizer.Form.NFKC).trim();}
 static Snapshot decode(byte[] bytes){
  if(bytes.length>4*1024*1024)throw new IllegalArgumentException("Catalog too large");
  var root=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
  if(root.get("version").getAsInt()!=25||!"display_mapping_only".equals(root.get("purpose").getAsString()))throw new IllegalArgumentException("Unsupported display catalog");
  var rows=root.getAsJsonArray("items");if(rows.size()>5000)throw new IllegalArgumentException("Too many appearances");
  Map<String,Appearance> exact=new HashMap<>();Set<String> blocked=new HashSet<>();
  for(var value:rows){var row=value.getAsJsonObject();String name=row.get("name").getAsString();if(name.isBlank()||name.length()>160)throw new IllegalArgumentException("Invalid display name");if(!row.has("displayApproved")||!row.get("displayApproved").isJsonPrimitive()||!row.get("displayApproved").getAsJsonPrimitive().isBoolean()||!row.get("displayApproved").getAsBoolean()){blocked.add(key(name));continue;}
   String model=row.get("itemModel").getAsString();if(!resource(model))throw new IllegalArgumentException("Invalid appearance");
   String base=!row.has("baseItem")||row.get("baseItem").isJsonNull()?"minecraft:apple":row.get("baseItem").getAsString();if(!resource(base))throw new IllegalArgumentException("Invalid base item");
   Integer cmd=!row.has("customModelData")||row.get("customModelData").isJsonNull()?null:row.get("customModelData").getAsBigDecimal().intValueExact();if(cmd!=null&&(cmd<0||cmd>1_000_000))throw new IllegalArgumentException("Invalid custom model data");
   var appearance=new Appearance(model,base,cmd);var old=exact.putIfAbsent(displayKey(name),appearance);if(old!=null&&!old.equals(appearance))throw new IllegalArgumentException("Conflicting display names");
  }
  if(root.has("pending"))for(var value:root.getAsJsonArray("pending")){String name=value.getAsJsonObject().get("name").getAsString();if(name.length()>160)throw new IllegalArgumentException();blocked.add(key(name));}
  return new Snapshot(Map.copyOf(exact),Set.copyOf(blocked));
 }
 private static boolean resource(String value){return value.length()<=256&&value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")&&!value.contains("..");}
 static void apply(Snapshot snapshot){published=snapshot;}
 static void clearPublished(){published=EMPTY;names=Map.of();}
 static Appearance appearance(String name){var live=published;String key=key(name);if(live.blocked.contains(key))return null;return live.items.get(displayKey(name));}

}
