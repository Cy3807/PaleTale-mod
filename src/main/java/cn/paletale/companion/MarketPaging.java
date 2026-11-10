package cn.paletale.companion;
import java.util.ArrayList;
import java.util.List;
import static cn.paletale.companion.MarketData.*;
/** A visible page may straddle several fixed-size API batches. Never expose their short tail as a screen page. */
public final class MarketPaging {
 /** The site's batch size, and the most ads one screen page may hold (two batches). */
 public static final int BATCH=12,MAX_ROWS=24;
 private MarketPaging(){}
 public static int firstBatch(int page,int rows){if(page<1||rows<1||rows>MAX_ROWS)throw new IllegalArgumentException();return ((page-1)*rows)/BATCH+1;}
 /** Whether the batches loaded so far (consecutive from {@link #firstBatch}, {@code last} being the latest) still leave the screen page short. */
 public static boolean needsNext(int page,int rows,Page last){return (page-1)*rows+rows>last.page()*BATCH&&last.hasMore();}
 public static Page window(int page,int rows,Page first,Page next){return window(page,rows,next==null?List.of(first):List.of(first,next));}
 public static Page window(int page,int rows,List<Page> batches){
  int offset=(page-1)*rows,firstBatch=firstBatch(page,rows),skip=offset-(firstBatch-1)*BATCH;var first=batches.getFirst();
  if(first.page()!=firstBatch||first.pageSize()!=BATCH)throw new IllegalStateException("广告页码发生变化，请重新搜索。");
  List<Ad> ads=new ArrayList<>();
  for(int i=0;i<batches.size();i++){
   var batch=batches.get(i);
   if(batch.page()!=firstBatch+i||batch.pageSize()!=BATCH||batch.total()!=first.total())throw new IllegalStateException("广告列表已更新，请重新搜索。");
   ads.addAll(batch.ads());
  }
  if(needsNext(page,rows,batches.getLast()))throw new IllegalStateException("广告列表已更新，请重新搜索。");
  int count=Math.min(rows,Math.max(0,first.total()-offset));
  if(count>0&&skip+count>ads.size())throw new IllegalStateException("广告列表未完整读取，请重新搜索。");
  return new Page(page,rows,first.total(),offset+count<first.total(),List.copyOf(ads.subList(Math.min(skip,ads.size()),Math.min(skip,ads.size())+count)));
 }
}
