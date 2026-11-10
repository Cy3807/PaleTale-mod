package cn.paletale.companion;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Official shop prices and daily quotas supplied by the player; unknown quotas stay unknown. */
public final class LedgerOfficial {
    private LedgerOfficial() {}
    public record Product(String item, int stars, double price, int limit, boolean mineral) {
        public String key() { return item + (stars == 0 ? "" : " · " + stars + "星"); }
    }
    /** used is the server's sold count for items; for fishing it is the remaining value from the latest receipt. */
    public record Reading(String account, long time, String name, String product, double used, double limit) {}
    public static final List<Product> PRODUCTS;
    static {
        List<Product> list = new ArrayList<>();
        for (String item : List.of("大豆", "玉米", "茄子")) {
            list.add(new Product(item, 1, 8, 108, false));
            list.add(new Product(item, 2, 62, 54, false));
            list.add(new Product(item, 3, 156, 27, false));
        }
        for (String item : List.of("蘑菇", "水稻")) {
            list.add(new Product(item, 1, 12, 72, false));
            list.add(new Product(item, 2, 93, 36, false));
            list.add(new Product(item, 3, 233, 18, false));
        }
        list.add(new Product("水晶番茄", 1, 23, 36, false));
        list.add(new Product("水晶番茄", 2, 187, 18, false));
        list.add(new Product("水晶番茄", 3, 467, 9, false));
        list.add(new Product("金锭", 0, 100, 999, true));
        list.add(new Product("铁锭", 0, 25, 999, true));
        PRODUCTS = List.copyOf(list);
    }
    private static final String NUMBER = "([0-9][0-9,]*(?:\\.[0-9]+)?)";
    private static final Pattern SALE = Pattern.compile("^商店\\s*成功出售\\s+(.+?)\\s*[,，]\\s*获得\\s*" + NUMBER + "(?:[金云]币|金|\\$|¥|￥)?[!！]?$");
    private static final Pattern BUY = Pattern.compile("^商店\\s*购买成功\\s*[,，]\\s*花费\\s*" + NUMBER + "(?:金币|云币|金|\\$|¥|￥)?[!！]?$");
    private static final Pattern ARCANE = Pattern.compile("^已花费\\s*" + NUMBER + "\\s*金币\\s*[,，]\\s*将\\s*(.{1,60}?)\\s*升级到\\s*(Lv\\s*\\d+)\\s*[!！。.]?$",Pattern.CASE_INSENSITIVE);
    private static final Pattern FISH = Pattern.compile("^您从鱼中赚了\\s*" + NUMBER + "金[!！]\\s*今天您还可以从市场中\\s*获得\\s*" + NUMBER + "金[!！]?$");
    private static final Pattern PRICE = Pattern.compile("出售\\s*[:：]\\s*" + NUMBER);
    private static final Pattern LIMIT = Pattern.compile("玩家出售限制\\s*[:：]\\s*(\\d+)\\s*/\\s*(\\d+)");
    private static double number(String text) { return Double.parseDouble(text.replace(",", "")); }
    public static Product product(String key) { return PRODUCTS.stream().filter(p -> p.key().equals(key)).findFirst().orElse(null); }
    public static LocalDate day(long time) { return Instant.ofEpochMilli(time).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate(); }
    /** Official shop quotas reset at 00:00 Beijing time, independently of calendar-day ledger summaries. */
    public static LocalDate quotaDay(long time) { return day(time); }
    /** The raw item name retains resource-pack star glyphs, unlike the ordinary chat cleaner. */
    private static String rawName(String text) { return text.replaceAll("§.", "").replaceAll("\\p{Cntrl}", "").replace(' ', ' ').strip(); }
    public static Optional<Reading> tooltip(String account, String title, List<String> lore, long now) {
        String text = LedgerParser.clean(String.join("\n", lore));
        var price = PRICE.matcher(text); var limit = LIMIT.matcher(text);
        if (!price.find() || !limit.find()) return Optional.empty();
        if (limit.group(1).length() > 9 || limit.group(2).length() > 9) return Optional.empty();
        String name = LedgerParser.clean(title); double value = number(price.group(1));
        for (Product p : PRODUCTS) if (name.startsWith(p.item()) && Math.abs(p.price() - value) < .001) {
            int used = Integer.parseInt(limit.group(1)), max = Integer.parseInt(limit.group(2));
            if (max <= 0 || used > max) return Optional.empty();
            return Optional.of(new Reading(account, now, rawName(title), p.key(), used, max));
        }
        return Optional.empty();
    }
    public static Optional<Reading> fishing(String account, String message, long now) {
        var m = FISH.matcher(LedgerParser.clean(message));
        if (!m.matches()) return Optional.empty();
        // The line gives this transaction plus the day's remaining value, not the day's cumulative earnings.
        return Optional.of(new Reading(account, now, "", "钓鱼", number(m.group(2)), 10_000));
    }
    public static Optional<LedgerEntry> parse(String message, long now, Map<String, String> names) {
        String text = LedgerParser.clean(message);
        if (text.length() > 400) return Optional.empty();
        var m = BUY.matcher(text);
        if (m.matches()) return positive(now, LedgerEntry.Kind.OFFICIAL_PURCHASE, number(m.group(1)), "", 0);
        m = ARCANE.matcher(text);
        if (m.matches()) return positive(now, LedgerEntry.Kind.OFFICIAL_PURCHASE, number(m.group(1)), "奥术 · " + m.group(2).strip() + " " + m.group(3).replaceAll("\\s+", ""), 0);
        m = FISH.matcher(text);
        if (m.matches()) return positive(now, LedgerEntry.Kind.OFFICIAL_FISH, number(m.group(1)), "钓鱼", 0);
        m = SALE.matcher(text);
        if (!m.matches()) return Optional.empty();
        double total = number(m.group(2));
        if (!Double.isFinite(total) || total <= 0) return Optional.empty();
        String name = m.group(1).strip();
        // First match exact glyphs learned from the official shop's item title.
        String raw = rawName(message);
        var rawItem = Pattern.compile("^商店\\s*成功出售\\s+(.+?)\\s*[,，]").matcher(raw);
        String exactName = rawItem.find() ? rawName(rawItem.group(1)) : "";
        String known = names.entrySet().stream().filter(e -> exactName.equals(e.getKey())).map(Map.Entry::getValue).findFirst().orElse("");
        Product selected = product(known);
        long stars = name.chars().filter(ch -> ch == '★').count();
        if (selected == null && stars > 0 && stars <= 3) selected = product(name.split("\\s+")[0] + " · " + stars + "星");
        if (selected == null) {
            List<Product> candidates = new ArrayList<>();
            for (Product p : PRODUCTS) if (name.startsWith(p.item()) && quantity(total, p.price()) > 0 && (p.limit() == 0 || quantity(total, p.price()) <= p.limit())) candidates.add(p);
            if (candidates.size() == 1) selected = candidates.get(0);
        }
        if (selected != null && name.startsWith(selected.item())) {
            int count = quantity(total, selected.price());
            return Optional.of(entry(now, selected.mineral() ? LedgerEntry.Kind.OFFICIAL_MINERAL : LedgerEntry.Kind.OFFICIAL_CROP, total, selected.key(), count));
        }
        boolean crop = PRODUCTS.stream().anyMatch(p -> !p.mineral() && name.startsWith(p.item()));
        return Optional.of(entry(now, crop ? LedgerEntry.Kind.OFFICIAL_CROP : LedgerEntry.Kind.OFFICIAL_OTHER, total, name + "（数量未确认）", 0));
    }
    private static Optional<LedgerEntry> positive(long now, LedgerEntry.Kind kind, double amount, String item, int quantity) {
        return Double.isFinite(amount) && (kind == LedgerEntry.Kind.OFFICIAL_PURCHASE ? amount >= 0 : amount > 0) ? Optional.of(entry(now, kind, amount, item, quantity)) : Optional.empty();
    }
    private static int quantity(double total, double price) {
        double count = total / price;
        return count > 0 && count <= Integer.MAX_VALUE && Math.abs(count - Math.rint(count)) < .000001 ? (int) Math.rint(count) : 0;
    }
    private static LedgerEntry entry(long now, LedgerEntry.Kind kind, double amount, String item, int quantity) {
        return new LedgerEntry(UUID.randomUUID().toString(), now, kind, "云币", amount, "官方", item, quantity, null, null, null, "", "", "");
    }
    public record Quota(String key, double used, double limit) {
        public boolean full() { return limit > 0 && used >= limit; }
    }
    /** Both the local ledger and a server snapshot can prove the cap reached; neither includes another account or day. */
    public static List<Quota> quotas(List<LedgerEntry> entries, List<Reading> readings, String account, LocalDate date) {
        Map<String, Double> counts = new java.util.HashMap<>();
        for (var e : entries) if (e.account().equals(account) && quotaDay(e.time()).equals(date)) {
            if (e.kind() == LedgerEntry.Kind.OFFICIAL_CROP || e.kind() == LedgerEntry.Kind.OFFICIAL_MINERAL) counts.merge(e.item(), (double) e.quantity(), Double::sum);
            if (e.kind() == LedgerEntry.Kind.OFFICIAL_FISH) counts.merge("钓鱼", e.amount(), Double::sum);
        }
        List<Quota> result = new ArrayList<>();
        for (Product p : PRODUCTS) {
            var reading = readings.stream().filter(r -> r.account().equals(account) && r.product().equals(p.key())).max(java.util.Comparator.comparingLong(Reading::time)).orElse(null);
            double used = counts.getOrDefault(p.key(), 0.0), cap = reading == null ? p.limit() : reading.limit();
            if (reading != null && quotaDay(reading.time()).equals(date)) used = Math.max(used, reading.used());
            result.add(new Quota(p.key(), used, cap));
        }
        var fish = readings.stream().filter(r -> r.account().equals(account) && r.product().equals("钓鱼") && quotaDay(r.time()).equals(date)).max(java.util.Comparator.comparingLong(Reading::time)).orElse(null);
        result.add(new Quota("钓鱼", Math.max(counts.getOrDefault("钓鱼", 0.0), fish == null ? 0 : Math.max(0, fish.limit() - fish.used())), fish == null ? 10000 : fish.limit()));
        return List.copyOf(result);
    }
    public static long recorded(List<LedgerEntry> entries, String account, String key, LocalDate date) {
        return entries.stream().filter(e -> e.account().equals(account) && e.kind().group().equals("official") && e.kind().sign > 0 && e.item().equals(key) && quotaDay(e.time()).equals(date)).mapToLong(LedgerEntry::quantity).sum();
    }
}
