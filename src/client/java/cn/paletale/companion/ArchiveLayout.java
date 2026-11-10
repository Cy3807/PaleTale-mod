package cn.paletale.companion;
import net.minecraft.client.gui.GuiGraphics;
/** All three catalog tabs share the archive's panel and separator coordinates, including scaled wide windows. */
record ArchiveLayout(int left,int panelWidth,int sidebar,int contentX,int contentWidth){
 static ArchiveLayout of(int width){int panel=Math.min(1120,width-16),left=(width-panel)/2,side=Math.max(70,Math.min(84,panel/9));return new ArchiveLayout(left,panel,side,left+side+14,panel-side-14);}
 int headerY(){return 38;}int dividerX(){return contentX-7;}int dividerTop(){return 44;}int dividerBottom(int height){return height-37;}
 void draw(GuiGraphics g,int height){g.fill(left,42,left+sidebar,height-38,MarketTheme.c(0xFFF0F1EA));MarketTheme.line(g,left,headerY(),panelWidth);g.vLine(dividerX(),dividerTop(),dividerBottom(height),MarketTheme.LINE);}
}
