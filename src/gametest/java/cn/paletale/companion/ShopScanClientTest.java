package cn.paletale.companion;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;

/** Real client chunks and mock HTTP: no production account, world or database writes. */
final class ShopScanClientTest {
    static void run(ClientGameTestContext context) throws Exception {
        var positions=new AtomicReference<List<ShopApi.Position>>();
        var reports=new AtomicInteger();var mines=new AtomicInteger();
        var gate=new AtomicReference<CountDownLatch>();
        var last=new AtomicReference<com.google.gson.JsonObject>();
        var token=new AtomicReference<>("scan-test-session");
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        var httpWorkers=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();server.setExecutor(httpWorkers);
        server.createContext("/api/mcmod/shops/",exchange->{
            var response=new com.google.gson.JsonObject();
            if(exchange.getRequestURI().getPath().endsWith("mine")) {
                mines.incrementAndGet();var rows=new com.google.gson.JsonArray();
                for(var p:positions.get()) {var row=new com.google.gson.JsonObject();row.addProperty("world",p.world());row.addProperty("x",p.x());row.addProperty("y",p.y());row.addProperty("z",p.z());rows.add(row);}
                response.add("shops",rows);
            } else {
                var body=com.google.gson.JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                last.set(body);reports.incrementAndGet();var latch=gate.get();
                if(latch!=null)try{latch.await(10,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
                response.addProperty("updated",body.getAsJsonArray("shops").size());
            }
            byte[] bytes=response.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);
            try(var stream=exchange.getResponseBody()){stream.write(bytes);}
        });server.start();
        var watch=new ShopWatch(new ShopApi(java.net.URI.create("http://127.0.0.1:"+server.getAddress().getPort())),token::get);
        try {
            context.runOnClient(client->{
                var base=client.player.blockPosition();var list=new java.util.ArrayList<ShopApi.Position>();
                for(int i=0;i<40;i++) {
                    var pos=base.offset(1+i%10,2,1+i/10);
                    var sign=new SignBlockEntity(pos,Blocks.OAK_WALL_SIGN.defaultBlockState());sign.setLevel(client.level);
                    sign.setText(sign.getFrontText().setMessage(0,Component.literal("ShopKeeper")).setMessage(1,Component.literal("64")).setMessage(2,Component.literal("B 10")).setMessage(3,Component.literal("Stone")),true);
                    client.level.getChunkAt(pos).getBlockEntities().put(pos,sign);
                    list.add(new ShopApi.Position(ShopScanner.world(client),pos.getX(),pos.getY(),pos.getZ()));
                }
                positions.set(List.copyOf(list));
            });
            var slices=new AtomicInteger();
            context.waitFor(client->{
                Object before=field(watch,"scan");int cursor=before==null?0:(int)field(before,"cursor");
                watch.tick(client);
                if(before!=null){int copied=(int)field(before,"cursor")-cursor;check(copied<=8,"No client tick copies more than eight bound positions");if(copied>0)slices.incrementAndGet();}
                return reports.get()==1&&field(watch,"scan")==null;
            });
            check(slices.get()>=5&&last.get().getAsJsonArray("shops").size()==40,"Dense bound shops are copied across multiple ticks then reported as a complete batch");
            context.runOnClient(client->set(watch,"nextScan",0L));
            context.waitFor(client->{watch.tick(client);return field(watch,"scan")==null;});
            check(reports.get()==1,"Unchanged scan does not send an extra HTTP report");
            context.runOnClient(client->{
                var p=positions.get().getFirst();var sign=(SignBlockEntity)client.level.getChunkAt(new BlockPos(p.x(),p.y(),p.z())).getBlockEntities().get(new BlockPos(p.x(),p.y(),p.z()));
                var snapshot=ShopScanner.snapshot(client,p,null,0);
                sign.setText(sign.getFrontText().setMessage(1,Component.literal("0")),true);
                check(snapshot.lines().get(1).equals("64"),"A queued snapshot is independent of subsequent Minecraft sign edits");
                set(watch,"nextScan",0L);gate.set(new CountDownLatch(1));
            });
            context.waitFor(client->{watch.tick(client);return reports.get()==2;});
            check(last.get().getAsJsonArray("shops").size()==1&&last.get().getAsJsonArray("shops").get(0).getAsJsonObject().get("quantity").getAsInt()==0,"Only the changed sign reaches the asynchronous report");
            context.runOnClient(client->{set(watch,"nextScan",0L);for(int i=0;i<100;i++)watch.tick(client);check(reports.get()==2,"A pending report cannot pile up scans");token.set(null);watch.tick(client);});
            gate.get().countDown();context.waitTicks(5);
            context.runOnClient(client->check(((java.util.Map<?,?>)field(watch,"lastSent")).isEmpty()&&field(watch,"scan")==null,"Late report acknowledgements cannot restore a logged-out session"));
            token.set("renewed-session");
            context.waitFor(client->{watch.tick(client);return reports.get()==3&&field(watch,"scan")==null;});
            check(mines.get()==2,"A renewed session refreshes its bound list and can scan again");
            System.out.println("PaleTale asynchronous bound-shop scan: 40 real sign entities, at least "+slices.get()+" copy ticks, no overlapping report; not a gameplay FPS benchmark");
        } finally {
            if(gate.get()!=null)gate.get().countDown();
            context.runOnClient(client->{watch.clear();if(positions.get()!=null)for(var p:positions.get())client.level.getChunkAt(new BlockPos(p.x(),p.y(),p.z())).getBlockEntities().remove(new BlockPos(p.x(),p.y(),p.z()));});
            server.stop(0);httpWorkers.close();
        }
    }
    private static Object field(Object instance,String name){try{var f=instance.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(instance);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static void set(Object instance,String name,Object value){try{var f=instance.getClass().getDeclaredField(name);f.setAccessible(true);f.set(instance,value);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
