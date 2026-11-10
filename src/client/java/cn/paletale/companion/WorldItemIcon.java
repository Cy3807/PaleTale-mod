package cn.paletale.companion;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** An item's picture in SMC lists: the server pack's own model, else the site's image, else the vanilla item, else a chest. */
final class WorldItemIcon {
    private WorldItemIcon() {}

    static void draw(GuiGraphics g, MarketApi market, String name, String path, String vanillaId, int x, int y, int size) {
        if (ServerItemIcons.draw(g, name, x, y, size)) return;
        if ((path.startsWith("/api/mcmod/") || path.startsWith("/smc-items/")) && MarketImages.INSTANCE.draw(g, market, path, x, y, size)) return;
        var id = Identifier.tryParse("minecraft:" + vanillaId);
        var entry = id == null ? Items.CHEST : BuiltInRegistries.ITEM.getValue(id);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(size / 16f, size / 16f);
        g.renderItem(new ItemStack(entry == null || entry == Items.AIR ? Items.CHEST : entry), 0, 0);
        g.pose().popMatrix();
    }
}
