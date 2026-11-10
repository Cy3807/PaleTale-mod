package cn.paletale.companion;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Shows the settings button in Mod Menu; the screen itself needs Cloth Config and falls back to nothing when it is missing. */
public final class PaleTaleModMenu implements ModMenuApi {
    @Override public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            try { return SiteSettingsScreen.create(parent); }
            catch (LinkageError missingCloth) { return parent; }
        };
    }
}
