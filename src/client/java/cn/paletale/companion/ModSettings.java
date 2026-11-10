package cn.paletale.companion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Player preferences kept in paletale/config/paletale.properties next to the site choice (SiteConfig keeps any other lines it finds there). */
final class ModSettings {
    private static final String HEADER = "PaleTale companion: site = main | backup; autoFavorite, dailySummary, wineTooltip, readLands, xaeroWaypoint = true | false";
    private ModSettings() {}
    private static Path file() { return ModPaths.config().resolve("paletale.properties"); }
    private static synchronized String read(String key, String fallback) {
        try {
            if (Files.isRegularFile(ModPaths.readable("paletale.properties"))) {
                Properties properties = new Properties();
                try (var in = Files.newBufferedReader(ModPaths.readable("paletale.properties"))) { properties.load(in); }
                return properties.getProperty(key, fallback).trim();
            }
        } catch (Exception ignored) { /* a broken file means the default */ }
        return fallback;
    }
    private static synchronized void write(String key, String value) {
        try {
            Properties properties = new Properties();
            if (Files.isRegularFile(ModPaths.readable("paletale.properties"))) try (var in = Files.newBufferedReader(ModPaths.readable("paletale.properties"))) { properties.load(in); }
            properties.setProperty(key, value);
            Files.createDirectories(file().getParent());
            try (var out = Files.newBufferedWriter(file())) { properties.store(out, HEADER); }
        } catch (Exception ignored) { /* the choice still applies until the game closes */ }
    }
    private static Double scale;
    static double uiScale() { if(scale==null){try{scale=Double.parseDouble(read("uiScale","1"));}catch(NumberFormatException ignored){scale=1d;}if(!Double.isFinite(scale))scale=1d;scale=Math.max(.75,Math.min(1.25,scale));}return scale; }
    static void setUiScale(double value) {scale=Math.max(.75,Math.min(1.25,Math.round(value*20)/20d));write("uiScale",String.valueOf(scale));}
    private static final java.util.Map<String, Boolean> FLAGS = new java.util.concurrent.ConcurrentHashMap<>();
    private static boolean flag(String key, boolean fallback) { return FLAGS.computeIfAbsent(key, k -> Boolean.parseBoolean(read(k, String.valueOf(fallback)))); }
    private static void setFlag(String key, boolean on) { FLAGS.put(key, on); write(key, String.valueOf(on)); }

    /** Dark colour scheme for every Paletale window. Off by default. */
    static boolean darkTheme() { return flag("darkTheme", false); }
    static void setDarkTheme(boolean on) { setFlag("darkTheme", on); }

    /** Buying from a shop whose owner has a public ad favourites that ad, so restock and price changes reach the buyer. Off until switched on. */
    static boolean autoFavorite() { return flag("autoFavorite", false); }
    static void setAutoFavorite(boolean on) { setFlag("autoFavorite", on); }
    /** Once a day, after joining a server: yesterday's income, spending and best seller from the ledger. On by default. */
    static boolean dailySummary() { return flag("dailySummary", true); }
    static void setDailySummary(boolean on) { setFlag("dailySummary", on); }
    /** Starting a navigation reads the player's territories from /l menu (without showing it). On by default. */
    static boolean readLands() { return flag("readLands", true); }
    static void setReadLands(boolean on) { setFlag("readLands", on); }

    /** The command wheel opens on a key press and stays up (click) instead of while the key is held. Off (hold) by default. */
    static boolean radialClick() { return flag("radialClick", false); }
    static void setRadialClick(boolean on) { setFlag("radialClick", on); }

    /** What a plain click on a player / land / nation / teleport point in chat does ("open" the SMC page, "msg", "pay", "navigate", "map"); "open" unless chosen otherwise. */
    static String chatClickAction(String kind) { String value = read("chatClick" + kind, "open"); return value.isEmpty() ? "open" : value; }
    static void setChatClickAction(String kind, String action) { write("chatClick" + kind, action); }

    /** Entry addresses of the SIMMC server besides the built-in ones, comma separated in the settings file (extraServers=host:port,...). */
    static java.util.List<String> extraServers() { String value = read("extraServers", ""); return value.isEmpty() ? java.util.List.of() : java.util.Arrays.stream(value.split(",")).map(String::trim).filter(e -> !e.isEmpty()).limit(8).toList(); }

    /** The command that invites a player to a team, "%s" being the player; a guess at the server's wording, changeable in the settings file (teamInviteCommand). */
    static String teamInviteCommand() { String value = read("teamInviteCommand", "party invite %s"); return value.contains("%s") ? value : "party invite %s"; }

    /** Whether the navigation destination is mirrored as a (temporary) waypoint in Xaero's Minimap when that mod is installed. */
    static boolean xaeroWaypoint() { return flag("xaeroWaypoint", true); }
    static void setXaeroWaypoint(boolean on) { setFlag("xaeroWaypoint", on); }
    /** Inventory tooltips of registered wines add their effect and level. On by default. */
    static boolean wineTooltip() { return flag("wineTooltip", true); }
    static void setWineTooltip(boolean on) { setFlag("wineTooltip", on); }
    /** The day (Beijing, yyyy-MM-dd) the daily summary was last shown. */
    /** The player has opened PaleTale's main window at least once; the welcome guide is only for those who never have. */
    static boolean archiveOpened() { return flag("archiveOpened", false); }
    static void markArchiveOpened() { if (!archiveOpened()) setFlag("archiveOpened", true); }
    /**
     * Installs from before this record existed: a settings file already written by an earlier version (daily summary date, UI scale, site) means the
     * mod has been in use, so the player is not treated as new. Run once at start-up.
     */
    static void inferArchiveOpened() {
        if (!read("archiveOpened", "").isEmpty()) return;
        if (!read("lastDaily", "").isEmpty() || !read("uiScale", "").isEmpty() || !read("site", "").isEmpty()) setFlag("archiveOpened", true);
    }
    /** The welcome guide was seen (closed once); it is not shown on its own again. */
    static boolean guideShown() { return flag("guideShown", false); }
    static void setGuideShown(boolean on) { setFlag("guideShown", on); }

    static boolean favoriteSound() { return flag("favoriteSound", true); }
    static void setFavoriteSound(boolean on) { setFlag("favoriteSound", on); }
    static boolean favoritePresence() { return flag("favoritePresence", true); }
    static void setFavoritePresence(boolean on) { setFlag("favoritePresence", on); }

    /** What the navigation guide line is drawn with: one of {@link NavParticles}' keys (default "light"; "off" draws nothing). */
    static String navParticle() { String key = read("navParticle", "light"); return NavParticles.known(key) ? key : "light"; }
    static void setNavParticle(String key) { if (NavParticles.known(key)) write("navParticle", key); }

    /** The font of PaleTale's windows: "serif" (the bundled font, default) or "minecraft" (the game's own pixel font). */
    static String uiFont() { return !PaleFont.supported() || "minecraft".equals(read("uiFont", "serif")) ? "minecraft" : "serif"; }
    static void setUiFont(String key) { if (java.util.List.of("serif", "minecraft").contains(key)) write("uiFont", key); }
    static boolean paleFont() { return !"minecraft".equals(uiFont()); }

    static boolean chatCoordinates() { return flag("chatCoordinates", true); }
    static boolean chatPlaces() { return flag("chatPlaces", true); }
    static boolean chatPlayers() { return flag("chatPlayers", true); }
    static boolean chatLinks() { return flag("chatLinks", true); }
    static boolean chatItems() { return flag("chatItems", true); }
    static boolean chatMenu() { return flag("chatMenu", true); }
    /** Another player naming you in public / land / nation chat shows a green @ID and rings once. */
    static boolean chatMentions() { return flag("chatMentions", true); }
    /** Global do-not-disturb: mentions stay highlighted but never ring. */
    static boolean chatDoNotDisturb() { return flag("chatDoNotDisturb", false); }
    static void setChat(String key, boolean on) { if (!java.util.Set.of("chatCoordinates","chatPlaces","chatPlayers","chatLinks","chatItems","chatMenu","chatMentions","chatDoNotDisturb").contains(key)) throw new IllegalArgumentException(); setFlag(key,on); ChatAssist.settingsChanged(); }

    static boolean scoreboard(String key) { return flag("scoreboard." + key, true); }
    /** 把服务器侧边栏并入 PaleTale 计分板，避免两块面板互相覆盖；默认开启。 */
    static boolean mergeServerScoreboard() { return flag("scoreboard.merge", true); }
    static void setScoreboard(String key, boolean on) { if (!java.util.Set.of("enabled", "monsoon", "favorites", "social", "merge").contains(key)) throw new IllegalArgumentException(); setFlag("scoreboard." + key, on); }

    /** Last observed Yun day anchor, stored as year|month|day|dayStartMillis|exact. */
    static String yunAnchor() { return read("yunAnchor", ""); }
    static void setYunAnchor(String value) { write("yunAnchor", value); }

    /** The ledger's left-column choices (category, flow, time window, official kind, statistics), kept so reopening the ledger does not reset them: "group|flow|range|official|stats". */
    static String ledgerView() { return read("ledgerView", ""); }
    static void setLedgerView(String value) { if (!value.equals(ledgerView())) write("ledgerView", value); }
    static String lastDaily() { return read("lastDaily", ""); }
    static void setLastDaily(String day) { write("lastDaily", day); }
}
