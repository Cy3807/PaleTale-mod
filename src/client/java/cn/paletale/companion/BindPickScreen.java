package cn.paletale.companion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import static cn.paletale.companion.MarketTheme.*;

/**
 * The small pop-up that picks the territory or the player a wheel slot is bound to. Territories: your cached list with each one's own icon (scrolls; a refresh button reads /l
 * again), or a typed name. Players: nothing is listed until an ID is typed; then the players whose ID matches are listed with their heads (the ones online first, then the archive's);
 * Tab completes to one (again for the next), a click picks it, Enter takes what is typed.
 */
final class BindPickScreen extends ChatDialog {
    private static final int ROW = 20, WIDTH = 300;
    private record Hit(String name, String image) {}
    private final boolean land; private final Consumer<String> picked; private final Runnable cancelled;
    private EditBox typed; private String note = ""; private boolean done, refreshing, started;
    private int rowsShown, scroll;
    /** Player mode: what the typed text matches, and the state of the archive search behind it. */
    private List<Hit> suggestions = List.of(); private List<Hit> cycle = List.of(); private int cycleAt = -1; private boolean completing;
    private String text = "", requested = ""; private long editedAt; private int generation;

    BindPickScreen(boolean land, Consumer<String> picked, Runnable cancelled) {
        super(null, land ? "绑定领地" : "绑定玩家"); this.land = land; this.picked = picked; this.cancelled = cancelled;
    }

    @Override protected int cardWidth() { return WIDTH; }
    @Override protected int contentHeight() {
        rowsShown = Math.max(3, Math.min(land ? 7 : 6, (height - 150) / ROW));
        // The buttons sit 8 below the last thing (the typed-name box for territories, the rows for players).
        return land ? 64 + rowsShown * ROW : 52 + rowsShown * ROW;
    }

    private int listY() { return land ? 34 : 34 + FIELD_H + 6; }

    @Override protected void layout() {
        int gap = 6, bw = (cardW - 2 * PAD - gap) / 2, y = cardY + cardH - PAD - 18;
        int inputY = land ? listY() + rowsShown * ROW + 18 : 34;
        typed = input(inputY, land ? "也可以直接输入领地名" : "输入玩家 ID", text, land ? 60 : 16);
        typed.setResponder(value -> { if (completing) return; text = value; cycle = List.of(); cycleAt = -1; editedAt = System.currentTimeMillis(); note = ""; localMatches(); });
        focus(typed);
        addRenderableWidget(new Rows(cardX + PAD, cardY + listY(), cardW - 2 * PAD, rowsShown * ROW));
        addRenderableWidget(new Action("绑定", cardX + PAD, y, bw, 18, true, null, b -> pick(typed.getValue().strip())));
        addRenderableWidget(new Action("返回", cardX + PAD + bw + gap, y, bw, 18, false, null, b -> onClose()));
        if (land) addRenderableWidget(new RefreshButton(cardX + cardW - PAD - 66, cardY + 2, 66, 18));
    }

    /** Reads /l again right now; the list follows the cache by itself. */
    private void refresh() {
        if (refreshing) return;
        refreshing = true; rebuildWidgets();
        LandMenu.read(entries -> { refreshing = false; if (minecraft.screen == this) rebuildWidgets(); }, 4000, true);
    }

    private List<String> online() {
        var out = new ArrayList<String>();
        var connection = minecraft.getConnection();
        if (connection != null) for (var info : connection.getOnlinePlayers()) out.add(info.getProfile().name());
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** The online players that match, shown at once while the archive is still being asked. */
    private void localMatches() {
        if (land) return;
        String lower = text.toLowerCase(Locale.ROOT);
        suggestions = lower.isEmpty() ? List.of() : online().stream().filter(n -> n.toLowerCase(Locale.ROOT).contains(lower))
                .sorted((a, b) -> Boolean.compare(!a.toLowerCase(Locale.ROOT).startsWith(lower), !b.toLowerCase(Locale.ROOT).startsWith(lower))).limit(rowsShown).map(n -> new Hit(n, null)).toList();
    }

    /** Waits for a short pause in typing, then asks the archive for the players whose ID matches. */
    @Override public void tick() {
        super.tick();
        if (!started) { started = true; if (land && LandCache.lands().isEmpty()) refresh(); }
        if (land || text.isBlank() || text.equals(requested) || System.currentTimeMillis() - editedAt < 250) return;
        requested = text; int run = ++generation; String asked = text;
        new ArchiveApi(PaleTaleClient.MARKET).search(ArchiveData.Kind.PLAYERS, asked, "", "", 0, "id", 1).whenComplete((page, failure) -> minecraft.execute(() -> {
            if (minecraft.screen != this || run != generation || failure != null) return;
            var merged = new ArrayList<Hit>(suggestions);
            for (var entry : page.entries()) {
                String id = entry.name().matches("[A-Za-z0-9_]{1,16}") ? entry.name() : entry.subtitle() != null && entry.subtitle().matches("[A-Za-z0-9_]{1,16}") ? entry.subtitle() : null;
                if (id != null && merged.stream().noneMatch(h -> h.name().equalsIgnoreCase(entry.name()) || h.name().equalsIgnoreCase(id))) merged.add(new Hit(id, entry.imagePath()));
            }
            suggestions = merged.stream().limit(rowsShown).toList();
        }));
    }

    private void complete() {
        if (cycle.isEmpty()) { cycle = suggestions; cycleAt = -1; }
        if (cycle.isEmpty()) return;
        cycleAt = (cycleAt + 1) % cycle.size();
        completing = true; typed.setValue(cycle.get(cycleAt).name()); completing = false;
    }

    private void pick(String name) {
        if (RadialSlots.bound((land ? "@land:" : "@player:") + name) == null) { note = land ? "领地名不能为空，不能含逗号或 @。" : "玩家 ID 只能是字母、数字、下划线，最长 16 位。"; return; }
        done = true; picked.accept(name);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (!land && event.key() == GLFW.GLFW_KEY_TAB) { complete(); return true; }
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) { pick(typed.getValue().strip()); return true; }
        return super.keyPressed(event);
    }

    /** The refresh button: a clock and the word. */
    private final class RefreshButton extends AbstractWidget {
        RefreshButton(int x, int y, int w, int h) { super(x, y, w, h, Component.literal("刷新")); }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), isHovered() ? MarketTheme.c(0xFFE2E8DE) : PAPER);
            g.renderOutline(getX(), getY(), getWidth(), getHeight(), LINE);
            g.renderItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CLOCK), getX() + 2, getY() + 1);
            g.drawString(font, refreshing ? "刷新中…" : "刷新", getX() + 21, getY() + 5, refreshing ? MUTED : INK, false);
        }
        @Override public void onClick(MouseButtonEvent event, boolean twice) { refresh(); }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { }
    }

    /** The rows: territories with their icons (scrolling with the wheel), or the players that match with their heads. A click takes one. */
    private final class Rows extends AbstractWidget {
        Rows(int x, int y, int w, int h) { super(x, y, w, h, Component.empty()); }
        private List<String> lands() { return LandCache.load(); }
        private int count() { return land ? lands().size() : suggestions.size(); }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
            g.renderOutline(getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2, LINE);
            int total = count();
            if (total == 0) { g.drawString(font, land ? (refreshing ? "正在读取领地…" : "还没有领地记录，点右上角的刷新") : text.isBlank() ? "输入玩家 ID，下面列出匹配的玩家" : "没有匹配的玩家", getX() + 6, getY() + 6, MUTED, false); return; }
            for (int r = 0; r < rowsShown && r + scroll < total; r++) {
                int at = r + scroll, y = getY() + r * ROW; boolean over = mouseX >= getX() && mouseX < getX() + getWidth() && mouseY >= y && mouseY < y + ROW;
                if (over || !land && at == cycleAt) g.fill(getX(), y, getX() + getWidth(), y + ROW, MarketTheme.c(0x553B6652));
                if (at > 0) g.hLine(getX() + 4, getX() + getWidth() - 5, y, LINE);
                if (land) {
                    String name = lands().get(at);
                    g.renderItem(LandCache.icon(name), getX() + 4, y + 2);
                    g.drawString(font, font.plainSubstrByWidth(name, getWidth() - 34), getX() + 26, y + 6, INK, false);
                } else {
                    var hit = suggestions.get(at); face(g, hit, getX() + 4, y + 2);
                    g.drawString(font, font.plainSubstrByWidth(hit.name(), getWidth() - 34), getX() + 26, y + 6, INK, false);
                }
            }
            if (total > rowsShown) {
                int track = getHeight(), thumb = Math.max(10, track * rowsShown / total), at = getY() + (track - thumb) * scroll / Math.max(1, total - rowsShown);
                g.fill(getX() + getWidth() - 3, getY(), getX() + getWidth() - 2, getY() + track, LINE); g.fill(getX() + getWidth() - 4, at, getX() + getWidth() - 1, at + thumb, ACCENT);
            }
        }
        /** A player's head: the skin the game holds for them if they are online, else the archive's picture, else their first letter. */
        private void face(GuiGraphics g, Hit hit, int x, int y) {
            var connection = minecraft.getConnection(); var info = connection == null ? null : connection.getPlayerInfo(hit.name());
            if (info != null) { net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, info.getSkin(), x, y, 16); return; }
            if (hit.image() != null && MarketImages.INSTANCE.draw(g, PaleTaleClient.MARKET, hit.image(), x, y, 16)) return;
            g.fill(x, y, x + 16, y + 16, MarketTheme.c(0xFFE1E5DB)); g.drawString(font, hit.name().substring(0, 1), x + 5, y + 4, MUTED, false);
        }
        @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
            if (count() <= rowsShown) return false;
            scroll = Math.max(0, Math.min(count() - rowsShown, scroll - (int) Math.signum(vertical)));
            return true;
        }
        @Override public void onClick(MouseButtonEvent event, boolean twice) {
            int at = (int) ((event.y() - getY()) / ROW) + scroll;
            if (at < 0 || at >= count()) return;
            pick(land ? lands().get(at) : suggestions.get(at).name());
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { }
    }

    @Override protected void drawContent(GuiGraphics g, int mouseX, int mouseY) {
        if (land) g.drawString(font, "或输入名称", cardX + PAD, cardY + listY() + rowsShown * ROW + 6, MUTED, false);
        else g.drawString(font, "玩家 ID（Tab 补全，回车绑定）", cardX + PAD, cardY + 22, MUTED, false);
        if (!note.isEmpty()) g.drawString(font, note, cardX + PAD, cardY + cardH - PAD - 32, MarketTheme.c(0xFFB3392F), false);
    }

    @Override public void onClose() { if (done) return; done = true; cancelled.run(); }
}
