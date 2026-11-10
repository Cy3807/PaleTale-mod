package cn.paletale.companion;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import static cn.paletale.companion.MarketData.*;
import static cn.paletale.companion.MarketTheme.*;

final class MarketCardButton extends Button {
    private final Ad ad;
    private final MarketApi api;
    private final NavTargets.Target place;
    private final boolean navigable;
    private net.minecraft.client.gui.components.Tooltip placeTip, shownTip;
    private final net.minecraft.client.gui.components.Tooltip titleTip,summaryTip;
    /** Where the place was drawn last time (x, y, width), for clicks; null when it is not shown. */
    private int[] placeBox;
    MarketCardButton(Ad ad, MarketApi api, int x, int y, int width, int height, OnPress action) {
        super(x, y, width, height, Component.literal("查看广告：" + ad.title()), action, DEFAULT_NARRATION);
        this.ad = ad; this.api = api; this.place = NavTargets.ofAd(ad); this.navigable = place != null;
        titleTip=net.minecraft.client.gui.components.Tooltip.create(Component.literal(ad.title()));summaryTip=ad.summary().isBlank()?null:net.minecraft.client.gui.components.Tooltip.create(Component.literal(ad.summary()));
    }
    @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), width = getWidth(), height = getHeight();
        var font = PaleFont.get();
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1, isHoveredOrFocused() ? MarketTheme.c(0xFFECEFE8) : MarketTheme.c(0xFFFAFAF7));
        g.renderOutline(x, y, width, height, isHoveredOrFocused() ? MUTED : LINE);
        // The cover: a band across the top with the ad's picture in its middle.
        // The cover has the site's 16:10 shape; when the card is too short for that, the cover gives way first (the text keeps at least 60).
        int coverHeight = Math.max(20, Math.min(Math.round((width - 2) * 10f / 16), height - 60));
        g.fill(x + 1, y + 1, x + width - 1, y + coverHeight, MarketTheme.c(0xFFE8EBE2));
        boolean noCover = ad.coverPath() == null || ad.coverPath().isBlank();
        int head = Math.min(coverHeight - 1, width - 2);
        if (noCover ? !MarketImages.INSTANCE.draw(g, api, ad.author().avatarPath(), x + 1 + (width - 2 - head) / 2, y + 1, head)
                : !MarketImages.INSTANCE.drawFill(g, api, ad.coverPath(), x + 1, y + 1, width - 2, coverHeight - 1)) {
            // Until the cover has loaded (or when the ad has no picture): the title's first character on the band.
            String initial = ad.title().isBlank() ? "广" : new String(Character.toChars(ad.title().codePointAt(0)));
            text(g, initial, x + (width - font.width(initial)) / 2, y + 1 + (coverHeight - 10) / 2, width, MUTED);
        }
        g.hLine(x + 1, x + width - 2, y + coverHeight, LINE);
        // Top to bottom: name, summary, status and place, then the advertiser; a short card drops the lower rows first.
        int bodyX = x + 6, bodyWidth = width - 12, lineY = y + coverHeight + 5, room = y + height - 4;
        String status = stateLabel(ad.state());
        int statusWidth = font.width(status) + 6;
        float titleScale = 1.25f;
        int titleWidth = bodyWidth;
        g.pose().pushMatrix(); g.pose().translate(bodyX, lineY); g.pose().scale(titleScale, titleScale);
        text(g, ad.title(), 0, 0, (int) (titleWidth / titleScale), INK);
        g.pose().popMatrix();
        int titleY = lineY, summaryY = -1;
        lineY += 15;
        placeBox = null;
        if (lineY + 9 <= room && !ad.summary().isBlank()) { text(g, ad.summary(), bodyX, lineY, bodyWidth, ACCENT); summaryY = lineY; lineY += 12; }
        if (lineY + 9 <= room) {
            var located = NavTargets.firstLocated(ad);
            String place = located != null ? located.name() : ad.locations().isEmpty() ? "地点未填写" : ad.locations().getFirst().name();
            g.renderOutline(bodyX, lineY - 2, statusWidth, 13, LINE);
            text(g, status, bodyX + 3, lineY, statusWidth - 3, MUTED);
            int placeX = bodyX + statusWidth + 5, placeWidth = bodyWidth - (placeX - bodyX);
            boolean overPlace = navigable && mouseX >= placeX && mouseX < placeX + placeWidth && mouseY >= lineY - 2 && mouseY < lineY + 10;
            compass(g, placeX, lineY - 2, 11);
            text(g, place, placeX + 14, lineY, placeWidth - 14, navigable ? (overPlace ? MarketTheme.c(0xFF2F9A5B) : MarketTheme.c(0xFF5BB57E)) : MarketTheme.c(0xFFD0645C));
            if (navigable) placeBox = new int[]{ placeX, lineY - 2, placeWidth };
            lineY += 12;
        }
        if (lineY + 9 <= room) {
            boolean avatar = MarketImages.INSTANCE.draw(g, api, ad.author().avatarPath(), bodyX, lineY - 1, 11);
            String author = ad.author().mcName() + (ad.author().name().equals(ad.author().mcName()) ? "" : " · " + ad.author().name());
            text(g, author, bodyX + (avatar ? 14 : 0), lineY, bodyWidth - (avatar ? 14 : 0), INK);
        }
        net.minecraft.client.gui.components.Tooltip wanted = null;
        if (onPlace(mouseX, mouseY)) wanted = placeTip;
        else if (mouseX >= bodyX && mouseX < bodyX + titleWidth && mouseY >= titleY && mouseY < titleY + 12 && font.width(ad.title()) * titleScale > titleWidth) wanted = titleTip;
        else if (summaryY >= 0 && mouseY >= summaryY && mouseY < summaryY + 10 && font.width(ad.summary()) > bodyWidth) wanted = summaryTip;
        if (wanted != shownTip) { setTooltip(wanted); shownTip = wanted; }
    }
    /** Whether a point (window coordinates) is on the place of the card, which starts the navigation when clicked. */
    boolean onPlace(double mx, double my) { return placeBox != null && mx >= placeBox[0] && mx < placeBox[0] + placeBox[2] && my >= placeBox[1] && my < placeBox[1] + 13; }
    Ad ad() { return ad; }
    NavTargets.Target place() { return place; }
    /** The tooltip of the card, and the one while the pointer is on the place (which starts the navigation). */
    void setTips(net.minecraft.client.gui.components.Tooltip card, net.minecraft.client.gui.components.Tooltip onPlace) { placeTip = onPlace; }
    private static String date(String date) { return date == null || date.isBlank() ? "未记录" : date.substring(0, Math.min(10, date.length())); }
}
