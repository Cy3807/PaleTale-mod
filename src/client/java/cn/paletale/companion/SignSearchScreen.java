package cn.paletale.companion;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import static cn.paletale.companion.MarketTheme.*;

/**
 * The quick wheel's sign search: a small pop-up that lists the signs around the player (the ones the game has loaded, read straight from the world; nothing is asked of the server), nearest
 * first. What is typed narrows the list to the signs whose text contains it. A row shows the sign's lines and how far it is; a click starts the navigation to the chest behind it.
 */
final class SignSearchScreen extends ChatDialog {
    private static final int ROW = 22, WIDTH = 320, RADIUS = 64;
    private EditBox typed; private String text = "";
    private List<ShopScanner.Sign> signs = List.of();
    private int rowsShown, scroll, scanTicks;

    SignSearchScreen() { super(null, "告示牌搜索"); }

    @Override protected int cardWidth() { return WIDTH; }
    @Override protected int contentHeight() { rowsShown = Math.max(4, Math.min(8, (height - 150) / ROW)); return 52 + rowsShown * ROW; }

    @Override protected void layout() {
        int bw = cardW - 2 * PAD, y = cardY + cardH - PAD - 18;
        typed = input(34, "输入告示牌上的字，留空列出附近全部", text, 24);
        typed.setResponder(value -> { text = value; scroll = 0; });
        focus(typed);
        addRenderableWidget(new Rows(cardX + PAD, cardY + 34 + FIELD_H + 6, cardW - 2 * PAD, rowsShown * ROW));
        addRenderableWidget(new Action("返回", cardX + PAD, y, bw, 18, false, null, b -> onClose()));
        scan();
    }

    private void scan() { signs = ShopScanner.signs(minecraft, RADIUS); }
    @Override public void tick() { super.tick(); if (++scanTicks % 20 == 0) scan(); }

    private static String plain(ShopScanner.Sign sign) {
        var lines = sign.raw().stream().map(ShopSigns::plain).filter(l -> !l.isEmpty()).toList();
        return String.join(" · ", lines);
    }
    private List<ShopScanner.Sign> shown() {
        String lower = text.strip().toLowerCase(Locale.ROOT);
        return signs.stream().filter(s -> !plain(s).isEmpty() && (lower.isEmpty() || plain(s).toLowerCase(Locale.ROOT).contains(lower))).toList();
    }

    private void go(ShopScanner.Sign sign) {
        int[] chest = ShopSigns.chestAt(sign.x(), sign.y(), sign.z(), sign.chestDir());
        int[] at = chest != null ? chest : new int[]{ sign.x(), sign.y(), sign.z() };
        String name = plain(sign); if (name.length() > 24) name = name.substring(0, 24);
        Navigation.start(new Navigation.Target(name, at[0], at[1], at[2], "告示牌", List.of(at), CityNavigation.MAIN_WORLD));
        minecraft.setScreen(null);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) { var list = shown(); if (!list.isEmpty()) go(list.get(0)); return true; }
        return super.keyPressed(event);
    }

    private final class Rows extends AbstractWidget {
        Rows(int x, int y, int w, int h) { super(x, y, w, h, Component.empty()); }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
            g.renderOutline(getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2, LINE);
            var list = shown(); int total = list.size();
            if (total == 0) { g.drawString(font, signs.isEmpty() ? "周围 " + RADIUS + " 格内没有已加载的告示牌" : "没有匹配的告示牌", getX() + 6, getY() + 8, MUTED, false); return; }
            scroll = Math.max(0, Math.min(Math.max(0, total - rowsShown), scroll));
            for (int r = 0; r < rowsShown && r + scroll < total; r++) {
                var sign = list.get(r + scroll); int y = getY() + r * ROW;
                boolean over = mouseX >= getX() && mouseX < getX() + getWidth() && mouseY >= y && mouseY < y + ROW;
                if (over) g.fill(getX(), y, getX() + getWidth(), y + ROW, MarketTheme.c(0x553B6652));
                if (r > 0) g.hLine(getX() + 4, getX() + getWidth() - 5, y, LINE);
                g.renderItem(new ItemStack(Items.OAK_SIGN), getX() + 4, y + 3);
                String distance = (int) sign.distance() + " 格";
                g.drawString(font, distance, getX() + getWidth() - 8 - font.width(distance), y + 7, MUTED, false);
                g.drawString(font, font.plainSubstrByWidth(plain(sign), getWidth() - 34 - font.width(distance) - 8), getX() + 26, y + 7, INK, false);
            }
            if (total > rowsShown) {
                int track = getHeight(), thumb = Math.max(10, track * rowsShown / total), at = getY() + (track - thumb) * scroll / Math.max(1, total - rowsShown);
                g.fill(getX() + getWidth() - 3, getY(), getX() + getWidth() - 2, getY() + track, LINE); g.fill(getX() + getWidth() - 4, at, getX() + getWidth() - 1, at + thumb, ACCENT);
            }
        }
        @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
            int total = shown().size(); if (total <= rowsShown) return false;
            scroll = Math.max(0, Math.min(total - rowsShown, scroll - (int) Math.signum(vertical)));
            return true;
        }
        @Override public void onClick(MouseButtonEvent event, boolean twice) {
            int at = (int) ((event.y() - getY()) / ROW) + scroll; var list = shown();
            if (at >= 0 && at < list.size()) go(list.get(at));
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { }
    }

    @Override protected void drawContent(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, "附近的告示牌（点一行导航到它后面的箱子，回车取第一行）", cardX + PAD, cardY + 22, MUTED, false);
    }
}
