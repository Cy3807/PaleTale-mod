package cn.paletale.companion;

import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Map-art facts start with the size tag ("中 3×2 · 二次元"); it is coloured like the website's size tags. */
final class MapSizeColor {
    private static final Pattern SIZE = Pattern.compile("^(超大|大|中|小) \\d+×\\d+");
    private MapSizeColor() {}

    /** Length of the size tag at the start of the facts, or 0 when there is none. */
    static int tagLength(String facts) {
        var match = SIZE.matcher(facts);
        return match.find() ? match.end() : 0;
    }
    static int color(String facts) {
        return switch (facts.startsWith("超大") ? "超大" : facts.isEmpty() ? "" : facts.substring(0, 1)) {
            case "超大" -> 0xFFC74B4B;
            case "大" -> 0xFFC28A1E;
            case "中" -> 0xFF2F7EC4;
            case "小" -> 0xFF3F8F55;
            default -> 0;
        };
    }
    /** The facts as one text, the size tag coloured and the rest in the given colour. */
    static MutableComponent styled(String facts, int rest) {
        int length = tagLength(facts);
        if (length == 0) return Component.literal(facts).withColor(rest & 0xFFFFFF);
        return Component.literal(facts.substring(0, length)).withColor(color(facts) & 0xFFFFFF)
                .append(Component.literal(facts.substring(length)).withColor(rest & 0xFFFFFF));
    }
    /** Draws the facts on one line, shortened with an ellipsis when too wide. */
    static void draw(GuiGraphics g, String facts, int x, int y, int width, int rest) {
        var font = PaleFont.get();
        int length = tagLength(facts);
        if (length == 0) { MarketTheme.text(g, facts, x, y, width, rest); return; }
        String head = facts.substring(0, length);
        MarketTheme.text(g, head, x, y, width, color(facts));
        MarketTheme.text(g, facts.substring(length), x + font.width(head), y, width - font.width(head), rest);
    }
}
