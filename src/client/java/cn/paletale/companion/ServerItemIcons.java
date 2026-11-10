package cn.paletale.companion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomModelData;
import java.util.*;
/** Pixels come from the active client pack; missing definitions fall back to the website image. */
final class ServerItemIcons {
 private static final Map<String,ItemStack> STACKS=new HashMap<>();
 static boolean draw(GuiGraphics g,String name,int x,int y,int size){var appearance=ServerItemCatalog.appearance(name);if(appearance==null)return false;var model=Identifier.tryParse(appearance.model());if(model==null||Minecraft.getInstance().getModelManager().getItemModel(model) instanceof MissingItemModel)return false;
  ItemStack stack=STACKS.computeIfAbsent(appearance.model()+"|"+appearance.baseItem()+"|"+appearance.customModelData(),key->{var base=Identifier.tryParse(appearance.baseItem());var item=base==null?Items.APPLE:BuiltInRegistries.ITEM.getValue(base);var value=new ItemStack(item==null||item==Items.AIR?Items.APPLE:item);value.set(DataComponents.ITEM_MODEL,model);if(appearance.customModelData()!=null)value.set(DataComponents.CUSTOM_MODEL_DATA,new CustomModelData(List.of(appearance.customModelData().floatValue()),List.of(),List.of(),List.of()));return value;});return ArchiveModels.INSTANCE.draw(g,stack,x,y,size);
 }
}
