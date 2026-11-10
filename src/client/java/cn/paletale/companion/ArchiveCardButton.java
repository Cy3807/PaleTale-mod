package cn.paletale.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import static cn.paletale.companion.MarketTheme.*;

class ArchiveCardButton extends Button {
    boolean showOnline;
    final ArchiveData.Entry entry;
    private final MarketApi api;
    ArchiveCardButton(ArchiveData.Entry entry, MarketApi api, int x, int y, int width, int height, OnPress action) {
        super(x, y, width, height, Component.literal("打开档案：" + entry.name()), action, DEFAULT_NARRATION);
        this.entry = entry; this.api = api;
    }
    @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), width = getWidth(), height = getHeight();
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1, isHoveredOrFocused() ? MarketTheme.c(0xFFECEFE8) : MarketTheme.c(0xFFFCFCFA));
        asset(g, "archive-frame", x, y, width, height, 240, 320);
        if (entry.kind().equals("items")) { renderItem(g, x, y, width, height); return; }
        boolean compact = height < 84;
        int size = Math.max(8, Math.min(width - 14, height - (compact ? 33 : 52)));
        if (!entry.kind().equals("items")) size = Math.min(size, 64);
        int imageX = x + (width - size) / 2, imageY = y + 7;
        // List icons are always flat 2D; 3D models appear only in the hover preview and the detail page.
        // The resource pack's own flat icon wins: nothing to download, cache or flash. Only entries the pack does not know fall back to the site's cover.
        if (!ArchiveModels.INSTANCE.draw(g, entry, imageX, imageY, size) && !MarketImages.INSTANCE.draw(g, api, entry.imagePath(), imageX, imageY, size) && (entry.id()==0&&entry.kind().equals("players") || !MarketImages.INSTANCE.loading(api, entry.imagePath()))) {
            String initial = entry.name().isBlank() ? "档" : new String(Character.toChars(entry.name().codePointAt(0)));
            g.fill(imageX, imageY, imageX + size, imageY + size, MarketTheme.c(0xFFF0F1EA));
            text(g, initial, imageX + size / 2 - 4, imageY + size / 2 - 4, 12, MUTED);
        }
        String counts = entry.views() < 0 ? "" : stats(entry.views(), entry.favorites(), width - 16 - PaleFont.get().width(entry.badge()) - 6);
        int countsWidth = counts.isEmpty() ? 0 : PaleFont.get().width(counts);
        if (!compact) {
            text(g, entry.badge(), x + 8, y + height - 39, width - 16 - countsWidth - 4, entry.hasModel() ? MarketTheme.c(0xFF527286) : MUTED);
            if (countsWidth > 0) g.drawString(PaleFont.get(), counts, x + width - 8 - countsWidth, y + height - 39, MUTED, false);
        }
        int statusOffset=showOnline?7:0;if(showOnline){var connection=Minecraft.getInstance().getConnection();int color=LocalRoster.online(entry.name())?MarketTheme.c(0xFF278B50):MarketTheme.c(0xFFB43D3D);g.fill(x+8,y+height-(compact?22:23),x+11,y+height-(compact?19:20),color);}text(g, entry.name(), x + 8+statusOffset, y + height - (compact ? 24 : 25), width - 16-statusOffset, INK);
        int lineY = y + height - 12;
        if (!entry.kind().equals("items")) text(g, entry.subtitle(), x + 8, lineY, width - 16, MUTED);
        else {
            var owner = entry.owner();
            boolean avatar = owner != null && MarketImages.INSTANCE.draw(g, api, owner.avatarPath(), x + 8, lineY - 1, 10);
            text(g, owner == null ? entry.category().equals("fish") ? entry.facts() : "拥有者未登记" : owner.name(), x + 8 + (avatar ? 13 : 0), lineY, width - 16 - (avatar ? 13 : 0), entry.category().equals("fish") && entry.facts().equals("有渔获资料") ? MarketTheme.c(0xFF2F6B44) : MUTED);
        }
    }
    /** Only items need an extra metadata line. The original player/org rendering path above stays intact. */
    private void renderItem(GuiGraphics g, int x, int y, int width, int height) {
        boolean compact = height < 84, miniature = height < 64;
        if (!miniature) {
            int size = Math.max(10, Math.min(width - 14, height - (compact ? 52 : 65)));
            int imageX = x + (width - size) / 2, imageY = y + 7;
            if (!ArchiveModels.INSTANCE.draw(g, entry, imageX, imageY, size) && !MarketImages.INSTANCE.draw(g, api, entry.imagePath(), imageX, imageY, size) && !MarketImages.INSTANCE.loading(api, entry.imagePath())) {
                g.fill(imageX, imageY, imageX + size, imageY + size, MarketTheme.c(0xFFF0F1EA));
                text(g, entry.name().isBlank() ? "档" : new String(Character.toChars(entry.name().codePointAt(0))), imageX + size / 2 - 4, imageY + size / 2 - 4, 12, MUTED);
            }
            String counts = entry.views() < 0 ? "" : stats(entry.views(), entry.favorites(), width - 16 - PaleFont.get().width(entry.badge()) - 6);
            int countsWidth = PaleFont.get().width(counts);
            if (!compact) {
                text(g, entry.badge(), x + 8, y + height - 50, width - 16 - countsWidth - 4, entry.hasModel() ? MarketTheme.c(0xFF527286) : MUTED);
                if (countsWidth > 0) g.drawString(PaleFont.get(), counts, x + width - 8 - countsWidth, y + height - 50, MUTED, false);
            }
            String metadata = itemMetadata(); boolean wine = entry.category().equals("wine");
            if (wine) EffectIcons.draw(g, entry.effect().isBlank() ? "unknown" : entry.effect(), x + 8, y + height - 38, 10);
            if (entry.category().equals("map_art")) MapSizeColor.draw(g, metadata, x + 8, y + height - 37, width - 16, MUTED);
            else text(g, metadata, x + 8 + (wine ? 13 : 0), y + height - 37, width - 16 - (wine ? 13 : 0), MUTED);
        }
        int statusOffset=showOnline?7:0;if(showOnline){var connection=Minecraft.getInstance().getConnection();int color=LocalRoster.online(entry.name())?MarketTheme.c(0xFF278B50):MarketTheme.c(0xFFB43D3D);g.fill(x+8,y+height-(compact?22:23),x+11,y+height-(compact?19:20),color);}text(g, entry.name(), x + 8+statusOffset, y + height - (compact ? 24 : 25), width - 16-statusOffset, INK);
        var owner = entry.owner();int lineY = y + height - 12;
        boolean avatar = owner != null && MarketImages.INSTANCE.draw(g, api, owner.avatarPath(), x + 8, lineY - 1, 10);
        text(g, owner == null ? entry.category().equals("fish") ? entry.facts() : "拥有者未登记" : owner.name(), x + 8 + (avatar ? 13 : 0), lineY, width - 16 - (avatar ? 13 : 0), entry.category().equals("fish") && entry.facts().equals("有渔获资料") ? MarketTheme.c(0xFF2F6B44) : MUTED);
    }
    private String itemMetadata() {
        if (entry.category().equals("fish")) return entry.subtitle();
        if (entry.category().equals("map_art")) return entry.facts();
        return entry.category().equals("wine") ? entry.effectText().isBlank() ? "效果未知" : entry.effectText() : "";
    }
    /** "浏览 N · ★ N", shortened to "★ N" when the card is narrow. */
    static String stats(int views, int favorites, int room) {
        var font = PaleFont.get();
        // ◉ views, ★ favourites: short enough for the narrow grid cards.
        String full = "◉ " + views + "  ★ " + favorites, tight = "◉" + views + " ★" + favorites;
        return font.width(full) <= room ? full : font.width(tight) <= room ? tight : font.width("◉" + views) <= room ? "◉" + views : "";
    }
    /** 3D entries show the native model (list: still, hover/detail: rotating); 2D entries a still flat picture. */
    static boolean picture(GuiGraphics g, ArchiveData.Entry entry, int x, int y, int size, float angle, MarketApi api) {
        if (entry.hasModel() && ArchiveModels.INSTANCE.drawModel(g, entry, x, y, size, angle)) return true;
        return ArchiveModels.INSTANCE.draw(g, entry, x, y, size) || MarketImages.INSTANCE.draw(g, api, entry.imagePath(), x, y, size);
    }
    void preview(GuiGraphics g, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (!isHovered() && !(entry.kind().equals("items") && isFocused())) return;
        var font = PaleFont.get();
        int width = Math.min(206, screenWidth - 16), height = Math.min(156, screenHeight - 16);
        int x = Math.max(8, Math.min((isHovered() ? mouseX : getX() + getWidth()) + 12, screenWidth - width - 8));
        int y = Math.max(8, Math.min((isHovered() ? mouseY : getY()) + 12, screenHeight - height - 8));
        g.nextStratum();
        g.fill(x, y, x + width, y + height, MarketTheme.c(0xFFFAFAF6)); g.renderOutline(x, y, width, height, LINE);
        int size = Math.min(60, height / 2 - 8);
        // Hover shows the rotating native 3D model when the loaded pack has it; otherwise the flat fallback.
        float angle = .6f + (System.nanoTime() / 1_000_000 % 8000) / 8000f * (float) Math.PI * 2;
        picture(g, entry, x + 8, y + 8, size, angle, api);
        text(g, entry.name(), x + size + 16, y + 11, width - size - 24, INK);
        text(g, entry.badge(), x + size + 16, y + 27, width - size - 24, MUTED);
        var owner = entry.owner();
        boolean avatar = owner != null && MarketImages.INSTANCE.draw(g, api, owner.avatarPath(), x + size + 16, y + 42, 10);
        text(g, entry.subtitle(), x + size + 16 + (avatar ? 13 : 0), y + 43, width - size - 24 - (avatar ? 13 : 0), MUTED);
        int textY = y + size + 17;
        if (entry.kind().equals("items") && !itemMetadata().isBlank()) { text(g, itemMetadata(), x + 8, textY, width - 16, MUTED); textY += 13; }
        var lines = font.split(Component.literal(entry.description().isBlank() ? "暂无简介，点击打开独立档案页。" : entry.description()), width - 16);
        for (var line : lines) {
            if (textY > y + height - 28) break;
            g.drawString(font, line, x + 8, textY, INK, false); textY += 12;
        }
    }
}
