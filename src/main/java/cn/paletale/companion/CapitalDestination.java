package cn.paletale.companion;
import com.google.gson.JsonObject;
/** Never invent a capital; accept only the exact requested country and bounded integer geometry. */
public record CapitalDestination(String nation,String capital,int x,int z){
 public static CapitalDestination parse(JsonObject root,String expected){
  try{
   if(root.get("version").getAsInt()!=1||!root.get("nation").getAsString().equals(expected))return null;
   String capital=root.get("capital").getAsString();double x=root.get("x").getAsDouble(),z=root.get("z").getAsDouble();
   if(capital.isBlank()||capital.length()>60||capital.chars().anyMatch(Character::isISOControl)||!Double.isFinite(x)||!Double.isFinite(z)||x!=Math.rint(x)||z!=Math.rint(z)||Math.abs(x)>1_000_000||Math.abs(z)>1_000_000)return null;
   return new CapitalDestination(expected,capital,(int)x,(int)z);
  }catch(RuntimeException invalid){return null;}
 }
}
