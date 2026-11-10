package cn.paletale.companion;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/** 640 small textures at most; two downloads/decode workers, never block the render thread. */
public final class MarketImages {
    public static final MarketImages INSTANCE = new MarketImages();
    private record Image(Identifier texture, int width, int height) {}
    private record Loaded(NativeImage pixels, boolean refresh) {}
    private static final java.util.regex.Pattern NPC_AVATAR = java.util.regex.Pattern.compile("/api/mcmod/nav/npcs/(?:(?:npc|shortcut)-[0-9]{1,3}|scan-[a-f0-9]{24})/avatar/([a-f0-9]{64})");
    private static final java.util.regex.Pattern ALLOWED = java.util.regex.Pattern.compile("(?:/smc-items/v(?:24|25)/[a-f0-9]{64}\\.png|/api/mcmod/nav/npcs/(?:(?:npc|shortcut)-[0-9]{1,3}|scan-[a-f0-9]{24})/avatar/[a-f0-9]{64}|/api/mcmod/market/[1-9][0-9]{5}/(?:icon|cover)|/api/mcmod/market/[1-9][0-9]{5}/product/(?:0|[1-9][0-9]?)/image|/api/mcmod/items/[1-9][0-9]{0,9}/image|/api/mcmod/orgs/[1-9][0-9]{0,9}/icon|/api/players/[a-fA-F0-9]{32}/avatar|/api/smc-world/players/[A-Za-z0-9_]{1,16}/avatar)");
    private final LinkedHashMap<String, Image> images = new LinkedHashMap<>(256, .75f, true);
    private final java.util.Set<String> pending = new java.util.HashSet<>();
    private final java.util.Map<String, Long> failed = new java.util.HashMap<>();
    private final java.util.concurrent.ExecutorService worker = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "PaleTale-icons"); thread.setDaemon(true); return thread;
    });
    private final java.util.concurrent.ExecutorService diskWorker=Executors.newSingleThreadExecutor(task->{Thread t=new Thread(task,"PaleTale-icon-cache");t.setDaemon(true);return t;});
    private final HttpClient http = HttpBodies.CLIENT;
    private int sequence;
    private int generation;

    public boolean draw(GuiGraphics graphics, MarketApi api, String path, int x, int y, int size) {
        Image image = request(api,path);
        if (image != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, image.texture(), x, y, 0, 0, size, size, image.width(), image.height(), image.width(), image.height());
            return true;
        }
        return false;
    }
    /** Draws the picture filling a rectangle (centred, the overflow cropped), for covers; false while it is not loaded. */
    public boolean drawFill(GuiGraphics graphics, MarketApi api, String path, int x, int y, int width, int height) {
        Image image = request(api, path);
        if (image == null || width < 1 || height < 1) return false;
        double target = (double) width / height, source = (double) image.width() / image.height();
        int regionWidth = image.width(), regionHeight = image.height(), u = 0, v = 0;
        if (source > target) { regionWidth = (int) Math.round(image.height() * target); u = (image.width() - regionWidth) / 2; }
        else { regionHeight = (int) Math.round(image.width() / target); v = (image.height() - regionHeight) / 2; }
        graphics.blit(RenderPipelines.GUI_TEXTURED, image.texture(), x, y, u, v, width, height, regionWidth, regionHeight, image.width(), image.height());
        return true;
    }
    /** Warm a fixed NPC portrait as soon as its hash is known, before the navigation window is opened. */
    public void prefetch(MarketApi api,String path) { request(api,path); }
    private Image request(MarketApi api,String key) {
        if(key==null||!ALLOWED.matcher(key).matches())return null;
        var image=images.get(key);if(image!=null)return image;
        if (pending.size() >= 96 || pending.contains(key) || failed.getOrDefault(key, 0L) > System.currentTimeMillis()) return null;
        if (failed.size() > 64) failed.clear();
        pending.add(key); int run = generation;
        CompletableFuture.supplyAsync(() -> loadCached(key), diskWorker).thenAccept(loaded -> Minecraft.getInstance().execute(() -> {
            if (run != generation) { if (loaded != null) loaded.pixels().close(); return; }
            pending.remove(key);
            if (loaded == null) { refresh(key,api.site().resolve(key),run); return; }
            install(key,loaded.pixels());
            if(loaded.refresh())refresh(key,api.site().resolve(key),run);
        }));
        return null;
    }
    private void install(String key,NativeImage pixels) {
        var old=images.remove(key);if(old!=null)Minecraft.getInstance().getTextureManager().release(old.texture());
        while(images.size()>=640){String oldest=images.keySet().iterator().next();Minecraft.getInstance().getTextureManager().release(images.remove(oldest).texture());}
        Identifier texture=Identifier.fromNamespaceAndPath("paletale","remote/"+sequence++);
        Minecraft.getInstance().getTextureManager().register(texture,new DynamicTexture(()->"PaleTale icon",pixels));
        images.put(key,new Image(texture,pixels.getWidth(),pixels.getHeight()));
    }
    /** True while the picture is still being fetched, so callers can hold their fallback back. */
    public boolean loading(MarketApi api, String path) { return path != null && pending.contains(path); }
    private static final java.nio.file.Path CACHE = Minecraft.getInstance().gameDirectory.toPath().resolve("paletale").resolve("cache").resolve("assets");
    private static final long FRESH_MS = 6L * 3600_000, KEEP_MS = 14L * 24 * 3600_000;
    private static java.nio.file.Path cacheFile(String key) throws Exception {
        var npc=NPC_AVATAR.matcher(key);if(npc.matches())return CACHE.resolve("npc").resolve(npc.group(1)+".png");
        var digest = java.security.MessageDigest.getInstance("SHA-256").digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return CACHE.resolve(java.util.HexFormat.of().formatHex(digest) + ".png");
    }
    /** Every valid disk image is shown first. Mutable stale images refresh without delaying their display. */
    private Loaded loadCached(String key) {
        try {
            var file=cacheFile(key);
            if (java.nio.file.Files.isRegularFile(file) && java.nio.file.Files.size(file) <= byteLimit(key)) {
                var saved=readImage(java.nio.file.Files.readAllBytes(file),key);
                if(saved!=null)return new Loaded(saved,!NPC_AVATAR.matcher(key).matches()&&System.currentTimeMillis()-java.nio.file.Files.getLastModifiedTime(file).toMillis()>=FRESH_MS);
            }
        } catch (Exception ignored) { /* Cache problems only cost a download. */ }
        return null;
    }
    /** A picture that is simply not there is remembered far longer than a failed download, so it is not requested again on every redraw. */
    private static final long MISSING_MS = 10 * 60_000L, RETRY_MS = 15_000L;
    private record Fetched(byte[] bytes, int status) {}
    private record Download(NativeImage pixels, int status) {}

    private void refresh(String key,URI uri,int run) {
        pending.add(key);
        CompletableFuture.supplyAsync(()->{var done=download(key,uri);if(done.pixels()!=null)saveImage(key,done.pixels());return done;},worker)
                .thenAccept(done->Minecraft.getInstance().execute(()->{
            if(run!=generation){if(done.pixels()!=null)done.pixels().close();return;}pending.remove(key);
            if(done.pixels()!=null)install(key,done.pixels());
            else failed.put(key,System.currentTimeMillis()+(done.status()==404||done.status()==410?MISSING_MS:RETRY_MS));
        }));
    }
    private Download download(String key,URI uri) {
        var npc=NPC_AVATAR.matcher(key);
        if(npc.matches()) {
            var fetched=fetch(uri,key);
            var fromSite=readImage(fetched.bytes(),key);if(fromSite!=null)return new Download(fromSite,fetched.status());
            // The public Minecraft texture hash also works before a shared portrait exists on the website.
            try {
                var skin=fetch(URI.create("https://textures.minecraft.net/texture/"+npc.group(1)),key);
                var pixels=NpcPortrait.fromSkin(skin.bytes());
                if(pixels!=null)return new Download(pixels,skin.status()==0?fetched.status():skin.status());
            } catch(Exception ignored) { /* the website portrait is the fallback below */ }
            return new Download(null,fetched.status());
        }
        int status = 0;
        // Two attempts at most: a 404 is final, a 5xx or a broken connection is worth one more try.
        for (int attempt = 0; attempt < 2; attempt++) {
            if (attempt > 0) {
                try { Thread.sleep(1000); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
            }
            var fetched=fetch(uri,key); status=fetched.status();
            var pixels=readImage(fetched.bytes(),key);if(pixels!=null)return new Download(pixels,status);
            if (status >= 400 && status < 500) break;
        }
        return new Download(null,status);
    }
    /** Covers are wide pictures (up to 512 px, 256 KB); everything else stays a small icon (128 px, 128 KB). */
    private static boolean cover(String key) { return key != null && key.endsWith("/cover"); }
    private static int byteLimit(String key) { return cover(key) ? 256 * 1024 : 128 * 1024; }
    private static int sideLimit(String key) { return cover(key) ? 512 : 128; }
    private Fetched fetch(URI uri, String key) {
        try {
            var response=http.send(HttpBodies.request(uri).timeout(Duration.ofSeconds(12)).GET().build(),HttpBodies.limited(byteLimit(key)));
            boolean png=response.statusCode()==200&&response.headers().firstValue("Content-Type").orElse("").startsWith("image/png");
            return new Fetched(png?response.body():null,response.statusCode());
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();return new Fetched(null,0);}catch(Exception offline){return new Fetched(null,0);}
    }
    private static NativeImage readImage(byte[] bytes, String key) {
        try {
            if(bytes==null||bytes.length<24||bytes.length>byteLimit(key)||java.nio.ByteBuffer.wrap(bytes).getLong()!=0x89504e470d0a1a0aL)return null;
            var header=java.nio.ByteBuffer.wrap(bytes);int width=header.getInt(16),height=header.getInt(20);
            return width>0&&height>0&&width<=sideLimit(key)&&height<=sideLimit(key)?NativeImage.read(bytes):null;
        }catch(Exception invalid){return null;}
    }
    private static void saveImage(String key,NativeImage pixels) {
        try {var file=cacheFile(key);java.nio.file.Files.createDirectories(file.getParent());var temp=java.nio.file.Files.createTempFile(file.getParent(),"portrait-",".png");pixels.writeToFile(temp);java.nio.file.Files.move(temp,file,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}catch(Exception ignored){}
    }
    /** Drop cache files nobody has refreshed for two weeks. */
    public void prune() {
        diskWorker.execute(() -> {
            // Pictures cached by earlier versions lived in <game>/paletale-cache.
            java.nio.file.Path legacy = CACHE.getParent().getParent().getParent().resolve("paletale-cache");
            try (var old = java.nio.file.Files.list(legacy)) {
                java.nio.file.Files.createDirectories(CACHE);
                old.forEach(path -> { try { java.nio.file.Files.move(path, CACHE.resolve(path.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING); } catch (Exception ignored) { } });
                java.nio.file.Files.deleteIfExists(legacy);
            } catch (Exception ignored) { }
            try (var files = java.nio.file.Files.list(CACHE)) {
                files.forEach(path -> { try { if (java.nio.file.Files.isRegularFile(path)&&System.currentTimeMillis() - java.nio.file.Files.getLastModifiedTime(path).toMillis() > KEEP_MS) java.nio.file.Files.deleteIfExists(path); } catch (Exception ignored) { } });
            } catch (Exception ignored) { }
        });
    }
    public void clear() {
        generation++; pending.clear(); failed.clear();
        if (worker instanceof java.util.concurrent.ThreadPoolExecutor pool) pool.getQueue().clear();
        if (diskWorker instanceof java.util.concurrent.ThreadPoolExecutor diskPool) diskPool.getQueue().clear();
        images.values().forEach(image -> Minecraft.getInstance().getTextureManager().release(image.texture()));
        images.clear();
    }
    public int loadedCount() { return images.size(); }
}
