package cn.paletale.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A screen that lives in a centred window over the dimmed game instead of covering the whole display.
 * Subclasses lay themselves out in window coordinates ({@code width}/{@code height} are the window's size, origin at its top-left);
 * this class shifts drawing and mouse input by the window's position, so {@code windowBackground}/{@code renderWindow}
 * replace {@code renderBackground}/{@code render} and every mouse position a subclass sees is window-relative.
 */
abstract class WindowScreen extends Screen {
    /** Window position and size in the real (GUI-scaled) screen, and the real screen's size. */
    protected int winX, winY, realWidth, realHeight;
    private Component fullNameTip;
    protected double windowScale=1,maxScale=1.25;
    private final java.util.Set<Object> sidebarDone = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    /** Whether this screen's full-width left-column buttons are restacked to the ledger's size and spacing (see {@link SidebarLayout}). */
    protected boolean standardSidebar() { return !(this instanceof LedgerScreen); }

    protected WindowScreen(Component title) { super(title); }

    /** Call first in {@code init()}: sizes the window from the current display and makes {@code width}/{@code height} the window's. */
    protected final void fitWindow() {
        PaleFont.apply(this);
        sidebarDone.clear();
        realWidth = minecraft.getWindow().getGuiScaledWidth(); realHeight = minecraft.getWindow().getGuiScaledHeight();
        int marginX = Math.max(10, realWidth / 16), marginY = Math.max(8, realHeight / 14);
        int windowWidth = Math.min(1160, realWidth - 2 * marginX), windowHeight = realHeight - 2 * marginY;
        // On a small display the window is the whole screen (the layouts need that room).
        if (windowWidth < 400) windowWidth = realWidth;
        if (windowHeight < 250) windowHeight = realHeight;
        winX = (realWidth - windowWidth) / 2; winY = (realHeight - windowHeight) / 2;
        maxScale=Math.max(.75,Math.min(1.25,Math.min(windowWidth/320d,windowHeight/220d)));
        windowScale=Math.min(maxScale,ModSettings.uiScale());
        width=(int)Math.floor(windowWidth/windowScale);height=(int)Math.floor(windowHeight/windowScale);
        // The installed mod version sits in the window's bottom-left corner on every screen; it also tells when a newer one is out.
        addRenderableWidget(new VersionBadge(6, height - 12));
        addRenderableWidget(new ScaleSlider(width-151,height-11));
        // The gear at the top right opens Paletale's settings from every window.
        if (!(this instanceof PaleSettingsScreen)) addRenderableWidget(new GearButton(width - 15, 2));
    }

    @Override protected void rebuildWidgets(){boolean keepScale=getFocused() instanceof ScaleSlider;super.rebuildWidgets();if(keepScale)for(var child:children())if(child instanceof ScaleSlider slider){setFocused(slider);break;}}

    @Override public final void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, realWidth, realHeight, 0x99000000);
        g.fill(winX + 3, winY + 4, winX + (int)(width*windowScale) + 3, winY + (int)(height*windowScale) + 4, 0x55000000);
        g.fill(winX - 1, winY - 1, winX + (int)(width*windowScale) + 1, winY + (int)(height*windowScale) + 1, MarketTheme.LINE);
        g.pose().pushMatrix(); g.pose().translate(winX, winY);g.pose().scale((float)windowScale,(float)windowScale);
        windowBackground(g, (int)((mouseX - winX)/windowScale), (int)((mouseY - winY)/windowScale), delta);
        g.pose().popMatrix();
    }

    @Override public final void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        fullNameTip=null;
        if (standardSidebar()) SidebarLayout.normalize(children(), width, height, sidebarDone);
        g.pose().pushMatrix(); g.pose().translate(winX, winY);g.pose().scale((float)windowScale,(float)windowScale);
        WindowTooltips.enter(this);
        try { renderWindow(g, (int)((mouseX - winX)/windowScale), (int)((mouseY - winY)/windowScale), delta); } finally { WindowTooltips.leave(); }
        g.pose().popMatrix();
        if(fullNameTip!=null)g.setTooltipForNextFrame(font,font.split(fullNameTip,Math.max(60,realWidth-24)),net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE,mouseX,mouseY,true);
    }
    /** For screens that scroll their own sidebar: restack it right after their layout, before they record the positions. */
    protected final void normalizeSidebar() { SidebarLayout.normalize(children(), width, height, sidebarDone); }
    final void fullNameTooltip(String name){fullNameTip=Component.literal(name);}

    protected void windowBackground(GuiGraphics g, int mouseX, int mouseY, float delta) { g.fill(0, 0, width, height, MarketTheme.PAPER); }
    protected void renderWindow(GuiGraphics g, int mouseX, int mouseY, float delta) { super.render(g, mouseX, mouseY, delta); }

    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(new net.minecraft.client.input.MouseButtonEvent((event.x() - winX)/windowScale, (event.y() - winY)/windowScale, event.buttonInfo()), doubleClick);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return super.mouseScrolled((x - winX)/windowScale, (y - winY)/windowScale, horizontal, vertical);
    }

    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event){return super.mouseReleased(new net.minecraft.client.input.MouseButtonEvent((event.x()-winX)/windowScale,(event.y()-winY)/windowScale,event.buttonInfo()));}
    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event,double dx,double dy){return super.mouseDragged(new net.minecraft.client.input.MouseButtonEvent((event.x()-winX)/windowScale,(event.y()-winY)/windowScale,event.buttonInfo()),dx/windowScale,dy/windowScale);}
    @Override public void mouseMoved(double x,double y){super.mouseMoved((x-winX)/windowScale,(y-winY)/windowScale);}
    /** The change is committed on release so the handle never disappears during a drag. */
    private final class ScaleSlider extends net.minecraft.client.gui.components.AbstractSliderButton {
        ScaleSlider(int x,int y){super(x,y,145,10,Component.literal("UI大小"),(windowScale-.75)/Math.max(.001,maxScale-.75));updateMessage();}
        private double chosen(){return Math.min(maxScale,Math.max(.75,Math.round((.75+value*(maxScale-.75))*20)/20d));}
        @Override protected void updateMessage(){setMessage(Component.literal("UI大小 "+Math.round(chosen()*100)+"%，左小右大"));}
        @Override protected void applyValue(){updateMessage();}
        private void commit(){double next=chosen();if(Math.abs(next-windowScale)<.001)return;ModSettings.setUiScale(next);minecraft.execute(()->{if(minecraft.screen==WindowScreen.this){rebuildWidgets();for(var child:children())if(child instanceof ScaleSlider slider){WindowScreen.this.setFocused(slider);break;}}});}
        private void pointer(net.minecraft.client.input.MouseButtonEvent event){setValue(Math.max(0,Math.min(1,(event.x()-getX()-64)/(getWidth()-82d))));}
        @Override public void onClick(net.minecraft.client.input.MouseButtonEvent event,boolean twice){pointer(event);}
        @Override protected void onDrag(net.minecraft.client.input.MouseButtonEvent event,double dx,double dy){pointer(event);}
        @Override public void onRelease(net.minecraft.client.input.MouseButtonEvent event){commit();}
        @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event){if(event.key()==262||event.key()==263){setValue(Math.max(0,Math.min(1,value+(event.key()==262?.1:-.1))));commit();return true;}return super.keyPressed(event);}
        @Override public void renderWidget(GuiGraphics g,int mx,int my,float delta){int x=getX(),y=getY(),a=x+64,b=x+getWidth()-18;MarketTheme.text(g,"UI "+Math.round(chosen()*100)+"%",x,y+1,49,MarketTheme.MUTED);MarketTheme.text(g,"小",x+51,y+1,10,MarketTheme.MUTED);MarketTheme.text(g,"大",x+getWidth()-10,y+1,10,MarketTheme.MUTED);g.fill(a,y+5,b,y+6,MarketTheme.LINE);int knob=a+(int)Math.round(value*(b-a));g.fill(knob-2,y+2,knob+3,y+9,isHoveredOrFocused()?MarketTheme.INK:MarketTheme.ACCENT);if(isFocused())g.renderOutline(x,y,getWidth(),getHeight(),MarketTheme.LINE);}
    }

    /** The settings gear, drawn from rectangles so it never depends on a font glyph. */
    private static final class GearButton extends Button {
        GearButton(int x, int y) {
            super(x, y, 12, 12, Component.literal("Paletale 设置"), button -> Minecraft.getInstance().setScreen(new PaleSettingsScreen(Minecraft.getInstance().screen)), DEFAULT_NARRATION);
            setTooltip(Tooltip.create(Component.literal("Paletale 设置")));
        }
        @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            int c = isHoveredOrFocused() ? MarketTheme.INK : MarketTheme.MUTED, x = getX(), y = getY();
            // Teeth (four bars through the centre), body ring and hub.
            g.fill(x + 5, y + 1, x + 7, y + 11, c); g.fill(x + 1, y + 5, x + 11, y + 7, c);
            g.fill(x + 2, y + 2, x + 4, y + 4, c); g.fill(x + 8, y + 2, x + 10, y + 4, c);
            g.fill(x + 2, y + 8, x + 4, y + 10, c); g.fill(x + 8, y + 8, x + 10, y + 10, c);
            g.fill(x + 3, y + 3, x + 9, y + 9, c);
            g.fill(x + 5, y + 5, x + 7, y + 7, MarketTheme.PAPER);
        }
    }

    /** "v0.9.14" in the corner; with a newer version out it says so, opens the official group on click and warns about third-party downloads on hover. */
    private static final class VersionBadge extends Button {
        private String shownTip = "";
        VersionBadge(int x, int y) {
            super(x, y, 60, 11, Component.literal("版本"), button -> {
                if (ModUpdate.newerVersion() == null) return;
                if (ModUpdate.canUpdate()) ModUpdate.download();
                else if (ModUpdate.state() != ModUpdate.State.READY) net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLink(Minecraft.getInstance().screen, ArchiveNavigation.QQ_GROUP).onPress(button);
            }, DEFAULT_NARRATION);
        }
        @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            var font = PaleFont.get();
            String latest = ModUpdate.newerVersion();
            var update = ModUpdate.state();
            String status = latest == null ? "" : switch (update) {
                case DOWNLOADING -> " → 正在下载 " + ModUpdate.progress();
                case READY -> " → 已下载 " + ModUpdate.stagedVersion() + "，重启游戏后生效";
                case FAILED -> " → 更新失败，点击重试";
                default -> " → 有新版本 " + latest + (ModUpdate.canUpdate() ? "，点击更新" : "");
            };
            String label = "v" + ModUpdate.installed() + (SiteConfig.local() ? " · 本地测试" : SiteConfig.preview() ? " · 预览站" : "") + status;
            setWidth(font.width(label) + 4);
            String tip = latest == null
                    ? "当前 " + ModUpdate.installed() + "，已是最新。\n请只从官方 QQ 群下载模组，\n避免第三方注入漏洞！"
                    : switch (update) {
                        case DOWNLOADING -> "正在从官网下载 " + latest + "，请稍候。";
                        case READY -> ModUpdate.leftover() ? "上次的自动替换没有完成，已重新安排。\n请完全退出游戏；若仍是旧版本，请把 mods 里的\nPaletale-" + ModUpdate.stagedVersion() + "-…jar.new 去掉 .new，并删除旧版 jar（排查见 paletale/update.log）。" : "新版本已下载。退出游戏后会自动替换，\n再次启动即是 " + ModUpdate.stagedVersion() + "。";
                        case FAILED -> ModUpdate.failure() + "\n点击重试，也可到官方 QQ 群下载。";
                        default -> ModUpdate.canUpdate()
                                ? "当前 " + ModUpdate.installed() + "，最新 " + latest + "。\n点击从官网下载，下载后重启游戏即可。\n不会在后台自动下载。"
                                : "当前 " + ModUpdate.installed() + "，最新 " + latest + "。\n请到官方 QQ 群下载模组（点击打开），\n避免第三方注入漏洞！";
                    };
            if (!tip.equals(shownTip)) { shownTip = tip; setTooltip(Tooltip.create(Component.literal(tip))); }
            g.drawString(font, label, getX() + 2, getY() + 1, latest == null ? (isHoveredOrFocused() ? MarketTheme.INK : MarketTheme.MUTED) : MarketTheme.c(0xFF9E4942), false);
        }
    }
}
