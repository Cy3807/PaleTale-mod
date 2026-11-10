package cn.paletale.companion;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import java.nio.charset.StandardCharsets;
import static cn.paletale.companion.MarketTheme.*;

/** Register the website account from inside SIMMC: the Mojang session proof is the identity check, the player only chooses a password. */
final class RegisterScreen extends WindowScreen {
    private final Screen parent;
    private EditBox password, confirm;
    private String note = "";
    private String pendingPassword = "", pendingConfirm = "";

    RegisterScreen(Screen parent) { super(Component.literal("注册网站账号")); this.parent = parent; }

    @Override protected void init() {
        fitWindow();
        int w = Math.min(320, width - 40), x = (width - w) / 2, y = height / 2 - 34;
        password = box(x, y, w, "设置密码（至少 8 位）", pendingPassword);
        confirm = box(x, y + 28, w, "再输入一次密码", pendingConfirm);
        boolean busy = PaleTaleClient.ACCOUNT.busy();
        var register = addRenderableWidget(new Action(busy ? "正在注册…" : "注册并登录", x, y + 58, w, 19, true, null, b -> submit()));
        register.active = !busy;
        int half = (w - 4) / 2;
        addRenderableWidget(new Action("取消", x, y + 82, half, 18, false, null, b -> onClose()));
        addRenderableWidget(new Action("改到网站注册", x + half + 4, y + 82, w - half - 4, 18, false, null,
                b -> ConfirmLinkScreen.confirmLink(this, PaleTaleClient.ACCOUNT.registerUri()).onPress(b)));
        setInitialFocus(password);
    }
    private EditBox box(int x, int y, int w, String hint, String value) {
        EditBox box = addRenderableWidget(new PaleInput(font, x + 9, y, w - 18, 20, Component.literal(hint)));
        box.setMaxLength(60); box.setHint(Component.literal(hint)); box.setBordered(false); box.setTextShadow(false);
        box.setTextColor(INK); box.setTextColorUneditable(MUTED);
        // Typed characters are shown as dots.
        box.addFormatter((text, offset) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
        box.setValue(value);
        box.setResponder(text -> { if (box == password) pendingPassword = text; else pendingConfirm = text; });
        return box;
    }
    private void submit() {
        String value = password.getValue();
        if (value.length() < 8) { note = "密码至少 8 位。"; return; }
        if (value.getBytes(StandardCharsets.UTF_8).length > 72) { note = "密码不能超过 72 字节（中文通常占 3 字节）。"; return; }
        if (!value.equals(confirm.getValue())) { note = "两次输入的密码不一致。"; return; }
        note = "";
        PaleTaleClient.ACCOUNT.register(minecraft, value);
    }
    @Override public void tick() {
        if (PaleTaleClient.ACCOUNT.loggedIn()) { pendingPassword = ""; pendingConfirm = ""; minecraft.setScreen(parent); }
    }
    @Override protected void windowBackground(GuiGraphics g, int x, int y, float delta) { g.fill(0, 0, width, height, PAPER); }
    @Override protected void renderWindow(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderWindow(g, mouseX, mouseY, delta);
        int w = Math.min(320, width - 40), x = (width - w) / 2, y = height / 2 - 34;
        text(g, "注册网站账号", x, y - 62, w, INK);
        for (EditBox field : new EditBox[]{ password, confirm }) if (field != null) g.renderOutline(field.getX() - 4, field.getY(), field.getWidth() + 8, 20, LINE);
        var lines = font.split(Component.literal("检测到当前正版账号还没有网站账号。设置一个登录密码即可完成注册，无需再去网站验证；密码只用于网站登录，不会显示或保存明文。"), w);
        for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), x, y - 46 + i * 12, MUTED, false);
        String status = note.isEmpty() ? PaleTaleClient.ACCOUNT.message() : note;
        if (!status.isEmpty()) text(g, status, x, y + 106, w, MarketTheme.c(0xFF9E4942));
    }
    /** Closing is allowed, but the screen comes back shortly while the account stays unregistered. */
    @Override public void onClose() { PaleTaleClient.registerReopenAt = System.currentTimeMillis() + 4000; minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
