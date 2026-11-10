package cn.paletale.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** Display-only apple stacks match the player's exported official menu; pixels always come from the currently loaded server pack. */
final class OfficialCropIcons {
    record Appearance(String model, int customId) {}
    private static final Map<String, ItemStack> STACKS = new HashMap<>();
    private OfficialCropIcons() {}
    static Appearance appearance(String key) {
        return switch (key) {
            case "大豆 · 1星" -> new Appearance("smc:dadou_p1", 4376);
            case "大豆 · 2星" -> new Appearance("smc:dadou_p2", 4377);
            case "大豆 · 3星" -> new Appearance("smc:dadou_p3", 4378);
            case "蘑菇 · 1星" -> new Appearance("smc:mogu_p1", 4381);
            case "蘑菇 · 2星" -> new Appearance("smc:mogu_p2", 4382);
            case "蘑菇 · 3星" -> new Appearance("smc:mogu_p3", 4383);
            case "玉米 · 1星" -> new Appearance("smc:yumi_p1", 4396);
            case "玉米 · 2星" -> new Appearance("smc:yumi_p2", 4397);
            case "玉米 · 3星" -> new Appearance("smc:yumi_p3", 4398);
            case "水稻 · 1星" -> new Appearance("smc:shuidao_p1", 4406);
            case "水稻 · 2星" -> new Appearance("smc:shuidao_p2", 4407);
            case "水稻 · 3星" -> new Appearance("smc:shuidao_p3", 4408);
            case "茄子 · 1星" -> new Appearance("smc:qiezi_p1", 4386);
            case "茄子 · 2星" -> new Appearance("smc:qiezi_p2", 4387);
            case "茄子 · 3星" -> new Appearance("smc:qiezi_p3", 4388);
            case "水晶番茄 · 1星" -> new Appearance("smc:shuijingfanqie_p1", 4411);
            case "水晶番茄 · 2星" -> new Appearance("smc:shuijingfanqie_p2", 4412);
            case "水晶番茄 · 3星" -> new Appearance("smc:shuijingfanqie_p3", 4413);
            default -> null;
        };
    }
    static boolean draw(GuiGraphics g, String key, int x, int y, int size) {
        Appearance value = appearance(key);
        if (value == null) {
            for (var product : LedgerOfficial.PRODUCTS) if (!product.mineral() && key.startsWith(product.item())) { value = appearance(product.item() + " · 1星"); break; }
        }
        if (value == null) return false;
        var model = Identifier.tryParse(value.model());
        if (model == null || Minecraft.getInstance().getModelManager().getItemModel(model) instanceof MissingItemModel) return false;
        var chosen = value;
        ItemStack stack = STACKS.computeIfAbsent(value.model(), name -> {
            var item = new ItemStack(Items.APPLE);
            item.set(DataComponents.ITEM_MODEL, model);
            item.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of((float) chosen.customId()), List.of(), List.of(), List.of()));
            return item;
        });
        return ArchiveModels.INSTANCE.draw(g, stack, x, y, size);
    }
}
