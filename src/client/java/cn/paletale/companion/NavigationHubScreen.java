package cn.paletale.companion;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import static cn.paletale.companion.MarketTheme.*;

/** A standalone navigation entry point. Each category supplies world-aware places, so later land/port providers can join here. */
final class NavigationHubScreen extends WindowScreen {
    private final Screen parent;
    private final MarketApi media;
    private int left,panelWidth;private int category, page, startX, contentWidth, rows, capacity, columnRows;
    private String query = "";
    private List<CityCatalog.Place> visible = List.of();
    private long refreshAt;
    private EditBox searchBox;
    private boolean queryDirty;
    private long catalogRevision;
    private Action account;
    private boolean custom=true,lands;
    private EditBox coordinatesBox;
    private String coordinateError = "";
    private static final String[] CATEGORIES = { "NPC", "区域", "快捷传送", "附近 NPC" };
    NavigationHubScreen(Screen parent, MarketApi media) { super(Component.literal("导航")); this.parent = parent; this.media = media; }
    static void start(CityCatalog.Place place) {
        Navigation.start(new Navigation.Target(place.name(), place.blockX(), place.blockY(), place.blockZ(), "主城", List.of(new int[]{place.blockX(), place.blockY(), place.blockZ()}), CityNavigation.WORLD));
    }
    @Override protected void rebuildWidgets() {
        var focused=getFocused();
        boolean restore=focused instanceof EditBox edit&&edit.isFocused();
        super.rebuildWidgets();
        // Screen.rebuildWidgets clears focus and applies its default keyboard focus after init.
        if(restore)setFocused(focused);
    }
    @Override protected void init() {
        fitWindow();
        var layout=ArchiveLayout.of(width);left=layout.left();panelWidth=layout.panelWidth();int sidebar=layout.sidebar();startX=layout.contentX();contentWidth=layout.contentWidth();
        ArchiveNavigation.tabs(this::addRenderableWidget, parent, media, ArchiveNavigation.Tab.NAVIGATION, left, panelWidth);
        int accountWidth = panelWidth < 600 ? 88 : 96;
        account = addRenderableWidget(new Action(PaleTaleClient.ACCOUNT.label(), left+panelWidth-accountWidth, 13, accountWidth, 18, false, "person", b -> PaleTaleClient.ACCOUNT.checkStatus(minecraft)));
        boolean expedition=ExpeditionMenu.active();if(expedition){custom=true;lands=false;}
        if(!expedition){
            addRenderableWidget(new Action("自选目的地", left, 88, sidebar, 18, custom, "compass", b -> { custom=true;lands=false;setFocused(null);rebuildWidgets(); }));
            addRenderableWidget(new Action("主城", left, 110, sidebar, 18, !custom&&!lands, "compass", b -> { custom=false;lands=false;page=0;setFocused(null);rebuildWidgets(); }));
            addRenderableWidget(new Action("传送过的领地", left, 132, sidebar, 18, lands, "compass", b -> { custom=false;lands=true;page=0;setFocused(null);rebuildWidgets(); }));
        }else addRenderableWidget(new Action("当前服务器坐标",left,88,sidebar,18,true,"compass",b->{custom=true;lands=false;setFocused(null);rebuildWidgets();}));
        addRenderableWidget(new Action("取消导航", left, height - 35, sidebar, 17, false, null, b -> { Navigation.cancel(); rebuildWidgets(); })).active = Navigation.target() != null;
        if(custom) { initCoordinates(); return; }
        if(lands) { initLands(sidebar); return; }
        int filterX = startX;
        for (int i = 0; i < CATEGORIES.length; i++) { final int value = i;
            int filterWidth = font.width(CATEGORIES[i]) + 12;
            addRenderableWidget(new Action(CATEGORIES[i], filterX, 48, filterWidth, 18, category == i, null, b -> { category = value; page = 0; rebuildWidgets(); }));
            filterX += filterWidth + 4;
        }
        if(searchBox==null) {
            searchBox=new EditBox(font,left+4,67,sidebar-8,10,Component.literal("搜索主城导航"));
            searchBox.setMaxLength(60);searchBox.setBordered(false);searchBox.setTextShadow(false);searchBox.setTextColor(INK);searchBox.setValue(query);
            searchBox.setResponder(value->{query=value;page=0;queryDirty=true;});
        }
        // Reuse the input so result/catalog refreshes preserve its caret, selection and scroll position.
        searchBox.setX(left+4);searchBox.setY(67);searchBox.setWidth(sidebar-8);addRenderableWidget(searchBox);
        rows = Math.max(1, (height - 116) / 32); capacity = rows * (contentWidth >= 360 ? 2 : 1);
        visible = places(); page = Math.max(0, Math.min(page, Math.max(0, (visible.size() - 1) / capacity)));
        columnRows = Math.max(1, (Math.min(capacity, visible.size() - page * capacity) + (contentWidth >= 360 ? 1 : 0)) / (contentWidth >= 360 ? 2 : 1));
        int half = contentWidth >= 360 ? (contentWidth - 12) / 2 : contentWidth;
        for (int i = page * capacity; i < Math.min(visible.size(), (page + 1) * capacity); i++) {
            var place = visible.get(i); int index = i - page * capacity, col = index / columnRows, row = index % columnRows;
            int x = startX + col * (half + 12), y = 74 + row * 32;
            var go = addRenderableWidget(new Action("导航", x + half - 47, y + 2, 47, 16, false, "compass", b -> { start(place); minecraft.setScreen(null); }));
            go.setTooltip(Tooltip.create(Component.literal(place.name() + "\n主城 · " + coordinates(place))));
        }
        addRenderableWidget(new Action("上一页", startX, height - 32, 55, 17, false, null, b -> { page--; rebuildWidgets(); })).active = page > 0;
        addRenderableWidget(new Action("下一页", width - 68, height - 32, 55, 17, false, null, b -> { page++; rebuildWidgets(); })).active = (page + 1) * capacity < visible.size();
        refreshAt = System.currentTimeMillis() + 2000;
        catalogRevision=CityCatalog.revision();
    }
    private List<LandSpawns.Visit> visitedLands = List.of();
    private void initLands(int sidebar) {
        if(searchBox==null) {
            searchBox=new EditBox(font,left+4,67,sidebar-8,10,Component.literal("搜索主城导航"));
            searchBox.setMaxLength(60);searchBox.setBordered(false);searchBox.setTextShadow(false);searchBox.setTextColor(INK);searchBox.setValue(query);
            searchBox.setResponder(value->{query=value;page=0;queryDirty=true;});
        }
        searchBox.setX(left+4);searchBox.setY(67);searchBox.setWidth(sidebar-8);addRenderableWidget(searchBox);
        String needle=query.toLowerCase(java.util.Locale.ROOT);
        visitedLands=LandSpawns.visits().stream().filter(v->v.land().toLowerCase(java.util.Locale.ROOT).contains(needle)).toList();
        rows=Math.max(1,(height-100)/32);capacity=rows;page=Math.max(0,Math.min(page,Math.max(0,(visitedLands.size()-1)/capacity)));
        for(int i=page*capacity;i<Math.min(visitedLands.size(),(page+1)*capacity);i++) {
            var visit=visitedLands.get(i);int y=58+(i-page*capacity)*32;ChatChannelIcons.request("领地:"+visit.land());
            addRenderableWidget(new Action("导航",startX+contentWidth-47,y+2,47,16,false,"compass",b->{Navigation.start(new Navigation.Target(visit.land(),visit.x(),null,visit.z(),"",List.of(),CityNavigation.MAIN_WORLD));minecraft.setScreen(null);}));
        }
        addRenderableWidget(new Action("上一页",startX,height-32,55,17,false,null,b->{page--;rebuildWidgets();})).active=page>0;
        addRenderableWidget(new Action("下一页",width-68,height-32,55,17,false,null,b->{page++;rebuildWidgets();})).active=(page+1)*capacity<visitedLands.size();
    }
    private void renderLands(GuiGraphics g) {
        hint(g,searchBox,"搜索领地名称");g.renderOutline(left,62,ArchiveLayout.of(width).sidebar(),20,LINE);
        text(g,"传送过的领地",startX,48,contentWidth,INK);
        if(visitedLands.isEmpty()) text(g,query.isEmpty()?"还没有记录。用 /l spawn 传送到领地出生点后会出现在这里（仅保存在本机）。":"没有符合条件的领地。",startX+2,76,contentWidth-4,MUTED);
        for(int i=page*capacity;i<Math.min(visitedLands.size(),(page+1)*capacity);i++) {
            var visit=visitedLands.get(i);int y=58+(i-page*capacity)*32;
            MarketTheme.item(g,ChatChannelIcons.icon("领地:"+visit.land()),startX+2,y+8,16);
            text(g,visit.land(),startX+23,y+4,contentWidth-80,INK);
            String when=visit.time()>0?" · 上次 "+java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm").format(java.time.Instant.ofEpochMilli(visit.time()).atZone(LedgerStore.BEIJING)):"";
            text(g,visit.x()+" / "+visit.z()+when,startX+23,y+16,contentWidth-80,MUTED);
            line(g,startX,y+31,contentWidth);
        }
        text(g,"第 "+(page+1)+" / "+Math.max(1,(visitedLands.size()+capacity-1)/capacity)+" 页 · 共 "+visitedLands.size()+" 处",startX+65,height-26,contentWidth-130,MUTED);
    }
    private void initCoordinates() {
        if(coordinatesBox==null) {
            coordinatesBox=new EditBox(font,startX+4,80,Math.max(45,contentWidth-8),14,Component.literal(ExpeditionMenu.active()?"当前服务器目的地坐标":"主世界目的地坐标"));
            coordinatesBox.setMaxLength(80);coordinatesBox.setBordered(false);coordinatesBox.setTextShadow(false);coordinatesBox.setTextColor(INK);
            coordinatesBox.setResponder(value->coordinateError="");
        }
        coordinatesBox.setX(startX+4);coordinatesBox.setY(80);coordinatesBox.setWidth(Math.max(45,contentWidth-8));addRenderableWidget(coordinatesBox);
        addRenderableWidget(new Action("开始导航",startX,102,80,18,false,"compass",b->startCoordinates()));
        addRenderableWidget(new Action("记录当前位置",startX+88,102,100,18,false,null,b->recordHere())).active=minecraft.player!=null;
        saved=savedHere();int capacity=savedCapacity();savedPage=Math.max(0,Math.min(savedPage,Math.max(0,(saved.size()-1)/capacity)));
        for(int i=savedPage*capacity;i<Math.min(saved.size(),(savedPage+1)*capacity);i++){
            var entry=saved.get(i);int y=SAVED_Y+(i-savedPage*capacity)*ROW;
            addRenderableWidget(new Action("删除",startX+contentWidth-38,y+1,38,16,false,null,b->minecraft.setScreen(new ConfirmDeleteDialog(this,entry))));
            addRenderableWidget(new Action("导航",startX+contentWidth-90,y+1,47,16,false,"compass",b->startSaved(entry)));
        }
        if(saved.size()>capacity){
            addRenderableWidget(new Action("上一页",startX,height-32,55,17,false,null,b->{savedPage--;rebuildWidgets();})).active=savedPage>0;
            addRenderableWidget(new Action("下一页",width-68,height-32,55,17,false,null,b->{savedPage++;rebuildWidgets();})).active=(savedPage+1)*capacity<saved.size();
        }
    }
    private static final int SAVED_Y=152,ROW=21;
    private int savedCapacity(){return Math.max(1,(height-SAVED_Y-40)/ROW);}
    private int savedPage;private List<CustomDestinations.Entry> saved=List.of();
    /** Only the points of the server the player is on (the main server, or the expedition server shown). */
    private static List<CustomDestinations.Entry> savedHere(){
        String server=ExpeditionMenu.activeServer();String key=server==null?"":server;
        return CustomDestinationStore.all().stream().filter(e->e.server().equals(key)).toList();
    }
    private void recordHere(){
        var player=minecraft.player;if(player==null)return;String server=ExpeditionMenu.activeServer();
        String world=Navigation.world(minecraft);
        minecraft.setScreen(new DestinationDialog(this,(int)Math.floor(player.getX()),(int)Math.floor(player.getY()),(int)Math.floor(player.getZ()),world.isEmpty()?CityNavigation.MAIN_WORLD:world,server==null?"":server));
    }
    private void startSaved(CustomDestinations.Entry e){
        Navigation.start(new Navigation.Target(e.name(),e.x(),e.y(),e.z(),"",List.of(new int[]{e.x(),e.y(),e.z()}),e.world().isEmpty()?CityNavigation.MAIN_WORLD:e.world(),e.server()));
        minecraft.setScreen(null);
    }
    private static String worldLabel(String world){
        return switch(world){case "minecraft:overworld"->"主世界";case "minecraft:the_nether"->"地狱";case "minecraft:the_end"->"末地";default->world.isEmpty()?"主世界":world;};
    }
    private void renderSaved(GuiGraphics g,int mouseX,int mouseY){
        text(g,"我的目的地 · 仅保存在本机",startX,SAVED_Y-26,contentWidth,INK);
        if(saved.isEmpty()){text(g,"还没有记录。站在想保存的地点，点上方「记录当前位置」。",startX+2,SAVED_Y-8,contentWidth-4,MUTED);return;}
        int total=contentWidth-100,nameW=total*22/100,noteW=total*30/100,whereW=total*24/100,coordW=total-nameW-noteW-whereW-12;
        int noteX=startX+2+nameW+4,whereX=noteX+noteW+4,coordX=whereX+whereW+4;
        text(g,"名称",startX+2,SAVED_Y-12,nameW,MUTED);text(g,"备注",noteX,SAVED_Y-12,noteW,MUTED);text(g,"世界 · 领地",whereX,SAVED_Y-12,whereW,MUTED);text(g,"坐标",coordX,SAVED_Y-12,coordW,MUTED);
        int capacity=savedCapacity();
        for(int i=savedPage*capacity;i<Math.min(saved.size(),(savedPage+1)*capacity);i++){
            var e=saved.get(i);int y=SAVED_Y+(i-savedPage*capacity)*ROW+4;
            String where=CustomDestinationStore.worldLabel(e.world(),e.server())+(CustomDestinationStore.recordsLand(e.world(),e.server())&&!e.land().isEmpty()?" · "+e.land():"");
            String coords=e.x()+" / "+e.y()+" / "+e.z();
            cell(g,e.name(),startX+2,y,nameW,INK,mouseX,mouseY);
            cell(g,e.note().isEmpty()?"—":e.note(),noteX,y,noteW,MUTED,mouseX,mouseY);
            cell(g,where,whereX,y,whereW,MUTED,mouseX,mouseY);
            cell(g,coords,coordX,y,coordW,MUTED,mouseX,mouseY);
            line(g,startX,y+16,contentWidth);
        }
        if(saved.size()>capacity)text(g,"第 "+(savedPage+1)+" / "+((saved.size()+capacity-1)/capacity)+" 页 · 共 "+saved.size()+" 处",startX+65,height-26,contentWidth-130,MUTED);
    }
    /** A fixed-width cell: text that does not fit ends in "..." and shows in full while the mouse is over it. */
    private void cell(GuiGraphics g,String value,int x,int y,int width,int color,int mouseX,int mouseY){
        var f=PaleFont.get();boolean cut=f.width(value)>width;
        String shown=cut?f.plainSubstrByWidth(value,Math.max(0,width-f.width("..."))).stripTrailing()+"...":value;
        g.drawString(f,shown,x,y,color,false);
        if(cut&&mouseX>=x&&mouseX<x+width&&mouseY>=y-2&&mouseY<y+12)fullNameTooltip(value);
    }
        private void startCoordinates() {
        var at=NavCoordinates.parse(coordinatesBox.getValue());
        if(at==null) {coordinateError="请输入有效坐标：X Y Z 或 X Z。";return;}
        String server=ExpeditionMenu.activeServer();String world=Navigation.world(minecraft);
        Navigation.start(new Navigation.Target("自选目的地",at.x(),at.y(),at.z(),"",at.y()==null?List.of():List.of(new int[]{at.x(),at.y(),at.z()}),world.isEmpty()?CityNavigation.MAIN_WORLD:world,server==null?"":server));
        minecraft.setScreen(null);
    }
    private List<CityCatalog.Place> places() {
        List<CityCatalog.Place> source = switch (category) { case 1 -> CityCatalog.destinations(); case 2 -> CityCatalog.shortcuts(); case 3 -> CityNpcScan.nearby(); default -> CityCatalog.npcs(); };
        return source.stream().filter(p -> (p.name() + p.description()).toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(java.util.Locale.ROOT))).toList();
    }
    @Override public void tick() {
        if(custom)return;
        if(lands){if(queryDirty){queryDirty=false;rebuildWidgets();}return;}
        if(catalogRevision!=CityCatalog.revision()){rebuildWidgets();return;}
        if (queryDirty) { queryDirty = false; rebuildWidgets(); return; }
        if (category == 3 && System.currentTimeMillis() >= refreshAt) { if (!places().equals(visible)) rebuildWidgets(); else refreshAt = System.currentTimeMillis() + 2000; }
    }
    @Override protected void windowBackground(GuiGraphics g,int x,int y,float delta){super.windowBackground(g,x,y,delta);ArchiveLayout.of(width).draw(g,height);}
    @Override protected void renderWindow(GuiGraphics g, int mouseX, int mouseY, float delta) {
        account.setMessage(Component.literal(PaleTaleClient.ACCOUNT.label()));
        account.setAvatar(media, ArchiveNavigation.avatarOf(PaleTaleClient.ACCOUNT.citizen()));
        account.active = !PaleTaleClient.ACCOUNT.busy() && (PaleTaleClient.ACCOUNT.loggedIn() || PaleTaleClient.ACCOUNT.canLogin(minecraft));
        ArchiveNavigation.brand(g,left,panelWidth);
        text(g, "导航", left+4, 47, 80, INK);
        if(custom) {
            boolean expedition=ExpeditionMenu.active();
            text(g,expedition?"当前服务器坐标":"自选目的地",startX,48,contentWidth,INK);
            text(g,expedition?"跨服区域仅提供当前服务器内的坐标引导。":"输入当前世界的坐标，Y 可省略。",startX,62,contentWidth,MUTED);
            g.renderOutline(startX,76,contentWidth,18,LINE);hint(g,coordinatesBox,"X Y Z，或 X Z");
            text(g,coordinateError,startX+196,106,contentWidth-196,MarketTheme.c(0xFF9E4942));
            renderSaved(g,mouseX,mouseY);
            super.renderWindow(g,mouseX,mouseY,delta);return;
        }
        if(lands) { renderLands(g); super.renderWindow(g,mouseX,mouseY,delta);return; }
        hint(g, searchBox, "搜索名称或用途");
        g.renderOutline(left, 62, ArchiveLayout.of(width).sidebar(), 20, LINE);
        if (visible.isEmpty()) text(g, category == 3 ? "附近暂无已加载的主城 NPC。" : "没有符合条件的地点。", startX + 2, 76, contentWidth - 4, MUTED);
        int half = contentWidth >= 360 ? (contentWidth - 12) / 2 : contentWidth;
        for (int i = page * capacity; i < Math.min(visible.size(), (page + 1) * capacity); i++) {
            var place = visible.get(i); int index = i - page * capacity, x = startX + index / columnRows * (half + 12), y = 74 + index % columnRows * 32;
            if (!CityNpcScan.head(g, place, x + 2, y + 2, 16)) drawIcon(g, place, x + 2, y + 2);
            text(g, place.name(), x + 23, y + 2, half - 74, INK);
            String status = CityNpcScan.seen(place) ? " · 附近" : "";
            text(g, place.description() + status, x + 23, y + 12, half - 25, MUTED);
            String distance = minecraft.player != null && CityNavigation.WORLD.equals(Navigation.world(minecraft)) ? " · " + Navigation.blocks(CityCatalog.distance(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ(), place.x(), place.y(), place.z())) : "";
            text(g, coordinates(place) + distance, x + 23, y + 22, half - 25, MUTED);
            line(g, x, y + 31, half);
        }
        text(g, "第 " + (page + 1) + " / " + Math.max(1, (visible.size() + capacity - 1) / capacity) + " 页 · 共 " + visible.size() + " 处", startX + 65, height - 26, contentWidth - 130, MUTED);
        super.renderWindow(g, mouseX, mouseY, delta);
    }
    private static void drawIcon(GuiGraphics g, CityCatalog.Place p, int x, int y) {
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(p.icon()));
        var stack = new net.minecraft.world.item.ItemStack(item);
        if (p.model() != null && !p.model().isEmpty()) {
            var model = net.minecraft.resources.Identifier.parse(p.model());
            if (net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(model.withPath("items/" + model.getPath() + ".json")).isPresent()) stack.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, model);
        }
        ArchiveModels.INSTANCE.draw(g, stack, x, y, 16);
    }
    private static String coordinates(CityCatalog.Place p) { return String.format(java.util.Locale.ROOT, "%.1f / %.1f / %.1f", p.x(), p.y(), p.z()); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
