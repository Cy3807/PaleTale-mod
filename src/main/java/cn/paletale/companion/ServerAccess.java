package cn.paletale.companion;

/** The canonical entry and short-lived destinations named by an actual transfer packet from that trusted connection. */
public final class ServerAccess {
    private static final java.util.Map<String,Long> TRANSFERS = new java.util.concurrent.ConcurrentHashMap<>();
    /** Extra entries (host or host:port) a player has put in the settings file, for an entry point that is not one of the names below (a direct address, a proxy). */
    private static volatile java.util.Set<String> extra = java.util.Set.of();
    public static void trust(java.util.Collection<String> entries) { extra = entries.stream().map(e -> e == null ? "" : e.trim().toLowerCase(java.util.Locale.ROOT)).filter(e -> e.matches("[a-z0-9.-]{1,253}(:[0-9]{1,5})?")).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
    /** The addresses the site lists (see ServerEntriesSync), checked like the player's own extra ones. */
    private static volatile java.util.Set<String> remote = java.util.Set.of();
    public static void trustRemote(java.util.Collection<String> entries) { remote = entries.stream().map(e -> e == null ? "" : e.trim().toLowerCase(java.util.Locale.ROOT)).filter(e -> e.matches("[a-z0-9.-]{1,253}(:[0-9]{1,5})?")).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
    private ServerAccess() {}
    private static String normalized(String address) { return address == null ? "" : address.trim().toLowerCase(java.util.Locale.ROOT); }
    public static boolean isSimmc(String address) {
        String clean=normalized(address);
        return clean.matches("simmc\\.silkysoftware\\.com\\.?(?::25565)?") || clean.matches("play\\.simmc\\.cn\\.?(?::25565)?") || extra.contains(clean) || remote.contains(clean) || TRANSFERS.getOrDefault(clean,0L)>System.currentTimeMillis();
    }
    public static boolean recordTransfer(String from,String host,int port) {
        if(!isSimmc(from)||host==null||!host.matches("[A-Za-z0-9.-]{1,253}")||port<1||port>65535)return false;
        long now=System.currentTimeMillis();TRANSFERS.values().removeIf(until->until<=now);
        if(TRANSFERS.size()>=8)return false;
        TRANSFERS.put(normalized(host)+":"+port,now+6*3600000L);return true;
    }
}
