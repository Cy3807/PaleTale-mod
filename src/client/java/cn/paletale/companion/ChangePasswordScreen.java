package cn.paletale.companion;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import java.nio.charset.StandardCharsets;
import static cn.paletale.companion.MarketTheme.*;

/** Change the website password from inside the game: the current password is checked by the site, nothing is stored or shown in clear. */
final class ChangePasswordScreen extends WindowScreen {
    private final Screen parent;
    private final FavoriteApi api;
    private EditBox current, next, confirm;
    private String note = "", currentText = "", nextText = "", confirmText = "";
    private boolean busy, done;

    ChangePasswordScreen(Screen parent, FavoriteApi api) { super(Component.literal("修改密码")); this.parent = parent; this.api = api; }

    @Override protected void init() {
        fitWindow();
        int w = Math.min(320, width - 40), x = (width - w) / 2, y = height / 2 - 40;
        current = box(x, y, w, "当前密码", currentText, 0);
        next = box(x, y + 28, w, "新密码（至少 8 位）", nextText, 1);
        confirm = box(x, y + 56, w, "再输入一次新密码", confirmText, 2);
        var save = addRenderableWidget(new Action(busy ? "正在提交…" : done ? "已完成" : "修改密码", x, y + 86, w, 19, true, null, b -> submit()));
        save.active = !busy && !done;
        addRenderableWidget(new Action(done ? "返回" : "取消", x, y + 110, w, 18, false, null, b -> onClose()));
        setInitialFocus(current);
    }
    private EditBox box(int x, int y, int w, String hint, String value, int slot) {
        EditBox box = addRenderableWidget(new PaleInput(font, x + 4, y, w - 8, 20, Component.literal(hint)));
        box.setMaxLength(60); box.setHint(Component.literal(hint)); box.setBordered(true); box.setTextShadow(false);
        box.setTextColor(INK); box.setTextColorUneditable(MUTED);
        // Typed characters are shown as dots.
        box.addFormatter((text, offset) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
        box.setValue(value);
        box.setResponder(text -> { if (slot == 0) currentText = text; else if (slot == 1) nextText = text; else confirmText = text; });
        return box;
    }
    private void submit() {
        String token = PaleTaleClient.ACCOUNT.token();
        if (token == null) { note = "请先登录 PaleTale 账号。"; return; }
        if (currentText.isEmpty()) { note = "请输入当前密码。"; return; }
        if (nextText.length() < 8) { note = "新密码至少 8 位。"; return; }
        if (nextText.getBytes(StandardCharsets.UTF_8).length > 72) { note = "新密码不能超过 72 字节（中文通常占 3 字节）。"; return; }
        if (!nextText.equals(confirmText)) { note = "两次输入的新密码不一致。"; return; }
        busy = true; note = "";
        rebuildWidgets();
        api.changePassword(token, currentText, nextText).whenComplete((message, failure) -> minecraft.execute(() -> {
            busy = false;
            if (failure == null) { done = true; note = message; currentText = ""; nextText = ""; confirmText = ""; }
            else note = failure.getCause() instanceof IllegalStateException cause ? cause.getMessage() : "修改失败，请稍后重试。";
            if (minecraft.screen == this) rebuildWidgets();
        }));
    }
    @Override protected void windowBackground(GuiGraphics g, int x, int y, float delta) { g.fill(0, 0, width, height, PAPER); }
    @Override protected void renderWindow(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderWindow(g, mouseX, mouseY, delta);
        int w = Math.min(320, width - 40), x = (width - w) / 2, y = height / 2 - 40;
        text(g, "修改网站密码", x, y - 44, w, INK);
        var lines = font.split(Component.literal("需要先输入当前密码；新密码至少 8 位。密码只用于网站登录，不会显示或保存明文。"), w);
        for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), x, y - 28 + i * 12, MUTED, false);
        if (!note.isEmpty()) text(g, note, x, y + 134, w, done ? MUTED : MarketTheme.c(0xFF9E4942));
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
