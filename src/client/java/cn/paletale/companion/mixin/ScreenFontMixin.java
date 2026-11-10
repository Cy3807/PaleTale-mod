package cn.paletale.companion.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A screen's font is final; PaleTale's windows swap it for the one the player chose. */
@Mixin(Screen.class)
public interface ScreenFontMixin {
    @Mutable @Accessor("font") void paletale$setFont(Font font);
}
