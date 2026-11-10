package cn.paletale.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;

final class MarketTheme {
    private static final java.util.Map<Integer, Integer> DARK_COLORS = new java.util.concurrent.ConcurrentHashMap<>();
    /** A light-theme colour as it should be drawn now (itself in the light theme, its dark counterpart in the dark one). */
    static int c(int light) { return PaleTheme.dark() ? DARK_COLORS.computeIfAbsent(light, PaleTheme::map) : light; }
    static int PAPER, INK, MUTED, LINE, ACCENT;
    static { refresh(); }
    /** Re-reads the five base colours after the theme switch; everything else asks {@link #c} each time it draws. */
    static void refresh() { PAPER = c(0xFFF5F4F0); INK = c(0xFF343735); MUTED = c(0xFF747772); LINE = c(0xFFC5C7BF); ACCENT = c(0xFF3B6652); }
    private static final java.util.Map<String, Identifier> DARK_TEXTURES = new java.util.HashMap<>();
    /** The icon texture for the current theme: a lightness-flipped copy of the dark-on-light original in the dark theme. */
    static Identifier texture(String name) {
        var light = Identifier.fromNamespaceAndPath("paletale", "textures/gui/" + name + ".png");
        if (!PaleTheme.dark()) return light;
        return DARK_TEXTURES.computeIfAbsent(name, n -> {
            var client = Minecraft.getInstance();
            try (var in = client.getResourceManager().open(light)) {
                var image = com.mojang.blaze3d.platform.NativeImage.read(in);
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) { int p = image.getPixel(x, y); if ((p >>> 24) != 0) image.setPixel(x, y, PaleTheme.map(p)); }
                var dark = Identifier.fromNamespaceAndPath("paletale", "dark/" + n);
                client.getTextureManager().register(dark, new net.minecraft.client.renderer.texture.DynamicTexture(() -> "paletale dark " + n, image));
                return dark;
            } catch (Exception failed) { return light; }
        });
    }
    static void line(GuiGraphics g, int x, int y, int width) { g.hLine(x, x + width, y, LINE); }
    /** Placeholder for an empty search box, drawn without the shadow the built-in hint uses (it looks bold on the pale paper). */
    static void hint(GuiGraphics g, net.minecraft.client.gui.components.EditBox box, String hint) {
        if (!box.getValue().isEmpty() || box.isFocused()) return;
        var font = PaleFont.get();
        g.drawString(font, font.plainSubstrByWidth(hint, Math.max(1, box.getWidth() - 4)), box.getX() + (box.isBordered() ? 4 : 0), box.getY() + (box.isBordered() ? (box.getHeight() - 8) / 2 : 0), MUTED, false);
    }
    static void text(GuiGraphics g, String value, int x, int y, int width, int color) {
        var font = PaleFont.get();
        if (width <= 0) return;
        String display = font.width(value) > width ? width < font.width("…") ? "" : font.plainSubstrByWidth(value, width - font.width("…")) + "…" : value;
        g.drawString(font, display, x, y, color, false);
    }
    private static final java.util.Map<String, net.minecraft.world.item.ItemStack> TAG_ITEMS = new java.util.HashMap<>();
    /** The game item that stands for a category (the site uses the same ones), or null when the name is not a category. */
    static net.minecraft.world.item.ItemStack tagItem(String icon) {
        if (TAG_ITEMS.isEmpty()) {
            var items = new String[][]{ { "all" }, { "wholesale" }, { "retail" }, { "wine" }, { "dish" }, { "map_art" }, { "materials" }, { "buying" }, { "modeling" }, { "service" }, { "event" }, { "recruitment" }, { "other" } };
            net.minecraft.world.item.Item[] which = { net.minecraft.world.item.Items.EMERALD, net.minecraft.world.item.Items.COBBLESTONE, net.minecraft.world.item.Items.DIAMOND, net.minecraft.world.item.Items.POTION,
                    net.minecraft.world.item.Items.COOKED_BEEF, net.minecraft.world.item.Items.PAINTING, net.minecraft.world.item.Items.BRICKS, net.minecraft.world.item.Items.OAK_LOG,
                    net.minecraft.world.item.Items.ARMOR_STAND, net.minecraft.world.item.Items.CRAFTING_TABLE, net.minecraft.world.item.Items.FIREWORK_ROCKET, net.minecraft.world.item.Items.PLAYER_HEAD,
                    net.minecraft.world.item.Items.BARRIER };
            for (int i = 0; i < items.length; i++) TAG_ITEMS.put(items[i][0], new net.minecraft.world.item.ItemStack(which[i]));
        }
        return TAG_ITEMS.get(icon);
    }
    static void item(GuiGraphics g, net.minecraft.world.item.ItemStack stack, int x, int y, int size) {
        g.pose().pushMatrix(); g.pose().translate(x, y); g.pose().scale(size / 16f, size / 16f);
        g.renderItem(stack, 0, 0);
        g.pose().popMatrix();
    }
    private static net.minecraft.world.item.ItemStack compassStack;
    /** The game's compass item as a small icon (the item is 16 px; this draws it at {@code size}). */
    static void compass(GuiGraphics g, int x, int y, int size) {
        if (compassStack == null) compassStack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COMPASS);
        g.pose().pushMatrix(); g.pose().translate(x, y); g.pose().scale(size / 16f, size / 16f);
        g.renderItem(compassStack, 0, 0);
        g.pose().popMatrix();
    }
    static void asset(GuiGraphics g, String name, int x, int y, int width, int height, int sourceWidth, int sourceHeight) {
        g.blit(RenderPipelines.GUI_TEXTURED, texture(name),
                x, y, 0, 0, width, height, sourceWidth, sourceHeight, sourceWidth, sourceHeight);
    }
    /** The mark on something clickable that is not a button (a name, a link): a soft tint and an underline while the mouse is over it. */
    static void hoverMark(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0x2A3B6652);
        g.hLine(x - 1, x + width, y + height, ACCENT);
    }
    static boolean over(double mx, double my, int x, int y, int width, int height) { return mx >= x && mx < x + width && my >= y && my < y + height; }

    static final class Action extends Button {
        private final boolean selected;
        private final String icon;
        private String displayLabel;
        private MarketApi avatarApi; private String avatarPath;
        /** The account button at the top right: only the player's head once signed in ("登录" until then), keeping its right edge. */
        private final boolean account;
        private final int accountRight;
        /** The signed-in player's head path (replaced by the client tests, which cannot sign in). */
        static java.util.function.Supplier<String> accountAvatar = () -> ArchiveNavigation.avatarOf(PaleTaleClient.ACCOUNT.citizen());
        private boolean ownTip;
        Action(String label, int x, int y, int width, int height, boolean selected, String icon, OnPress action) {
            super(x, y, width, height, Component.literal(label), action, DEFAULT_NARRATION);
            this.selected = selected; this.icon = icon;
            this.account = "person".equals(icon) && label.equals(PaleTaleClient.ACCOUNT.label());
            this.accountRight = x + width;
        }
        private void syncAccount() {
            String label = PaleTaleClient.ACCOUNT.label();
            if (!getMessage().getString().equals(label)) setMessage(Component.literal(label));
            String path = accountAvatar.get();
            setAvatar(PaleTaleClient.MARKET, path);
            boolean head = path != null && !path.isEmpty();
            int target = head ? 24 : 40;
            if (getWidth() != target) { setWidth(target); setX(accountRight - target); }
            setDisplayLabel(head ? "" : label.equals("验证中…") ? "验证中" : "登录");
            if (head && !ownTip) { ownTip = true; setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(label + "\n点击验证 SIMMC 登录状态"))); }
        }
        /** Replaces the icon by a player head (drawn once it has loaded); null restores the icon. */
        void setAvatar(MarketApi api, String path) { avatarApi = api; avatarPath = path; }
        /** Short visual label with the full accessible button name retained. */
        void setDisplayLabel(String label) { displayLabel = label; }
        String displayLabel() { return displayLabel == null ? getMessage().getString() : displayLabel; }
        @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            int fill = selected ? MarketTheme.c(0xFFE1E5DB) : isHoveredOrFocused() && active ? MarketTheme.c(0xFFE2E8DE) : PAPER;
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), fill);
            if (selected || isFocused()) g.renderOutline(getX(), getY(), getWidth(), getHeight(), LINE);
            if (account) {
                syncAccount();
                if (avatarPath != null && !avatarPath.isEmpty()) {
                    int headX = getX() + (getWidth() - 14) / 2, headY = getY() + (getHeight() - 14) / 2;
                    if (!MarketImages.INSTANCE.draw(g, avatarApi, avatarPath, headX, headY, 14)) g.fill(headX, headY, headX + 14, headY + 14, MarketTheme.c(0xFFE1E5DB));
                    return;
                }
            }
            boolean head = avatarPath != null && MarketImages.INSTANCE.draw(g, avatarApi, avatarPath, getX() + 3, getY() + (getHeight() - 12) / 2, 12);
            if(!head && avatarPath!=null&&!avatarPath.isEmpty()){int headY=getY()+(getHeight()-12)/2;g.fill(getX()+3,headY,getX()+15,headY+12,MarketTheme.c(0xFFE1E5DB));text(g,getMessage().getString().isEmpty()?"?":getMessage().getString().substring(0,1),getX()+6,headY+2,8,MUTED);}
            boolean bare = getMessage().getString().isEmpty(); // an icon alone sits in the middle
            if (!head && "compass".equals(icon)) compass(g, bare ? getX() + (getWidth() - 12) / 2 : getX() + 3, getY() + (getHeight() - 12) / 2, 12);
            else if (!head && icon != null && tagItem(icon) != null) item(g, tagItem(icon), bare ? getX() + (getWidth() - 12) / 2 : getX() + 3, getY() + (getHeight() - 12) / 2, 12);
            else if (!head && icon != null) asset(g, icon, bare ? getX() + (getWidth() - 10) / 2 : getX() + 3, getY() + (getHeight() - 10) / 2, 10, 10, 24, 24);
            int offset = avatarPath != null && !avatarPath.isEmpty() ? 19 : icon == null ? 5 : 16;
            text(g, displayLabel(), getX() + offset, getY() + (getHeight() - 8) / 2, getWidth() - offset - 3, active ? INK : MUTED);
        }
    }
}
