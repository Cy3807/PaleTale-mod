package cn.paletale.companion;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

public final class NoticeV2RegressionTest {
    private static int checks;
    public static void main(String[] args)throws Exception{run();}
    static void run()throws Exception{
        try(var fixture=new NoticeFixture()){
            var api=new NoticeV2Api(fixture::site);
            var preview=api.unread("fixture-token").get(5,TimeUnit.SECONDS);
            require(preview.unreadTotal()==38&&preview.items().size()==20,"Authoritative unread exceeds preview cap");
            require(preview.items().getFirst().id()>Integer.MAX_VALUE,"Long IDs survive HTTP parsing");
            var unread=new AtomicLong(38);var inbox=new NoticeInbox(api,()->"fixture-token",Runnable::run,()->{},unread::set);
            inbox.refresh();await(()->inbox.page!=null&&!inbox.loading);
            var groups=inbox.page;require(groups.groups().size()==8&&groups.groups().getFirst().count()==21,"One stable ad group contains all 21 cross-page children");
            require(fixture.requests.stream().noneMatch(s->s.startsWith("POST")),"GET groups never marks read");
            inbox.expand(groups.groups().getFirst());await(()->!inbox.loading);
            var first=inbox.page;require(first.items().size()==8&&inbox.details(),"Expand reads first detail page only");
            inbox.next();await(()->!inbox.loading);var second=inbox.page;
            require(Collections.disjoint(first.items().stream().map(NoticeV2Api.Item::id).toList(),second.items().stream().map(NoticeV2Api.Item::id).toList()),"Detail cursor pagination has no repeated IDs");
            inbox.next();await(()->!inbox.loading);require(inbox.page.items().size()==5&&!inbox.canNext(),"Third child page reaches all 21 entries");
            inbox.previous();inbox.collapse();inbox.next();await(()->!inbox.loading);
            require(inbox.page.groups().stream().noneMatch(g->g.key().equals("ad:1")),"Same ad never reappears on next group page");
            inbox.previous();inbox.expand(groups.groups().get(6));await(()->!inbox.loading);
            require(inbox.page.items().getFirst().targetType().equals("other"),"Other notifications survive expansion");
            inbox.collapse();inbox.expand(groups.groups().get(7));await(()->!inbox.loading);
            require(!inbox.page.items().getFirst().available()&&!inbox.page.items().getFirst().detail().isBlank(),"Unavailable target retains notification text");
            require(fixture.requests.stream().noneMatch(s->s.startsWith("POST")),"Expand and all pagination remain read-only");
            fixture.writeStatus=503;long id=inbox.page.items().getFirst().id();inbox.toggle(id);var old=inbox.page;
            inbox.mutate(false,false);await(()->!inbox.busy);
            require(inbox.page==old&&inbox.selected.contains(id)&&unread.get()==38&&inbox.message.contains("可能已生效"),"Unconfirmed write retains list, selection and unread count");
            fixture.writeStatus=429;inbox.mutate(false,false);await(()->!inbox.busy);
            require(inbox.page==old&&inbox.selected.contains(id)&&inbox.message.contains("频繁"),"429 never downgrades or clears state");
            fixture.writeStatus=0;fixture.partial=true;inbox.collapse();inbox.expand(groups.groups().getFirst());await(()->!inbox.loading);
            inbox.selectAll();int before=fixture.requests.size();inbox.mutate(false,false);await(()->!inbox.busy&&!inbox.loading&&inbox.page!=null&&inbox.page.mode().equals("groups"));
            require(unread.get()==37&&inbox.message.contains("另有 7 条尚未确认"),"Partial confirmation uses server total, never requested-count subtraction");
            require(fixture.requests.subList(before,fixture.requests.size()).stream().anyMatch(s->s.contains("view=groups")&&!s.contains("cursor=")),"Confirmed write rebuilds root snapshot");
            int roots=fixture.requests.size();fixture.expireNext.set(2);inbox.next();await(()->!inbox.loading);
            require(inbox.page==null&&fixture.requests.size()-roots==2&&inbox.message.contains("过期"),"410 discards snapshot and auto-restarts at most once");
            inbox.refresh();await(()->!inbox.loading&&inbox.page!=null);fixture.getStatus=503;old=inbox.page;inbox.next();await(()->!inbox.loading);
            require(inbox.page==old&&unread.get()==37,"Failed GET preserves last visible list and count");
            fixture.getStatus=0;fixture.missing=true;inbox.expand(inbox.page.groups().getFirst());await(()->!inbox.loading);
            require(inbox.page.missingIds().size()==1&&inbox.page.items().size()==7&&inbox.canNext(),"Missing raw IDs retain cursor progress");
            fixture.missing=false;inbox.toggle(inbox.page.items().getFirst().id());inbox.mutate(true,false);await(()->!inbox.loading&&!inbox.busy&&inbox.page!=null&&inbox.page.mode().equals("groups"));
            require(fixture.requests.stream().anyMatch(s->s.contains("/delete?v=2")&&s.contains("4000000")),"Delete transmits explicit long IDs and rebuilds snapshot");
            inbox.mutate(true,true);await(()->!inbox.loading&&!inbox.busy&&inbox.page!=null);
            require(unread.get()==2,"Clear-all uses returned unread total when new notifications remain");
            fixture.legacy=true;var legacyApi=new NoticeV2Api(fixture::site);var legacy=legacyApi.groups("fixture-token",null).get(5,TimeUnit.SECONDS);
            require(!legacy.v2()&&legacy.pageSize()==8&&legacy.items().size()==4,"200 old schema explicitly falls back to legacy paginated view");
            fixture.legacyCount=false;inbox.refresh();await(()->!inbox.loading&&inbox.page!=null);
            require(unread.get()==-1,"Direct legacy list without authoritative count invalidates a previous exact count");
            fixture.legacy=false;fixture.getStatus=429;int requests=fixture.requests.size();
            try{new NoticeV2Api(fixture::site).groups("fixture-token",null).get(5,TimeUnit.SECONDS);throw new AssertionError("429 expected");}catch(ExecutionException expected){require(NoticeInbox.cause(expected) instanceof NoticeV2Api.Failure failure&&failure.status==429,"HTTP429 remains a typed failure");}
            require(fixture.requests.size()==requests+1,"No legacy retry after HTTP failure");
        }
        System.out.println("PaleTale notification v2 regression checks passed: "+checks);
    }
    private static void await(BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!condition.getAsBoolean()){if(System.nanoTime()>end)throw new AssertionError("Timed out");Thread.sleep(5);}}
    private static void require(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
}
