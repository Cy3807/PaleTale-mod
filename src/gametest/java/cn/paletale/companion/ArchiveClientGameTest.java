package cn.paletale.companion;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.EditBox;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;

final class ArchiveClientGameTest {
    private static final String UUID = "11111111222233334444555555555555";
    /** Queries the mock site received, to check which sort the screens ask for. */
    private static final java.util.Set<String> QUERIES = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Map<String,java.util.concurrent.atomic.AtomicInteger> IMAGE_REQUESTS=new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String,java.util.concurrent.CountDownLatch> IMAGE_GATES=new java.util.concurrent.ConcurrentHashMap<>();
    private static void persistentImages(ClientGameTestContext context,MarketApi media) {
        var cache=new MarketImages();
        String key="/api/players/"+java.util.UUID.randomUUID().toString().replace("-","")+"/avatar";
        context.runOnClient(client->cache.prefetch(media,key));context.waitFor(client->cache.loadedCount()==1);
        java.nio.file.Path file=context.computeOnClient(client->{try {var method=MarketImages.class.getDeclaredMethod("cacheFile",String.class);method.setAccessible(true);return(java.nio.file.Path)method.invoke(null,key);}catch(ReflectiveOperationException e){throw new AssertionError(e);}});
        context.runOnClient(client->{try {require(java.nio.file.Files.isRegularFile(file),"Player avatar is saved to disk");java.nio.file.Files.setLastModifiedTime(file,java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()-7*3600000L));cache.clear();}catch(java.io.IOException e){throw new AssertionError(e);}});
        var gate=new java.util.concurrent.CountDownLatch(1);IMAGE_GATES.put(key,gate);
        try {
            context.runOnClient(client->cache.prefetch(media,key));
            context.waitFor(client->cache.loadedCount()==1);
            context.waitFor(client->IMAGE_REQUESTS.get(key).get()==2);
            context.runOnClient(client->require(gate.getCount()==1&&cache.loadedCount()==1,"Stale player avatar is displayed while the network refresh is still blocked"));
        }finally{gate.countDown();IMAGE_GATES.remove(key);}
        context.waitFor(client->!cache.loading(media,key));
        int calls=IMAGE_REQUESTS.get(key).get();context.runOnClient(client->{cache.clear();cache.prefetch(media,key);});context.waitFor(client->cache.loadedCount()==1);
        context.runOnClient(client->require(IMAGE_REQUESTS.get(key).get()==calls,"Recreating player textures from a fresh disk cache makes no HTTP image request"));
        var releaseWorkers=new java.util.concurrent.CountDownLatch(1);var workersReady=new java.util.concurrent.CountDownLatch(2);var downloads=context.computeOnClient(client->(java.util.concurrent.ExecutorService)WorldClientGameTest.field(cache,"worker"));for(int i=0;i<2;i++)downloads.execute(()->{workersReady.countDown();try{releaseWorkers.await(10,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        try{try{require(workersReady.await(5,java.util.concurrent.TimeUnit.SECONDS),"download workers occupied");}catch(InterruptedException e){throw new AssertionError(e);}context.runOnClient(client->{cache.clear();cache.prefetch(media,key);});context.waitFor(client->cache.loadedCount()==1);context.runOnClient(client->require(releaseWorkers.getCount()==1,"disk avatar displays while both download workers are blocked"));}finally{releaseWorkers.countDown();}
        String hash=java.util.UUID.randomUUID().toString().replace("-","").repeat(2),npc="/api/mcmod/nav/npcs/scan-"+hash.substring(0,24)+"/avatar/"+hash;
        context.runOnClient(client->{cache.clear();cache.prefetch(media,npc);});context.waitFor(client->cache.loadedCount()==1);
        var npcFile=file.getParent().resolve("npc").resolve(hash+".png");
        context.runOnClient(client->{try {require(java.nio.file.Files.isRegularFile(npcFile),"Newly discovered NPC avatar paths are allowed and cached by immutable skin hash");java.nio.file.Files.setLastModifiedTime(npcFile,java.nio.file.attribute.FileTime.fromMillis(0));cache.clear();cache.prefetch(media,npc);}catch(java.io.IOException e){throw new AssertionError(e);}});
        context.waitFor(client->cache.loadedCount()==1);
        context.runOnClient(client->require(IMAGE_REQUESTS.get(npc).get()==1,"Fixed NPC portraits survive texture teardown without visiting the NPC or downloading again"));
        context.runOnClient(client->{
            try {
                var path=java.nio.file.Files.createTempFile("paletale-skin-v0.11.10-",".png");
                try(var skin=new com.mojang.blaze3d.platform.NativeImage(64,64,true)) {
                    skin.fillRect(0,0,64,64,0);skin.fillRect(8,8,8,8,0xffff0000);skin.fillRect(40,8,8,8,0x800000ff);skin.writeToFile(path);
                    try(var portrait=NpcPortrait.fromSkin(java.nio.file.Files.readAllBytes(path))) {int pixel=portrait.getPixel(40,40);require(portrait.getWidth()==80&&((pixel>>>16)&255)==127&&(pixel&255)==128,"Local fixed-skin fallback blends the correct face and hat UV layers");}
                }finally{java.nio.file.Files.deleteIfExists(path);}
                require(NpcPortrait.fromSkin(new byte[24])==null,"Malformed skin images cannot enter the persistent portrait cache");
            }catch(java.io.IOException e){throw new AssertionError(e);}
            client.getToastManager().clear();
            client.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("头像缓存验证")) {
                @Override public void renderBackground(net.minecraft.client.gui.GuiGraphics g,int x,int y,float delta){g.fill(0,0,width,height,MarketTheme.PAPER);}
                @Override public void render(net.minecraft.client.gui.GuiGraphics g,int x,int y,float delta){g.drawString(font,"NPC头像：已从磁盘恢复，无需重新接近NPC",20,20,MarketTheme.INK,false);cache.draw(g,media,npc,20,40,64);}
            });
        });
        context.takeScreenshot("paletale-persistent-portraits-v0.11.10");context.runOnClient(client->{cache.clear();client.setScreen(null);});
    }
    private static JsonObject entry(boolean player, int id) {
        JsonObject entry = new JsonObject();
        entry.addProperty("kind", player ? "players" : "items"); entry.addProperty("id", id);
        entry.addProperty("name", player ? "ArchivePlayer" : "樱花测试藏品" + id);
        entry.addProperty("subtitle", player ? "档案测试玩家" : "拥有者 · ArchivePlayer");
        entry.addProperty("description", "这是用于验证公开档案的中文简介。\n支持独立页面、图片预览与完整文字阅读。");
        entry.addProperty("category", player ? "player" : id % 2 == 0 ? "dish" : "wine");
        entry.addProperty("badge", player ? "玩家" : id % 2 == 0 ? "菜" : "3D 酒");
        entry.addProperty("hasModel", !player && id % 2 != 0);
        if (!player) { entry.addProperty("views", 40 - id); entry.addProperty("favorites", id); }
        if (!player && id % 2 != 0) { entry.addProperty("effect", "speed"); entry.addProperty("effectText", "迅捷 II"); }
        if (!player && id == 1) entry.addProperty("brand", "&#fff5e1&lଘ&#fff3e2&l玩&#fff2e4&l家&#ffd1e8&l1\n#ffcee6&l牵着#ffadd3&l手✧一同许下心愿");
        entry.addProperty("imagePath", player ? "/api/players/" + UUID + "/avatar" : "/api/mcmod/items/" + id + "/image");
        if (!player && id <= 3) {
            JsonObject appearance = new JsonObject();
            if (Boolean.getBoolean("paletale.test.nativeModels")) {
                appearance.addProperty("itemModel", id == 2 ? "smc:food/kejinshiwu/food_gn511_7" : "");
                if (id != 2) appearance.addProperty("customModelData", id == 1 ? 30009 : 30008);
            } else appearance.addProperty("itemModel", id == 3 ? "paletale_test:missing" : "paletale_test:test");
            entry.add("nativeAppearance", appearance);
        }
        if (player) entry.add("owner", null);
        else {
            JsonObject owner = new JsonObject();owner.addProperty("playerId", 12);owner.addProperty("name", "档案测试玩家");owner.addProperty("mcName", "ArchivePlayer");owner.addProperty("verified", true);owner.addProperty("avatarPath", "/api/players/" + UUID + "/avatar");entry.add("owner", owner);
        }
        return entry;
    }
    /** Replay the user's exact unregistered NPC through the production discovery/tick/cache path. No live server upload. */
    private static void automaticNpcDiscovery(ClientGameTestContext context,MarketApi media) {
        String id="scan-77e78c9591c88e485a6294f9";
        context.runOnClient(client->{
            try {
                var dynamic=CityCatalog.class.getDeclaredField("DISCOVERED");dynamic.setAccessible(true);
                ((java.util.Map<?,?>)dynamic.get(null)).remove(id);
                var profiles=CityNpcSync.class.getDeclaredField("profiles");profiles.setAccessible(true);((java.util.Map<?,?>)profiles.get(null)).remove(id);
                var dimension=net.minecraft.world.level.Level.class.getDeclaredField("dimension");dimension.setAccessible(true);Object world=dimension.get(client.level);
                var at=client.player.position();var upload=CityNpcSync.class.getDeclaredField("uploadAfter");upload.setAccessible(true);long uploadBefore=upload.getLong(null);
                var fetch=CityNpcSync.class.getDeclaredField("fetchAfter");fetch.setAccessible(true);long fetchBefore=fetch.getLong(null);
                var inCity=CityNpcSync.class.getDeclaredField("inCity");inCity.setAccessible(true);boolean wasCity=inCity.getBoolean(null);
                var npc=new net.minecraft.client.player.RemotePlayer(client.level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"妖精奇遇商店兑换(左键跳转到宝箱获取界面)"));
                npc.setPos(154.6841865802189,39,44.70572288187195);
                try {
                    dimension.set(client.level,net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.Identifier.parse(CityNavigation.WORLD)));
                    client.player.setPos(152,39,44);upload.setLong(null,Long.MAX_VALUE);fetch.setLong(null,Long.MAX_VALUE);inCity.setBoolean(null,true);
                    var candidate=CityNpcScan.class.getDeclaredMethod("candidate",net.minecraft.world.entity.Entity.class);candidate.setAccessible(true);candidate.invoke(null,npc);
                    var pending=CityNpcScan.class.getDeclaredField("nextPending");pending.setAccessible(true);pending.setLong(null,0);
                    CityNpcScan.tick(client);
                    require(CityCatalog.npcs().stream().noneMatch(p->p.id().equals(id)),"An unknown NPC is not accepted from a single unstable observation");
                    pending.setLong(null,0);CityNpcScan.tick(client);
                    require(CityCatalog.npcs().stream().anyMatch(p->p.id().equals(id))&&CityNpcScan.nearby().stream().anyMatch(p->p.id().equals(id)),"The user's real fairy-shop name/type/position is discovered and shown locally without a website");
                    require(CityNpcSync.profile(id)!=null,"New NPC metadata enters the durable upload queue even before its skin is ready");
                    require(!CityNpcSync.accepted(200,"<html>site</html>".getBytes(java.nio.charset.StandardCharsets.UTF_8))&&CityNpcSync.accepted(200,"{\"ok\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"An HTML 200 is not acknowledged as an upload or given a rapid success retry");
                }finally{CityNpcScan.forget(npc);dimension.set(client.level,world);client.player.setPos(at);upload.setLong(null,uploadBefore);fetch.setLong(null,fetchBefore);inCity.setBoolean(null,wasCity);}
            }catch(ReflectiveOperationException e){throw new AssertionError(e);}
        });
        context.waitFor(client->{
            try {
                var state=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(client.gameDirectory.toPath().resolve("paletale/cache/navigation/npcs.json"))).getAsJsonObject();
                return java.util.stream.StreamSupport.stream(state.getAsJsonArray("discovered").spliterator(),false).anyMatch(p->p.getAsJsonObject().get("id").getAsString().equals(id))&&java.util.stream.StreamSupport.stream(state.getAsJsonArray("profiles").spliterator(),false).anyMatch(p->p.getAsJsonObject().get("id").getAsString().equals(id));
            }catch(Exception pending){return false;}
        });
        context.runOnClient(client->{
            try {
                var dynamic=CityCatalog.class.getDeclaredField("DISCOVERED");dynamic.setAccessible(true);((java.util.Map<?,?>)dynamic.get(null)).remove(id);
                var loaded=CityNpcSync.class.getDeclaredField("loaded");loaded.setAccessible(true);loaded.setBoolean(null,false);CityNpcSync.prepare();
                require(CityCatalog.npcs().stream().anyMatch(p->p.id().equals(id))&&CityNpcSync.profile(id)!=null,"The discovered NPC and pending record survive a disk-cache reload");
            }catch(ReflectiveOperationException e){throw new AssertionError(e);}
            client.setScreen(new NavigationHubScreen(client.screen,media));
            var search=client.screen.children().stream().filter(c->c instanceof EditBox).map(EditBox.class::cast).findFirst().orElseThrow();search.setValue("妖精奇遇商店兑换");
        });
        context.waitTicks(3);context.takeScreenshot("paletale-auto-discovered-fairy-shop-v0.11.8");
    }

    /** Click a real product's three-shop navigation button and keep its complete detail window underneath. */
    private static void multipleShopDialog(ClientGameTestContext context,MarketApi media) {
        MarketDetailScreen[] parent=new MarketDetailScreen[1];
        context.runOnClient(client->{
            var shops=new java.util.ArrayList<MarketData.Shop>();
            for(int x=6621;x<=6623;x++)shops.add(new MarketData.Shop(new ShopSigns.Reading("minecraft:overworld",x,71,6515,"Tester","圆石",ShopSigns.Mode.SELL,64,null,1.0,"x+"),""));
            var product=new MarketData.Product("圆石","多箱子导航背景验证","1 云币","available",0,null,"","","minecraft:cobblestone","圆石",false,java.util.List.copyOf(shops));
            var ad=new MarketData.Ad("123456","多箱子测试商店","完整纸色详情应保留在导航弹窗下方。","active",java.util.List.of(),new MarketData.Author("测试商家","Tester","",0),java.util.List.of(product),java.util.List.of(),"","","",4,0);
            parent[0]=new MarketDetailScreen(client.screen,null,media,ad);
            client.setScreen(parent[0]);
        });
        context.waitTicks(3);
        double[] at=context.computeOnClient(client->{
            try {
                var field=MarketDetailScreen.class.getDeclaredField("navHits");field.setAccessible(true);
                @SuppressWarnings("unchecked") var hits=(java.util.List<int[]>)field.get(parent[0]);var hit=hits.getFirst();
                return new double[]{(parent[0].winX+hit[0]+hit[2]/2.0)*client.getWindow().getScreenWidth()/client.getWindow().getGuiScaledWidth(),(parent[0].winY+hit[1]+hit[3]/2.0)*client.getWindow().getScreenHeight()/client.getWindow().getGuiScaledHeight()};
            }catch(ReflectiveOperationException e){throw new AssertionError(e);}
        });
        context.getInput().setCursorPos(at[0],at[1]);context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitForScreen(NavigateScreen.class);
        context.waitTicks(3);
        context.takeScreenshot("paletale-multiple-shop-background-v0.11.7");
        realClick(context,"返回");context.waitForScreen(MarketDetailScreen.class);
        context.runOnClient(client->require(client.screen==parent[0],"Closing the shop picker restores the same detail screen"));
        context.runOnClient(client->client.screen.onClose());
    }

    /** Exercise replanning across real client dimension keys and successive teleport landings, in an isolated QA world. */
    private static void mainlandNavigation(ClientGameTestContext context) {
        Object[] state=new Object[5];
        try {
            context.runOnClient(client->{
                try {
                    var dimension=net.minecraft.world.level.Level.class.getDeclaredField("dimension");dimension.setAccessible(true);
                    state[0]=dimension.get(client.level);state[1]=client.player.position();state[2]=Navigation.points();state[3]=Navigation.lands();
                    LandSpawns.get("QA_已采集落点");
                    var spawns=LandSpawns.class.getDeclaredField("SPAWNS");spawns.setAccessible(true);
                    @SuppressWarnings("unchecked") var local=(java.util.Map<String,int[]>)spawns.get(null);
                    state[4]=new java.util.HashMap<>(local);local.put("QA_已采集落点",new int[]{300,400});
                    var merge=Navigation.class.getDeclaredMethod("withSpawns",java.util.List.class);merge.setAccessible(true);
                    @SuppressWarnings("unchecked") var merged=(java.util.List<NavPlanner.Land>)merge.invoke(null,java.util.List.of(new NavPlanner.Land("QA_已采集落点","",0,0,java.util.List.of(),new int[]{1,2}),new NavPlanner.Land("QA_其他领地","",0,0,java.util.List.of(),null,new int[]{0,0,500,500})));
                    require(merged.getFirst().spawn()[0]==300&&merged.getFirst().spawn()[1]==400&&merged.getLast().spawn()==null,"A new local observation overrides the old site snapshot and is not assigned to another land just by bounding box");
                    var lands=Navigation.class.getDeclaredField("lands");lands.setAccessible(true);
                    lands.set(null,java.util.List.of(new NavPlanner.Land("白渚","",6776,6344,java.util.List.of("baigang"),new int[]{6615,6476}),new NavPlanner.Land("乌瑞亚王国","",-7568,13456,java.util.List.of("wuruiyawangguo"),new int[]{-7431,13491})));
                    var landsAt=Navigation.class.getDeclaredField("landsAt");landsAt.setAccessible(true);landsAt.setLong(null,System.currentTimeMillis());
                    Navigation.usePoints(new NavPoints(java.util.List.of(new NavPoints.Hub("baigang","白港",6600,6476,"sea",true,20,80000),new NavPoints.Hub("wuruiyawangguo","乌瑞亚鼠鱼港",-7429,13515,"sea",true,20,80000),new NavPoints.Hub("hanmuhuiguan","寒木会馆",6409,15400,"sea",true,20,80000)),java.util.List.of(new NavPoints.Warp("借道RP",6600,6476))));
                    dimension.set(client.level,net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.Identifier.parse(CityNavigation.WORLD)));
                    // Pause the isolated integrated server so fake client coordinates never request real travel/chunks.
                    client.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("Route QA")) {
                        @Override public void renderBackground(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float delta) {}
                        @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float delta) {}
                        @Override public boolean isPauseScreen() { return true; }
                    });
                    Navigation.start(new Navigation.Target("寒木酒馆",6467,72,15620,"",java.util.List.of()));
                    require(!Navigation.inTargetWorld(client)&&Navigation.currentPlan().next().kind()==NavPlanner.Kind.HOME,"A mainland address viewed in the city prefers a real home entry over borrowing RP to save only five seconds");
                    require(Navigation.worldNotice(Navigation.world(client),Navigation.target().world()).equals("当前在主城，目标在主世界"),"Cross-world HUD names both the city location and mainland destination correctly");
                    require(Navigation.worldNotice(CityNavigation.MAIN_WORLD,CityNavigation.WORLD).equals("当前在主世界，目标在主城"),"The reverse journey names the correct destination world");
                }catch(ReflectiveOperationException e){throw new AssertionError(e);}
            });
            context.takeScreenshot("paletale-mainland-from-city-v0.11.6");
            context.runOnClient(client->{
                try {
                    var dimension=net.minecraft.world.level.Level.class.getDeclaredField("dimension");dimension.setAccessible(true);
                    dimension.set(client.level,net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.Identifier.parse(CityNavigation.MAIN_WORLD)));
                    var tick=Navigation.class.getDeclaredMethod("tick",net.minecraft.client.Minecraft.class);tick.setAccessible(true);
                    client.player.setPos(6615,72,6476);Navigation.spawnLearned();tick.invoke(null,client);
                    require(Navigation.inTargetWorld(client)&&Navigation.currentPlan().next().kind()==NavPlanner.Kind.TRAVEL&&Navigation.currentPlan().next().command().equals("port travel baigang hanmuhuiguan"),"After arriving at 白渚, the running navigation advances to 寒木会馆 instead of changing home");
                    client.player.setPos(-7431,72,13491);Navigation.spawnLearned();tick.invoke(null,client);
                    require(Navigation.currentPlan().steps().stream().noneMatch(s->s.kind()==NavPlanner.Kind.HOME),"A manual detour to 乌瑞亚 does not make the running route send the player back to 白渚");
                    client.player.setPos(6409,72,15354);Navigation.spawnLearned();tick.invoke(null,client);
                    require(Navigation.currentPlan().direct()&&Math.round(Navigation.currentPlan().directDistance())==272&&Navigation.currentPlan().next().command()==null,"The running HUD completes the final 272 blocks on foot and removes the teleport command");
                }catch(ReflectiveOperationException e){throw new AssertionError(e);}
            });
            context.takeScreenshot("paletale-mainland-final-walk-v0.11.6");
            context.runOnClient(client->{
                try {
                    client.player.setPos(6467,72,15620);Navigation.spawnLearned();
                    var tick=Navigation.class.getDeclaredMethod("tick",net.minecraft.client.Minecraft.class);tick.setAccessible(true);tick.invoke(null,client);
                    require(Navigation.target()==null&&Navigation.currentPlan()==null,"The final destination ends navigation instead of suggesting another teleport");
                }catch(ReflectiveOperationException e){throw new AssertionError(e);}
            });
            context.runOnClient(client->{
                try {
                    var dimension=net.minecraft.world.level.Level.class.getDeclaredField("dimension");dimension.setAccessible(true);
                    dimension.set(client.level,net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.Identifier.parse(CityNavigation.WORLD)));
                    Navigation.usePoints(new NavPoints(Navigation.points().hubs(),java.util.List.of(new NavPoints.Warp("目标附近RP",6409,15400))));
                    Navigation.start(new Navigation.Target("寒木酒馆",6467,72,15620,"",java.util.List.of()));
                    require(Navigation.currentPlan().next().kind()==NavPlanner.Kind.WARP&&Navigation.currentPlan().next().command().equals("warp 目标附近RP")&&Navigation.currentPlan().steps().size()==2,"A destination near RP uses that RP directly, without a home detour or port transfer");
                }catch(ReflectiveOperationException e){throw new AssertionError(e);}
            });
            context.takeScreenshot("paletale-rp-near-target-v0.11.6");
            context.runOnClient(client->{
                try {
                    var dimension=net.minecraft.world.level.Level.class.getDeclaredField("dimension");dimension.setAccessible(true);
                    dimension.set(client.level,net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.Identifier.parse(CityNavigation.MAIN_WORLD)));
                    client.player.setPos(6409,72,15400);Navigation.spawnLearned();
                    var tick=Navigation.class.getDeclaredMethod("tick",net.minecraft.client.Minecraft.class);tick.setAccessible(true);tick.invoke(null,client);
                    require(Navigation.currentPlan().direct()&&Navigation.currentPlan().next().command()==null,"After arriving at the direct RP, continue on foot instead of switching entries");
                }catch(ReflectiveOperationException e){throw new AssertionError(e);}
            });
        }finally {
            context.runOnClient(client->{
                Navigation.clear();
                try {
                    if(state[0]==null)return;
                    var dimension=net.minecraft.world.level.Level.class.getDeclaredField("dimension");dimension.setAccessible(true);dimension.set(client.level,state[0]);
                    client.player.setPos((net.minecraft.world.phys.Vec3)state[1]);Navigation.usePoints((NavPoints)state[2]);
                    var lands=Navigation.class.getDeclaredField("lands");lands.setAccessible(true);lands.set(null,state[3]);
                    if(state[4]!=null) {
                        var spawns=LandSpawns.class.getDeclaredField("SPAWNS");spawns.setAccessible(true);
                        @SuppressWarnings("unchecked") var local=(java.util.Map<String,int[]>)spawns.get(null);
                        @SuppressWarnings("unchecked") var before=(java.util.Map<String,int[]>)state[4];local.clear();local.putAll(before);
                    }
                    client.setScreen(null);
                }catch(ReflectiveOperationException e){throw new AssertionError(e);}
            });
        }
    }

    /** A genuine mouse click at the button's centre (hit-testing included), unlike clickScreenButton. */
    private static void navigationHub(ClientGameTestContext context, MarketApi media) {
        context.runOnClient(client -> {
            try { for(String name:java.util.List.of("profiles","acknowledged")){var field=CityNpcSync.class.getDeclaredField(name);field.setAccessible(true);Object cache=field.get(null);if(cache instanceof java.util.Map<?,?> map)map.clear();else if(cache instanceof java.util.Set<?> set)set.clear();} }catch(ReflectiveOperationException e){throw new AssertionError(e);}
            client.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false));
            ((net.minecraft.client.gui.screens.ChatScreen) client.screen).handleChatInput("/pt nav", false);
            client.setScreen(null);
        });
        context.waitForScreen(NavigationHubScreen.class);
        context.runOnClient(client -> client.setScreen(ArchiveNavigation.open(null, media, ArchiveNavigation.Tab.NAVIGATION)));
        context.takeScreenshot("paletale-navigation-hub-v0.11.6");
        context.runOnClient(client -> {
            var buttons = client.screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.Button).map(net.minecraft.client.gui.components.Button.class::cast).toList();
            require(buttons.stream().allMatch(a -> buttons.stream().noneMatch(b -> a != b && a.getY() == 13 && b.getY() == 13 && a.getX() < b.getX() + b.getWidth() && a.getX() + a.getWidth() > b.getX())), "Navigation tab does not overlap the header controls");
            require(buttons.stream().anyMatch(b->b.getMessage().getString().equals("加入官方群")) && buttons.stream().noneMatch(b->b.getMessage().getString().equals("扫描附近")||b.getMessage().getString().equals("一键传送")||b.getMessage().getString().startsWith("v0.")), "The group stays visible, scanning is automatic and the redundant footer/version controls are absent");
            var place = CityCatalog.shop("农作物商店");
            var npc = new net.minecraft.client.player.RemotePlayer(client.level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), place.name()));
            npc.setPos(place.x(), place.y(), place.z());
            var person = new net.minecraft.client.player.RemotePlayer(client.level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "RealPlayer"));
            person.setPos(place.x(), place.y(), place.z());
            var observer = new net.minecraft.world.entity.decoration.ArmorStand(client.level, place.x() + 2, place.y(), place.z());
            var found = CityNpcScan.find(CityNavigation.WORLD, observer, java.util.List.of(npc, person));
            require(found.size() == 1 && found.get(place.id()) == npc && npc.getSkin() != null, "Loaded real player entities scan only as registered NPCs and expose Minecraft skin data");
            require(CityNpcScan.find(CityNavigation.MAIN_WORLD, observer, java.util.List.of(npc)).isEmpty(), "Scanning rejects matching city coordinates in another world");
            try {
                var field=net.minecraft.world.level.Level.class.getDeclaredField("dimension");field.setAccessible(true);Object original=field.get(client.level);
                var position=client.player.position();
                try {
                    field.set(client.level,net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.Identifier.parse(CityNavigation.WORLD)));
                    client.player.setPos(place.x()+12,place.y(),place.z());
                    CityNpcScan.remember(place.id(),npc);
                    NavigationHubScreen.start(place);
                    require(npc.isCurrentlyGlowing()&&!person.isCurrentlyGlowing()&&!npc.hasGlowingTag()&&npc.getTeamColor()==0xFFFFFF,"The actual Entity renderer glow hook highlights only the target NPC without changing server flags");
                    // A render hook applies to every entity, so guard both its allocations and cache lifecycle.
                    for(int i=0;i<50000;i++){CityNpcScan.highlighted(npc);CityNpcScan.highlighted(person);}
                    var threads=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
                    if(threads.isThreadAllocatedMemorySupported()) {
                        threads.setThreadAllocatedMemoryEnabled(true);
                        long thread=Thread.currentThread().threadId(),before=threads.getThreadAllocatedBytes(thread),started=System.nanoTime();int matches=0;
                        for(int i=0;i<100000;i++){if(CityNpcScan.highlighted(npc))matches++;if(CityNpcScan.highlighted(person))matches++;}
                        long elapsed=System.nanoTime()-started,allocated=threads.getThreadAllocatedBytes(thread)-before;
                        require(matches==100000&&allocated<65536,"200,000 target/non-target render checks allocate under 64 KiB in total");
                        System.out.println("Navigation highlight probe: 200000 checks, "+allocated+" allocated bytes, "+elapsed+" ns (synthetic CPU probe, not gameplay FPS)");
                    }
                    CityNpcScan.forget(npc);require(!CityNpcScan.highlighted(npc)&&!CityNpcScan.hasHighlight(),"Entity unload immediately removes the cached highlight and enables the position marker");
                    CityNpcScan.remember(place.id(),npc);require(CityNpcScan.highlighted(npc),"A newly observed target is highlighted without waiting for route replanning");
                    field.set(client.level,original);CityNpcScan.refreshHighlight(client);
                    require(!CityNpcScan.highlighted(npc),"Leaving the target world invalidates the cached highlight");
                    field.set(client.level,net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.Identifier.parse(CityNavigation.WORLD)));
                    CityNpcScan.refreshHighlight(client);require(CityNpcScan.highlighted(npc),"Returning to the city restores the current target highlight");
                    NavigationHubScreen.start(CityCatalog.shop("矿物商店"));
                    require(!CityNpcScan.highlighted(npc),"Changing route stops highlighting the former NPC");
                    NavigationHubScreen.start(place);
                    Navigation.clear();require(!npc.isCurrentlyGlowing(),"Cancelling navigation removes its NPC glow");
                    var scoreboard=client.level.getScoreboard();
                    var team=scoreboard.addPlayerTeam("nav-white-qa");team.setColor(net.minecraft.ChatFormatting.RED);
                    scoreboard.addPlayerToTeam(npc.getScoreboardName(),team);scoreboard.addPlayerToTeam(person.getScoreboardName(),team);
                    npc.setGlowingTag(true);person.setGlowingTag(true);
                    // Simulate the shared flag received in an actual server entity-data packet.
                    var glowing=net.minecraft.world.entity.Entity.class.getDeclaredMethod("setSharedFlag",int.class,boolean.class);glowing.setAccessible(true);
                    glowing.invoke(npc,6,true);glowing.invoke(person,6,true);
                    try {
                        require(npc.isCurrentlyGlowing()&&person.isCurrentlyGlowing()&&npc.getTeamColor()==net.minecraft.ChatFormatting.RED.getColor(),"The server team colour and synchronized glow exist before navigation");
                        NavigationHubScreen.start(place);
                        require(npc.getTeamColor()==0xFFFFFF&&npc.isCurrentlyGlowing()&&npc.hasGlowingTag()&&person.getTeamColor()==net.minecraft.ChatFormatting.RED.getColor()&&person.isCurrentlyGlowing(),"White navigation overrides the server colour only for the target NPC and retains server glow flags");
                        Navigation.clear();
                        require(npc.getTeamColor()==net.minecraft.ChatFormatting.RED.getColor()&&npc.isCurrentlyGlowing(),"Stopping navigation restores server colour and its existing glow");
                    } finally { Navigation.clear();npc.setGlowingTag(false);person.setGlowingTag(false);glowing.invoke(npc,6,false);glowing.invoke(person,6,false);scoreboard.removePlayerTeam(team); }
                    CityNpcScan.forget(npc);
                }finally{Navigation.clear();field.set(client.level,original);client.player.setPos(position);}
            }catch(ReflectiveOperationException e){throw new AssertionError(e);}

        });
        context.runOnClient(client -> {
            // Rendering fixture only: a Minecraft-provided skin exercises the production face cache without pretending to be a live SIMMC skin.
            try {
                var field = CityNpcScan.class.getDeclaredField("skins"); field.setAccessible(true);
                @SuppressWarnings("unchecked") var skins = (java.util.Map<String, net.minecraft.world.entity.player.PlayerSkin>) field.get(null);
                var npc = new net.minecraft.client.player.RemotePlayer(client.level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "SkinFixture"));
                skins.put(CityCatalog.npcs().getFirst().id(), npc.getSkin());
                var place=CityCatalog.npcs().getFirst();
                CityNpcSync.learn(place,"a".repeat(64));CityNpcSync.learn(place,"b".repeat(64));
                require(CityNpcSync.profile(place.id()).skinHash().equals("a".repeat(64)),"A cached NPC is not rewritten or requeued just because it is seen again");
                CityNpcSync.learn(place,"b".repeat(64),true);
                require(CityNpcSync.profile(place.id()).personalized()&&CityNpcSync.profile(place.id()).skinHash()==null,"Viewer-dependent NPCs receive a one-time marker upgrade without caching or uploading the viewer's skin hash");

            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
        context.takeScreenshot("paletale-navigation-head-fixture-v0.11.6");
        realClick(context, "区域"); context.waitTicks(3);
        context.takeScreenshot("paletale-navigation-destinations-v0.11.6");
        realClick(context, "快捷传送"); context.waitTicks(3);
        context.runOnClient(client -> {
            var shortcut=CityCatalog.shortcuts().getFirst();
            var preview=NpcEntityIcons.preview(shortcut,null);
            require(preview!=null&&preview.getType()==net.minecraft.world.entity.EntityType.VEX,"Unloaded shortcut NPCs preview as their captured vex type rather than Steve");
            require(NpcEntityIcons.preview(shortcut,preview)==preview&&NpcEntityIcons.preview(CityCatalog.npcs().getFirst(),null)==null,"Live creature previews preserve the actual entity and never replace player NPC portraits");
        });
        context.takeScreenshot("paletale-navigation-shortcuts-v0.11.6");
        realClick(context, "附近 NPC"); context.waitTicks(3);
        context.takeScreenshot("paletale-navigation-scan-v0.11.6");
        context.runOnClient(client -> require(CityNpcScan.nearby().isEmpty(), "The actual non-city test world has no city NPC sightings"));
        realClick(context, "NPC"); context.waitTicks(3);
        nativeNavigationSearch(context);
        context.waitTicks(3);
        context.runOnClient(client -> require(client.screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals("导航")).count() == 2, "Searching leaves the header tab and one matching NPC action"));
        context.takeScreenshot("paletale-navigation-search-v0.11.9");
        double[] hover=context.computeOnClient(client->{
            var go=client.screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals("导航")).map(net.minecraft.client.gui.components.Button.class::cast).reduce((a,b)->b).orElseThrow();
            var window=(WindowScreen)client.screen;
            return new double[]{(window.winX+go.getX()+go.getWidth()/2.0)*client.getWindow().getScreenWidth()/client.getWindow().getGuiScaledWidth(),(window.winY+go.getY()+go.getHeight()/2.0)*client.getWindow().getScreenHeight()/client.getWindow().getGuiScaledHeight()};
        });
        context.getInput().setCursorPos(hover[0],hover[1]);context.waitTicks(10);context.takeScreenshot("paletale-navigation-npc-tooltip-v0.11.9");
        realClick(context, "导航");
        context.runOnClient(client -> require(client.screen == null && Navigation.target().x() == 28 && Navigation.target().y() == 43 && Navigation.target().z() == 28 && Navigation.currentPlan().next().command().equals("spawn"), "The NPC action starts the corrected city target and retains the cross-world one-key command"));
        context.waitTicks(3);
        context.takeScreenshot("paletale-navigation-one-key-v0.11.6");
        context.runOnClient(client -> {
            int before=(int)LedgerStore.visible().stream().filter(e->e.kind()==LedgerEntry.Kind.OFFICIAL_CROP).count();
            LedgerWatch.handle("商店 成功出售 蘑菇 ★★★，获得 4,194$!");LedgerWatch.handle("商店 成功出售 蘑菇 ★★★，获得 4,194$!");
            require(LedgerStore.visible().stream().filter(e->e.kind()==LedgerEntry.Kind.OFFICIAL_CROP).count()==before+2,"Separate identical official receipts are both recorded, even within 400ms");
            LedgerWatch.handle("商店 购买成功，花费 39!");LedgerWatch.handle("商店 购买成功，花费 39!");
            Navigation.clear();ModSettings.setLedgerView("|0|0|0|false");client.setScreen(new LedgerScreen(null,media));
        });
        realClick(context,"官方");context.waitTicks(3);
        context.runOnClient(client -> require(shown(client.screen).stream().filter(e->e.kind()==LedgerEntry.Kind.OFFICIAL_CROP).count()==1&&shown(client.screen).stream().filter(e->e.kind()==LedgerEntry.Kind.OFFICIAL_PURCHASE).count()==3,"Daily sales share one row while all three purchases retain separate rows"));
        context.takeScreenshot("paletale-official-daily-income-v0.11.6");
        double[] row=context.computeOnClient(client->{
            try{var list=shown(client.screen);int index=0;while(list.get(index).kind()!=LedgerEntry.Kind.OFFICIAL_CROP)index++;
                var top=LedgerScreen.class.getDeclaredField("rowTop");top.setAccessible(true);var left=LedgerScreen.class.getDeclaredField("contentX");left.setAccessible(true);var window=(WindowScreen)client.screen;
                return new double[]{(left.getInt(client.screen)+20+window.winX)*client.getWindow().getScreenWidth()/(double)client.getWindow().getGuiScaledWidth(),(top.getInt(client.screen)+index*13+6+window.winY)*client.getWindow().getScreenHeight()/(double)client.getWindow().getGuiScaledHeight()};
            }catch(ReflectiveOperationException e){throw new AssertionError(e);}
        });
        context.getInput().setCursorPos(row[0],row[1]);context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitForScreen(LedgerDailyScreen.class);context.takeScreenshot("paletale-official-daily-receipts-v0.11.6");
        int[] viewport = context.computeOnClient(client -> new int[]{client.getWindow().getScreenWidth(), client.getWindow().getScreenHeight(), client.options.guiScale().get()});
        context.runOnClient(client -> {
            Navigation.clear(); client.getWindow().setWindowed(1000, 650); client.options.guiScale().set(2); client.resizeDisplay();
            client.setScreen(ArchiveNavigation.open(null, media, ArchiveNavigation.Tab.NAVIGATION));
        });
        context.waitTicks(3);
        context.runOnClient(client -> {
            var screen = (WindowScreen) client.screen;
            var buttons = screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.Button).map(net.minecraft.client.gui.components.Button.class::cast).toList();
            require(buttons.stream().allMatch(a -> buttons.stream().noneMatch(b -> a != b && a.getY() == 13 && b.getY() == 13 && a.getX() < b.getX() + b.getWidth() && a.getX() + a.getWidth() > b.getX())), "All six tabs fit the compact navigation header");
            require(buttons.stream().filter(b -> b.getMessage().getString().equals("导航") && b.getY() != 13).allMatch(b -> b.getY() + 30 < screen.height - 34), "Compact rows remain above the navigation footer");
        });
        context.takeScreenshot("paletale-navigation-compact-v0.11.6");
        context.runOnClient(client -> { client.getWindow().setWindowed(800, 600); client.resizeDisplay(); });
        context.waitTicks(3);
        context.runOnClient(client -> {
            var screen = (WindowScreen) client.screen;
            var buttons = screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.Button).map(net.minecraft.client.gui.components.Button.class::cast).toList();
            require(buttons.stream().allMatch(a -> buttons.stream().noneMatch(b -> a != b && a.getY() == 13 && b.getY() == 13 && a.getX() < b.getX() + b.getWidth() && a.getX() + a.getWidth() > b.getX())), "Six tabs and the account fit the narrow window");
            require(buttons.stream().filter(b -> b.getY() == 13 && b instanceof MarketTheme.Action && java.util.List.of("物品档案", "玩家档案", "组织档案", "广场·测试", "账本", "导航").contains(b.getMessage().getString())).allMatch(b -> b.getWidth() >= client.font.width(((MarketTheme.Action) b).displayLabel()) + 8), "Narrow labels have enough padding to remain readable and retain full accessible names");
        });
        context.takeScreenshot("paletale-navigation-narrow-v0.11.6");
        context.runOnClient(client -> { client.getWindow().setWindowed(viewport[0], viewport[1]); client.options.guiScale().set(viewport[2]); client.resizeDisplay(); });
        context.waitTicks(3);
        realClick(context,"自选目的地");context.waitTicks(2);
        context.runOnClient(client->{var box=navigationSearch(client);box.setValue("bad coordinates");});
        realClick(context,"开始导航");
        context.runOnClient(client->require(client.screen instanceof NavigationHubScreen&&Navigation.target()==null,"Invalid manual coordinates keep the form open without starting navigation"));
        context.runOnClient(client->navigationSearch(client).setValue("120 64 -350"));
        context.takeScreenshot("paletale-manual-destination-v0.11.11");
        realClick(context,"开始导航");
        context.runOnClient(client->{
            var target=Navigation.target();
            require(client.screen==null&&target.x()==120&&target.y()==64&&target.z()==-350&&target.world().equals(CityNavigation.MAIN_WORLD),"Manual XYZ starts a main-world route");
            Navigation.clear();client.setScreen(new NavigationHubScreen(null,media));
        });
        realClick(context,"自选目的地");
        context.runOnClient(client->navigationSearch(client).setValue("-400 750"));
        realClick(context,"开始导航");
        context.runOnClient(client->{
            var target=Navigation.target();
            require(target.x()==-400&&target.z()==750&&target.y()==null&&target.world().equals(CityNavigation.MAIN_WORLD)&&target.marks().isEmpty(),"Omitting Y retains an unknown height and never creates a city or fabricated-height marker");
            Navigation.clear();client.setScreen(new NavigationHubScreen(null,media));
        });
    }
    private static EditBox navigationSearch(net.minecraft.client.Minecraft client) { return client.screen.children().stream().filter(c->c instanceof EditBox).map(EditBox.class::cast).findFirst().orElseThrow(); }
    private static void nativeNavigationSearch(ClientGameTestContext context) {
        double[] point=context.computeOnClient(client->{
            var search=navigationSearch(client);var window=(WindowScreen)client.screen;
            return new double[]{(window.winX+search.getX()+8)*client.getWindow().getScreenWidth()/client.getWindow().getGuiScaledWidth(),(window.winY+search.getY()+6)*client.getWindow().getScreenHeight()/client.getWindow().getGuiScaledHeight()};
        });
        context.getInput().setCursorPos(point[0],point[1]);context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.getInput().typeChar('矿');context.waitTicks(3);
        context.runOnClient(client->require(navigationSearch(client).getValue().equals("矿")&&navigationSearch(client).isFocused()&&client.screen.getFocused()==navigationSearch(client),"Clicking and typing the first search character retains native keyboard focus after filtering"));
        context.getInput().typeChar('物');context.waitTicks(3);
        context.runOnClient(client->require(navigationSearch(client).getValue().equals("矿物")&&navigationSearch(client).isFocused(),"A second Chinese character can be typed after the result refresh"));
        context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE);context.waitTicks(2);
        context.runOnClient(client->require(navigationSearch(client).getValue().equals("矿")&&navigationSearch(client).isFocused(),"Backspace keeps the search editable"));
        context.getInput().typeChar('物');context.waitTicks(2);
        context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_HOME);modifiedKey(context,org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT,org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT);
        context.runOnClient(client->{
            require(navigationSearch(client).getHighlighted().equals("矿"),"The native keyboard can select part of the search text");
            try{var revision=CityCatalog.class.getDeclaredField("revision");revision.setAccessible(true);revision.setLong(null,CityCatalog.revision()+1);}catch(ReflectiveOperationException e){throw new AssertionError(e);}
        });
        context.waitTicks(3);
        context.runOnClient(client->require(navigationSearch(client).getHighlighted().equals("矿")&&navigationSearch(client).isFocused(),"A background NPC catalog refresh preserves the current search selection and focus"));
        context.getInput().typeChar('X');context.waitTicks(2);
        context.runOnClient(client->require(navigationSearch(client).getValue().equals("X物"),"Typing replaces the selected text rather than appending or losing input"));
        String clipboard=context.computeOnClient(client->client.keyboardHandler.getClipboard());
        int command=System.getProperty("os.name","").startsWith("Mac")?org.lwjgl.glfw.GLFW.GLFW_MOD_SUPER:org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL;
        try {
            context.runOnClient(client->client.keyboardHandler.setClipboard("矿物"));
            modifiedKey(context,org.lwjgl.glfw.GLFW.GLFW_KEY_A,command);modifiedKey(context,org.lwjgl.glfw.GLFW.GLFW_KEY_V,command);context.waitTicks(3);
            context.runOnClient(client->require(navigationSearch(client).getValue().equals("矿物")&&navigationSearch(client).isFocused(),"Native select-all and paste work in the focused navigation search"));
        }finally{context.runOnClient(client->client.keyboardHandler.setClipboard(clipboard));}
        modifiedKey(context,org.lwjgl.glfw.GLFW.GLFW_KEY_A,command);context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE);context.waitTicks(2);
        context.runOnClient(client->require(navigationSearch(client).getValue().isEmpty()&&navigationSearch(client).isFocused(),"Clearing the query restores the list without disabling the input"));
        context.getInput().typeChar('矿');context.waitTicks(2);context.getInput().typeChar('物');context.waitTicks(2);
    }
    private static void modifiedKey(ClientGameTestContext context,int key,int modifiers) {
        // Fabric's 1.21.11 TestInput sends modifier=0 even while Shift/Command is held.
        context.runOnClient(client->{
            var input=(net.fabricmc.fabric.mixin.client.gametest.input.KeyboardHandlerAccessor)(Object)client.keyboardHandler;
            var event=new net.minecraft.client.input.KeyEvent(key,0,modifiers);
            input.invokeOnKey(client.getWindow().handle(),org.lwjgl.glfw.GLFW.GLFW_PRESS,event);
            input.invokeOnKey(client.getWindow().handle(),org.lwjgl.glfw.GLFW.GLFW_RELEASE,event);
        });context.waitTicks(1);
    }
    /** The official source filter is one button that shows the current choice and moves to the next on a click: click it until it shows {@code label}. */
    private static void selectSource(ClientGameTestContext context, String label) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String shown = context.computeOnClient(client -> client.screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.Button b && b.getY() == 62 && b.getX() > 260).map(c -> ((net.minecraft.client.gui.components.Button) c).getMessage().getString().strip()).findFirst().orElse(""));
            if (shown.equals(label)) return;
            realClick(context, shown, true);
        }
        throw new AssertionError("source filter never showed " + label);
    }
    private static void realClick(ClientGameTestContext context, String label) { realClick(context, label, false); }
    private static void realClick(ClientGameTestContext context, String label, boolean sourceFilter) {
        double[] point = context.computeOnClient(client -> {
            var button = client.screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().strip().equals(label))
                    .map(net.minecraft.client.gui.components.Button.class::cast).filter(b -> !sourceFilter || b.getY() == 62).reduce((first, last) -> last).orElseThrow(() -> new AssertionError("no button " + label));
            return new double[]{(button.getX() + button.getWidth() / 2.0 + (client.screen instanceof WindowScreen window ? window.winX : 0)) * client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth(),
                    (button.getY() + button.getHeight() / 2.0 + (client.screen instanceof WindowScreen window ? window.winY : 0)) * client.getWindow().getScreenHeight() / client.getWindow().getGuiScaledHeight()};
        });
        context.getInput().setCursorPos(point[0], point[1]);
        context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
    /** A signed-in session for the test (the account tick drops it again without a real server, so set it right before use). */
    private static void signIn() {
        try {
            var field = ClientAccount.class.getDeclaredField("login"); field.setAccessible(true);
            field.set(PaleTaleClient.ACCOUNT, new ModAuthApi.Login("test-token", new ModAuthApi.Citizen(1, "Tester", "Tester", UUID), System.currentTimeMillis() + 3_600_000, null));
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    @SuppressWarnings("unchecked")
    private static java.util.List<LedgerEntry> shown(Object screen) {
        try { var field = LedgerScreen.class.getDeclaredField("shown"); field.setAccessible(true); return (java.util.List<LedgerEntry>) field.get(screen); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static String state(Object screen) {
        StringBuilder out = new StringBuilder(screen.getClass().getSimpleName());
        for (String name : new String[]{"type", "effect", "favorites", "loading"}) try {
            var field = screen.getClass().getDeclaredField(name); field.setAccessible(true); out.append(' ').append(name).append('=').append(field.get(screen));
        } catch (ReflectiveOperationException ignored) { }
        return out.toString();
    }
    static void run(ClientGameTestContext context) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();server.setExecutor(workers);
        server.createContext("/api/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/image") || path.endsWith("/avatar") || path.matches("/api/mcmod/nav/npcs/[^/]+/avatar/[a-f0-9]{64}")) {
                IMAGE_REQUESTS.computeIfAbsent(path,p->new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
                var gate=IMAGE_GATES.get(path);if(gate!=null)try{gate.await(30,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
                byte[] image;
                try (var stream = ArchiveClientGameTest.class.getResourceAsStream("/assets/paletale/textures/gui/wine.png")) { image = stream.readAllBytes(); }
                exchange.getResponseHeaders().set("Content-Type", "image/png");exchange.sendResponseHeaders(200, image.length);
                try (var stream = exchange.getResponseBody()) { stream.write(image); } return;
            }
            if (path.equals("/api/mcmod/notifications")) {
                String items = "[{\"id\":11,\"kind\":\"price\",\"adCode\":\"123456\",\"adTitle\":\"测试广告\",\"adOwner\":\"店主\",\"adOwnerId\":\"ShopKeeper\",\"itemId\":0,\"product\":\"樱花酒\",\"change\":\"10 云币 → 12 云币\",\"detail\":\"樱花酒：10 云币 → 12 云币\",\"read\":false,\"createdAt\":\"2026-10-18T10:00:00Z\"},"
                        + "{\"id\":10,\"kind\":\"effect_approved\",\"adCode\":\"\",\"adTitle\":\"某款酒\",\"adOwner\":\"\",\"adOwnerId\":\"\",\"itemId\":5,\"product\":\"\",\"change\":\"你提交的「力量 1」已通过，已上架为：力量 I\",\"detail\":\"某款酒：你提交的「力量 1」已通过\",\"read\":true,\"createdAt\":\"2026-10-17T10:00:00Z\"}]";
                byte[] bytes = ("{\"page\":1,\"pageSize\":8,\"total\":2,\"items\":" + items + "}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, bytes.length);
                try (var stream = exchange.getResponseBody()) { stream.write(bytes); } return;
            }
            if (path.equals("/api/mcmod/notifications/delete") || path.equals("/api/mcmod/notifications/read")) {
                QUERIES.add(path.substring(path.lastIndexOf('/') + 1) + ":" + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] ok = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, ok.length);
                try (var stream = exchange.getResponseBody()) { stream.write(ok); } return;
            }
            if (path.equals("/api/mcmod/view")) { QUERIES.add("view:" + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)); exchange.sendResponseHeaders(204, -1); exchange.close(); return; }
            if (path.startsWith("/api/mcmod/market")) {
                QUERIES.add(path + "?" + exchange.getRequestURI().getRawQuery());
                // Market mock: the favourites list holds 1 ad, the full list 7.
                boolean favourites = path.endsWith("/favorites");
                int total = favourites ? 1 : 7;
                String body = "{\"version\":1,\"page\":1,\"pageSize\":12,\"total\":" + total + ",\"hasMore\":false,\"ads\":["
                        + String.join(",", java.util.Collections.nCopies(total, MarketClientGameTest.AD)) + "]}";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(favourites && !exchange.getRequestHeaders().containsKey("Authorization") ? 401 : 200, bytes.length);
                try (var stream = exchange.getResponseBody()) { stream.write(bytes); } return;
            }
            QUERIES.add(path + "?" + exchange.getRequestURI().getRawQuery());
            boolean player = path.contains("/players");String query = URLDecoder.decode(exchange.getRequestURI().getRawQuery() == null ? "" : exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
            JsonObject response = new JsonObject();response.addProperty("version", 1);int status = 200;
            if (path.matches("/api/mcmod/(items|players)/[0-9]+")) {
                int id = Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
                if (id == 999) { response.addProperty("message", "档案不存在或未公开。");status = 404; }
                else {
                    response.add("entry", entry(player, id));JsonArray owned = new JsonArray();
                    if (player) for (int i = 1; i <= 12; i++) owned.add(entry(false, i));
                    response.addProperty("ownedTotal", player ? 25 : 0);response.add("ownedItems", owned);response.addProperty("adsTotal", 0);response.add("ads", new JsonArray());
                }
            } else {
                List<JsonObject> entries = new ArrayList<>();
                for (int i = 1; i <= (player ? 1 : 25); i++) {
                    var value = entry(player, player ? 12 : i);
                    if (query.contains("type=wine") && i % 2 == 0) continue;
                    if (query.contains("q=无结果")) continue;
                    if (query.contains("favorites=1") && i != 3) continue;
                    entries.add(value);
                }
                int page = query.contains("page=2") ? 2 : 1;JsonArray values = new JsonArray();
                for (int i = (page - 1) * 24; i < Math.min(page * 24, entries.size()); i++) values.add(entries.get(i));
                response.addProperty("kind", player ? "players" : "items");response.addProperty("page", page);response.addProperty("pageSize", 24);response.addProperty("total", entries.size());response.addProperty("hasMore", entries.size() > page * 24);response.add("entries", values);
                if (query.contains("q=fail")) { status = 503;response.addProperty("message", "公开档案暂时无法读取，请稍后重试。"); }
            }
            byte[] bytes = response.toString().getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type", "application/json");exchange.sendResponseHeaders(status, bytes.length);
            try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
        });
        server.start();
        MarketApi media = new MarketApi(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
        try {
            context.runOnClient(client -> { MarketImages.INSTANCE.clear();client.setScreen(new ArchiveScreen(null, media, ArchiveData.Kind.ITEMS, 0)); });
            await(context);
            context.runOnClient(client -> {
                var page = ((ArchiveScreen) client.screen).result();
                require(page.total() == 25, "Item index reaches native grid");
                require(!ArchiveModels.INSTANCE.resolve(page.entries().get(0)).isEmpty(), "Native resource-pack wine model resolves");
                require(!ArchiveModels.INSTANCE.resolve(page.entries().get(1)).isEmpty(), "ITEM_MODEL food resolves without CMD");
                require(ArchiveModels.INSTANCE.resolve(page.entries().get(2)).isEmpty(), "Unknown model or unmatched CMD falls back, never previous wine");
                var range = com.google.gson.JsonParser.parseString("{\"type\":\"range_dispatch\",\"property\":\"custom_model_data\",\"entries\":[{\"threshold\":30000,\"model\":{\"type\":\"model\",\"model\":\"minecraft:item/diamond\"}}]}").getAsJsonObject();
                require(ArchiveModels.matches(range, 30000, 0) && !ArchiveModels.matches(range, 30001, 0), "Floor dispatch cannot silently select a different custom ID");
            });
            context.getInput().setCursorPos(0, 0);
            context.takeScreenshot("paletale-items-grid");
            context.clickScreenButton("上传");
            context.takeScreenshot("paletale-items-upload-menu");
            context.clickScreenButton("上传");
            double[] point = context.computeOnClient(client -> {
                var card = client.screen.children().stream().filter(ArchiveCardButton.class::isInstance).map(ArchiveCardButton.class::cast).findFirst().orElseThrow();
                return new double[]{(card.getX() + card.getWidth() / 2.0 + (client.screen instanceof WindowScreen window ? window.winX : 0)) * client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth(),
                        (card.getY() + 15.0 + (client.screen instanceof WindowScreen window ? window.winY : 0)) * client.getWindow().getScreenHeight() / client.getWindow().getGuiScaledHeight()};
            });
            context.getInput().setCursorPos(point[0], point[1]);
            context.waitFor(client -> client.screen.children().stream().anyMatch(child -> child instanceof ArchiveCardButton card && card.isHovered()), 100);
            context.takeScreenshot("paletale-items-hover-preview");
            context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitForScreen(ArchiveDetailScreen.class);awaitDetail(context);
            context.runOnClient(client -> require(((ArchiveDetailScreen) client.screen).detail().entry().id() == 1, "Click opens independent item detail"));
            context.waitFor(client -> QUERIES.stream().anyMatch(q -> q.startsWith("view:") && q.contains("\"item\"") && q.contains("\"id\":1")), 100);
            context.takeScreenshot("paletale-item-independent-page");
            // Hovering the version in the corner shows the official-download notice.
            double[] badge = context.computeOnClient(client -> {
                var version = client.screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance).map(net.minecraft.client.gui.components.Button.class::cast).filter(b -> b.getMessage().getString().equals("版本")).findFirst().orElseThrow();
                var window = (WindowScreen) client.screen;
                return new double[]{(version.getX() + version.getWidth() / 2.0 + window.winX) * client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth(),
                        (version.getY() + version.getHeight() / 2.0 + window.winY) * client.getWindow().getScreenHeight() / client.getWindow().getGuiScaledHeight()};
            });
            context.getInput().setCursorPos(badge[0], badge[1]);
            context.waitTicks(30);
            context.takeScreenshot("paletale-version-tip");
            context.getInput().setCursorPos(0, 0);
            // 提交效果 hands over to the website: a confirm-link screen for the wine's own page with the submission panel open.
            net.minecraft.client.gui.screens.Screen[] detailScreen = new net.minecraft.client.gui.screens.Screen[1];
            context.runOnClient(client -> detailScreen[0] = client.screen);
            context.clickScreenButton("SMC世界");context.runOnClient(client->{require(client.screen instanceof WorldScreen,"item world icon opens SMC search");require(WorldClientGameTest.field(client.screen,"searchType").equals("items"),"archive item restricts world search to items");client.setScreen(detailScreen[0]);});awaitDetail(context);
            realClick(context, "提交效果");
            context.waitForScreen(net.minecraft.client.gui.screens.ConfirmLinkScreen.class);
            context.takeScreenshot("paletale-effect-submit-link");
            context.runOnClient(client -> client.setScreen(detailScreen[0]));
            context.waitForScreen(ArchiveDetailScreen.class);
            // The ledger: server messages become entries; a sale of an item one of my ad products trades links to that ad.
            context.runOnClient(client -> {
                if (Boolean.getBoolean("paletale.test.officialCrops")) {
                    require(LedgerStore.all().stream().anyMatch(e -> e.id().equals("migration-fixture")), "A legacy ledger was migrated before the client read its entries");
                    require(java.nio.file.Files.exists(LedgerStore.folder().resolve("backups/ledger-2026-10-20.json")) && !java.nio.file.Files.exists(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("paletale.properties")), "Legacy preferences and daily backups moved under paletale/config");
                    for (var p : LedgerOfficial.PRODUCTS) if (!p.mineral()) {
                        var icon = OfficialCropIcons.appearance(p.key());
                        require(icon != null && !(client.getModelManager().getItemModel(net.minecraft.resources.Identifier.parse(icon.model())) instanceof net.minecraft.client.renderer.item.MissingItemModel), "Official exported crop model loads: " + p.key());
                    }
                }
                LedgerStore.clear();
                ShopIndex.set(java.util.List.of(new ShopApi.MyShop(new ShopApi.Position("minecraft:overworld", 6622, 71, 6515), new ShopApi.Position("minecraft:overworld", 6622, 71, 6516), "444618", "全smc最萌萌的店铺", 3, "圆石商品", "圆石")));
                String me = client.getUser().getName();
                LedgerWatch.handle("Energy_Kasa 从你的商店里购买了 1 圆石，你因此赚得了 $0.95");
                LedgerWatch.handle("你位于 6622, 71, 6516, 的商店的 圆石 脱销了。");
                LedgerWatch.handle("$10已被发送到Bob。");
                LedgerWatch.handle(me + "收到了$25。");
                LedgerWatch.handle("Energy_Kasa 向 " + me + " 打赏了（¥9191.00）");
                LedgerWatch.handle("玩家 TipthereA 向你打赏了 $100.00");
                LedgerWatch.handle("你已向 TipthereA 打赏 20.00");
                LedgerWatch.handle("商店信息：\n所有者：Bob\n物品：圆石    [物品预览]\n库存:40\n每个圆石价格$1\n这个商店现在正在出售物品。");
                LedgerWatch.handle("支付成功：\n1 圆石 共 $1");
                var all = LedgerStore.all();
                require(all.size() == 8, "Eight ledger entries recorded");
                require(all.get(7).kind() == LedgerEntry.Kind.SHOP_PURCHASE && all.get(7).counterparty().equals("Bob") && all.get(7).adCode().isEmpty(), "Buying from another shop: owner recorded, not linked to my ads");
                require(all.get(0).adCode().equals("444618") && all.get(1).adCode().equals("444618"), "Sale and sold-out notice link to the ad (by item and by chest position)");
                client.setScreen(new LedgerScreen(null, media));
            });
            context.waitForScreen(LedgerScreen.class);
            context.takeScreenshot("paletale-ledger");
            context.clickScreenButton("统计");
            context.takeScreenshot("paletale-ledger-stats");
            // Official shop lore is read from an actual menu, including the pack's star glyphs.
            context.runOnClient(client -> {
                var inventory = new net.minecraft.world.SimpleContainer(27);
                var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHEAT);
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("大豆 \uE001\uE001\uE002"));
                stack.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(net.minecraft.network.chat.Component.literal("出售: 62$"), net.minecraft.network.chat.Component.literal("玩家出售限制: 3/54"))));
                inventory.setItem(0, stack);
                var menu = net.minecraft.world.inventory.ChestMenu.threeRows(55, client.player.getInventory(), inventory);
                client.setScreen(new net.minecraft.client.gui.screens.inventory.ContainerScreen(menu, client.player.getInventory(), net.minecraft.network.chat.Component.literal("官方商店")));
            });
            context.waitTicks(15);
            long[] previousMenuTime = { 0 };
            context.runOnClient(client -> {
                var read = LedgerStore.official().stream().filter(r -> r.product().equals("大豆 · 2星")).findFirst().orElseThrow();
                previousMenuTime[0] = System.currentTimeMillis() - 86_400_000;
                LedgerStore.official(new LedgerOfficial.Reading(read.account(), previousMenuTime[0], read.name(), read.product(), read.used(), read.limit()));
            });
            context.waitTicks(10);
            context.runOnClient(client -> {
                var read = LedgerStore.official().stream().filter(r -> r.product().equals("大豆 · 2星")).findFirst().orElseThrow();
                require(read.time() == previousMenuTime[0], "An unchanged cached menu cannot timestamp an old quota as new after a refresh boundary");
                LedgerStore.official(new LedgerOfficial.Reading(read.account(), System.currentTimeMillis(), read.name(), read.product(), read.used(), read.limit()));
            });
            context.runOnClient(client -> {
                require(LedgerStore.official().stream().anyMatch(r -> r.account().equals(LedgerStore.account()) && r.product().equals("大豆 · 2星") && r.used() == 3 && r.limit() == 54), "Actual official menu provides the used count and limit");
                LedgerWatch.handle("商店 成功出售 大豆 \uE001\uE001\uE002，获得 186\uE010");
                LedgerWatch.handle("商店 成功出售 金锭，获得 2,000\uE010");
                LedgerWatch.handle("商店 购买成功，花费 10,000金币!");
                LedgerWatch.handle("您从鱼中赚了600.00金！今天您还可以从市场中\n获得9400.00金");
                var official = LedgerStore.visible().stream().filter(e -> e.kind().group().equals("official")).toList();
                client.getToastManager().clear();
                require(official.size() == 4 && official.get(0).quantity() == 3 && official.get(0).raw().contains("186") && official.get(3).amount() == 600, "Official chat through the watch records four real ledger entries");
                LedgerStore.flushNow();
                try {
                    var saved = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(LedgerStore.folder().resolve("ledger.json"))).getAsJsonObject();
                    require(saved.getAsJsonArray("official").size() >= 2 && saved.getAsJsonArray("entries").size() == 12, "Background snapshot and exit flush preserve official entries and quotas");
                } catch (java.io.IOException error) { throw new AssertionError(error); }
                ModSettings.setLedgerView("|0|0|0|false"); // the ledger remembers its view (stats on since the first part of this test); this part starts from the plain list
                client.setScreen(new LedgerScreen(null, media));
            });
            context.waitForScreen(LedgerScreen.class);
            realClick(context, "官方");
            context.takeScreenshot("paletale-official-ledger-v0.10.8");
            context.runOnClient(client -> {
                var officialButton = client.screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals("官方")).map(net.minecraft.client.gui.components.Button.class::cast).findFirst().orElseThrow();
                var shopButton = client.screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals("商店交易")).map(net.minecraft.client.gui.components.Button.class::cast).findFirst().orElseThrow();
                require(officialButton.getY() < shopButton.getY(), "Official navigation is immediately above shop trades");
            });
            realClick(context, "收入");
            context.runOnClient(client -> require(shown(client.screen).size() == 3 && shown(client.screen).stream().allMatch(e -> e.kind().sign > 0), "Income button filters the official table"));
            context.takeScreenshot("paletale-official-income-v0.10.8");
            realClick(context, "支出");
            context.runOnClient(client -> require(shown(client.screen).size() == 1 && shown(client.screen).getFirst().kind() == LedgerEntry.Kind.OFFICIAL_PURCHASE, "Spending button still finds official purchases after removing their source option"));
            context.takeScreenshot("paletale-official-spending-v0.10.8");
            realClick(context, "收支");
            for (String label : java.util.List.of("全部", "农作物", "矿物", "钓鱼", "其他")) realClick(context, label, true);
            context.runOnClient(client -> require(shown(client.screen).size() == 4, "The source cycle returns to all after Other, with no official-purchase option"));

            realClick(context, "全部", true);
            context.runOnClient(client -> {
                var box = client.screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals("日期/金额")).findFirst();
                require(box.isPresent(), "Compact ledger keeps date and amount filters accessible");
            });
            realClick(context, "日期/金额");
            context.runOnClient(client -> require(client.screen.children().stream().filter(c -> c instanceof EditBox).count() == 5, "Date and amount fields open without overlapping the compact sidebar"));
            context.takeScreenshot("paletale-official-filters-v0.10.8");
            realClick(context, "返回分类");
            context.takeScreenshot("paletale-official-crop-filter-v0.10.8");
            context.runOnClient(client -> {
                require(client.screen instanceof LedgerScreen && client.screen.children().stream().noneMatch(c -> c instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals("每日额度")), "Daily quotas are inline and the separate-page button is gone");
                var full = new LedgerEntry("full-crop", System.currentTimeMillis(), LedgerEntry.Kind.OFFICIAL_CROP, "云币", 864, "官方", "大豆 · 1星", 108, null, null, null, "", "", "");
                LedgerStore.add(full);
            });
            context.waitTicks(25);
            context.takeScreenshot("paletale-official-inline-crops-v0.10.8");
            context.runOnClient(client -> require(client.screen.children().stream().noneMatch(c -> c instanceof net.minecraft.client.gui.components.Button b && java.util.List.of("上一组", "下一组").contains(b.getMessage().getString())), "All six crops share one page with no group navigation"));
            selectSource(context, "矿物");
            context.takeScreenshot("paletale-official-inline-minerals-v0.10.8");
            selectSource(context, "钓鱼");
            context.takeScreenshot("paletale-official-inline-fishing-v0.10.8");
            context.runOnClient(client -> LedgerWatch.handle("您从鱼中赚了9400.00金！今天您还可以从市场中获得0.00金"));
            context.waitTicks(25);
            context.takeScreenshot("paletale-official-inline-fishing-full-v0.10.8");
            int[] originalViewport = context.computeOnClient(client -> new int[]{ client.getWindow().getScreenWidth(), client.getWindow().getScreenHeight(), client.options.guiScale().get() });
            context.runOnClient(client -> {
                client.getWindow().setWindowed(1600, 1000); client.options.guiScale().set(2); client.resizeDisplay();
                require(LedgerStore.folder().equals(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("paletale/config")), "The client uses the new shared paletale/config folder");
            });
            context.waitTicks(5);
            selectSource(context, "农作物");
            context.takeScreenshot("paletale-official-inline-large-v0.10.8");
            realClick(context, "导航");
            context.runOnClient(client -> {
                require(Navigation.target().world().equals(CityNavigation.WORLD) && Navigation.target().x() == 42 && Navigation.target().y() == 54 && Navigation.target().z() == 9, "Ledger navigation selects the current crop NPC in the city world");
                require(!Navigation.inTargetWorld(client) && Navigation.currentPlan().next().command().equals("spawn"), "The mainland test world offers /spawn instead of pointing at a wrong-world coordinate");
                client.setScreen(null); client.getToastManager().clear();
            });
            context.waitTicks(5);
            context.takeScreenshot("paletale-city-navigation-entry-v0.10.8");
            context.runOnClient(client -> {
                Navigation.clear();
                try {
                    var dispatcher = net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.getActiveDispatcher();
                    require(dispatcher!=null,"The normal navigation commands remain registered");
                    for(String alias:java.util.List.of("pt","paletale"))require(dispatcher.getRoot().getChild(alias).getChild("pos")==null&&dispatcher.getRoot().getChild(alias).getChild("npcpos")==null&&dispatcher.getRoot().getChild(alias).getChild("nav")!=null,"Release removes both capture commands and aliases while preserving navigation");
                } catch (Exception failure) { throw new AssertionError(failure); }
            });
            context.takeScreenshot("paletale-navigation-point-records-v0.10.8");
            navigationHub(context, media);
            mainlandNavigation(context);


            context.runOnClient(client -> { client.getWindow().setWindowed(originalViewport[0], originalViewport[1]); client.options.guiScale().set(originalViewport[2]); client.resizeDisplay(); });
            context.waitTicks(5);
            context.runOnClient(client -> client.setScreen(new LedgerDetailScreen(null, LedgerStore.visible().stream().filter(e -> e.kind() == LedgerEntry.Kind.OFFICIAL_PURCHASE).findFirst().orElseThrow())));
            context.waitForScreen(LedgerDetailScreen.class);
            context.takeScreenshot("paletale-official-purchase-v0.10.8");
            context.runOnClient(client -> client.setScreen(new LedgerScreen(null, media)));

            context.clickScreenButton("统计");
            context.runOnClient(client -> client.setScreen(new LedgerDetailScreen(null, LedgerStore.visible().get(LedgerStore.visible().size() - 1))));
            context.waitForScreen(LedgerDetailScreen.class);
            context.takeScreenshot("paletale-ledger-detail");
            context.runOnClient(client -> client.setScreen(new LedgerSettingsScreen(null)));
            context.waitForScreen(LedgerSettingsScreen.class);
            context.takeScreenshot("paletale-ledger-settings");
            context.runOnClient(client -> client.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("tooltip")) {
                @Override public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float delta) {
                    g.fill(0, 0, width, height, 0xFF1A1030);
                    for(var sample:java.util.List.of(new WineEffectIndex.Info("speed","Known", ""),new WineEffectIndex.Info("unknown","Unknown", ""))) {
                        var component=WineTooltips.line(sample);
                        var text=component.getSiblings().getLast();
                        require(text.getStyle().getColor().getValue()==(sample.key().equals("unknown")?0xAA0000:0xFFFFFF),"Rendered wine effect text matches Minecraft dark-red/white formatting");
                    }
                    String[][] samples = { { "speed", "迅捷 II" }, { "strength", "力量 III" }, { "luck", "幸运" }, { "sober", "解酒" }, { "brand", "烙印" }, { "unknown", "未知" } };
                    for (int i = 0; i < samples.length; i++) g.drawString(font, WineTooltips.line(new WineEffectIndex.Info(samples[i][0], samples[i][1], "")), 10, 10 + i * 14, 0xFFFFFFFF, false);
                }
            }));
            context.waitFor(client -> client.screen != null && "tooltip".equals(client.screen.getTitle().getString()));
            context.takeScreenshot("paletale-wine-tooltip-line-v0.11.6");
            context.runOnClient(client -> {
                Navigation.usePoints(NavPoints.parse("{\"version\":1,\"hubs\":[[\"a\",\"起点港\",40,-30,\"sea\",1,10,80000],[\"b\",\"终点港\",19990,100,\"sea\",0,10,4500],[\"c\",\"银丰落叶驿站\",19000,200,\"east\",1,15,6000]],\"warps\":[[\"兔子洞地下酒吧--(长期)\",19400,-50]]}"));
                client.setScreen(new NavigateScreen(client.screen, "测试广告", java.util.List.of(new NavTargets.Target("广告地址 · 主城", 20000, 0, 70, "广告地址", java.util.List.of()), new NavTargets.Target("箱子商店 · 圆石", 120, 60, null, "箱子商店", java.util.List.of()))));
            });
            context.waitForScreen(NavigateScreen.class);
            context.takeScreenshot("paletale-navigate");
            multipleShopDialog(context,media);
            automaticNpcDiscovery(context,media);
            persistentImages(context,media);
            ShopScanClientTest.run(context);
            context.runOnClient(client -> client.setScreen(new PaleSettingsScreen(null)));
            context.waitForScreen(PaleSettingsScreen.class);
            context.takeScreenshot("paletale-settings");
            context.runOnClient(client -> client.setScreen(new LedgerDetailScreen(null, LedgerStore.visible().stream().filter(e -> e.kind() == LedgerEntry.Kind.SOLD_OUT).findFirst().orElseThrow())));
            context.waitForScreen(LedgerDetailScreen.class);
            context.takeScreenshot("paletale-ledger-soldout-detail");
            context.runOnClient(client -> { LedgerStore.clear(); client.setScreen(detailScreen[0]); });
            context.waitForScreen(ArchiveDetailScreen.class);
            if (Boolean.getBoolean("paletale.test.nativeModels")) {
                context.runOnClient(client -> client.setScreen(new ArchiveDetailScreen(client.screen, media, ArchiveData.Kind.ITEMS, 2)));awaitDetail(context);
                context.takeScreenshot("paletale-native-food-model");
                context.runOnClient(client -> client.screen.onClose());awaitDetail(context);
            }
            context.clickScreenButton("查看拥有者");awaitDetail(context);
            context.runOnClient(client -> require(((ArchiveDetailScreen) client.screen).detail().entry().kind().equals("players"), "Owner link reaches player detail"));
            context.takeScreenshot("paletale-player-independent-page");
            var playerSource=context.computeOnClient(client->client.screen);context.clickScreenButton("SMC世界");context.runOnClient(client->{require(client.screen instanceof WorldEntityScreen&&WorldClientGameTest.field(client.screen,"type").equals("player"),"player icon opens world player details");client.setScreen(playerSource);});awaitDetail(context);
            context.clickScreenButton("查看拥有物品");await(context);
            context.clickScreenButton("玩家档案");await(context);
            context.runOnClient(client -> require(((ArchiveScreen) client.screen).result().entries().getFirst().name().equals("ArchivePlayer"), "Navigation opens player cards"));
            context.getInput().setCursorPos(0, 0);
            context.takeScreenshot("paletale-players-grid");
            context.clickScreenButton("物品档案");await(context);
            context.clickScreenButton("酒");await(context);
            context.runOnClient(client -> require(((ArchiveScreen) client.screen).result().total() == 13, "Wine filter reaches API"));
            realClick(context, "效果：全部效果");
            context.waitForScreen(EffectPickerScreen.class);
            context.takeScreenshot("paletale-wine-effect-picker");
            realClick(context, "迅捷");await(context);
            require(QUERIES.stream().anyMatch(q -> q.startsWith("/api/mcmod/items?") && q.contains("type=wine") && q.contains("effect=speed")), "Wine effect filter reaches API");
            realClick(context, "效果：迅捷");context.waitForScreen(EffectPickerScreen.class);
            realClick(context, "全部效果");await(context);
            context.clickScreenButton("清除筛选");await(context);
            // Favourites filter: needs a signed-in session; the mock site reports one favourite.
            context.runOnClient(client -> {
                ClientAccount.sessionPinned = true;
                try {
                    var field = ClientAccount.class.getDeclaredField("login"); field.setAccessible(true);
                    field.set(PaleTaleClient.ACCOUNT, new ModAuthApi.Login("test-token", new ModAuthApi.Citizen(1, "Tester", "Tester", UUID), System.currentTimeMillis() + 3_600_000, null));
                } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
                // Close first: the old screen saves its (cleared) filters before the new one reads them.
                client.setScreen(null);
                client.setScreen(new ArchiveScreen(null, media, ArchiveData.Kind.ITEMS, 0));
            });
            await(context);
            realClick(context, "我的收藏");await(context);
            context.runOnClient(client -> require(((ArchiveScreen) client.screen).result().total() == 1, "Favourites filter reaches API (total " + ((ArchiveScreen) client.screen).result().total() + ")"));
            realClick(context, "我的收藏");await(context);
            context.runOnClient(client -> require(((ArchiveScreen) client.screen).result().total() == 25, "Favourites filter can be switched off (total " + ((ArchiveScreen) client.screen).result().total() + ", queries " + QUERIES.stream().filter(q -> q.startsWith("/api/mcmod/items?")).toList().reversed().stream().limit(3).toList() + ")"));
            // Current v2 inbox has no automatic read on open; use its maintained HTTP/widget contract.
            NoticeClientGameTest.run(context);
            context.runOnClient(client -> client.setScreen(new MarketScreen(null, media)));
            context.waitFor(client -> client.screen instanceof MarketScreen market && !market.isLoading() && market.result() != null, 200);
            context.runOnClient(client -> signIn());
            realClick(context, "★ 收藏");
            context.waitFor(client -> client.screen instanceof MarketScreen market && !market.isLoading() && market.result() != null, 200);
            context.runOnClient(client -> require(((MarketScreen) client.screen).result().total() == 1, "Market favourites filter reaches API (total " + ((MarketScreen) client.screen).result().total() + ", error " + ((MarketScreen) client.screen).error() + ")"));
            context.runOnClient(client -> signIn());
            realClick(context, "★ 收藏");
            context.waitFor(client -> client.screen instanceof MarketScreen market && !market.isLoading() && market.result() != null, 200);
            context.runOnClient(client -> require(((MarketScreen) client.screen).result().total() == 7, "Market favourites filter can be switched off"));
            realClick(context, "排序：随机");
            context.waitFor(client -> client.screen instanceof MarketScreen market && !market.isLoading() && market.result() != null, 200);
            require(QUERIES.stream().anyMatch(q -> q.startsWith("/api/mcmod/market?") && q.contains("sort=views")), "Market sort reaches API");
            context.runOnClient(client -> client.setScreen(new ArchiveScreen(null, media, ArchiveData.Kind.ITEMS, 0)));
            await(context);
            realClick(context, "排序：随机");await(context);
            require(QUERIES.stream().anyMatch(q -> q.startsWith("/api/mcmod/items?") && q.contains("sort=views")), "Item sort reaches API");
            context.runOnClient(client -> require(((ArchiveScreen) client.screen).result().entries().getFirst().views() >= 0, "Item counters parsed"));
            context.takeScreenshot("paletale-items-ranked");
            realClick(context, "排序：浏览量");await(context);
            realClick(context, "排序：收藏量");await(context);
            // The 地图画 category asks the site for map arts only.
            realClick(context, "地图画");await(context);
            context.runOnClient(client -> require(QUERIES.stream().anyMatch(q -> q.contains("/api/mcmod/items?") && q.contains("type=map_art")), "Map art category reaches the site"));
            realClick(context, "全部");await(context);
            for (int i = 0; i < 24; i++) {
                if (context.computeOnClient(client -> ((ArchiveScreen) client.screen).result().page() == 2)) break;
                context.clickScreenButton("下一页");await(context);
            }
            context.runOnClient(client -> require(((ArchiveScreen) client.screen).result().page() == 2, "Grid viewport and API pagination work together"));
            setQuery(context, "无结果");await(context);
            context.runOnClient(client -> require(((ArchiveScreen) client.screen).result().total() == 0, "Archive empty state visible"));
            setQuery(context, "fail");await(context);
            context.runOnClient(client -> require(!((ArchiveScreen) client.screen).error().isEmpty(), "Archive network error is not an empty directory"));
            context.takeScreenshot("paletale-archive-failure");
            context.runOnClient(client -> client.setScreen(new ArchiveDetailScreen(null, media, ArchiveData.Kind.ITEMS, 999)));awaitDetail(context);
            context.runOnClient(client -> require(!((ArchiveDetailScreen) client.screen).error().isEmpty(), "Unavailable item detail does not expose a card"));
            if (Boolean.getBoolean("paletale.test.live")) live(context);
        } finally { server.stop(0);workers.close(); }
    }
    private static void live(ClientGameTestContext context) {
        MarketApi media = new MarketApi(MarketApi.PREVIEW);
        context.runOnClient(client -> { MarketImages.INSTANCE.clear();client.setScreen(new ArchiveScreen(null, media, ArchiveData.Kind.ITEMS, 0)); });await(context);
        context.runOnClient(client -> {
            var page = ((ArchiveScreen) client.screen).result();require(page != null && page.total() > 24, "Live item catalog is paginated");
            System.out.println("PALETALE_LIVE_ITEMS total=" + page.total() + " first=" + page.entries().getFirst().name());
        });
        int visibleImages = context.computeOnClient(client -> (int) client.screen.children().stream()
                .filter(child -> child instanceof ArchiveCardButton card && !card.entry.imagePath().isBlank()
                        && ArchiveModels.INSTANCE.resolve(card.entry).isEmpty()).count());
        context.waitFor(client -> MarketImages.INSTANCE.loadedCount() >= visibleImages, 1200);
        context.getInput().setCursorPos(0, 0);context.takeScreenshot("paletale-live-items-grid");
        int itemId = context.computeOnClient(client -> ((ArchiveScreen) client.screen).result().entries().getFirst().id());
        context.runOnClient(client -> client.setScreen(new ArchiveDetailScreen(client.screen, media, ArchiveData.Kind.ITEMS, itemId)));awaitDetail(context);
        context.runOnClient(client -> require(((ArchiveDetailScreen) client.screen).detail() != null, "Live item opens independent detail"));context.takeScreenshot("paletale-live-item-page");
        if (Boolean.getBoolean("paletale.test.nativeModels")) {
            String[] names = { "典仪协奏-长路归乡", "萌力觉醒" };
            for (int i = 0; i < names.length; i++) {
                final int index = i;
                context.runOnClient(client -> client.setScreen(new ArchiveScreen(null, media, ArchiveData.Kind.ITEMS, 0)));await(context);
                setQuery(context, names[i]);await(context);
                int nativeId = context.computeOnClient(client -> {
                    var entries = ((ArchiveScreen) client.screen).result().entries();
                    var matching = entries.stream().filter(entry -> entry.nativeAppearance() != null && (index == 0
                            ? Integer.valueOf(30009).equals(entry.nativeAppearance().customModelData())
                            : entry.nativeAppearance().itemModel().equals("smc:food/kejinshiwu/food_gn511_7"))).findFirst().orElseThrow();
                    require(!ArchiveModels.INSTANCE.resolve(matching).isEmpty(), "Real website component matches loaded SIMMC source model");
                    System.out.println("PALETALE_LIVE_NATIVE id=" + matching.id() + " name=" + matching.name());
                    return matching.id();
                });
                context.takeScreenshot(index == 0 ? "paletale-live-native-wine-list" : "paletale-live-native-food-list");
                int[] center = context.computeOnClient(client -> {
                    var card = client.screen.children().stream().filter(ArchiveCardButton.class::isInstance).map(ArchiveCardButton.class::cast).findFirst().orElseThrow();
                    double scale = client.getWindow().getGuiScale();
                    return new int[] { (int) ((card.getX() + card.getWidth() / 2) * scale), (int) ((card.getY() + card.getHeight() / 2) * scale) };
                });
                context.getInput().setCursorPos(center[0], center[1]);context.waitTicks(4);
                context.takeScreenshot(index == 0 ? "paletale-live-native-wine-hover" : "paletale-live-native-food-hover");
                context.runOnClient(client -> client.setScreen(new ArchiveDetailScreen(client.screen, media, ArchiveData.Kind.ITEMS, nativeId)));awaitDetail(context);
                context.takeScreenshot(index == 0 ? "paletale-live-native-wine" : "paletale-live-native-food");
            }
        }
        context.runOnClient(client -> client.setScreen(new ArchiveScreen(null, media, ArchiveData.Kind.PLAYERS, 0)));await(context);
        context.runOnClient(client -> {
            var page = ((ArchiveScreen) client.screen).result();require(page != null && page.total() > 0, "Live player directory loaded");
            System.out.println("PALETALE_LIVE_PLAYERS total=" + page.total() + " first=" + page.entries().getFirst().name());
        });
        context.takeScreenshot("paletale-live-players-grid");
        int playerId = context.computeOnClient(client -> ((ArchiveScreen) client.screen).result().entries().getFirst().id());
        context.runOnClient(client -> client.setScreen(new ArchiveDetailScreen(client.screen, media, ArchiveData.Kind.PLAYERS, playerId)));awaitDetail(context);
        context.runOnClient(client -> require(((ArchiveDetailScreen) client.screen).detail() != null, "Live player detail and possessions loaded"));context.takeScreenshot("paletale-live-player-page");
        int expectedOwned = context.computeOnClient(client -> ((ArchiveDetailScreen) client.screen).detail().ownedTotal());
        int expectedAds = context.computeOnClient(client -> ((ArchiveDetailScreen) client.screen).detail().adsTotal());
        context.clickScreenButton("查看拥有物品");await(context);
        context.runOnClient(client -> require(((ArchiveScreen) client.screen).result().total() == expectedOwned, "Real player ownership link filters by exact registered owner"));
        context.runOnClient(client -> client.screen.onClose());awaitDetail(context);
        context.clickScreenButton("查看玩家广告");
        context.waitFor(client -> client.screen instanceof MarketScreen screen && !screen.isLoading(), 600);
        context.runOnClient(client -> require(((MarketScreen) client.screen).result().total() == expectedAds, "Real player advert link uses exact ID and includes every public state"));
    }
    private static void setQuery(ClientGameTestContext context, String value) {
        context.runOnClient(client -> client.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().setValue(value));context.clickScreenButton("搜");
    }
    private static void await(ClientGameTestContext context) { context.waitFor(client -> client.screen instanceof ArchiveScreen screen && !screen.isLoading(), 600); }
    private static void awaitDetail(ClientGameTestContext context) { context.waitFor(client -> client.screen instanceof ArchiveDetailScreen screen && !screen.isLoading(), 600); }
    private static void require(boolean success, String label) { if (!success) throw new AssertionError(label); }
}
