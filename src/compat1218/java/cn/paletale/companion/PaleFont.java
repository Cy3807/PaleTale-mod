package cn.paletale.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;

/** 1.21.8 has no bundled UI font: PaleTale's windows keep the game's own font and the setting is hidden. */
final class PaleFont {
    private PaleFont() {}
    static boolean supported() { return false; }
    static Font get() { return Minecraft.getInstance().font; }
    static void apply(Screen screen) { }
}
