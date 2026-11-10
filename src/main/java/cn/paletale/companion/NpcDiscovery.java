package cn.paletale.companion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;

/** Evidence for automatic city discovery: a service name, a supported living type and an unlisted player if applicable. */
public final class NpcDiscovery {
    private static final Set<String> TYPES=Set.of("minecraft:player","minecraft:vex","minecraft:villager","minecraft:wandering_trader","minecraft:allay");
    private static final String SERVICE=".*(?:商店|收购|兑换|快捷传送|玩法咨询|教程|抽奖|吟游诗人|厨师|奥术师|接待|任务NPC|任务 NPC).*";
    public record Seen(String name,double x,double y,double z) {}
    private NpcDiscovery() {}
    public static boolean supports(String type) { return type!=null&&TYPES.contains(type); }
    public static String cleanName(String name) { return LedgerParser.clean(name).replaceAll("[\\p{Cntrl}]", "").strip(); }
    public static boolean serviceName(String name) { return name!=null&&!name.isBlank()&&name.length()<=120&&name.matches(SERVICE); }
    public static String id(String type,String name,double x,double y,double z) {
        String source=CityNavigation.WORLD+"\n"+type+"\n"+name+"\n"+(long)Math.floor(x)+","+(long)Math.floor(y)+","+(long)Math.floor(z);
        try { return "scan-"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))).substring(0,24); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public static CityCatalog.Place place(String world,String type,String name,double x,double y,double z,boolean listedPlayer) {
        name=cleanName(name);
        if(!CityNavigation.WORLD.equals(world)||!supports(type)||!serviceName(name)||type.equals("minecraft:player")&&listedPlayer||!Double.isFinite(x+y+z)||Math.abs(x)>=1_000_000||Math.abs(z)>=1_000_000||Math.abs(y)>=2048)return null;
        return new CityCatalog.Place(id(type,name,x,y,z),name,"自动发现",type,"minecraft:compass",null,null,null,null,x,y,z);
    }
    public static boolean stable(Seen before,Seen after) { return before!=null&&before.name().equals(after.name())&&Math.hypot(before.x()-after.x(),before.z()-after.z())<=.5&&Math.abs(before.y()-after.y())<=1; }
    public static boolean valid(CityCatalog.Place place) {
        if(place==null)return false;
        var parsed=place(CityNavigation.WORLD,place.entityType(),place.name(),place.x(),place.y(),place.z(),false);
        return parsed!=null&&parsed.id().equals(place.id());
    }
}
