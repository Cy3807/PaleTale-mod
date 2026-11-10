package cn.paletale.companion;

import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

/** The looks the navigation guide line can have (picked in the settings); "off" keeps the compass bar but draws no specks. */
final class NavParticles {
    record Style(String key, String label, ParticleOptions particle) {}
    static final List<Style> STYLES = List.of(
            new Style("light", "白光", ParticleTypes.END_ROD),
            new Style("gold", "金光", dust(0xFFC83C)),
            new Style("red", "赤焰", dust(0xFF3B30)),
            new Style("green", "翠绿", dust(0x3CDC5A)),
            new Style("blue", "湛蓝", dust(0x3C9BFF)),
            new Style("purple", "紫晶", dust(0xB450FF)),
            new Style("off", "关闭", null));
    private static ParticleOptions dust(int rgb) { return new DustParticleOptions(rgb, 1.3f); }
    private NavParticles() {}
    static boolean known(String key) { return STYLES.stream().anyMatch(style -> style.key().equals(key)); }
    static Style current() { String key = ModSettings.navParticle(); return STYLES.stream().filter(style -> style.key().equals(key)).findFirst().orElse(STYLES.getFirst()); }
    /** The style after the current one, wrapping around. */
    static Style next() { return STYLES.get((STYLES.indexOf(current()) + 1) % STYLES.size()); }
}
