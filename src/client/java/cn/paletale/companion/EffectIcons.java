package cn.paletale.companion;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import java.util.Map;

/** Vanilla icons for the wine effects: the game's own mob-effect sprites, or a vanilla item for categories without one. */
final class EffectIcons {
    private static final Map<String, String> SPRITES = Map.ofEntries(Map.entry("speed", "speed"), Map.entry("haste", "haste"), Map.entry("strength", "strength"),
            Map.entry("jump", "jump_boost"), Map.entry("resistance", "resistance"), Map.entry("fire_resistance", "fire_resistance"), Map.entry("hunger", "hunger"),
            Map.entry("absorption", "absorption"), Map.entry("glowing", "glowing"), Map.entry("luck", "luck"), Map.entry("unluck", "unluck"));
    private static final Map<String, String> ITEMS = Map.of("sober", "milk_bucket", "summon", "cat_spawn_egg", "transform", "totem_of_undying",
            "head", "player_head", "brand", "name_tag", "enchant", "enchanted_book", "chat", "writable_book", "unknown", "barrier");
    private EffectIcons() {}
    /** Draws the icon for an effect key at the given size; keys with no icon (the "all" entry) draw nothing. */
    static void draw(GuiGraphics g, String key, int x, int y, int size) {
        String sprite = SPRITES.get(key);
        if (sprite != null) g.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("mob_effect/" + sprite), x, y, size, size);
        else if (ITEMS.containsKey(key)) ArchiveModels.INSTANCE.drawVanilla(g, ITEMS.get(key), x, y, size);
    }
}
