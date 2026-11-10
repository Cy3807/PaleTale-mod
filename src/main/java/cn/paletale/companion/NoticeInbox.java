package cn.paletale.companion;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** UI-thread state machine. GET is read-only; writes never optimistically clear unread state. */
public final class NoticeInbox {
    private final NoticeV2Api api;
    private final Supplier<String> token;
    private final Executor ui;
    private final Runnable changed;
    private final LongConsumer unread;
    private final List<NoticeV2Api.Page> groupPages=new ArrayList<>(), detailPages=new ArrayList<>();
    private int groupIndex, detailIndex, generation;
    private NoticeV2Api.Group expanded;
    public volatile NoticeV2Api.Page page;
    public volatile boolean loading, busy;
    public volatile String message="";
    public final Set<Long> selected=new LinkedHashSet<>();
    /** Group rows are ticked by key; reading or deleting them applies to every notice inside. */
    public final Set<String> selectedGroups=new LinkedHashSet<>();
    public NoticeInbox(NoticeV2Api api,Supplier<String> token,Executor ui,Runnable changed,LongConsumer unread){this.api=api;this.token=token;this.ui=ui;this.changed=changed;this.unread=unread;}
    public boolean details(){return expanded!=null;}
    public int pageNumber(){return (details()?detailIndex:groupIndex)+1;}
    public boolean canPrevious(){return !loading&&!busy&&(details()?detailIndex:groupIndex)>0;}
    public boolean canNext(){return !loading&&!busy&&page!=null&&(page.v2()?page.nextCursor()!=null:(long)page.legacyPage()*page.pageSize()<page.total());}
    public void refresh(){restart(1);}
    public void close(){generation++;}
    private void restart(int recovery){
        generation++;groupPages.clear();detailPages.clear();groupIndex=detailIndex=0;expanded=null;selected.clear();selectedGroups.clear();
        fetch(null,false,recovery,true);
    }
    public void expand(NoticeV2Api.Group group){if(loading||busy)return;expanded=group;detailPages.clear();detailIndex=0;selected.clear();selectedGroups.clear();fetch(group.detailsCursor(),true,1,false);}
    public void collapse(){if(loading||busy)return;expanded=null;selected.clear();selectedGroups.clear();page=groupPages.isEmpty()?null:groupPages.get(groupIndex);changed.run();}
    public void previous(){if(!canPrevious())return;selected.clear();selectedGroups.clear();if(details())page=detailPages.get(--detailIndex);else page=groupPages.get(--groupIndex);message="";changed.run();}
    public void next(){
        if(!canNext())return;selected.clear();selectedGroups.clear();var pages=details()?detailPages:groupPages;int index=details()?detailIndex:groupIndex;
        if(index+1<pages.size()){if(details())detailIndex++;else groupIndex++;page=pages.get(index+1);message="";changed.run();return;}
        if(!page.v2())fetchLegacy(page.legacyPage()+1);else fetch(page.nextCursor(),details(),1,false);
    }
    private String begin(){String value=token.get();if(value==null){message="请先登录 PaleTale 账号。";loading=busy=false;changed.run();return null;}return value;}
    private void fetchLegacy(int number){String value=begin();if(value==null)return;loading=true;changed.run();int run=++generation;api.legacyPage(value,number).whenComplete((data,error)->ui.execute(()->loaded(run,value,data,error,false,0,false)));}
    private void fetch(String cursor,boolean detail,int recovery,boolean root){
        String value=begin();if(value==null)return;loading=true;changed.run();int run=++generation;
        CompletableFuture<NoticeV2Api.Page> request=detail?api.details(value,cursor):api.groups(value,cursor);
        request.whenComplete((data,error)->ui.execute(()->loaded(run,value,data,error,detail,recovery,root)));
    }
    private boolean current(int run,String value){
        if(run!=generation)return false;
        if(!Objects.equals(value,token.get())){page=null;groupPages.clear();detailPages.clear();selected.clear();selectedGroups.clear();loading=busy=false;message="模组会话已变更，请重新打开通知。";changed.run();return false;}
        return true;
    }
    private void loaded(int run,String value,NoticeV2Api.Page data,Throwable error,boolean detail,int recovery,boolean root){
        if(!current(run,value))return;loading=false;
        if(error!=null){
            var cause=cause(error);
            if(cause instanceof NoticeV2Api.Failure failure&&failure.status==410){
                // Every expired snapshot is discarded. At most one automatic restart per user operation.
                page=null;groupPages.clear();detailPages.clear();selected.clear();expanded=null;
                if(recovery>0){message="通知列表已更新，正在重新读取…";restart(recovery-1);return;}
            }
            if(detail&&detailPages.isEmpty()){expanded=null;page=groupPages.isEmpty()?null:groupPages.get(groupIndex);}
            message=errorMessage(error);changed.run();return;
        }
        var pages=detail?detailPages:groupPages;
        if(!root&&!pages.isEmpty()&&data.v2()&&!pages.getFirst().snapshot().equals(data.snapshot())){message="通知列表不一致，请刷新后重试。";changed.run();return;}
        if(detail&&!data.groupKey().equals(expanded.key())){message="通知分组不一致，请刷新后重试。";changed.run();return;}
        if(root){groupPages.clear();groupIndex=0;expanded=null;}
        pages.add(data);if(detail)detailIndex=pages.size()-1;else groupIndex=pages.size()-1;
        page=data;unread.accept(data.unreadTotal());
        if(message.startsWith("通知列表"))message="";
        changed.run();
    }
    public void selectAll(){if(page==null||loading||busy)return;boolean all=!page.items().isEmpty()&&page.items().stream().allMatch(item->selected.contains(item.id()));if(all)selected.clear();else page.items().forEach(item->selected.add(item.id()));changed.run();}
    public void toggle(long id){if(loading||busy)return;if(!selected.remove(id))selected.add(id);changed.run();}
    public void mutate(boolean delete,boolean all){
        if(page==null||loading||busy||!all&&selected.isEmpty())return;String value=begin();if(value==null)return;
        var requested=List.copyOf(selected);boolean v2=page.v2();busy=true;message="正在等待服务端确认…";changed.run();int run=++generation;
        api.mutate(value,all?List.of():requested,delete,all,v2).whenComplete((result,error)->ui.execute(()->{
            if(!current(run,value))return;busy=false;
            if(error!=null){message=errorMessage(error);changed.run();return;}
            // Only confirmedIds count as acknowledged. All visible state is then re-read in a new snapshot.
            if(result.v2()){
                unread.accept(result.unreadTotal());long pending=all?0:requested.stream().filter(id->!result.confirmedIds().contains(id)).count();
                message=all?"清空请求已确认，正在重新读取。":"已确认 "+result.confirmedIds().size()+" 条"+(pending>0?"；另有 "+pending+" 条尚未确认。":"。");
            }else message="旧版请求已提交，正在重新读取；未读数量以服务器返回为准。";
            page=null;restart(1);
        }));
    }
    public void toggleGroup(String key){if(loading||busy)return;if(!selectedGroups.remove(key))selectedGroups.add(key);changed.run();}
    /** Reads or deletes every notice of the ticked groups without opening them: reads each group's pages, then confirms the ids in chunks. */
    public void mutateGroups(boolean delete){
        if(page==null||loading||busy||!page.v2()||details()||selectedGroups.isEmpty())return;String value=begin();if(value==null)return;
        var chosen=page.groups().stream().filter(group->selectedGroups.contains(group.key())).toList();
        busy=true;message="正在等待服务端确认…";changed.run();int run=++generation;
        CompletableFuture<List<Long>> collected=CompletableFuture.completedFuture(new ArrayList<>());
        for(var group:chosen)collected=collected.thenCompose(ids->collectIds(value,group.detailsCursor(),ids,0,!delete).thenApply(list->list));
        collected.thenCompose(ids->mutateChunks(value,ids,0,0,delete)).whenComplete((confirmed,error)->ui.execute(()->{
            if(!current(run,value))return;busy=false;
            if(error!=null){message=errorMessage(error);changed.run();return;}
            message="已确认 "+confirmed+" 条"+(delete?"已删除":"已读")+"。";page=null;restart(1);
        }));
    }
    private CompletableFuture<List<Long>> collectIds(String value,String cursor,List<Long> ids,int pages,boolean unreadOnly){
        return api.details(value,cursor).thenCompose(data->{
            for(var item:data.items())if(!unreadOnly||!item.read())ids.add(item.id());
            if(data.nextCursor()!=null&&pages<49)return collectIds(value,data.nextCursor(),ids,pages+1,unreadOnly);
            return CompletableFuture.completedFuture(ids);
        });
    }
    private CompletableFuture<Integer> mutateChunks(String value,List<Long> ids,int from,int confirmed,boolean delete){
        if(from>=ids.size())return CompletableFuture.completedFuture(confirmed);
        int size=delete?100:50;var chunk=ids.subList(from,Math.min(ids.size(),from+size));
        return api.mutate(value,List.copyOf(chunk),delete,false,true).thenCompose(result->{
            unread.accept(result.unreadTotal());
            return mutateChunks(value,ids,from+size,confirmed+(delete?chunk.size():result.confirmedIds().size()),delete);
        });
    }
    public static Throwable cause(Throwable error){while(error instanceof CompletionException||error instanceof ExecutionException){if(error.getCause()==null)break;error=error.getCause();}return error;}
    public static String errorMessage(Throwable error){var value=cause(error);return value instanceof NoticeV2Api.Failure?value.getMessage():"通知结果尚未确认，请刷新核对或重试。";}
}
