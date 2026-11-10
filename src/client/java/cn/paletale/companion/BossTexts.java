package cn.paletale.companion;

import net.minecraft.client.Minecraft;
import java.util.ArrayList;
import java.util.List;

/** The titles of the boss bars currently shown (the SIMMC server announces tips there). Needs the access widener for {@code BossHealthOverlay.events}. */
final class BossTexts {
    private BossTexts() {}
    static List<String> titles(Minecraft client) {
        List<String> out = new ArrayList<>();
        try {
            for (var event : client.gui.getBossOverlay().events.values()) out.add(event.getName().getString());
        } catch (RuntimeException ignored) { /* never break the game over a ledger */ }
        return out;
    }
}
