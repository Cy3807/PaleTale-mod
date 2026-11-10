package cn.paletale.companion;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import java.util.*;

/** Real Minecraft widgets + loopback HTTP replay, not SIMMC/website end-to-end acceptance. */
final class NoticeClientGameTest {
    private NoticeClientGameTest(){}
    static void run(ClientGameTestContext context)throws Exception{
        try(var fixture=new NoticeFixture()){
            var media=new NoticeApi(fixture.site());
            var previousSite=context.computeOnClient(c->PaleTaleClient.MARKET.site());
            int[] viewport=context.computeOnClient(c->new int[]{c.getWindow().getScreenWidth(),c.getWindow().getScreenHeight(),c.options.guiScale().get()});
            try{
                context.runOnClient(c->{PaleTaleClient.MARKET.retarget(fixture.site());c.getWindow().setWindowed(1600,1000);c.options.guiScale().set(2);c.resizeDisplay();c.setScreen(new NotificationsScreen(null,media,()->"fixture-token"));});
                await(context);context.waitTicks(3);
                context.runOnClient(c->{require(inbox(c).page.mode().equals("groups")&&NoticeWatch.unreadCount()==38,"38 authoritative unread; folded group list");require(fixture.requests.stream().noneMatch(s->s.startsWith("POST")),"Opening is read-only");});
                context.runOnClient(c->{for(var child:c.screen.children())if(child instanceof net.minecraft.client.gui.components.Button b&&java.util.List.of("全部","未读","已读").contains(b.getMessage().getString()))require(b.getWidth()>=c.font.width(b.getMessage()),"read filter labels fit without truncation");});context.takeScreenshot("paletale-notices-v2-groups-scale2-v0.11.14");
                context.runOnClient(c->((net.minecraft.client.gui.components.EditBox)WorldClientGameTest.field(c.screen,"search")).setValue("no-matching-notification-fixture"));context.waitTicks(3);context.runOnClient(c->require(((java.util.List<?>)WorldClientGameTest.field(c.screen,"filtered")).isEmpty(),"keyword search filters visible notifications"));context.runOnClient(c->((net.minecraft.client.gui.components.EditBox)WorldClientGameTest.field(c.screen,"search")).setValue(""));context.waitTicks(3);
                context.clickScreenButton("未读");context.runOnClient(c->{var screen=(NotificationsScreen)c.screen;for(Object index:(java.util.List<?>)WorldClientGameTest.field(screen,"filtered"))require(inbox(c).page.groups().get((Integer)index).unreadAtSnapshot()>0,"unread filter excludes read groups");});context.clickScreenButton("已读");context.runOnClient(c->{var screen=(NotificationsScreen)c.screen;for(Object index:(java.util.List<?>)WorldClientGameTest.field(screen,"filtered"))require(inbox(c).page.groups().get((Integer)index).unreadAtSnapshot()==0,"read filter excludes unread groups");});context.clickScreenButton("全部");
                context.clickScreenButton("Paletale 设置");context.clickScreenButton("返回");await(context);
                context.runOnClient(c->require(inbox(c).page.mode().equals("groups"),"Returning from the original settings gear creates an active inbox"));
                context.clickScreenButton("展开：广告1");await(context);
                context.runOnClient(c->require(inbox(c).page.items().getFirst().id()>Integer.MAX_VALUE,"Real UI receives long IDs"));
                context.takeScreenshot("paletale-notices-v2-detail-scale2-v0.11.14");
                for(int i=0;i<3&&context.computeOnClient(c->inbox(c).pageNumber()==1);i++){context.clickScreenButton("下一页");await(context);}
                context.runOnClient(c->require(inbox(c).pageNumber()==2&&inbox(c).page.groupKey().equals("ad:1"),"Viewport paging continues same ad detail cursor"));
                context.takeScreenshot("paletale-notices-v2-detail-page2-v0.11.14");
                context.clickScreenButton("全选本页");fixture.writeStatus=503;
                context.clickScreenButton("标为已读");await(context);
                context.runOnClient(c->require(inbox(c).selected.size()==8&&NoticeWatch.unreadCount()==38&&inbox(c).message.contains("可能已生效"),"503 retains checkboxes, rows and badge"));
                context.takeScreenshot("paletale-notices-v2-unconfirmed-v0.11.14");
                fixture.writeStatus=0;fixture.partial=true;
                context.clickScreenButton("标为已读");await(context);
                context.runOnClient(c->require(inbox(c).page.mode().equals("groups")&&NoticeWatch.unreadCount()==37&&inbox(c).message.contains("尚未确认"),"Confirmed subset refreshes snapshot and applies exact server count"));
                context.takeScreenshot("paletale-notices-v2-partial-confirmation-v0.11.14");
                context.runOnClient(c->{var box=inbox(c);box.expand(box.page.groups().get(6));});await(context);
                context.runOnClient(c->require(inbox(c).page.items().getFirst().targetType().equals("other"),"Other target is retained in detail view"));
                context.takeScreenshot("paletale-notices-v2-other-v0.11.14");
                context.clickScreenButton("收起分组");
                context.runOnClient(c->{var box=inbox(c);box.expand(box.page.groups().get(7));});await(context);
                context.takeScreenshot("paletale-notices-v2-unavailable-v0.11.14");
                context.clickScreenButton("收起分组");
                int writes=(int)fixture.requests.stream().filter(s->s.startsWith("POST")).count();
                context.clickScreenButton("清空全部");
                context.runOnClient(c->require(fixture.requests.stream().filter(s->s.startsWith("POST")).count()==writes,"First clear-all click only asks for confirmation"));
                context.takeScreenshot("paletale-notices-v2-clear-confirmation-v0.11.14");
                context.clickScreenButton("刷新");await(context);
                context.runOnClient(c->{fixture.expireNext.set(2);inbox(c).next();});await(context);
                context.runOnClient(c->require(inbox(c).page==null&&inbox(c).message.contains("过期"),"Repeated410 stops after one root restart"));
                context.takeScreenshot("paletale-notices-v2-expired-bounded-v0.11.14");
                context.runOnClient(c->inbox(c).refresh());await(context);
                fixture.getStatus=429;context.runOnClient(c->inbox(c).next());await(context);
                context.runOnClient(c->require(inbox(c).page.v2()&&NoticeWatch.unreadCount()==37&&inbox(c).message.contains("频繁"),"429 preserves v2 list and badge"));
                fixture.getStatus=0;
                for(int scale:new int[]{3,4}){
                    context.runOnClient(c->{c.options.guiScale().set(scale);c.resizeDisplay();});await(context);context.waitTicks(2);
                    context.takeScreenshot("paletale-notices-v2-groups-scale"+scale+"-v0.11.14");
                }
                fixture.legacy=true;fixture.legacyCount=false;
                var legacyPreview=new NoticeV2Api(fixture::site).unread("fixture-token").get(5,java.util.concurrent.TimeUnit.SECONDS);
                context.runOnClient(c->{NoticeWatch.updateUnread(0);NoticeWatch.acceptPreview(legacyPreview);require(NoticeWatch.unreadCount()>0&&!NoticeWatch.countExact(),"Old server without exact total keeps an unknown-count notification badge");});
                context.runOnClient(c->{NoticeWatch.updateUnread(38);c.setScreen(new NotificationsScreen(null,new NoticeApi(fixture.site()),()->"fixture-token"));});await(context);
                context.runOnClient(c->require(!inbox(c).page.v2()&&inbox(c).page.items().size()==4&&!NoticeWatch.countExact(),"Old200 schema renders legacy fallback without dropping rows"));
                context.takeScreenshot("paletale-notices-legacy-fallback-v0.11.14");
            }finally{context.runOnClient(c->{PaleTaleClient.MARKET.retarget(previousSite);NoticeWatch.updateUnread(0);c.setScreen(null);c.getWindow().setWindowed(viewport[0],viewport[1]);c.options.guiScale().set(viewport[2]);c.resizeDisplay();});}
        }
    }
    private static NoticeInbox inbox(net.minecraft.client.Minecraft c){return ((NotificationsScreen)c.screen).inbox();}
    private static void await(ClientGameTestContext context){context.waitFor(c->c.screen instanceof NotificationsScreen screen&&screen.inbox()!=null&&!screen.inbox().loading&&!screen.inbox().busy,600);}
    private static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
}
