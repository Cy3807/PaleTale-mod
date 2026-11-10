package cn.paletale.companion;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Places an ad can send you to: the coordinates written in its locations and the positions of its bound chest shops. */
public final class NavTargets {
    /**
     * {@code y}: the height when the coordinates gave one, else null. {@code kind}: 广告地址 / 箱子商店. {@code marks}: blocks to light up (the sign and chest of a shop; the address block when its height is known), each {x, y, z}.
     */
    public record Target(String name, int x, int z, Integer y, String kind, List<int[]> marks) {}
    private static final Pattern NUMBER = Pattern.compile("-?\\d{1,6}");
    private NavTargets() {}

    /** "x / y / z" (the site's format), "x y z" or "x z": {x, z} or {x, z, y}; null when it is not coordinates. */
    public static int[] parse(String text) {
        if (text == null) return null;
        Matcher m = NUMBER.matcher(text.replace('，', ',').replace('　', ' '));
        List<Integer> numbers = new ArrayList<>();
        while (m.find() && numbers.size() < 4) numbers.add(Integer.parseInt(m.group()));
        if (numbers.size() == 3) return new int[]{ numbers.get(0), numbers.get(2), numbers.get(1) };
        if (numbers.size() == 2) return new int[]{ numbers.get(0), numbers.get(1) };
        return null;
    }
    /** How wide a text is: a Chinese character counts 1, a letter or digit half. */
    static double units(String text) { double sum = 0; for (int i = 0; i < text.length(); ) { int cp = text.codePointAt(i); sum += cp < 0x250 ? 0.5 : 1; i += Character.charCount(cp); } return sum; }
    /** The start of a text that fits in {@code budget} units, then "..." when it was cut. */
    static String cut(String text, double budget) {
        if (units(text) <= budget) return text;
        StringBuilder out = new StringBuilder(); double used = 0;
        for (int i = 0; i < text.length(); ) { int cp = text.codePointAt(i); double w = cp < 0x250 ? 0.5 : 1; if (used + w > budget) break; out.appendCodePoint(cp); used += w; i += Character.charCount(cp); }
        return out + "...";
    }
    /**
     * A name that fits {@code budget} units: "title" alone gets all of it; "title · product" is kept whole when it fits, else the shorter part keeps what it needs and the longer
     * one gets the rest (each at least half the budget when both are long).
     */
    static String fit(String name, double budget) {
        String[] parts = name.split(" · ", 2);
        if (parts.length == 1) return cut(name, budget);
        double a = units(parts[0]), b = units(parts[1]), half = budget / 2;
        if (a + b <= budget) return name;
        if (a <= half) return parts[0] + " · " + cut(parts[1], budget - a);
        if (b <= half) return cut(parts[0], budget - b) + " · " + parts[1];
        return cut(parts[0], half) + " · " + cut(parts[1], half);
    }
    /** The first address with coordinates, or null. */
    public static MarketData.Location firstLocated(MarketData.Ad ad) {
        for (var location : ad.locations()) if (parse(location.coordinates()) != null) return location;
        return null;
    }
    /** The place the advertiser set first (the first address with coordinates), or null. */
    public static Target ofAd(MarketData.Ad ad) {
        for (var location : ad.locations()) {
            int[] at = parse(location.coordinates());
            if (at != null) return new Target(ad.title(), at[0], at[1], at.length > 2 ? at[2] : null, "广告", at.length > 2 ? List.of(new int[]{ at[0], at[2], at[1] }) : List.of());
        }
        return null;
    }
    /** The chest shops bound to a product, one target each (same place only once). */
    public static List<Target> ofProduct(MarketData.Ad ad, MarketData.Product product) {
        List<Target> targets = new ArrayList<>();
        for (var shop : product.shops()) {
            var reading = shop.reading();
            if (targets.stream().anyMatch(t -> t.x() == reading.x() && t.z() == reading.z())) continue;
            targets.add(shopTarget(ad, product, reading));
        }
        return targets;
    }
    private static Target shopTarget(MarketData.Ad ad, MarketData.Product product, ShopSigns.Reading reading) {
        List<int[]> marks = new ArrayList<>();
        marks.add(new int[]{ reading.x(), reading.y(), reading.z() });
        if (reading.hasChest()) marks.add(reading.chest());
        return new Target(ad.title() + " · " + (product.name().isBlank() ? reading.itemName() : product.name()), reading.x(), reading.z(), reading.y(), "广告", List.copyOf(marks));
    }
    public static List<Target> of(MarketData.Ad ad) {
        List<Target> targets = new ArrayList<>();
        for (var location : ad.locations()) {
            int[] at = parse(location.coordinates());
            if (at != null) targets.add(new Target(ad.locations().size() > 1 && !location.name().isBlank() ? ad.title() + " · " + location.name() : ad.title(), at[0], at[1], at.length > 2 ? at[2] : null, "广告", at.length > 2 ? List.of(new int[]{ at[0], at[2], at[1] }) : List.of()));
        }
        for (var product : ad.products()) for (var shop : product.shops()) {
            var reading = shop.reading();
            if (targets.stream().anyMatch(t -> t.x() == reading.x() && t.z() == reading.z())) continue;
            targets.add(shopTarget(ad, product, reading));
        }
        return targets;
    }
}
