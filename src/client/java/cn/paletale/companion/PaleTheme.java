package cn.paletale.companion;
/** Light and dark palettes: dark colours are the light ones with their lightness flipped, so every screen follows the one switch. */
final class PaleTheme {
    private PaleTheme() {}
    static boolean dark() { return ModSettings.darkTheme(); }
    /** The dark counterpart of an opaque light-theme colour; alpha is kept. */
    static int map(int argb) {
        float r = (argb >> 16 & 255) / 255f, g = (argb >> 8 & 255) / 255f, b = (argb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), l = (max + min) / 2, d = max - min, h = 0, s = 0;
        if (d > 0) {
            s = d / (1 - Math.abs(2 * l - 1));
            if (max == r) h = ((g - b) / d + 6) % 6; else if (max == g) h = (b - r) / d + 2; else h = (r - g) / d + 4;
        }
        float nl = 0.09f + (1 - l) * 0.88f, c = Math.min(d, 1 - Math.abs(2 * nl - 1)), x = c * (1 - Math.abs(h % 2 - 1)), m = nl - c / 2, nr, ng, nb;
        if (h < 1) { nr = c; ng = x; nb = 0; } else if (h < 2) { nr = x; ng = c; nb = 0; } else if (h < 3) { nr = 0; ng = c; nb = x; }
        else if (h < 4) { nr = 0; ng = x; nb = c; } else if (h < 5) { nr = x; ng = 0; nb = c; } else { nr = c; ng = 0; nb = x; }
        return (argb & 0xFF000000) | Math.round((nr + m) * 255) << 16 | Math.round((ng + m) * 255) << 8 | Math.round((nb + m) * 255);
    }
}
