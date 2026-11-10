package cn.paletale.companion;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A brand wine's own brand text as the website shows it: up to three lines, with colour codes ({@code &#rrggbb} or {@code #rrggbb}, legacy {@code &0}–{@code &f})
 * and format codes ({@code &l} bold, {@code &o} italic, {@code &n} underline, {@code &m} strike-through, {@code &r} reset). A colour code resets the formatting, like in the game.
 */
final class BrandText {
    private static final Pattern HEX = Pattern.compile("^&?#([0-9a-fA-F]{6})");
    private static final Pattern CODE = Pattern.compile("^&([0-9a-fk-orA-FK-OR])");
    private static final int[] LEGACY = { 0x000000, 0x0000aa, 0x00aa00, 0x00aaaa, 0xaa0000, 0xaa00aa, 0xffaa00, 0xaaaaaa, 0x555555, 0x5555ff, 0x55ff55, 0x55ffff, 0xff5555, 0xff55ff, 0xffff55, 0xffffff };
    private BrandText() {}

    /** One styled component per non-empty line (at most three). */
    static List<Component> lines(String raw) {
        List<Component> out = new ArrayList<>();
        if (raw == null) return out;
        for (String line : raw.split("\n")) {
            String text = line.strip();
            if (text.isEmpty()) continue;
            out.add(parse(text));
            if (out.size() == 3) break;
        }
        return out;
    }

    static Component parse(String line) {
        MutableComponent root = Component.empty();
        Integer color = null; boolean bold = false, italic = false, underline = false, strike = false;
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < line.length();) {
            String rest = line.substring(i);
            Matcher hex = HEX.matcher(rest);
            if (hex.find()) {
                flush(root, buffer, color, bold, italic, underline, strike);
                color = Integer.parseInt(hex.group(1), 16); bold = italic = underline = strike = false; i += hex.end(); continue;
            }
            Matcher code = CODE.matcher(rest);
            if (code.find()) {
                flush(root, buffer, color, bold, italic, underline, strike);
                char key = Character.toLowerCase(code.group(1).charAt(0));
                int legacy = Character.digit(key, 16);
                if (legacy >= 0) { color = LEGACY[legacy]; bold = italic = underline = strike = false; }
                else if (key == 'l') bold = true;
                else if (key == 'o') italic = true;
                else if (key == 'n') underline = true;
                else if (key == 'm') strike = true;
                else if (key == 'r') { color = null; bold = italic = underline = strike = false; }
                i += 2; continue;
            }
            int cp = line.codePointAt(i);
            buffer.appendCodePoint(cp); i += Character.charCount(cp);
        }
        flush(root, buffer, color, bold, italic, underline, strike);
        return root;
    }

    private static void flush(MutableComponent root, StringBuilder buffer, Integer color, boolean bold, boolean italic, boolean underline, boolean strike) {
        if (buffer.length() == 0) return;
        Style style = Style.EMPTY.withColor(color == null ? TextColor.fromRgb(0xF1EEF5) : TextColor.fromRgb(color)).withBold(bold).withItalic(italic).withUnderlined(underline).withStrikethrough(strike);
        root.append(Component.literal(buffer.toString()).withStyle(style));
        buffer.setLength(0);
    }
}
