package cn.paletale.companion;

import cn.paletale.companion.mixin.FontAccessMixin;
import cn.paletale.companion.mixin.ScreenFontMixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/**
 * The font PaleTale's windows are drawn with: a bundled serif font (assets/paletale/font/ui.json; symbols and any character it lacks come from the game's own font),
 * or the game's font when the player prefers that in the settings. Only PaleTale's own windows use it; chat and the rest of the game are untouched.
 */
final class PaleFont {
    private static final FontDescription UI = new FontDescription.Resource(Identifier.fromNamespaceAndPath("paletale", "ui"));
    private static Font basis, custom;
    private PaleFont() {}

    static boolean supported() { return true; }

    static Font get() {
        var client = Minecraft.getInstance();
        if (!ModSettings.paleFont()) return client.font;
        if (custom == null || basis != client.font) { basis = client.font; custom = build(UI); }
        return custom;
    }

    private static Font build(FontDescription face) {
        Font.Provider base = ((FontAccessMixin) (Object) basis).paletale$provider();
        return new Font(new Font.Provider() {
            @Override public GlyphSource glyphs(FontDescription description) { return base.glyphs(FontDescription.DEFAULT.equals(description) ? face : description); }
            @Override public EffectGlyph effect() { return base.effect(); }
        });
    }

    /** Gives a window the chosen font; call before it lays itself out. */
    static void apply(Screen screen) { ((ScreenFontMixin) (Object) screen).paletale$setFont(get()); }
}
