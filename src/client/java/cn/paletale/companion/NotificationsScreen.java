package cn.paletale.companion;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import static cn.paletale.companion.MarketTheme.*;

/** The original paper/list window, backed by the read-only snapshot inbox. */
public final class NotificationsScreen extends WindowScreen {
    private static final String[] KIND = { "price", "价格变动", "state", "状态变动", "published", "新发布", "effect_approved", "效果通过", "effect_rejected", "效果未通过", "item_approved", "地图画通过", "item_rejected", "地图画未通过", "review_wine", "待审核·酒效果", "review_map", "待审核·地图画" };
    private final Screen parent;
    private final NoticeApi api;
    private final Supplier<String> token;
    private NoticeInbox inbox;
    private String sessionToken;
    private boolean confirmClear;
    /** Requests waiting for a yes or no (organization invitations and the like); shown above the list, a few at a time. */
    private List<NoticeV2Api.Action> actions = List.of();
    private NoticeV2Api actionApi;
    private boolean actionBusy;
    private String actionMessage = "";
    private int actionsTop, actionsShown;
    private static final int ACTION_ROW = 30, ACTION_MAX = 3;
    private ArchiveLayout layout;private String query="",noticeKind="";private int readMode,typeOffset,typeTop=143;private net.minecraft.client.gui.components.EditBox search;private java.util.List<Integer> filtered=java.util.List.of();
    private int left, panelWidth, rowHeight = 46, capacity = 8, localPage, contentTop = 46;

    public NotificationsScreen(Screen parent, NoticeApi api) { this(parent, api, PaleTaleClient.ACCOUNT::token); }
    NotificationsScreen(Screen parent, NoticeApi api, Supplier<String> token) {
        super(Component.literal("通知")); this.parent = parent; this.api = api; this.token = token;
    }
    NoticeInbox inbox() { return inbox; }
    @Override protected void init() {
        fitWindow();
        layout=ArchiveLayout.of(width);panelWidth=layout.contentWidth();left=layout.contentX();
        ArchiveNavigation.tabs(this::addRenderableWidget,parent,PaleTaleClient.MARKET,ArchiveNavigation.Tab.NOTIFICATIONS,layout.left(),layout.panelWidth());
        addRenderableWidget(new Action(PaleTaleClient.ACCOUNT.label(),layout.left()+layout.panelWidth()-(layout.panelWidth()<600?88:96),13,layout.panelWidth()<600?88:96,18,false,"person",b->PaleTaleClient.ACCOUNT.checkStatus(minecraft)));
        if (inbox == null || !Objects.equals(sessionToken, token.get())) {
            if (inbox != null) inbox.close();
            sessionToken = token.get(); localPage = 0;
            inbox = new NoticeInbox(new NoticeV2Api(api::site), token, minecraft::execute, () -> { if (minecraft.screen == this) rebuildWidgets(); }, NoticeWatch::updateUnread);
            inbox.refresh();
            actionApi = new NoticeV2Api(api::site); actions = List.of(); actionMessage = "";
            loadActions();
            return;
        }
        search=addRenderableWidget(new net.minecraft.client.gui.components.EditBox(font,layout.left()+4,70,layout.sidebar()-8,16,Component.literal("搜索本页通知")));search.setBordered(false);search.setTextShadow(false);search.setTextColor(INK);search.setMaxLength(100);search.setValue(query);search.setResponder(value->{query=value;localPage=0;inbox.selected.clear();inbox.selectedGroups.clear();minecraft.execute(()->{if(minecraft.screen==this){rebuildWidgets();setFocused(search);}});});
        String[] modes={"全部","未读","已读"};int needed=java.util.Arrays.stream(modes).mapToInt(value->font.width(value)+6).sum();boolean stacked=needed>layout.sidebar();typeTop=116+(stacked?54:18)+9;int filterX=layout.left();for(int i=0;i<3;i++){final int mode=i;int buttonWidth=stacked?layout.sidebar():font.width(modes[i])+6+(layout.sidebar()-needed)/3;var filter=addRenderableWidget(new WorldTradeButton(modes[i],stacked?layout.left():filterX,116+(stacked?i*18:0),buttonWidth,readMode==i,b->{readMode=mode;changedFilter();}));filter.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("筛选本页："+modes[i])));filterX+=buttonWidth;}

        int typeRows=Math.max(1,(height-typeTop-34)/18),types=KIND.length/2+1;typeOffset=Math.min(typeOffset,Math.max(0,types-typeRows));for(int i=typeOffset;i<Math.min(types,typeOffset+typeRows);i++){String key=i==0?"":KIND[(i-1)*2],name=i==0?"全部类型":KIND[(i-1)*2+1];var button=addRenderableWidget(new Action(name,layout.left(),typeTop+(i-typeOffset)*18,layout.sidebar(),17,noticeKind.equals(key),null,b->{noticeKind=key;changedFilter();}));button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("筛选本页；分组按最新通知类型判断")));}
        filtered=filterRows();
        boolean groups = inbox.page != null && inbox.page.v2() && !inbox.details();
        boolean all = inbox.page!=null&&!filtered.isEmpty()&&filtered.stream().allMatch(i->groups?inbox.selectedGroups.contains(inbox.page.groups().get(i).key()):inbox.selected.contains(inbox.page.items().get(i).id()));
        int ticked = groups ? inbox.selectedGroups.size() : inbox.selected.size();
        String[] labels = { confirmClear ? "再点一次确认清空" : "清空全部", "删除所选" + (ticked == 0 ? "" : "（" + ticked + "）"), "标为已读", all ? "取消全选" : "全选本页", "刷新" };
        int totalWidth = 0; for (String label : labels) totalWidth += font.width(label) + 22;
        boolean wide = panelWidth >= totalWidth + 190;
        int x = wide ? left + panelWidth : left, toolbarY = 46;
        for (int i = 0; i < labels.length; i++) {
            int w = font.width(labels[i]) + 16, index = i;
            if (wide) x -= w + 6;
            else if (x + w > left + panelWidth) { x = left; toolbarY += 23; }
            var button = addRenderableWidget(new Action(labels[i], x, toolbarY, w, 19, index == 0 && confirmClear, null, b -> action(index)));
            button.active = !inbox.loading && !inbox.busy && (index == 4 || inbox.page != null && (index == 0 ? inbox.page.total() > 0 : index == 3 ? groups ? !inbox.page.groups().isEmpty() : !inbox.page.items().isEmpty() : ticked > 0));
            if (!wide) x += w + 6;
        }
        contentTop = toolbarY + 33;
        actionsTop = contentTop; actionsShown = Math.min(ACTION_MAX, actions.size());
        if (actionsShown > 0) {
            for (int i = 0; i < actionsShown; i++) {
                var request = actions.get(i); int rowY = actionsTop + i * ACTION_ROW;
                var no = addRenderableWidget(new Action("拒绝", left + panelWidth - 40, rowY + 5, 38, 18, false, null, b -> respond(request, false)));
                var yes = addRenderableWidget(new Action("同意", left + panelWidth - 82, rowY + 5, 38, 18, false, null, b -> respond(request, true)));
                no.active = yes.active = !actionBusy;
            }
            contentTop += actionsShown * ACTION_ROW + (actions.size() > actionsShown ? 12 : 0) + 4;
        }
        capacity = Math.max(1, Math.min(8, (height - contentTop - 42) / 36));
        rowHeight = Math.max(36, Math.min(54, (height - contentTop - 42) / capacity));
        localPage = Math.max(0, Math.min(localPage, Math.max(0, (rowCount() - 1) / capacity)));
        var previous = addRenderableWidget(new Action("上一页", left, height - 28, 56, 18, false, null, b -> {
            if (localPage > 0) { localPage--; rebuildWidgets(); } else { localPage = 0; inbox.previous(); }
        }));
        var next = addRenderableWidget(new Action("下一页", left + panelWidth - 56, height - 28, 56, 18, false, null, b -> {
            if ((localPage + 1) * capacity < rowCount()) { localPage++; rebuildWidgets(); } else { localPage = 0; inbox.next(); }
        }));
        previous.active = !inbox.loading && !inbox.busy && (localPage > 0 || inbox.canPrevious());
        next.active = !inbox.loading && !inbox.busy && ((localPage + 1) * capacity < rowCount() || inbox.canNext());
        if (inbox.details()) {
            var collapse = addRenderableWidget(new Action("收起分组", left + 62, height - 28, 64, 18, false, null, b -> { localPage = 0; inbox.collapse(); }));
            collapse.active = !inbox.loading && !inbox.busy;
        }
        if (inbox.page == null || inbox.loading) return;
        int end = Math.min(rowCount(), (localPage + 1) * capacity);
        for (int i = localPage * capacity; i < end; i++) {
            int y = contentTop + (i - localPage * capacity) * rowHeight;
            if (groups) {
                var group = inbox.page.groups().get(filtered.get(i));
                var expand = addRenderableWidget(new Action("展开：" + group.title(), left + panelWidth - 44, y + rowHeight - 23, 42, 18, false, null, b -> { localPage = 0; confirmClear = false; inbox.expand(group); }));
                expand.setDisplayLabel("展开");
                expand.active = !inbox.busy;
                var groupBox = addRenderableWidget(new Action(inbox.selectedGroups.contains(group.key()) ? "✔" : "", left, y + (rowHeight - 6) / 2 - 7, 18, 18, inbox.selectedGroups.contains(group.key()), null, b -> inbox.toggleGroup(group.key())));
                groupBox.active = !inbox.busy;
            } else {
                var item = inbox.page.items().get(filtered.get(i));
                var checkbox = addRenderableWidget(new Action(inbox.selected.contains(item.id()) ? "✔" : "", left, y + (rowHeight - 6) / 2 - 7, 18, 18, inbox.selected.contains(item.id()), null, b -> inbox.toggle(item.id())));
                checkbox.active = !inbox.busy;
            }
        }
    }
    private void loadActions() {
        var source = actionApi; String value = token.get();
        if (source == null || value == null) return;
        source.actions(value).whenComplete((list, failure) -> minecraft.execute(() -> {
            if (source != actionApi) return;
            actions = failure == null ? list : List.of();
            if (minecraft.screen == this) rebuildWidgets();
        }));
    }
    private void respond(NoticeV2Api.Action request, boolean accept) {
        var source = actionApi; String value = token.get();
        if (source == null || value == null || actionBusy) return;
        actionBusy = true; actionMessage = "正在提交…"; rebuildWidgets();
        source.respond(value, request.id(), accept).whenComplete((status, failure) -> minecraft.execute(() -> {
            actionBusy = false;
            actionMessage = failure != null ? (failure.getCause() instanceof NoticeV2Api.Failure bad ? bad.getMessage() : failure instanceof NoticeV2Api.Failure bad2 ? bad2.getMessage() : "暂时无法处理，请稍后重试。")
                    : switch (status) { case "accepted" -> "已同意。"; case "declined" -> "已拒绝。"; case "expired" -> "这条请求已过期。"; case "cancelled" -> "这条请求已失效或被撤回。"; default -> "找不到这条请求。"; };
            if (minecraft.screen == this) rebuildWidgets();
            loadActions();
        }));
    }
    private static String actionLabel(String type) { return switch (type) { case "org_invite" -> "组织邀请"; case "org_assist" -> "协助者任命"; default -> "待处理事项"; }; }
    private static String daysLeft(String expiresAt) {
        try { long left = java.time.Instant.parse(expiresAt).toEpochMilli() - System.currentTimeMillis(); return left <= 0 ? "即将过期" : "还剩 " + (left + 86_399_999L) / 86_400_000L + " 天"; }
        catch (RuntimeException bad) { return ""; }
    }
    private java.util.List<Integer> filterRows(){var ids=new java.util.ArrayList<Integer>();if(inbox==null||inbox.page==null)return ids;boolean groups=inbox.page.v2()&&!inbox.details();int count=groups?inbox.page.groups().size():inbox.page.items().size();String needle=query.toLowerCase(java.util.Locale.ROOT);for(int i=0;i<count;i++){String value,type;boolean read;if(groups){var row=inbox.page.groups().get(i);type=row.latestKind();read=row.unreadAtSnapshot()==0;value=row.title()+" "+row.latestDetail()+" "+label(row.latestKind());}else{var row=inbox.page.items().get(i);type=row.kind();read=row.read();value=row.adTitle()+" "+row.product()+" "+row.detail()+" "+row.change()+" "+label(row.kind());}if((readMode==0||readMode==1&&!read||readMode==2&&read)&&(noticeKind.isEmpty()||noticeKind.equals(type))&&value.toLowerCase(java.util.Locale.ROOT).contains(needle))ids.add(i);}return java.util.List.copyOf(ids);}
    private int rowCount(){return filtered.size();}
    private void action(int index) {
        if (index == 0) { if (!confirmClear) { confirmClear = true; rebuildWidgets(); } else { confirmClear = false; localPage = 0; inbox.mutate(true, true); } }
        else { confirmClear = false; boolean groupMode = inbox.page != null && inbox.page.v2() && !inbox.details(); if (index == 1) { if (groupMode) inbox.mutateGroups(true); else inbox.mutate(true, false); } else if (index == 2) { if (groupMode) inbox.mutateGroups(false); else inbox.mutate(false, false); } else if (index == 3) { selectVisible(groupMode); } else { localPage = 0; inbox.refresh(); } }
    }
    private void changedFilter(){localPage=0;confirmClear=false;inbox.selected.clear();inbox.selectedGroups.clear();rebuildWidgets();}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){double mx=(x-winX)/windowScale,my=(y-winY)/windowScale;if(layout!=null&&mx>=layout.left()&&mx<layout.left()+layout.sidebar()&&my>=typeTop&&my<height-34){typeOffset=Math.max(0,Math.min(KIND.length/2,typeOffset+(vertical<0?1:-1)));rebuildWidgets();return true;}return super.mouseScrolled(x,y,horizontal,vertical);}
    private void selectVisible(boolean groups){var indices=java.util.List.copyOf(filtered);boolean all=!indices.isEmpty()&&indices.stream().allMatch(i->groups?inbox.selectedGroups.contains(inbox.page.groups().get(i).key()):inbox.selected.contains(inbox.page.items().get(i).id()));for(int i:indices){if(groups){String key=inbox.page.groups().get(i).key();if(inbox.selectedGroups.contains(key)==all)inbox.toggleGroup(key);}else{long id=inbox.page.items().get(i).id();if(inbox.selected.contains(id)==all)inbox.toggle(id);}}}
    @Override public void tick() {
        super.tick();
        if (inbox != null && !Objects.equals(sessionToken, token.get())) rebuildWidgets();
    }
    @Override protected void windowBackground(GuiGraphics g, int x, int y, float delta) {
        g.fill(0, 0, width, height, PAPER);if(layout!=null){layout.draw(g,height);g.renderOutline(layout.left()+1,67,layout.sidebar()-2,22,LINE);} line(g, left, contentTop - 6, panelWidth);
    }
    private String label(String kind) { for (int k = 0; k + 1 < KIND.length; k += 2) if (KIND[k].equals(kind)) return KIND[k + 1]; return "通知"; }
    private void row(GuiGraphics g, int y, String kind, String title, String detail, String when, boolean read, String code, long itemId, boolean available, boolean group) {
        int textX = left + 26, size = Math.min(26, rowHeight - 8), thumbWidth = available && code.matches("[1-9][0-9]{5}") ? size * 8 / 5 : size, bodyX = textX + thumbWidth + 8;
        g.renderOutline(left + 2, y + (rowHeight - 6) / 2 - 5, 14, 14, LINE);
        boolean ad = available && code.matches("[1-9][0-9]{5}");
        String icon = ad ? "/api/mcmod/market/" + code + "/cover" : available && itemId > 0 ? "/api/mcmod/items/" + itemId + "/image" : "";
        // An ad shows its wide cover (16:10), an item its square picture.
        boolean drawn = !icon.isEmpty() && (ad ? MarketImages.INSTANCE.drawFill(g, PaleTaleClient.MARKET, icon, textX, y + 2, thumbWidth, size) : MarketImages.INSTANCE.draw(g, PaleTaleClient.MARKET, icon, textX, y + 2, size));
        if (!drawn) g.fill(textX, y + 2, textX + thumbWidth, y + 2 + size, MarketTheme.c(0xFFF0F1EA));
        String time = when.substring(0, Math.min(16, when.length())).replace('T', ' ');
        int timeWidth = panelWidth >= 340 && !time.isBlank() ? font.width(time) + 8 : 0;
        text(g, (read ? "" : "● ") + label(kind) + " · " + title, bodyX, y + 3, left + panelWidth - bodyX - timeWidth - 4, read ? MUTED : INK);
        if (timeWidth > 0) g.drawString(font, time, left + panelWidth - font.width(time) - 4, y + 3, MUTED, false);
        var rows = font.split(Component.literal(detail), Math.max(1, left + panelWidth - bodyX - (group ? 50 : 4)));
        for (int r = 0; r < Math.min(rowHeight < 45 ? 1 : 2, rows.size()); r++) g.drawString(font, rows.get(r), bodyX, y + 15 + r * 11, MUTED, false);
        g.hLine(left, left + panelWidth, y + rowHeight - 2, LINE);
    }
    @Override protected void renderWindow(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.renderWindow(g, mouseX, mouseY, delta);
        if(layout!=null){ArchiveNavigation.brand(g,layout.left(),layout.panelWidth());text(g,"我的通知",layout.left()+3,48,layout.sidebar()-6,INK);if(search!=null)hint(g,search,"搜索本页通知");}
        for (int i = 0; i < actionsShown; i++) {
            var request = actions.get(i); int rowY = actionsTop + i * ACTION_ROW, textRight = left + panelWidth - 90;
            text(g, "待处理 · " + actionLabel(request.type()) + (daysLeft(request.expiresAt()).isEmpty() ? "" : " · " + daysLeft(request.expiresAt())), left + 4, rowY + 3, textRight - left - 4, INK);
            text(g, request.detail(), left + 4, rowY + 15, textRight - left - 4, MUTED);
            g.hLine(left, left + panelWidth, rowY + ACTION_ROW - 2, LINE);
        }
        if (actions.size() > actionsShown) text(g, "另有 " + (actions.size() - actionsShown) + " 条待处理，处理后会显示出来", left + 4, actionsTop + actionsShown * ACTION_ROW + 1, panelWidth - 8, MUTED);
        if (!actionMessage.isEmpty()) text(g, actionMessage, left, height - 52, panelWidth, MUTED);
        String unread = NoticeWatch.countExact() ? "未读 " + NoticeWatch.unreadCount() : "未读待核对";
        text(g, unread, layout.left()+3,100,layout.sidebar()-6,MUTED);
        if (inbox == null) return;
        if (inbox.loading) { text(g, "正在读取通知…", left, contentTop + 8, panelWidth, MUTED); return; }
        if (inbox.page == null) { text(g, inbox.message.isEmpty() ? "通知暂时无法读取。" : inbox.message, left, contentTop + 8, panelWidth, MarketTheme.c(0xFF9E4942)); return; }
        if (rowCount() == 0) text(g, "本页没有符合条件的通知。", left, contentTop + 8, panelWidth, MUTED);
        boolean groups = inbox.page.v2() && !inbox.details();
        for (int i = localPage * capacity; i < Math.min(rowCount(), (localPage + 1) * capacity); i++) {
            int y = contentTop + (i - localPage * capacity) * rowHeight;
            if (groups) { var item = inbox.page.groups().get(filtered.get(i)); row(g, y, item.latestKind(), item.title(), "共 " + item.count() + " 条 · 载入时未读 " + item.unreadAtSnapshot() + " · " + item.latestDetail(), item.latestAt(), item.unreadAtSnapshot() == 0, item.adCode(), item.targetType().equals("item") && item.targetId() != null ? item.targetId() : 0, item.available(), true); }
            else { var item = inbox.page.items().get(filtered.get(i)); String detail = (item.product().isBlank() ? "" : item.product() + "：") + (item.change().isBlank() ? item.detail() : item.change()); row(g, y, item.kind(), item.adTitle(), detail, item.createdAt(), item.read(), item.adCode(), item.itemId(), item.available(), false); }
        }
        String count = "第 " + inbox.pageNumber() + " 页 · 共 " + (groups ? inbox.page.totalGroups() + " 组" : inbox.page.total() + " 条") + (rowCount() > capacity ? " · 屏 " + (localPage + 1) + "/" + ((rowCount() + capacity - 1) / capacity) : "");
        int countLeft = left + (inbox.details() ? 132 : 62), countWidth = panelWidth - (inbox.details() ? 194 : 124);
        text(g, count, countLeft, height - 23, countWidth, MUTED);
        if (!inbox.message.isEmpty()) text(g, inbox.message, left, height - 42, panelWidth, MarketTheme.c(0xFF9E4942));
    }
    /** Esc and 返回 inside an opened group go back to the group list first; only from the list do they leave the notifications. */
    @Override public void onClose() {
        if (inbox != null && inbox.page != null && inbox.page.v2() && inbox.details()) { if (!inbox.loading && !inbox.busy) { localPage = 0; inbox.collapse(); } return; }
        if (inbox != null) inbox.close(); minecraft.setScreen(parent);
    }
    @Override public void removed() { if (inbox != null) { inbox.close(); inbox = null; } super.removed(); }
    @Override public boolean isPauseScreen() { return false; }
}
