package cn.paletale.companion;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Adds 「[icon] 迅捷 II」 under the name of a potion that is a wine registered on the site (matched by its custom model data). The index is fetched from the site, at most every half hour. */
final class WineTooltips {
    private static volatile WineEffectIndex index = WineEffectIndex.EMPTY;
    private static volatile long fetchedAt; private static volatile boolean loading;
    private static final HttpClient HTTP = HttpBodies.CLIENT;
    /** The glyph order of assets/paletale/font/wine.json: one icon per effect key. */
    private static final java.util.List<String> ICON_KEYS = java.util.List.of("speed", "haste", "strength", "jump", "resistance", "fire_resistance", "hunger", "absorption", "glowing", "luck", "unluck",
            "sober", "transform", "brand", "enchant", "chat", "unknown");
    private WineTooltips() {}
    private static net.minecraft.network.chat.Style iconStyle() {
        return net.minecraft.network.chat.Style.EMPTY.withFont(new net.minecraft.network.chat.FontDescription.Resource(Identifier.fromNamespaceAndPath("paletale", "wine")));
    }

    static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (!ModSettings.wineTooltip() || stack.isEmpty()) return;
            refresh();
            // The wine is recognised by its material (item model + custom model data); the name is only a fallback for wines without one.
            var info = lookup(stack);
            if (info == null) return;
            // The effect goes first under the name.
            lines.add(Math.min(1, lines.size()), line(info));
            // 烙印 wines: Shift previews the brand text shown when the wine is drunk.
            if (!info.brand().isBlank()) {
                int at = Math.min(2, lines.size());
                if (shiftDown()) {
                    var preview = BrandText.lines(info.brand());
                    lines.add(at++, Component.literal("烙印预览").withStyle(ChatFormatting.GRAY));
                    for (var brandLine : preview) lines.add(at++, brandLine);
                } else lines.add(at, Component.literal("按住 Shift 预览烙印").withStyle(ChatFormatting.DARK_GRAY));
            }
        });
    }
    private static boolean shiftDown() {
        long window = net.minecraft.client.Minecraft.getInstance().getWindow().handle();
        return org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS || org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }
    static Integer customOf(net.minecraft.world.item.ItemStack stack) {
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
        return data == null || data.floats().isEmpty() || data.floats().get(0) != Math.rint(data.floats().get(0)) ? null : Math.round(data.floats().get(0));
    }
    private static String modelOf(net.minecraft.world.item.ItemStack stack) {
        var model = stack.get(net.minecraft.core.component.DataComponents.ITEM_MODEL);
        return model == null ? "" : model.toString();
    }
    private static WineEffectIndex.Info lookup(net.minecraft.world.item.ItemStack stack) {
        return index.effectOf(stack.getItem() == net.minecraft.world.item.Items.POTION, modelOf(stack), customOf(stack), stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME) ? stack.getHoverName().getString() : "");
    }
    /** 「[icon] 迅捷 II」 with the same icon the archive uses; white text for known effects, dark red for unknown. */
    static Component line(WineEffectIndex.Info info) {
        boolean known = !info.key().equals("unknown");
        int glyph = ICON_KEYS.indexOf(info.key());
        var line = Component.empty();
        if (glyph >= 0) line.append(Component.literal(String.valueOf((char) (0xE000 + glyph))).withStyle(iconStyle())).append(Component.literal(" "));
        return line.append(Component.literal(info.text()).withStyle(known ? ChatFormatting.WHITE : ChatFormatting.DARK_RED));
    }
    private static void refresh() {
        long now = System.currentTimeMillis();
        if (loading || now - fetchedAt < 30 * 60_000L) return;
        loading = true; fetchedAt = now;
        HTTP.sendAsync(HttpBodies.request(SiteConfig.current().resolve("/api/mcmod/items/wine-effects")).timeout(Duration.ofSeconds(15)).header("Accept", "application/json").GET().build(),
                HttpBodies.text()).whenComplete((response, failure) -> {
            try {
                if (failure == null && response.statusCode() == 200 && response.body().length() < 4_000_000) index = WineEffectIndex.parse(response.body());
                else fetchedAt = System.currentTimeMillis() - 28 * 60_000L; // a failed attempt is retried after two minutes
            } catch (RuntimeException ignored) { fetchedAt = System.currentTimeMillis() - 28 * 60_000L; }
            finally { loading = false; }
        });
    }
}
