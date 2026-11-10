package cn.paletale.companion;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import static cn.paletale.companion.MarketTheme.*;

/**
 * The editor of the quick wheel, in three columns under a row of slot tabs: name and command on the left, a list of ready-made presets (and the two bindings) in the middle, the
 * icons on the right. Switching to another tab keeps the edit of the slot being left when it is valid.
 */
final class RadialEditScreen extends WindowScreen {
    private static final int CELL = 24, ROW_H = 20, GAP = 14;
    private static final List<ItemStack> STACKS = new ArrayList<>();
    private final Screen parent; private final int index; private final Draft draft;
    private EditBox label, command;
    /** The chosen icon (an item id, or ""): picked from the grid, never typed. */
    private String iconId = "";
    private String note = "";
    private int leftX, midX, rightX, colW, yTop, yBody, yButtons;

    /** What the fields held when the player left for a picker, so coming back does not lose it. */
    record Draft(String label, String command, String icon) {}
    RadialEditScreen(Screen parent, int index) { this(parent, index, null); }
    RadialEditScreen(Screen parent, int index, Draft draft) { super(Component.literal("编辑快捷轮盘")); this.draft = draft; this.parent = parent; this.index = index; }

    private static ItemStack stack(String id) {
        var key = Identifier.tryParse(RadialSlots.icon(id));
        return key == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(key).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    @Override protected void init() {
        fitWindow();
        if (STACKS.isEmpty()) for (String id : RadialSlots.ICONS) STACKS.add(stack(id));
        var slot = draft != null ? new RadialSlots.Slot(draft.label(), draft.command(), draft.icon()) : RadialMenuScreen.slots().get(index);
        iconId = slot.icon();
        int margin = 16, total = width - 2 * margin; colW = (total - 2 * GAP) / 3;
        leftX = margin; midX = leftX + colW + GAP; rightX = midX + colW + GAP;
        // The slot tabs run along the top: the number and the name of each slot.
        yTop = 14; int tabW = (total - 7 * 3) / RadialSlots.COUNT; var slots = RadialMenuScreen.slots();
        var icons = RadialArt.slotIcons(slots);
        for (int i = 0; i < RadialSlots.COUNT; i++) addRenderableWidget(new SlotTab(margin + i * (tabW + 3), yTop, tabW, i, slots.get(i), icons.get(i)));
        yBody = yTop + 30;
        label = box(leftX, yBody, colW, "例：我的领地", slot.label(), RadialSlots.LABEL_MAX);
        command = box(leftX, yBody + 48, colW, "例：l spawn 白渚", slot.command(), RadialSlots.COMMAND_MAX);
        yButtons = height - 40;
        int bgap = 6, bw = (colW - 2 * bgap) / 3;
        addRenderableWidget(new Action("保存", leftX, yButtons, bw, 18, true, null, b -> save()));
        addRenderableWidget(new Action("清空", leftX + bw + bgap, yButtons, bw, 18, false, null, b -> { RadialMenuScreen.set(index, RadialSlots.EMPTY); onClose(); }));
        addRenderableWidget(new Action("取消", leftX + 2 * (bw + bgap), yButtons, bw, 18, false, null, b -> onClose()));
        int listH = Math.max(ROW_H * 3, height - 40 - (yBody + 16));
        addRenderableWidget(new PresetList(midX, yBody + 16, colW, listH));
        // As many columns as fit, with the cells shrinking (never below the 16 px of an item) until all the icons are in view.
        int avail = height - 40 - (yBody + 16), cell = CELL, columns = 3, rows = 0;
        for (int size : new int[]{ 24, 22, 20, 18 }) { cell = size; columns = Math.max(3, colW / size); rows = (RadialSlots.ICONS.size() + columns - 1) / columns; if (rows * size <= avail) break; }
        addRenderableWidget(new IconGrid(rightX, yBody + 16, columns, rows, cell));
        setFocused(command); command.setFocused(true);
    }

    private EditBox box(int x, int captionY, int w, String hint, String value, int max) {
        EditBox box = addRenderableWidget(new PaleInput(font, x, captionY + 12, w, 18, Component.literal(hint)));
        box.setMaxLength(max); box.setHint(Component.literal(hint)); box.setValue(value);
        return box;
    }

    /** Moves to another slot, keeping what was typed here when it is a valid slot. */
    private void switchTo(int next) {
        if (next == index) return;
        if (RadialSlots.command(command.getValue()) != null) RadialMenuScreen.set(index, RadialSlots.slot(label.getValue(), command.getValue(), iconId));
        minecraft.setScreen(new RadialEditScreen(parent, next));
    }

    /** Binds the slot to one territory or one player: the wheel then opens a small ring of what to do with it. */
    private void bind(boolean land) {
        var current = new Draft(label.getValue(), command.getValue(), iconId);
        minecraft.setScreen(new BindPickScreen(land,
                name -> minecraft.setScreen(new RadialEditScreen(parent, index, new Draft(name.length() > RadialSlots.LABEL_MAX ? name.substring(0, RadialSlots.LABEL_MAX) : name, (land ? "@land:" : "@player:") + name, ""))),
                () -> minecraft.setScreen(new RadialEditScreen(parent, index, current))));
    }

    private void save() {
        if (RadialSlots.command(command.getValue()) == null) { note = "指令无效：不能为空、不含换行，动作写成 @名称。"; return; }
        RadialMenuScreen.set(index, RadialSlots.slot(label.getValue(), command.getValue(), iconId));
        onClose();
    }

    /** One slot tab: its number and, after it, the slot's own icon (an item, or the first letter of its name, or a plus when empty). */
    private final class SlotTab extends AbstractWidget {
        private final int slot; private final RadialArt.Icon icon;
        SlotTab(int x, int y, int w, int slot, RadialSlots.Slot content, RadialArt.Icon icon) {
            super(x, y, w, 20, Component.literal((slot + 1) + " " + (content.empty() ? "空" : content.label()))); this.slot = slot; this.icon = icon;
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(content.empty() ? "空槽" : content.label())));
        }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
            boolean chosen = slot == index;
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), chosen ? MarketTheme.c(0xFFE1E5DB) : isHovered() ? MarketTheme.c(0xFFE2E8DE) : PAPER);
            if (chosen) g.renderOutline(getX(), getY(), getWidth(), getHeight(), LINE);
            g.drawString(font, String.valueOf(slot + 1), getX() + 6, getY() + 6, chosen ? INK : MUTED, false);
            int ix = getX() + 6 + font.width(String.valueOf(slot + 1)) + 6, iy = getY() + 2;
            if (!icon.stack().isEmpty()) g.renderItem(icon.stack(), ix, iy);
            else if (!icon.glyph().isEmpty()) g.drawString(font, icon.glyph(), ix + 4, iy + 4, INK, false);
            else g.drawString(font, "+", ix + 5, iy + 4, MUTED, false);
        }
        @Override public void onClick(MouseButtonEvent event, boolean twice) { switchTo(slot); }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { }
    }

    /** The presets as a list, one per row with its icon and what it does; the last two rows pick a territory or a player to bind. Scrolls with the wheel. */
    private final class PresetList extends AbstractWidget {
        private int scroll;
        PresetList(int x, int y, int w, int h) { super(x, y, w, h, Component.empty()); }
        private int rows() { return RadialSlots.PRESETS.size() + 2; }
        private int visible() { return Math.max(1, getHeight() / ROW_H); }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
            g.renderOutline(getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2, LINE);
            for (int r = 0; r < visible() && r + scroll < rows(); r++) {
                int at = r + scroll, y = getY() + r * ROW_H; boolean over = mouseX >= getX() && mouseX < getX() + getWidth() && mouseY >= y && mouseY < y + ROW_H;
                if (over) g.fill(getX(), y, getX() + getWidth(), y + ROW_H, MarketTheme.c(0xFFE2E8DE));
                if (at > 0) g.hLine(getX() + 4, getX() + getWidth() - 5, y, LINE);
                boolean bind = at >= RadialSlots.PRESETS.size();
                String name = bind ? (at == RadialSlots.PRESETS.size() ? "绑定领地…" : "绑定玩家…") : RadialSlots.PRESETS.get(at).label();
                var icon = bind ? (at == RadialSlots.PRESETS.size() ? new ItemStack(net.minecraft.world.item.Items.CAMPFIRE) : new ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD)) : stack(RadialSlots.PRESETS.get(at).icon());
                if (!icon.isEmpty()) g.renderItem(icon, getX() + 3, y + 2);
                g.drawString(font, name, getX() + 24, y + 6, INK, false);
            }
            if (rows() > visible()) {
                int track = getHeight(), thumb = Math.max(10, track * visible() / rows()), at = getY() + (track - thumb) * scroll / Math.max(1, rows() - visible());
                g.fill(getX() + getWidth() - 3, getY(), getX() + getWidth() - 2, getY() + track, LINE); g.fill(getX() + getWidth() - 4, at, getX() + getWidth() - 1, at + thumb, ACCENT);
            }
        }
        @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
            if (rows() <= visible()) return false;
            scroll = Math.max(0, Math.min(rows() - visible(), scroll - (int) Math.signum(vertical)));
            return true;
        }
        @Override public void onClick(MouseButtonEvent event, boolean twice) {
            int at = (int) ((event.y() - getY()) / ROW_H) + scroll;
            if (at < 0 || at >= rows()) return;
            if (at < RadialSlots.PRESETS.size()) { var preset = RadialSlots.PRESETS.get(at); label.setValue(preset.label()); command.setValue(preset.command()); iconId = preset.icon(); }
            else bind(at == RadialSlots.PRESETS.size());
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { }
    }

    /** The grid of vanilla items; clicking one chooses it, clicking the chosen one takes it off. */
    private final class IconGrid extends AbstractWidget {
        private final int columns, cell;
        IconGrid(int x, int y, int columns, int rows, int cell) { super(x, y, columns * cell, rows * cell, Component.empty()); this.columns = columns; this.cell = cell; }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
            String chosen = RadialSlots.icon(iconId);
            for (int i = 0; i < STACKS.size(); i++) {
                int cx = getX() + i % columns * cell, cy = getY() + i / columns * cell; boolean on = ("minecraft:" + RadialSlots.ICONS.get(i)).equals(chosen), over = mouseX >= cx && mouseX < cx + cell && mouseY >= cy && mouseY < cy + cell;
                if (on) g.fill(cx, cy, cx + cell - 1, cy + cell - 1, MarketTheme.c(0x553B6652));
                if (on || over) g.renderOutline(cx, cy, cell - 1, cell - 1, on ? ACCENT : LINE);
                if (!STACKS.get(i).isEmpty()) g.renderItem(STACKS.get(i), cx + (cell - 16) / 2, cy + (cell - 16) / 2);
            }
        }
        @Override public void onClick(MouseButtonEvent event, boolean twice) {
            int col = (int) ((event.x() - getX()) / cell), row = (int) ((event.y() - getY()) / cell), i = row * columns + col;
            if (col >= 0 && col < columns && row >= 0 && i >= 0 && i < RadialSlots.ICONS.size()) { String pick = "minecraft:" + RadialSlots.ICONS.get(i); iconId = pick.equals(RadialSlots.icon(iconId)) ? "" : pick; }
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { }
    }

    @Override protected void windowBackground(GuiGraphics g, int x, int y, float delta) { g.fill(0, 0, width, height, PAPER); }
    @Override protected void renderWindow(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderWindow(g, mouseX, mouseY, delta);
        g.drawString(font, font.plainSubstrByWidth("名称（最多 " + RadialSlots.LABEL_MAX + " 字）", colW), leftX, yBody, MUTED, false);
        g.drawString(font, font.plainSubstrByWidth("指令（不带斜杠）或动作", colW), leftX, yBody + 48, MUTED, false);
        g.drawString(font, font.plainSubstrByWidth("预设（点一下填入）", colW), midX, yBody, MUTED, false);
        g.drawString(font, font.plainSubstrByWidth("图标（再点一次取消）", colW), rightX, yBody, MUTED, false);
        g.fill(midX - GAP / 2, yBody, midX - GAP / 2 + 1, height - 24, LINE); g.fill(rightX - GAP / 2, yBody, rightX - GAP / 2 + 1, height - 24, LINE);
        if (!note.isEmpty()) text(g, note, leftX, yButtons - 16, colW, MarketTheme.c(0xFF9E4942));
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
