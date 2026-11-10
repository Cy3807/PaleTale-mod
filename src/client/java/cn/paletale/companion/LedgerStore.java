package cn.paletale.companion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The ledger lives in the game's config folder on this computer ({@code paletale/config/ledger.json}, daily backups in {@code paletale/config/backups/}); nothing is ever sent to the site.
 * Entries are kept oldest-first and carry the account they were recorded on; by default the screen shows only the current account (a setting turns that off).
 * Writing happens on a background thread.
 */
final class LedgerStore {
    static final int LIMIT = 20_000;
    private static final int BACKUPS_KEPT = 7;
    static final int[] RETENTION_CHOICES = { 90, 180, 365, 0 }; // days; 0 = keep for ever
    private static final Gson GSON = new GsonBuilder().create();
    private static final List<LedgerEntry> ENTRIES = new ArrayList<>();
    private static final List<LedgerOfficial.Reading> OFFICIAL = new ArrayList<>();
    private static final List<String> UNMATCHED = new ArrayList<>();
    private static boolean loaded, dirty, separateAccounts = true;
    private static int retentionDays = 90;
    private static long saveAt, nextPrune, revision;
    private static boolean receiptRecovery;
    static synchronized long revision() { load(); return revision; }
    private LedgerStore() {}

    static Path folder() { return ModPaths.config(); }
    private static Path file() { return folder().resolve("ledger.json"); }
    private static Path backups() { return folder().resolve("backups"); }


    // ── identity of the current game account ──
    /** The server runs on Beijing time, so every date in the ledger does too. */
    static final java.time.ZoneId BEIJING = java.time.ZoneId.of("Asia/Shanghai");
    static String account() { var user = Minecraft.getInstance().getUser(); return user == null || user.getProfileId() == null ? "" : user.getProfileId().toString().replace("-", ""); }
    static String accountName() { var user = Minecraft.getInstance().getUser(); return user == null ? "" : user.getName(); }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try {
            Path source = Files.isRegularFile(ModPaths.readable("ledger.json")) ? ModPaths.readable("ledger.json") : null;
            if (source == null) return;
            JsonObject root = JsonParser.parseString(Files.readString(source, StandardCharsets.UTF_8)).getAsJsonObject();
            receiptRecovery = root.has("officialReceiptRecovery") && root.get("officialReceiptRecovery").getAsInt() >= 1;
            if (root.has("separateAccounts")) separateAccounts = root.get("separateAccounts").getAsBoolean();
            if (root.has("retentionDays")) retentionDays = Math.max(0, root.get("retentionDays").getAsInt());
            String me = account(), myName = accountName();
            if (root.has("entries")) for (var element : root.getAsJsonArray("entries")) {
                try {
                    LedgerEntry entry = GSON.fromJson(element, LedgerEntry.class).normalized();
                    // Entries from before accounts were recorded belong to whoever is playing now (it was their own local file).
                    ENTRIES.add(entry.account().isEmpty() ? entry.withAccount(me, myName) : entry);
                } catch (RuntimeException broken) { /* skip one damaged line */ }
            }
            if (root.has("official")) for (var element : root.getAsJsonArray("official")) {
                try { var reading = GSON.fromJson(element, LedgerOfficial.Reading.class); if (reading.account() != null && reading.product() != null && reading.name() != null) OFFICIAL.add(reading); } catch (RuntimeException broken) { /* skip damaged snapshot */ }
            }
            if (root.has("unmatched")) for (var element : root.getAsJsonArray("unmatched")) UNMATCHED.add(element.getAsString());
            if (!source.equals(file())) { dirty = true; revision++; saveAt = 0; } // move the old file into the new folder on the next save
            prune();
            if (!receiptRecovery) {
                var unknown = List.copyOf(UNMATCHED); var already = List.copyOf(ENTRIES);
                WRITER.execute(() -> {
                    try {
                        var repaired = LedgerReceiptRecovery.read(Minecraft.getInstance().gameDirectory.toPath().resolve("logs"), me, myName, unknown, already, java.time.ZoneId.systemDefault(), LocalDate.now());
                        if(!repaired.isEmpty()){Files.createDirectories(backups());Path backup=backups().resolve("ledger-before-official-recovery-v1.json");if(!Files.exists(backup))Files.copy(source,backup);}
                        synchronized(LedgerStore.class) { if(receiptRecovery)return; ENTRIES.addAll(repaired);ENTRIES.sort(java.util.Comparator.comparingLong(LedgerEntry::time));receiptRecovery=true;dirty=true;revision++;saveAt=0; }
                    } catch(IOException ignored) { /* no guessed amounts or dates when the source is unavailable */ }
                });
            }
        } catch (IOException | RuntimeException broken) { /* a damaged file starts an empty ledger; the daily backups are still there */ }
    }

    // ── reading ──
    static synchronized List<LedgerEntry> all() { load(); return List.copyOf(ENTRIES); }
    /** What the screen shows: only the current account's entries unless the account setting is off. */
    static synchronized List<LedgerEntry> visible() {
        load();
        String me = account();
        List<LedgerEntry> list = new ArrayList<>();
        for (LedgerEntry entry : ENTRIES) if (!separateAccounts || entry.account().equals(me)) list.add(entry.safeShopCounterparty());
        return list;
    }
    static synchronized int accountCount() { load(); Set<String> ids = new HashSet<>(); for (LedgerEntry entry : ENTRIES) ids.add(entry.account()); return ids.size(); }
    /** Recording is always on (the switch was removed). */
    static synchronized boolean enabled() { return true; }
    static synchronized List<String> unmatched() { load(); return List.copyOf(UNMATCHED); }

    static synchronized List<LedgerOfficial.Reading> official() { load(); return List.copyOf(OFFICIAL); }
    static synchronized void official(LedgerOfficial.Reading reading) {
        load();
        var old = OFFICIAL.stream().filter(r -> r.account().equals(reading.account()) && r.product().equals(reading.product())).findFirst().orElse(null);
        if (old != null && LedgerOfficial.quotaDay(old.time()).equals(LedgerOfficial.quotaDay(reading.time())) && old.used() == reading.used() && old.limit() == reading.limit() && old.name().equals(reading.name())) return;
        OFFICIAL.removeIf(r -> r.account().equals(reading.account()) && r.product().equals(reading.product()));
        OFFICIAL.add(reading);
        dirty = true; revision++; saveAt = System.currentTimeMillis() + 2000;
    }

    // ── settings ──
    static synchronized boolean separateAccounts() { load(); return separateAccounts; }
    static synchronized void setSeparateAccounts(boolean value) { load(); separateAccounts = value; dirty = true; revision++; saveAt = 0; }
    static synchronized int retentionDays() { load(); return retentionDays; }
    static synchronized void setRetentionDays(int days) { load(); retentionDays = days; prune(); dirty = true; revision++; saveAt = 0; }
    static String retentionLabel(int days) { return days == 0 ? "永久保留" : days == 90 ? "3 个月" : days == 180 ? "6 个月" : days == 365 ? "1 年" : days + " 天"; }

    /** Drops entries older than the retention period (the daily backups still hold them for a week). */
    private static void prune() {
        nextPrune = System.currentTimeMillis() + 3_600_000L;
        if (retentionDays <= 0) return;
        long cutoff = System.currentTimeMillis() - retentionDays * 86_400_000L;
        if (ENTRIES.removeIf(entry -> entry.time() < cutoff)) { dirty = true; revision++; saveAt = System.currentTimeMillis() + 2000; }
    }

    // ── writing entries ──
    /** Adds one entry (stamped with the current account; linked to an ad of the player's when that is unambiguous). */
    static synchronized void add(LedgerEntry entry) {
        if (!entry.recordable()) return;
        load();
        ENTRIES.add(autoLink(entry.account().isEmpty() ? entry.withAccount(account(), accountName()) : entry));
        while (ENTRIES.size() > LIMIT) ENTRIES.remove(0);
        dirty = true; revision++; saveAt = System.currentTimeMillis() + 2000;
    }
    /** Atomic bounded merge: do not add a historical event already recorded live or by an earlier import. */
    static synchronized int importBatch(String expectedAccount,List<LedgerEntry> incoming) {
        load();if(!account().equals(expectedAccount))throw new IllegalStateException("账号已切换");
        var additions=LedgerImportMerge.additions(ENTRIES,incoming,expectedAccount,LIMIT);int added=additions.size();ENTRIES.addAll(additions);
        if(added>0){ENTRIES.sort(java.util.Comparator.comparingLong(LedgerEntry::time));dirty=true;revision++;saveAt=0;}return added;
    }

    static synchronized void addUnmatched(String text) {
        load();
        if (UNMATCHED.contains(text)) return;
        UNMATCHED.add(text.length() > 200 ? text.substring(0, 200) : text);
        while (UNMATCHED.size() > 100) UNMATCHED.remove(0);
        dirty = true; revision++; saveAt = System.currentTimeMillis() + 2000;
    }
    static LedgerEntry autoLink(LedgerEntry entry) {
        if (entry.kind() == LedgerEntry.Kind.SOLD_OUT && entry.x() != null) {
            var shop = ShopIndex.at(entry.x(), entry.y(), entry.z());
            return shop == null ? entry : entry.withAd(shop.adCode(), shop.adTitle(), shop.productName());
        }
        if (entry.kind() == LedgerEntry.Kind.SHOP_SALE) {
            var candidates = ShopIndex.forItem(entry.item());
            if (candidates.size() == 1) return entry.withAd(candidates.get(0).adCode(), candidates.get(0).adTitle(), candidates.get(0).productName());
        }
        return entry;
    }
    /** The ads this entry could belong to (the player's bound products trading the item), for the manual choice on the screen. */
    static List<ShopApi.MyShop> candidates(LedgerEntry entry) {
        return entry.kind() == LedgerEntry.Kind.SHOP_SALE ? ShopIndex.forItem(entry.item()) : List.of(); // only sales from the player's own shops belong to the player's ads
    }
    /** Cycles an unclear sale through its candidate ads (and back to none). */
    static synchronized void cycleAd(String id) {
        load();
        for (int i = 0; i < ENTRIES.size(); i++) {
            LedgerEntry entry = ENTRIES.get(i);
            if (!entry.id().equals(id)) continue;
            var options = candidates(entry);
            if (options.isEmpty()) return;
            int current = -1;
            for (int k = 0; k < options.size(); k++) if (options.get(k).adCode().equals(entry.adCode()) && options.get(k).productName().equals(entry.productName())) current = k;
            ENTRIES.set(i, current + 1 >= options.size() ? entry.withAd("", "", "") : entry.withAd(options.get(current + 1).adCode(), options.get(current + 1).adTitle(), options.get(current + 1).productName()));
            dirty = true; revision++; saveAt = System.currentTimeMillis() + 500;
            return;
        }
    }
    /** Re-links every entry that has no ad yet (after the bound shops were loaded). */
    static synchronized void relink() {
        load();
        boolean changed = false;
        for (int i = 0; i < ENTRIES.size(); i++) {
            LedgerEntry entry = ENTRIES.get(i);
            if (!entry.adCode().isEmpty()) continue;
            LedgerEntry linked = autoLink(entry);
            if (!linked.adCode().isEmpty()) { ENTRIES.set(i, linked); changed = true; }
        }
        if (changed) { dirty = true; revision++; saveAt = System.currentTimeMillis() + 2000; }
    }
    /** Deletes the chosen entries (the screen's selection mode). */
    static synchronized void remove(Set<String> ids) {
        load();
        if (ENTRIES.removeIf(entry -> ids.contains(entry.id()))) { dirty = true; revision++; saveAt = 0; }
    }
    /** Links an entry to an ad (used when the seller's ad was looked up on the site). */
    static synchronized void setAd(String id, String code, String title, String product) {
        load();
        for (int i = ENTRIES.size() - 1; i >= 0 && i >= ENTRIES.size() - 20; i--) if (ENTRIES.get(i).id().equals(id)) { ENTRIES.set(i, ENTRIES.get(i).withAd(code, title, product)); dirty = true; revision++; saveAt = System.currentTimeMillis() + 500; return; }
    }
    /** Fills in who sent a transfer when a nearby line named them. */
    static synchronized void setCounterparty(String id, String name) {
        load();
        for (int i = ENTRIES.size() - 1; i >= 0 && i >= ENTRIES.size() - 10; i--) if (ENTRIES.get(i).id().equals(id)) { ENTRIES.set(i, ENTRIES.get(i).withCounterparty(name)); dirty = true; revision++; saveAt = System.currentTimeMillis() + 500; return; }
    }
    /** Clears what this view shows: the current account's entries, or everything when accounts are not kept apart. */
    static synchronized void clear() {
        load();
        if (separateAccounts) { String me = account(); ENTRIES.removeIf(entry -> entry.account().equals(me)); OFFICIAL.removeIf(reading -> reading.account().equals(me)); } else { ENTRIES.clear(); OFFICIAL.clear(); }
        receiptRecovery = true; UNMATCHED.clear(); dirty = true; revision++; saveAt = 0;
    }

    // ── saving (background thread) ──
    /** Called every tick: shortly after a change the ledger is handed to a background thread to write; the game never waits for the disk. */
    static synchronized void tick() {
        long now = System.currentTimeMillis();
        if (loaded && now >= nextPrune) prune();
        if (dirty && now >= saveAt) requestSave();
    }

    private record Snapshot(boolean separate, int retention, List<LedgerEntry> entries, List<String> unmatched, List<LedgerOfficial.Reading> official, boolean recovered) {}
    private static final java.util.concurrent.atomic.AtomicReference<Snapshot> PENDING = new java.util.concurrent.atomic.AtomicReference<>();
    private static final java.util.concurrent.ExecutorService WRITER = java.util.concurrent.Executors.newSingleThreadExecutor(task -> { Thread thread = new Thread(task, "paletale-ledger-writer"); thread.setDaemon(true); return thread; });

    /** Takes a cheap copy of the entries (they are immutable) and lets the writer thread serialise and write the newest copy. */
    private static synchronized void requestSave() {
        dirty = false;
        PENDING.set(new Snapshot(separateAccounts, retentionDays, new ArrayList<>(ENTRIES), new ArrayList<>(UNMATCHED), new ArrayList<>(OFFICIAL), receiptRecovery));
        WRITER.execute(LedgerStore::writePending);
    }
    private static void writePending() {
        Snapshot snapshot = PENDING.getAndSet(null);
        if (snapshot == null) return; // a newer write already covered it
        write(snapshot);
    }
    private static boolean write(Snapshot snapshot) {
        try {
            Files.createDirectories(folder());
            JsonObject root = new JsonObject();
            root.addProperty("version", 2); root.addProperty("officialReceiptRecovery", snapshot.recovered() ? 1 : 0); root.addProperty("separateAccounts", snapshot.separate()); root.addProperty("retentionDays", snapshot.retention());
            JsonArray list = new JsonArray(); for (LedgerEntry entry : snapshot.entries()) list.add(GSON.toJsonTree(entry));
            root.add("entries", list);
            root.add("official", GSON.toJsonTree(snapshot.official()));
            JsonArray unmatched = new JsonArray(); snapshot.unmatched().forEach(unmatched::add); root.add("unmatched", unmatched);
            Path temp = file().resolveSibling("ledger.json.tmp");
            Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(temp, file(), StandardCopyOption.REPLACE_EXISTING);
            // Legacy files are migrated (including conflicting copies) by ModPaths, never deleted here.
            dailyBackup();return true;
        } catch (IOException failed) { return false; }
    }
    /** Import runs this off-thread; failures remain dirty for retry instead of reporting a saved ledger. */
    static boolean flushImport() {
        java.util.concurrent.Future<Boolean> task;
        synchronized(LedgerStore.class){if(!loaded)return true;dirty=false;Snapshot snapshot=new Snapshot(separateAccounts,retentionDays,new ArrayList<>(ENTRIES),new ArrayList<>(UNMATCHED),new ArrayList<>(OFFICIAL),receiptRecovery);PENDING.set(null);task=WRITER.submit(()->write(snapshot));}
        boolean saved=false;try{saved=task.get(5,java.util.concurrent.TimeUnit.SECONDS);}catch(Exception ignored){}
        if(!saved)synchronized(LedgerStore.class){dirty=true;saveAt=System.currentTimeMillis()+5000;}
        return saved;
    }
    /** One copy per day (the first write of the day), the last seven days kept. */
    private static void dailyBackup() throws IOException {
        Path dir = backups();
        Files.createDirectories(dir);
        Path today = dir.resolve("ledger-" + LocalDate.now(BEIJING) + ".json");
        if (!Files.exists(today)) Files.copy(file(), today);
        try (var stream = Files.list(dir)) {
            List<Path> old = stream.filter(path -> path.getFileName().toString().matches("ledger-\\d{4}-\\d{2}-\\d{2}\\.json")).sorted().toList();
            for (int i = 0; i < old.size() - BACKUPS_KEPT; i++) Files.deleteIfExists(old.get(i));
        }
    }
    /** On game exit: anything not written yet is written now (the one place that waits). */
    static void flushNow() {
        Snapshot last;
        synchronized (LedgerStore.class) {
            if (!loaded) return;
            dirty = false;
            last = new Snapshot(separateAccounts, retentionDays, new ArrayList<>(ENTRIES), new ArrayList<>(UNMATCHED), new ArrayList<>(OFFICIAL), receiptRecovery);
        }
        PENDING.set(null);
        try { WRITER.submit(() -> write(last)).get(5, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignored) { write(last); }
    }
}
