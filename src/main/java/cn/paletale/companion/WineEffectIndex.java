package cn.paletale.companion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;

/**
 * Wines and their effect (key and text, "迅捷 II"; "未知" when the site has none), from the site's compact index.
 * Only a potion can be a wine (every wine item on the server is a potion). It is recognised by its material: the custom model data (the wine's number),
 * or the item model plus that number; only a wine with no recorded material is matched by its name (letters, digits and CJK characters only).
 */
public final class WineEffectIndex {
    /** {@code brand}: the brand text of a 烙印 wine (colour codes kept), "" for every other wine. */
    public record Info(String key, String text, String brand) {}
    private final Map<String, Info> byMaterial, byModel, byName;
    private final Map<Integer, Info> byCustom;
    private WineEffectIndex(Map<String, Info> byMaterial, Map<String, Info> byModel, Map<Integer, Info> byCustom, Map<String, Info> byName) { this.byMaterial = byMaterial; this.byModel = byModel; this.byCustom = byCustom; this.byName = byName; }
    public static final WineEffectIndex EMPTY = new WineEffectIndex(Map.of(), Map.of(), Map.of(), Map.of());
    private static final String POTION = "minecraft:potion";

    /** Colour codes, spaces, dashes (- — –) and decoration symbols do not count when matching a name. */
    public static String normalize(String name) {
        return name == null ? "" : name.replaceAll("§.", "").replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase();
    }
    /** "model|id": a blank model means the default potion; null when the material says nothing specific (a plain potion with no custom model data). */
    static String materialKey(String model, Integer customModelData) {
        String id = model == null || model.isBlank() ? POTION : model.trim().toLowerCase();
        if (customModelData == null && id.equals(POTION)) return null;
        return id + "|" + (customModelData == null ? "" : customModelData);
    }
    public static WineEffectIndex parse(String json) {
        Map<String, Info> material = new HashMap<>(), models = new HashMap<>(), names = new HashMap<>();
        Map<Integer, Info> customs = new HashMap<>();
        java.util.Set<String> modelClash = new java.util.HashSet<>(); java.util.Set<Integer> customClash = new java.util.HashSet<>();
        JsonArray wines = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("wines");
        if (wines != null) for (JsonElement element : wines) {
            JsonArray row = element.getAsJsonArray();
            if (row.size() < 2) continue;
            String text = row.get(1).getAsString();
            String brand = row.size() > 5 && !row.get(5).isJsonNull() ? row.get(5).getAsString() : "";
            Info info = new Info(row.size() > 2 ? row.get(2).getAsString() : "unknown", text.isBlank() ? "未知" : text, brand);
            String model = row.size() > 3 && !row.get(3).isJsonNull() ? row.get(3).getAsString() : "";
            Integer custom = row.size() > 4 && !row.get(4).isJsonNull() ? row.get(4).getAsInt() : null;
            String key = materialKey(model, custom);
            if (key != null) {
                material.putIfAbsent(key, info);
                // The server's own model alone also identifies a wine when no other wine shares it.
                String modelId = model.isBlank() ? POTION : model.trim().toLowerCase();
                if (!modelId.startsWith("minecraft:") && models.putIfAbsent(modelId, info) != null && !models.get(modelId).equals(info)) modelClash.add(modelId);
                if (custom != null && customs.putIfAbsent(custom, info) != null && !customs.get(custom).equals(info)) customClash.add(custom);
                continue;
            }
            String name = normalize(row.get(0).getAsString());
            if (!name.isEmpty()) names.putIfAbsent(name, info);
        }
        modelClash.forEach(models::remove); customClash.forEach(customs::remove);
        return new WineEffectIndex(Map.copyOf(material), Map.copyOf(models), Map.copyOf(customs), Map.copyOf(names));
    }
    /**
     * The effect of the wine this stack is, or null. {@code potion}: the stack is a potion (anything else is never a wine). {@code model}: its item model id,
     * {@code customModelData}: its custom model data number, {@code itemName}: its custom name, used only for wines without a recorded material.
     */
    public Info effectOf(boolean potion, String model, Integer customModelData, String itemName) {
        if (!potion) return null;
        String key = materialKey(model, customModelData);
        Info info = key == null ? null : byMaterial.get(key);
        if (info == null && customModelData != null) info = byCustom.get(customModelData);
        // The model alone identifies a wine only for the server's own models (never a vanilla one).
        if (info == null && model != null && !model.isBlank() && !model.toLowerCase().startsWith("minecraft:")) info = byModel.get(model.trim().toLowerCase());
        return info != null ? info : byName.get(normalize(itemName));
    }
    public int size() { return byMaterial.size() + byName.size(); }
}
