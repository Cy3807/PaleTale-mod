package cn.paletale.companion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Merged shop rows (the catalog's optional merge=1): one row stands for every chest with the same item, owner, land, price and direction.
 * Paging, totals and merging stay with the server; this only reads the extra {@code positions} / {@code boxTotal} fields. A row without
 * positions (old site, mine=1) or with a single one behaves exactly like a plain per-chest row.
 */
public final class WorldMerge {
    private WorldMerge() {}
    /** Appended to shop-list requests only; item directories and "my shops" never merge. */
    public static final String PARAM = "&merge=1";
    public static final int MAX_POSITIONS = 500;
    public record Position(long id, int x, int y, int z, String observedAt) {
        public String coordinates() { return x + " " + y + " " + z; }
    }

    /** The chests of a merged row, newest observation first as sent; empty when the row carries no positions. */
    public static List<Position> positions(JsonObject row) {
        if (row == null || !row.has("positions") || !row.get("positions").isJsonArray()) return List.of();
        JsonArray array = row.getAsJsonArray("positions");
        if (array.size() > MAX_POSITIONS) throw new IllegalArgumentException("Too many positions");
        List<Position> found = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject entry = element.getAsJsonObject();
            long x = entry.get("x").getAsLong(), y = entry.get("y").getAsLong(), z = entry.get("z").getAsLong();
            if (Math.abs(x) > 1_000_000 || Math.abs(z) > 1_000_000 || Math.abs(y) > 2048) throw new IllegalArgumentException("Position out of range");
            long id = 0;
            try { if (entry.has("id") && !entry.get("id").isJsonNull()) id = entry.get("id").getAsLong(); } catch (RuntimeException ignored) { /* ids are informational; the site sends them as strings */ }
            String seen = entry.has("observedAt") && !entry.get("observedAt").isJsonNull() ? entry.get("observedAt").getAsString() : "";
            found.add(new Position(id, (int) x, (int) y, (int) z, seen));
        }
        return List.copyOf(found);
    }
    /** Like {@link #positions} but never throws: a malformed list simply means a plain row (used while drawing). */
    public static List<Position> safePositions(JsonObject row) { try { return positions(row); } catch (RuntimeException invalid) { return List.of(); } }
    /** True only for a real group: more than one chest. */
    public static boolean grouped(JsonObject row) { return safePositions(row).size() > 1; }
    /** Coordinates column: the latest chest, with "+N" for the other chests of a group. */
    public static String coordinates(JsonObject row) {
        String base = field(row, "x") + ", " + field(row, "y") + ", " + field(row, "z");
        int count = safePositions(row).size();
        return count > 1 ? base + " +" + (count - 1) : base;
    }
    /** Rows the server reports for the current query (the paging basis). */
    public static int total(JsonObject data) { return data.get("total").getAsInt(); }
    /** Chests behind those rows; the old site and unmerged lists have none, so it equals the row total. */
    public static int boxTotal(JsonObject data) { return data.has("boxTotal") && !data.get("boxTotal").isJsonNull() ? data.get("boxTotal").getAsInt() : total(data); }
    private static String field(JsonObject row, String key) { return row.has(key) && !row.get(key).isJsonNull() ? row.get(key).getAsString() : ""; }
}
