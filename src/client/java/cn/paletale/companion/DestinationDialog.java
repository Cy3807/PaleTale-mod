package cn.paletale.companion;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import static cn.paletale.companion.MarketTheme.*;

/** Small pop-up that saves the place the player stands on as a destination, with a name and an optional note. In the main world it also reads the territory silently (see {@link LandHere}). */
final class DestinationDialog extends ChatDialog {
    private final int px, py, pz; private final String world, server;
    private EditBox name, note; private String typedName = "", typedNote = "", error = "";
    private final boolean landRelevant; private boolean reading; private String land = ""; private Action saveButton;

    DestinationDialog(Screen parent, int x, int y, int z, String world, String server) {
        super(parent, "记录当前位置"); this.px = x; this.py = y; this.pz = z; this.world = world; this.server = server;
        landRelevant = CustomDestinationStore.recordsLand(world, server);
        if (landRelevant) reading = LandHere.start(result -> { reading = false; land = result == null ? "" : result; if (saveButton != null) saveButton.active = true; });
    }

    @Override protected int contentHeight() { return landRelevant ? 118 : 106; }

    @Override protected void layout() {
        int gap = 6, bw = (CARD_W - 2 * PAD - gap) / 2, y = cardY + cardH - PAD - 18, top = landRelevant ? 48 : 36;
        name = input(top, "名称（必填）", typedName, CustomDestinations.NAME_MAX);
        name.setResponder(t -> { typedName = t; error = ""; });
        note = input(top + 28, "备注（可选）", typedNote, CustomDestinations.NOTE_MAX);
        note.setResponder(t -> typedNote = t);
        focus(name);
        saveButton = addRenderableWidget(new Action("保存", cardX + PAD, y, bw, 18, true, null, b -> save()));
        saveButton.active = !reading;
        addRenderableWidget(new Action("取消", cardX + PAD + bw + gap, y, bw, 18, false, null, b -> onClose()));
    }

    private void save() {
        if (reading) return;
        if (typedName.isBlank()) { error = "请先填写名称。"; return; }
        if (!CustomDestinationStore.add(typedName, typedNote, px, py, pz, world, server, land)) { error = "已保存 " + CustomDestinations.MAX + " 个，请先删除一些。"; return; }
        onClose();
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) { save(); return true; }
        return super.keyPressed(event);
    }

    @Override protected void drawContent(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, CustomDestinationStore.worldLabel(world, server) + " · " + px + " / " + py + " / " + pz, cardX + PAD, cardY + 22, MUTED, false);
        if (landRelevant) g.drawString(font, "领地：" + (reading ? "读取中…" : land.isEmpty() ? "未能读取" : land), cardX + PAD, cardY + 34, MUTED, false);
        if (!error.isEmpty()) g.drawString(font, error, cardX + PAD, cardY + contentHeight() - 2, MarketTheme.c(0xFFB3392F), false);
    }
}
