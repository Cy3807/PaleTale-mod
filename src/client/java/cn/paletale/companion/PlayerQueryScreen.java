package cn.paletale.companion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The command wheel's player query, in the wheel's own form: a ring whose middle is a small input box (focused at once, just type). The tiles are the online players matching what is
 * typed; Tab completes to one (again for the next), a click on a tile picks it, Enter sends /l player <name>, right click or Esc closes. Nothing is dimmed.
 */
final class PlayerQueryScreen extends Screen {
    /** The box lives inside the ring's hole (72 px across): it must not reach the tiles. */
    private static final int BOX_W = 64, BOX_H = 16;
    private EditBox name;
    private String typed = "";
    private int focusTicks;
    private List<String> cycle = List.of(); private int cycleAt = -1; private boolean completing;
    private int hover = -1; private final long openedAt = System.currentTimeMillis();

    PlayerQueryScreen() { super(Component.literal("玩家")); }

    @Override protected void init() {
        PaleFont.apply(this); RadialArt.reset();
        name = addRenderableWidget(new PaleInput(font, width / 2 - BOX_W / 2, height / 2 - BOX_H / 2, BOX_W, BOX_H, Component.literal("玩家名")));
        name.setMaxLength(16);
        name.setValue(typed);
        name.setResponder(text -> { if (!completing) { typed = text; cycle = List.of(); cycleAt = -1; } ((PaleInput) name).error = false; });
        focusInput();
    }

    private List<String> online() {
        var out = new ArrayList<String>();
        var connection = minecraft.getConnection();
        if (connection != null) for (var info : connection.getOnlinePlayers()) out.add(info.getProfile().name());
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }
    /** Players whose name starts with the text first, then those that merely contain it. */
    private List<String> matches(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        var all = online(); var out = new ArrayList<String>();
        for (String n : all) if (n.toLowerCase(Locale.ROOT).startsWith(lower)) out.add(n);
        for (String n : all) if (!out.contains(n) && n.toLowerCase(Locale.ROOT).contains(lower)) out.add(n);
        return out;
    }

    /** Tab: completes to the first online player matching what was typed; pressing it again goes on to the next one. */
    private void complete() {
        if (cycle.isEmpty()) { cycle = matches(typed); cycleAt = -1; }
        if (cycle.isEmpty()) return;
        cycleAt = (cycleAt + 1) % cycle.size();
        completing = true; name.setValue(cycle.get(cycleAt)); completing = false;
    }

    private void submit(String value) {
        value = value.strip();
        if (!value.matches("[A-Za-z0-9_]{1,16}")) { ((PaleInput) name).error = true; return; }
        // The ring of what to do with the player comes up (the SMC page with balance, territories and online time is its first tile).
        minecraft.setScreen(null);
        RadialWheel.showPlayer(value);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_TAB) { complete(); return true; }
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) { submit(name.getValue()); return true; }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(event);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (event.button() == 1) { onClose(); return true; }
        if (event.button() == 0 && hover >= 0) { var list = tiles(); if (hover < list.size()) { submit(list.get(hover)); return true; } }
        return super.mouseClicked(event, twice);
    }

    /** Nothing typed yet: the favourite players (online ones first); then whoever matches what is typed. */
    private List<String> tiles() { return (typed.isEmpty() ? RadialWheel.favoritePlayers() : matches(typed)).stream().limit(RadialSlots.COUNT).toList(); }

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) { }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int cx = width / 2, cy = height / 2; var font = PaleFont.get(); var list = tiles();
        int at = Math.hypot(mouseX - cx, mouseY - cy) < RadialArt.INNER + 4 ? -1 : RadialArt.sectorAt(mouseX - cx, mouseY - cy, RadialSlots.COUNT);
        hover = at >= 0 && at < list.size() ? at : -1;
        var icons = new ArrayList<RadialArt.Icon>();
        for (int i = 0; i < RadialSlots.COUNT; i++) icons.add(i < list.size() ? RadialArt.Icon.sized(RadialArt.headOf(list.get(i)), 1.5f) : RadialArt.Icon.NONE);
        RadialArt.draw(g, font, cx, cy, RadialSlots.COUNT, icons, hover, Math.min(1, (System.currentTimeMillis() - openedAt) / 140.0), null);
        // The names of the tiles sit just outside the ring, so the icons stay bare like everywhere on the wheel.
        for (int i = 0; i < list.size(); i++) {
            double angle = Math.toRadians(360.0 / RadialSlots.COUNT * i), radius = RadialArt.OUTER + 16;
            String label = font.plainSubstrByWidth(list.get(i), 70); int w = font.width(label) + 8;
            int lx = cx + (int) Math.round(Math.sin(angle) * radius), ly = cy - (int) Math.round(Math.cos(angle) * radius);
            int x = Math.abs(Math.sin(angle)) < .3 ? lx - w / 2 : Math.sin(angle) > 0 ? lx - 4 : lx - w + 4;
            boolean on = i == hover;
            g.fill(x, ly - 7, x + w, ly + 6, on ? MarketTheme.ACCENT : MarketTheme.PAPER); g.renderOutline(x, ly - 7, w, 13, MarketTheme.LINE);
            g.drawString(font, label, x + 4, ly - 4, on ? MarketTheme.c(0xFFF5F4F0) : MarketTheme.INK, false);
        }
        super.render(g, mouseX, mouseY, delta);
    }
    /** Typing goes to the screen's focused widget, which setInitialFocus leaves alone when the last input came from the mouse (the box then only looked focused): focus it on the screen itself. */
    private void focusInput() { setFocused(name); name.setFocused(true); name.setCanLoseFocus(false); }

    /** The box must be ready for typing from the first frame, whatever else the game does with focus while a screen opens. */
    @Override public void tick() { if (focusTicks++ < 40 && getFocused() != name) focusInput(); }
    @Override public void onClose() { minecraft.setScreen(null); }
    @Override public boolean isPauseScreen() { return false; }
}
