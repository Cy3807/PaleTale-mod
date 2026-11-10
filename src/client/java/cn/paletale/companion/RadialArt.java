package cn.paletale.companion;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.List;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * How the command wheel looks, in PaleTale's own colours (paper tiles with a grey outline and white inner line, green when lit; PaleTheme's flipped copies in the dark theme).
 * The wheel is eight separate tiles with a gap between them and nothing in the middle but a small name plate; a second-level wheel has as many tiles as it has entries.
 * Tiles are painted once into textures (2x resolution, anti-aliased by 3x3 sampling) per tile count and theme, and the lit tile of each sector the first time it is pointed at, so a frame costs
 * two texture blits and the icons. Drawn art (textures/gui/radial/plate.png and sector.png) replaces the painted eight-tile wheel when present.
 */
final class RadialArt {
    static final int INNER = 36, OUTER = 96, EXTENT = 108, GAP = 3, MAIN = RadialSlots.COUNT;
    private static final int SCALE = 2;

    /** What a tile shows: an item, or a stand-in glyph, or a plus (an empty slot you can fill), or nothing. */
    record Icon(ItemStack stack, String glyph, boolean plus, float scale) {
        Icon(ItemStack stack, String glyph, boolean plus) { this(stack, glyph, plus, 0f); }
        static final Icon NONE = new Icon(ItemStack.EMPTY, "", false), PLUS = new Icon(ItemStack.EMPTY, "", true);
        static Icon of(ItemStack stack) { return new Icon(stack, "", false); }
        /** An item drawn at a fixed size (1 = 16 px) instead of the wheel's usual one. */
        static Icon sized(ItemStack stack, float scale) { return new Icon(stack, "", false, scale); }
    }
    private record Tex(Identifier id, int side) {}
    private static final java.util.Map<Long, Tex> TEXTURES = new java.util.HashMap<>();
    private static int subCount = -1;
    private static boolean packedTried, packedOk; private static int packedSide;
    private static int lastHover = -1; private static long hoverSince;
    private RadialArt() {}

    static int tiles(int n) { return Math.max(2, n); }

    /** The sector a direction (offset from the centre) points at. */
    static int sectorAt(double dx, double dy) { return sectorAt(dx, dy, MAIN); }
    static int sectorAt(double dx, double dy, int n) {
        double step = 360.0 / n, angle = Math.toDegrees(Math.atan2(dx, -dy)); if (angle < 0) angle += 360;
        return (int) (((angle + step / 2) % 360) / step) % n;
    }

    // ---- painting the textures ----
    private static int argb(int a, int r, int g, int b) { return a << 24 | r << 16 | g << 8 | b; }
    private static int mix(int from, int to, double t) {
        int out = 0;
        for (int shift = 24; shift >= 0; shift -= 8) out |= (int) Math.round((from >>> shift & 255) * (1 - t) + (to >>> shift & 255) * t) << shift;
        return out;
    }
    private static int shade(int light, boolean dark) { return dark ? PaleTheme.map(light) : light; }

    /** Distance of a point from the nearest border between two sectors, in pixels (the border lines run through the centre). */
    private static double borderDistance(double dx, double dy, int n) {
        double r = Math.hypot(dx, dy), step = 360.0 / n, angle = Math.toDegrees(Math.atan2(dx, -dy)); if (angle < 0) angle += 360;
        double within = ((angle + step / 2) % 360) % step;
        return r * Math.sin(Math.toRadians(Math.min(within, step - within)));
    }

    /** A tile of one sector: a paper card with a grey outline and a white inner line (green when lit); transparent outside the tile. */
    private static int tile(double dx, double dy, int n, boolean lit, boolean dark) {
        double r = Math.hypot(dx, dy); if (r < INNER || r > OUTER) return 0;
        double e = borderDistance(dx, dy, n) - GAP; if (e < 0) return 0;
        double edge = Math.min(Math.min(r - INNER, OUTER - r), e);
        if (edge < 2.2) return shade(lit ? 0xE62C4D3D : 0xDC747772, dark);
        if (edge < 3.2) return shade(lit ? 0xB0E4EFE8 : 0xA0FFFFFF, dark);
        double t = (r - INNER) / (OUTER - INNER);
        return lit ? mix(shade(0xDC3B6652, dark), shade(0xDC5A8F76, dark), t) : mix(shade(0xC8F7F6F2, dark), shade(0xC8E6E4DC, dark), t);
    }

    private interface Painter { int at(double dx, double dy); }

    /** Pixels the art cannot reach (outside the ring, or in another sector than the one painted) are cleared without the 3x3 sampling; the margin covers the anti-aliasing. */
    private static NativeImage paintImage(int side, int n, int only, Painter painter) {
        var image = new NativeImage(side, side, true);
        for (int y = 0; y < side; y++) for (int x = 0; x < side; x++) {
            double cdx = (x + .5) / SCALE - EXTENT, cdy = (y + .5) / SCALE - EXTENT, cr = Math.hypot(cdx, cdy);
            if (cr > OUTER + 2 || cr < INNER - 2 || only >= 0 && sectorAt(cdx, cdy, n) != only && borderDistance(cdx, cdy, n) > 2.5) { image.setPixel(x, y, 0); continue; }
            long a = 0, r = 0, g = 0, b = 0;
            for (int sy = 0; sy < 3; sy++) for (int sx = 0; sx < 3; sx++) {
                int c = painter.at((x + (sx + .5) / 3) / SCALE - EXTENT, (y + (sy + .5) / 3) / SCALE - EXTENT), ca = c >>> 24;
                a += ca; r += (long) (c >> 16 & 255) * ca; g += (long) (c >> 8 & 255) * ca; b += (long) (c & 255) * ca;
            }
            image.setPixel(x, y, a == 0 ? 0 : argb((int) Math.round(a / 9.0), (int) (r / a), (int) (g / a), (int) (b / a)));
        }
        return image;
    }

    private static Identifier register(String name, NativeImage image) {
        var id = Identifier.fromNamespaceAndPath("paletale", "radial/" + name);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "PaleTale wheel " + name, image));
        return id;
    }

    /** A square PNG of the resource pack (the mod's own or a pack's), 216..1024 px; null when absent or unusable. */
    private static NativeImage packed(String name) {
        try (var in = Minecraft.getInstance().getResourceManager().open(Identifier.fromNamespaceAndPath("paletale", "textures/gui/radial/" + name + ".png"))) {
            var image = NativeImage.read(in);
            if (image.getWidth() == image.getHeight() && image.getWidth() >= EXTENT * 2 && image.getWidth() <= 1024) return image;
            image.close();
        } catch (java.io.IOException | RuntimeException absent) { /* painted instead */ }
        return null;
    }

    /** The plate rotated by a number of sectors (bilinear), so one drawn highlight serves all eight. */
    private static NativeImage rotated(NativeImage source, int steps) {
        int n = source.getWidth(); var out = new NativeImage(n, n, true);
        double a = Math.toRadians(360.0 / MAIN * steps), cos = Math.cos(a), sin = Math.sin(a), c = n / 2.0;
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            double dx = x + .5 - c, dy = y + .5 - c, sx = dx * cos + dy * sin + c - .5, sy = -dx * sin + dy * cos + c - .5;
            int x0 = (int) Math.floor(sx), y0 = (int) Math.floor(sy); double fx = sx - x0, fy = sy - y0;
            double a4 = 0, r = 0, g = 0, b = 0;
            for (int k = 0; k < 4; k++) {
                int px = x0 + (k & 1), py = y0 + (k >> 1); double w = ((k & 1) == 0 ? 1 - fx : fx) * ((k >> 1) == 0 ? 1 - fy : fy);
                if (px < 0 || py < 0 || px >= n || py >= n || w == 0) continue;
                int p = source.getPixel(px, py); double pa = (p >>> 24) * w;
                a4 += pa; r += (p >> 16 & 255) * pa; g += (p >> 8 & 255) * pa; b += (p & 255) * pa;
            }
            out.setPixel(x, y, a4 < .5 ? 0 : argb((int) Math.round(a4), (int) Math.round(r / a4), (int) Math.round(g / a4), (int) Math.round(b / a4)));
        }
        return out;
    }

    /** Drawn art for the eight-tile wheel, loaded once: the plate and the eight rotated highlights, used in both themes. */
    private static boolean loadPacked() {
        if (packedTried) return packedOk;
        packedTried = true;
        var plate = packed("plate"); var sector = packed("sector");
        if (plate != null && sector != null && plate.getWidth() == sector.getWidth()) {
            packedSide = plate.getWidth();
            for (int i = 0; i < MAIN; i++) for (int theme = 0; theme < 2; theme++) TEXTURES.put(key(theme, MAIN, i), new Tex(theme == 0 ? register("lit" + i, rotated(sector, i)) : TEXTURES.get(key(0, MAIN, i)).id(), packedSide));
            Identifier id = register("plate", plate); TEXTURES.put(key(0, MAIN, -1), new Tex(id, packedSide)); TEXTURES.put(key(1, MAIN, -1), new Tex(id, packedSide));
            sector.close(); packedOk = true;
        } else { if (plate != null) plate.close(); if (sector != null) sector.close(); }
        return packedOk;
    }

    private static long key(int theme, int n, int sector) { return ((long) theme << 40) | ((long) n << 20) | (sector + 1); }

    /** The plate (sector -1) or the lit tile of a sector for a tile count and the current theme, painted on first use. */
    private static Tex texture(int n, int sector) {
        int theme = PaleTheme.dark() ? 1 : 0; boolean dark = theme == 1;
        if (n == MAIN && loadPacked()) return TEXTURES.get(key(theme, n, sector));
        if (n != MAIN && subCount != n) {
            // Only one second-level layout is kept: the one just left is released.
            if (subCount > 0) for (var it = TEXTURES.entrySet().iterator(); it.hasNext(); ) { var e = it.next(); if (((e.getKey() >> 20) & 0xFFFFF) == subCount) { Minecraft.getInstance().getTextureManager().release(e.getValue().id()); it.remove(); } }
            subCount = n;
        }
        return TEXTURES.computeIfAbsent(key(theme, n, sector), k -> {
            int side = EXTENT * 2 * SCALE; String name = (sector < 0 ? "plate" : "lit" + sector) + "_" + n + (dark ? "_dark" : "");
            return new Tex(register(name, readyOrPaint(k, side, n, sector, painterFor(n, sector, dark))), side);
        });
    }

    /** Images painted ahead on a background thread (pure arithmetic), waiting for the render thread to upload them. */
    private static final java.util.concurrent.ConcurrentHashMap<Long, NativeImage> READY = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<Long> WARMING = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static NativeImage readyOrPaint(long key, int side, int n, int sector, Painter painter) {
        var ready = READY.remove(key);
        return ready != null ? ready : paintImage(side, n, sector, painter);
    }
    private static Painter painterFor(int n, int sector, boolean dark) {
        return (dx, dy) -> sector < 0 ? tile(dx, dy, n, false, dark) : sectorAt(dx, dy, n) == sector ? tile(dx, dy, n, true, dark) : 0;
    }
    /** Paints the plate and every lit sector of an {@code n}-tile wheel in the background, so the first frames never stall on painting. Cheap to call again; nothing happens for the drawn-art wheel. */
    static void warm(int n) {
        int count = tiles(n), theme = PaleTheme.dark() ? 1 : 0; boolean dark = theme == 1;
        if (count == MAIN && loadPacked() || !WARMING.add(key(theme, count, -1))) return;
        Thread.ofPlatform().daemon().name("paletale-wheel-paint").priority(Thread.MIN_PRIORITY).start(() -> {
            try {
                // Plate and every lit sector for the eight-tile wheel; bigger rings (territories) only get the plate, the rest is painted when pointed at.
                for (int sector = -1; sector < (count <= MAIN ? count : 0); sector++) {
                    long k = key(theme, count, sector);
                    if (!TEXTURES.containsKey(k)) READY.putIfAbsent(k, paintImage(EXTENT * 2 * SCALE, count, sector, painterFor(count, sector, dark)));
                }
            } catch (RuntimeException failed) { /* painted on demand instead */ }
            finally { WARMING.remove(key(theme, count, -1)); }
        });
    }

    private static void blit(GuiGraphics g, Tex tex, int cx, int cy, int color) {
        g.blit(RenderPipelines.GUI_TEXTURED, tex.id(), cx - EXTENT, cy - EXTENT, 0, 0, EXTENT * 2, EXTENT * 2, tex.side(), tex.side(), tex.side(), tex.side(), color);
    }
    private static int alpha(double a) { return (int) Math.round(255 * Math.max(0, Math.min(1, a))) << 24 | 0xFFFFFF; }

    /** Restarts the highlight and opening animations (the wheel just appeared or changed level). */
    static void reset() { lastHover = -1; hoverSince = 0; }

    /** The icons of the saved slots of the main wheel. */
    private static List<RadialSlots.Slot> iconSlots; private static List<Icon> iconList = List.of(); private static long iconsAt;
    /** The icons are rebuilt when the slots change and at most every 1.5 s otherwise (a bound player's head follows who is online): a frame only reads the list. */
    static List<Icon> slotIcons(List<RadialSlots.Slot> slots) {
        long now = System.currentTimeMillis();
        if (slots == iconSlots && now - iconsAt < 1500) return iconList;
        iconList = buildSlotIcons(slots); iconSlots = slots; iconsAt = now;
        return iconList;
    }
    private static List<Icon> buildSlotIcons(List<RadialSlots.Slot> slots) {
        List<Icon> out = new java.util.ArrayList<>();
        for (var slot : slots) {
            if (slot.empty()) { out.add(Icon.PLUS); continue; }
            ItemStack stack = stack(slot.icon());
            var bound = RadialSlots.bound(slot.command());
            if (stack.isEmpty() && bound != null) { out.add(Icon.of(bound[0].equals("land") ? LandCache.icon(bound[1]) : headOf(bound[1]))); continue; }
            out.add(stack.isEmpty() ? new Icon(ItemStack.EMPTY, slot.label().isEmpty() ? "?" : slot.label().substring(0, slot.label().offsetByCodePoints(0, 1)), false) : Icon.of(stack));
        }
        return out;
    }

    /** Draws a wheel of {@code n} tiles (at least three) centred on (cx, cy). {@code open} runs 0..1 as it appears; the hub shows only a small name plate. */
    static void draw(GuiGraphics g, Font font, int cx, int cy, int n, List<Icon> icons, int hover, double open, String name) {
        int count = tiles(n);
        long now = System.currentTimeMillis();
        if (hover != lastHover) { lastHover = hover; hoverSince = now; }
        double ease = 1 - Math.pow(1 - open, 3), size = .86 + .14 * ease, grow = hover >= 0 ? 1 + .06 * Math.min(1, (now - hoverSince) / 90.0) : 1;
        g.pose().pushMatrix(); g.pose().translate(cx, cy); g.pose().scale((float) size, (float) size); g.pose().translate(-cx, -cy);
        blit(g, texture(count, -1), cx, cy, alpha(ease));
        if (hover >= 0 && hover < count) {
            g.pose().pushMatrix(); g.pose().translate(cx, cy); g.pose().scale((float) grow, (float) grow); g.pose().translate(-cx, -cy);
            blit(g, texture(count, hover), cx, cy, alpha(Math.min(1, (now - hoverSince) / 60.0) * ease));
            g.pose().popMatrix();
        }
        float scale = count <= 8 ? 2f : count <= 14 ? 1.5f : 1f;
        for (int i = 0; i < count && i < icons.size(); i++) {
            var icon = icons.get(i); boolean on = i == hover;
            double angle = Math.toRadians(360.0 / count * i), mid = (INNER + OUTER) / 2.0 + (on ? 3 : 0);
            int lx = cx + (int) Math.round(Math.sin(angle) * mid), ly = cy - (int) Math.round(Math.cos(angle) * mid);
            int onColor = MarketTheme.c(0xFFF5F4F0);
            if (icon.plus()) { int c = on ? onColor : MarketTheme.MUTED; g.fill(lx - 5, ly, lx + 6, ly + 1, c); g.fill(lx, ly - 5, lx + 1, ly + 6, c); continue; }
            float drawn = icon.scale() > 0 ? icon.scale() : scale;
            g.pose().pushMatrix(); g.pose().translate(lx, ly); g.pose().scale(drawn, drawn);
            if (!icon.stack().isEmpty()) g.renderItem(icon.stack(), -8, -8);
            else if (!icon.glyph().isEmpty()) g.drawString(font, icon.glyph(), -font.width(icon.glyph()) / 2, -4, on ? onColor : MarketTheme.INK, false);
            g.pose().popMatrix();
        }
        // The name plate in the middle, drawn like the mod's own windows: paper, grey line, ink text, green underline.
        if (name != null) {
            int w = Math.max(54, font.width(name) + 18), h = 20, x = cx - w / 2, y = cy - h / 2;
            g.fill(x, y, x + w, y + h, MarketTheme.PAPER); g.renderOutline(x, y, w, h, MarketTheme.LINE);
            g.fill(x + 3, y + h - 4, x + w - 3, y + h - 3, MarketTheme.c(0x553B6652));
            g.drawString(font, name, cx - font.width(name) / 2, y + 5, MarketTheme.INK, false);
        }
        g.pose().popMatrix();
    }

    /** A player's head as the game draws it in the inventory (3D, with their skin when they are online and the game holds it). */
    private record Head(ItemStack stack, long at) {}
    private static final java.util.Map<String, Head> HEADS = new java.util.HashMap<>();
    /** One stack per name for 3 s: a new unresolved profile every frame would make the game look the skin up again and again. */
    static ItemStack headOf(String name) {
        long now = System.currentTimeMillis(); var cached = HEADS.get(name);
        if (cached != null && now - cached.at() < 3000) return cached.stack();
        if (HEADS.size() > 64) HEADS.clear();
        var made = makeHead(name); HEADS.put(name, new Head(made, now));
        return made;
    }
    private static ItemStack makeHead(String name) {
        var head = new ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
        var connection = Minecraft.getInstance().getConnection();
        var info = connection == null ? null : connection.getPlayerInfo(name);
        GameProfile profile = null;
        if (info != null) { profile = info.getProfile(); SkinMemory.remember(profile); }
        // Not online: the skin this client saw them with, else the game looks it up by name.
        else profile = SkinMemory.profile(name);
        if (profile != null) head.set(net.minecraft.core.component.DataComponents.PROFILE, net.minecraft.world.item.component.ResolvableProfile.createResolved(profile));
        else if (name.matches("[A-Za-z0-9_]{1,16}")) head.set(net.minecraft.core.component.DataComponents.PROFILE, net.minecraft.world.item.component.ResolvableProfile.createUnresolved(name));
        return head;
    }

    private static final java.util.Map<String, ItemStack> STACKS = new java.util.HashMap<>();
    static ItemStack stack(String icon) {
        if (icon.isEmpty()) return ItemStack.EMPTY;
        return STACKS.computeIfAbsent(icon, key -> {
            var id = Identifier.tryParse(key);
            return id == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
        });
    }
}
