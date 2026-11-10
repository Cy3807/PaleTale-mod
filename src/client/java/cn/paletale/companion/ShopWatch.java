package cn.paletale.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** Known bound signs only: bounded main-thread copies, then background selection, parsing, comparison and HTTP. */
final class ShopWatch {
    private static final long SCAN_MS=10_000,REFRESH_MS=300_000,COPY_BUDGET_NS=500_000;
    private static final int COPIES_PER_TICK=8;
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(task->{var t=new Thread(task,"paletale-shop-scan");t.setDaemon(true);return t;});
    private final ShopApi api;
    private final Supplier<String> session;
    private List<ShopApi.Position> watched=List.of();
    private final Map<String,ShopSigns.Reading> lastSent=new HashMap<>();
    private final Map<String,Long> sentAt=new HashMap<>();
    private long nextScan,nextRefresh,generation;
    private boolean refreshing,wasLoggedIn;
    private String activeToken;
    private Scan scan;
    private static final class Scan {
        final ClientLevel level;
        final String token;
        final long generation;
        final java.util.ArrayList<ShopScanData.Sample> samples=new java.util.ArrayList<>();
        List<ShopApi.Position> positions;
        int cursor;
        boolean processing;
        volatile boolean cancelled;
        Scan(ClientLevel level,String token,long generation){this.level=level;this.token=token;this.generation=generation;}
    }
    ShopWatch(ShopApi api){this(api,()->PaleTaleClient.ACCOUNT.token());}
    ShopWatch(ShopApi api,Supplier<String> session){this.api=api;this.session=session;}
    private void cancelScan(){if(scan!=null)scan.cancelled=true;scan=null;}
    void refresh(Minecraft client) {
        String token=session.get();if(token==null||refreshing)return;
        refreshing=true;long run=generation;
        api.mineDetailed(token).whenComplete((shops,failure)->client.execute(()->{
            if(run!=generation||!token.equals(session.get()))return;
            refreshing=false;nextRefresh=System.currentTimeMillis()+REFRESH_MS;
            if(failure!=null)return;
            cancelScan();ShopIndex.set(shops);LedgerStore.relink();
            watched=shops.stream().map(ShopApi.MyShop::sign).distinct().toList();
            var keys=watched.stream().map(ShopApi.Position::key).collect(java.util.stream.Collectors.toSet());
            lastSent.keySet().retainAll(keys);sentAt.keySet().retainAll(keys);
        }));
    }
    void clear(){generation++;cancelScan();ShopIndex.set(List.of());watched=List.of();lastSent.clear();sentAt.clear();wasLoggedIn=false;refreshing=false;activeToken=null;nextScan=nextRefresh=0;}
    private boolean valid(Scan work,Minecraft client){return scan==work&&!work.cancelled&&work.generation==generation&&client.level==work.level&&client.player!=null&&work.token.equals(session.get());}
    void tick(Minecraft client) {
        String token=session.get();
        if(!java.util.Objects.equals(activeToken,token)){clear();activeToken=token;}
        if(token==null)return;
        if(client.level==null||client.player==null){if(wasLoggedIn)clear();return;}
        long now=System.currentTimeMillis();
        if(!wasLoggedIn||now>=nextRefresh){wasLoggedIn=true;refresh(client);}
        if(scan!=null){if(!valid(scan,client)){cancelScan();return;}copySlice(client,scan);return;}
        if(watched.isEmpty()||now<nextScan)return;
        nextScan=now+SCAN_MS;
        var work=new Scan(client.level,session.get(),generation);scan=work;
        var positions=watched;String world=ShopScanner.world(client);
        double x=client.player.getX(),y=client.player.getY(),z=client.player.getZ();
        CompletableFuture.supplyAsync(()->work.cancelled?List.<ShopApi.Position>of():ShopScanData.nearby(positions,world,x,y,z),WORKER).whenComplete((nearby,error)->client.execute(()->{
            if(!valid(work,client))return;
            if(error!=null){cancelScan();return;}work.positions=nearby;
        }));
    }
    private void copySlice(Minecraft client,Scan work) {
        if(work.positions==null||work.processing)return;
        long began=System.nanoTime();int count=0;
        while(work.cursor<work.positions.size()&&count<COPIES_PER_TICK&&(count==0||System.nanoTime()-began<COPY_BUDGET_NS)) {
            var p=work.positions.get(work.cursor++);count++;
            var sample=ShopScanner.snapshot(client,p,lastSent.get(p.key()),sentAt.getOrDefault(p.key(),0L));
            if(sample!=null)work.samples.add(sample);
        }
        if(work.cursor<work.positions.size())return;
        work.processing=true;var samples=List.copyOf(work.samples);
        CompletableFuture.supplyAsync(()->work.cancelled?List.<ShopSigns.Reading>of():ShopScanData.changed(samples,System.currentTimeMillis()),WORKER).whenComplete((changed,error)->client.execute(()->{
            if(!valid(work,client))return;
            if(error!=null||changed.isEmpty()){cancelScan();return;}
            // JSON construction also runs off-thread; at most one scan/report is pending.
            CompletableFuture.supplyAsync(()->work.cancelled?CompletableFuture.<Integer>failedFuture(new java.util.concurrent.CancellationException()):api.report(work.token,changed),WORKER)
                    .thenCompose(future->future).whenComplete((updated,failure)->client.execute(()->{
                        if(failure!=null)ModLog.failed("箱子商店上报",failure);
                        if(!valid(work,client))return;
                        if(failure==null){long sent=System.currentTimeMillis();changed.forEach(r->{lastSent.put(r.key(),r);sentAt.put(r.key(),sent);});}
                        cancelScan();
                    }));
        }));
    }
}
