package cn.paletale.companion;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * Opens a fish's archive page from the fished item: the item's custom model data is looked up in the website's fish table
 * ({@link FishIndex}). Works on the held item and, with an inventory open, on the hovered slot. Anything that does not match is ignored.
 */
final class FishLookup {
    private static KeyMapping key;
    private static boolean down;
    private FishLookup() {}

    static void register(KeyMapping binding) {
        key = binding;
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (fishOf(stack) == null) return;
            lines.add(Math.min(1, lines.size()), Component.literal(key.isUnbound() ? "鱼类档案 · 在按键设置里绑定「查看鱼类档案」后可查看" : "鱼类档案 · 按 ").withStyle(ChatFormatting.GRAY)
                    .append(key.isUnbound() ? Component.empty() : key.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.WHITE)));
        });
    }

    /** Polled once per tick (edge-triggered): the held item without a screen, the hovered slot in an inventory. */
    static void tick(Minecraft client) {
        if (key == null) return;
        boolean pressed = !key.isUnbound() && pressed(client);
        boolean fresh = pressed && !down;
        down = pressed;
        if (!fresh || client.player == null) return;
        Integer id = null;
        if (client.screen instanceof AbstractContainerScreen<?> container) {
            var slot = container.hoveredSlot;
            id = slot == null ? null : fishOf(slot.getItem());
        } else if (client.screen == null) {
            id = fishOf(client.player.getMainHandItem());
            if (id == null) id = fishOf(client.player.getOffhandItem());
        }
        if (id != null) client.setScreen(new ArchiveDetailScreen(client.screen, PaleTaleClient.MARKET, ArchiveData.Kind.ITEMS, id));
    }

    /** Keyboard keys only: an open inventory takes the key press itself, so the binding is read straight from the window. */
    private static boolean pressed(Minecraft client) {
        var bound = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(key);
        if (bound.getType() != com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM) return false;
        return GLFW.glfwGetKey(client.getWindow().handle(), bound.getValue()) == GLFW.GLFW_PRESS;
    }

    static Integer fishOf(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : FishIndex.current().idOfCustom(WineTooltips.customOf(stack));
    }
}
