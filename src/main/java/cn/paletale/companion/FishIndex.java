package cn.paletale.companion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The website's fish table as the mod sees it: archive id, code, the custom model data of the fished item and the name.
 * Fish live in the website's database, so nothing about their number, ids or names is written into the mod; the index decides.
 * A bad or oversized answer is rejected as a whole and the previous index stays.
 */
public final class FishIndex {
    public static final int MAX_BYTES = 64 * 1024, MAX_ROWS = 5000;
    public static final long ID_MIN = 1L, ID_MAX = 1_899_999_999L;
    public static final FishIndex EMPTY = new FishIndex(Map.of(), Map.of());
    private static volatile FishIndex current = EMPTY;
    private final Map<Integer, Integer> idByCustom;
    private final Map<Integer, String> nameById;

    private FishIndex(Map<Integer, Integer> idByCustom, Map<Integer, String> nameById) {
        this.idByCustom = idByCustom; this.nameById = nameById;
    }

    public static FishIndex decode(byte[] bytes) {
        if (bytes == null || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Fish index too large");
        var root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        if (root.get("version").getAsInt() != 1) throw new IllegalArgumentException("Unsupported fish index");
        var fields = root.getAsJsonArray("fields");
        if (fields.size() != 4 || !fields.get(0).getAsString().equals("id") || !fields.get(1).getAsString().equals("code")
                || !fields.get(2).getAsString().equals("customId") || !fields.get(3).getAsString().equals("name")) throw new IllegalArgumentException("Unexpected fish fields");
        JsonArray rows = root.getAsJsonArray("fish");
        if (rows.size() > MAX_ROWS) throw new IllegalArgumentException("Too many fish");
        Map<Integer, String> names = new HashMap<>();
        Map<Integer, Integer> byCustom = new HashMap<>();
        java.util.Set<Integer> ambiguous = new java.util.HashSet<>();
        for (JsonElement element : rows) {
            JsonArray row = element.getAsJsonArray();
            if (row.size() != 4) throw new IllegalArgumentException("Bad fish row");
            long rawId = row.get(0).getAsLong();
            if (rawId < ID_MIN || rawId > ID_MAX) throw new IllegalArgumentException("Fish id out of range");
            int id = (int) rawId;
            String code = row.get(1).getAsString();
            if (!code.matches("[a-z0-9-]{1,40}")) throw new IllegalArgumentException("Bad fish code");
            String name = row.get(3).getAsString();
            if (name.isBlank() || name.length() > 100 || name.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Bad fish name");
            if (names.putIfAbsent(id, name) != null) throw new IllegalArgumentException("Duplicate fish id");
            JsonElement custom = row.get(2);
            if (custom.isJsonNull()) continue;
            double value = custom.getAsDouble();
            if (!Double.isFinite(value) || value != Math.rint(value) || value < 0 || value > 16_777_216) throw new IllegalArgumentException("Bad custom model data");
            int key = (int) value;
            // Two fish sharing one model number cannot be told apart: neither is matched.
            if (byCustom.putIfAbsent(key, id) != null) ambiguous.add(key);
        }
        ambiguous.forEach(byCustom::remove);
        return new FishIndex(Map.copyOf(byCustom), Map.copyOf(names));
    }

    /** The archive id of the fish whose fished item carries this custom model data, or null (no fish, unknown or ambiguous). */
    public Integer idOfCustom(Integer customModelData) { return customModelData == null ? null : idByCustom.get(customModelData); }
    public String name(int id) { return nameById.get(id); }
    public int size() { return nameById.size(); }

    public static FishIndex current() { return current; }
    public static void use(FishIndex index) { current = index == null ? EMPTY : index; }
    static void resetForTest() { current = EMPTY; }
}
