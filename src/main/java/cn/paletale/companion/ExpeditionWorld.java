package cn.paletale.companion;

/** Stable ids for SIMMC's Bungee-connected expedition servers and their /m menu labels. */
public enum ExpeditionWorld {
    NETHER("nether", "地狱远征-地狱", "地狱远征"),
    FLESH("flesh", "诡异的血肉裂缝-血肉", "诡异的血肉裂缝"),
    DUNGEON("dungeon", "地牢裂缝-地牢", "地牢裂缝"),
    END("end", "末地世界-末地", "末地世界");

    private final String id;
    private final String menuName;
    private final String[] aliases;
    ExpeditionWorld(String id, String menuName,String... aliases) { this.id = id; this.menuName = menuName;this.aliases=aliases; }
    public String id() { return id; }
    public String menuName() { return menuName; }
    /** The short name after the dash (地狱、血肉、地牢、末地). */
    public String shortName() { return menuName.substring(menuName.lastIndexOf('-') + 1); }
    public boolean matchesMenu(String text){String candidate=normalize(text);if(candidate.contains(normalize(menuName)))return true;for(String alias:aliases)if(candidate.contains(normalize(alias)))return true;return false;}
    private static String normalize(String value){return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");}
    public static ExpeditionWorld byId(String id) {
        for (var world : values()) if (world.id.equalsIgnoreCase(id)) return world;
        return null;
    }
}
