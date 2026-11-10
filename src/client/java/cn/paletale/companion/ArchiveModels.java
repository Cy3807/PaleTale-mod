package cn.paletale.companion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Display-only stacks; never placed in an inventory or sent to a server. Uses the currently loaded packs. */
final class ArchiveModels {
    static final ArchiveModels INSTANCE = new ArchiveModels();
    private final Map<ArchiveData.NativeAppearance, ItemStack> stacks = new LinkedHashMap<>();
    private final Map<ArchiveData.NativeAppearance, ItemStack> flatStacks = new LinkedHashMap<>();
    private final Map<Identifier, JsonObject> definitions = new LinkedHashMap<>();
    private ResourceManager resources;
    private Object bakedGeneration;
    void clear() { stacks.clear(); flatStacks.clear(); definitions.clear(); resources = null; bakedGeneration = null; }
    ItemStack resolve(ArchiveData.Entry entry) {
        return entry.kind().equals("items") ? resolve(entry.nativeAppearance()) : ItemStack.EMPTY;
    }
    /** A plain vanilla stack (registry id without namespace); empty when this client's registry has no such item. */
    ItemStack vanilla(String id) {
        if (id == null || !id.matches("[a-z0-9_]{1,64}")) return ItemStack.EMPTY;
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(id));
        return item.isPresent() && item.get() != Items.AIR ? new ItemStack(item.get()) : ItemStack.EMPTY;
    }
    boolean drawVanilla(GuiGraphics g, String id, int x, int y, int size) { return draw(g, vanilla(id), x, y, size); }
    ItemStack resolve(ArchiveData.NativeAppearance hint) {
        Minecraft client = Minecraft.getInstance();
        Object generation = client.getModelManager().getItemModel(Identifier.withDefaultNamespace("potion"));
        if (resources != client.getResourceManager() || generation != bakedGeneration) {
            clear(); resources = client.getResourceManager(); bakedGeneration = generation;
        }
        if (hint == null) return ItemStack.EMPTY;
        var cached = stacks.get(hint);
        if (cached != null) return cached;
        ItemStack stack;
        try { stack = create(hint); } catch (RuntimeException brokenPack) { stack = ItemStack.EMPTY; }
        if (stacks.size() >= 1024) stacks.remove(stacks.keySet().iterator().next());
        stacks.put(hint, stack); return stack;
    }
    ItemStack resolveFlat(ArchiveData.NativeAppearance hint) {
        resolve((ArchiveData.NativeAppearance) null); // refreshes the caches when the resource packs changed
        if (hint == null) return ItemStack.EMPTY;
        var cached = flatStacks.get(hint);
        if (cached != null) return cached;
        ItemStack stack;
        try {
            Identifier model = hint.itemModel().isBlank() ? Identifier.withDefaultNamespace("potion") : Identifier.tryParse(hint.itemModel());
            stack = model == null ? ItemStack.EMPTY : build(model, hint);
        } catch (RuntimeException brokenPack) { stack = ItemStack.EMPTY; }
        if (flatStacks.size() >= 1024) flatStacks.remove(flatStacks.keySet().iterator().next());
        flatStacks.put(hint, stack); return stack;
    }
    private ItemStack create(ArchiveData.NativeAppearance hint) {
        Identifier model = hint.itemModel().isBlank() ? Identifier.withDefaultNamespace("potion") : Identifier.tryParse(hint.itemModel());
        if (model == null) return ItemStack.EMPTY;
        // Dishes: the site records the flat food/<group>/<name> definition; the solid 3D dish is dishes/dingzhi2/<name> (stage 0 of 3).
        Identifier solid = solidDish(model);
        if (solid != null) { ItemStack stack = build(solid, hint); if (!stack.isEmpty()) return stack; }
        return build(model, hint);
    }
    static Identifier solidDish(Identifier model) {
        String[] parts = model.getPath().split("/");
        return parts.length == 3 && parts[0].equals("food") ? model.withPath("dishes/dingzhi2/" + parts[2]) : null;
    }
    private ItemStack build(Identifier model, ArchiveData.NativeAppearance hint) {
        JsonObject definition = definition(model);
        if (definition == null || (hint.itemModel().isBlank() && !hasCustomDispatch(definition.getAsJsonObject("model"), 0)) || !matches(definition.getAsJsonObject("model"), hint.customModelData(), 0)) return ItemStack.EMPTY;
        if (Minecraft.getInstance().getModelManager().getItemModel(model) instanceof MissingItemModel) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(Items.POTION);
        stack.set(DataComponents.ITEM_MODEL, model);
        if (hint.customModelData() != null)
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(hint.customModelData().floatValue()), List.of(), List.of(), List.of()));
        return stack;
    }
    private JsonObject definition(Identifier model) {
        if (definitions.containsKey(model)) return definitions.get(model);
        JsonObject data = null;
        try {
            var resource = resources.getResource(model.withPath("items/" + model.getPath() + ".json"));
            if (resource.isPresent()) try (var input = resource.get().open()) {
                byte[] bytes = input.readNBytes(2 * 1024 * 1024 + 1);
                if (bytes.length <= 2 * 1024 * 1024) data = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        } catch (Exception ignored) { /* Broken or absent pack definition falls back to the public cover. */ }
        if (definitions.size() >= 256) definitions.remove(definitions.keySet().iterator().next());
        definitions.put(model, data); return data;
    }
    private static boolean hasCustomDispatch(JsonElement value, int depth) {
        if (value == null || depth > 24) return false;
        if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            if (object.has("property") && object.get("property").isJsonPrimitive()
                    && object.get("property").getAsString().replace("minecraft:", "").equals("custom_model_data")) return true;
            for (var child : object.entrySet()) if (hasCustomDispatch(child.getValue(), depth + 1)) return true;
        } else if (value.isJsonArray()) for (var child : value.getAsJsonArray()) if (hasCustomDispatch(child, depth + 1)) return true;
        return false;
    }
    // range_dispatch is a floor lookup: require an exact ID to avoid displaying the previous wine for an unknown ID.
    static boolean matches(JsonObject model, Integer customId, int depth) {
        if (model == null || depth > 24) return false;
        try {
            String type = model.get("type").getAsString().replace("minecraft:", "");
            if (type.equals("range_dispatch") && model.get("property").getAsString().replace("minecraft:", "").equals("custom_model_data")) {
                if (customId == null || (model.has("index") && model.get("index").getAsInt() != 0)) return false;
                double value = customId * (model.has("scale") ? model.get("scale").getAsDouble() : 1);
                for (JsonElement element : model.getAsJsonArray("entries")) {
                    var row = element.getAsJsonObject();
                    if (row.get("threshold").getAsDouble() == value) return matches(row.getAsJsonObject("model"), customId, depth + 1);
                }
                return false;
            }
            if (type.equals("model")) return model.has("model");
            if (type.equals("select")) {
                for (JsonElement element : model.getAsJsonArray("cases")) if (!matches(element.getAsJsonObject().getAsJsonObject("model"), customId, depth + 1)) return false;
                return !model.has("fallback") || matches(model.getAsJsonObject("fallback"), customId, depth + 1);
            }
            if (type.equals("condition")) return matches(model.getAsJsonObject("on_true"), customId, depth + 1) && matches(model.getAsJsonObject("on_false"), customId, depth + 1);
            if (type.equals("composite")) {
                for (var child : model.getAsJsonArray("models")) if (!matches(child.getAsJsonObject(), customId, depth + 1)) return false;
                return true;
            }
        } catch (RuntimeException ignored) { }
        return false;
    }
    /** Flat, non-rotating picture of the loaded pack item (GUI transform). */
    /** The flat 2D picture (the pack's own inventory icon): lists, small icons and the detail page's still image. Dishes use their flat food/ definition here, never the animated solid model. */
    boolean draw(GuiGraphics g, ArchiveData.Entry entry, int x, int y, int size) { return entry.kind().equals("items") && draw(g, entry.nativeAppearance(), x, y, size); }
    boolean draw(GuiGraphics g, ArchiveData.NativeAppearance hint, int x, int y, int size) { return draw(g, resolveFlat(hint), x, y, size); }
    boolean draw(GuiGraphics g, ItemStack stack, int x, int y, int size) {
        if (stack.isEmpty()) return false;
        g.enableScissor(x, y, x + size, y + size);
        g.pose().pushMatrix();
        g.pose().translate(x + size * .08f, y + size * .08f); g.pose().scale(size * .84f / 16);
        g.renderFakeItem(stack, 0, 0);
        g.pose().popMatrix(); g.disableScissor(); return true;
    }
    boolean drawModel(GuiGraphics g, ArchiveData.Entry entry, int x, int y, int size, float angle) { return drawModel(g, resolve(entry), x, y, size, angle); }
    boolean drawModel(GuiGraphics g, ArchiveData.NativeAppearance hint, int x, int y, int size, float angle) { return drawModel(g, resolve(hint), x, y, size, angle); }
    private boolean drawModel(GuiGraphics g, ItemStack stack, int x, int y, int size, float angle) {
        if (stack.isEmpty()) return false;
        Minecraft client = Minecraft.getInstance();
        var state = new ItemEntityRenderState();
        state.entityType = EntityType.ITEM; state.count = 1; state.lightCoords = 15728880;
        client.getItemModelResolver().updateForTopItem(state.item, stack, ItemDisplayContext.NONE, client.level, null, 0);
        if (state.item.isEmpty()) return false;
        var bounds = state.item.getModelBoundingBox();
        float extent = (float) Math.max(.2, Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize())));
        state.boundingBoxWidth = extent; state.boundingBoxHeight = (float) bounds.getYsize();
        // ItemEntityRenderer raises models by -minY + 0.1625 and rotates about their model origin.
        Vector3f center = new Vector3f(0, (float) bounds.getYsize() / 2 + .1625f, 0);
        Quaternionf rotation = new Quaternionf().rotationZ((float) Math.PI).rotateX(-.18f).rotateY(angle);
        // The 3D picture is placed in real screen coordinates (it is not moved by the window's drawing offset).
        int ox = client.screen instanceof WindowScreen window ? window.winX : 0, oy = client.screen instanceof WindowScreen window ? window.winY : 0;
        double ui=client.screen instanceof WindowScreen window?window.windowScale:1;
        g.submitEntityRenderState(state, (float)(size * ui * .72f / extent), center, rotation, null, ox+(int)Math.round(x*ui), oy+(int)Math.round(y*ui), ox+(int)Math.round((x+size)*ui), oy+(int)Math.round((y+size)*ui));
        return true;
    }
}
