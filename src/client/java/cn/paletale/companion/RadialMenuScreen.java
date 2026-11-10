package cn.paletale.companion;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Edits the command wheel: click a slot to change it. (The wheel itself is {@link RadialWheel}, a HUD layer shown while the key is held.) */
final class RadialMenuScreen extends Screen {
    private static List<RadialSlots.Slot> store;
    private final Screen parent; private int hover = -1; private final long openedAt = System.currentTimeMillis();

    RadialMenuScreen(Screen parent) { super(Component.literal("快捷轮盘")); this.parent = parent; }

    static synchronized List<RadialSlots.Slot> slots() {
        if (store == null) store = RadialSlots.load(ModPaths.readable("radial.txt"));
        return store;
    }
    static synchronized void set(int index, RadialSlots.Slot slot) {
        var next = new ArrayList<>(slots()); next.set(index, slot); store = List.copyOf(next);
        RadialSlots.save(ModPaths.config().resolve("radial.txt"), store);
    }

    @Override protected void init() { PaleFont.apply(this); RadialArt.reset(); }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (hover < 0) return super.mouseClicked(event, twice);
        minecraft.setScreen(new RadialEditScreen(this, hover));
        return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(event);
    }
    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) { g.fill(0, 0, width, height, 0x99000000); }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        int cx = width / 2, cy = height / 2; var font = PaleFont.get();
        hover = Math.hypot(mouseX - cx, mouseY - cy) < RadialArt.INNER ? -1 : RadialArt.sectorAt(mouseX - cx, mouseY - cy);
        double fade = Math.min(1, (System.currentTimeMillis() - openedAt) / 140.0);
        RadialArt.draw(g, font, cx, cy, RadialSlots.COUNT, RadialArt.slotIcons(slots()), hover, fade, hover >= 0 ? "编辑：" + (slots().get(hover).empty() ? "空槽" : slots().get(hover).label()) : "点击槽位编辑");
        String hint = "按住 " + PaleTaleClient.RADIAL.getTranslatedKeyMessage().getString() + " 呼出轮盘，松开发送 · Esc 返回";
        g.drawString(font, hint, cx - font.width(hint) / 2, Math.min(height - 14, cy + RadialArt.EXTENT + 10), 0xFFEEECE4, true);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
