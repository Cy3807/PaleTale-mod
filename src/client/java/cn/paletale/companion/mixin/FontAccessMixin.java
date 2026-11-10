package cn.paletale.companion.mixin;

import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets PaleFont build a second Font on top of the game's own glyph lookup. */
@Mixin(Font.class)
public interface FontAccessMixin {
    @Accessor("provider") Font.Provider paletale$provider();
}
