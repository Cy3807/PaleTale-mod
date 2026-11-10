package cn.paletale.companion;

/** Public NPC appearance only; no player account/profile properties or rotating yaw are uploaded. */
public record NpcProfile(String id, String world, String name, String entityType, double x, double y, double z, String skinHash, boolean personalized) {
    public static NpcProfile of(CityCatalog.Place place, String hash) { return of(place,hash,false); }
    public static NpcProfile of(CityCatalog.Place place, String hash, boolean personalized) { return new NpcProfile(place.id(), CityNavigation.WORLD, place.name(), place.entityType(), place.x(), place.y(), place.z(), personalized ? null : hash, personalized); }
    public boolean valid() {
        return id != null && (id.matches("(?:npc|shortcut)-[0-9]{1,3}")||id.matches("scan-[a-f0-9]{24}")&&NpcDiscovery.serviceName(name)&&id.equals(NpcDiscovery.id(entityType,name,x,y,z))) && CityNavigation.WORLD.equals(world) && name != null && name.length() <= 120
                && Double.isFinite(x+y+z) && Math.abs(x) < 1_000_000 && Math.abs(z) < 1_000_000 && Math.abs(y) < 2048
                && ("minecraft:player".equals(entityType) ? (personalized ? skinHash == null : skinHash==null?id.startsWith("scan-"):skinHash.matches("[a-f0-9]{64}")) : NpcDiscovery.supports(entityType) && skinHash == null && !personalized);
    }
    public String avatarPath() { return skinHash == null ? null : "/api/mcmod/nav/npcs/" + id + "/avatar/" + skinHash; }
    public static String skinHash(String property) {
        try {
            if (property == null || property.length() > 12000) return null;
            var json = com.google.gson.JsonParser.parseString(new String(java.util.Base64.getDecoder().decode(property), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            return textureHash(json.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString());
        } catch (RuntimeException invalid) { return null; }
    }
    public static String textureHash(String url) {
        try {
            var uri = java.net.URI.create(url);
            if (!uri.getScheme().matches("https?") || !"textures.minecraft.net".equals(uri.getHost()) || uri.getRawQuery() != null || uri.getUserInfo() != null || uri.getPort() != -1) return null;
            return uri.getPath().matches("/texture/[a-f0-9]{64}") ? uri.getPath().substring(9) : null;
        } catch (RuntimeException invalid) { return null; }
    }
}
