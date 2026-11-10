package cn.paletale.companion;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class WorldMergeTest {
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    public static void run() {
        var merged = json("""
            {"mode":"sell","quantity":192,"x":10,"y":64,"z":-20,"owner":"Shop","itemName":"钻石",
             "positions":[{"id":7,"x":10,"y":64,"z":-20,"observedAt":"2026-11-08T10:00:00Z"},{"id":3,"x":11,"y":64,"z":-21,"observedAt":"2026-11-07T10:00:00Z"},{"id":1,"x":12,"y":65,"z":-22,"observedAt":"2026-11-06T10:00:00Z"}]}""");
        check(WorldMerge.grouped(merged) && WorldMerge.positions(merged).size() == 3, "group with three chests");
        check(WorldMerge.positions(merged).get(0).id() == 7 && WorldMerge.positions(merged).get(0).coordinates().equals("10 64 -20"), "newest first, as sent");
        check(WorldMerge.coordinates(merged).equals("10, 64, -20 +2"), "coordinates show the latest chest and +N");
        check(merged.get("quantity").getAsInt() == 192, "quantity is the server's sum");
        var old = json("{\"mode\":\"both\",\"quantity\":64,\"x\":1,\"y\":2,\"z\":3}");
        check(!WorldMerge.grouped(old) && WorldMerge.positions(old).isEmpty() && WorldMerge.coordinates(old).equals("1, 2, 3"), "old rows without positions behave as before");
        var single = json("{\"quantity\":64,\"x\":1,\"y\":2,\"z\":3,\"positions\":[{\"id\":5,\"x\":1,\"y\":2,\"z\":3,\"observedAt\":\"2026-11-08T10:00:00Z\"}]}");
        check(!WorldMerge.grouped(single) && WorldMerge.coordinates(single).equals("1, 2, 3"), "a single position is a plain row");
        var page = json("{\"version\":1,\"total\":12,\"boxTotal\":40,\"shops\":[]}");
        check(WorldMerge.total(page) == 12 && WorldMerge.boxTotal(page) == 40, "total pages by merged rows, boxTotal counts chests");
        check(WorldMerge.boxTotal(json("{\"total\":9}")) == 9, "boxTotal falls back to total on the old site");
        boolean rejected = false;
        try { WorldMerge.positions(json("{\"positions\":[{\"x\":2000000,\"y\":1,\"z\":1}]}")); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "out-of-range positions rejected");
        check(WorldMerge.positions(json("{\"positions\":[{\"id\":\"abc\",\"x\":1,\"y\":2,\"z\":3},{\"id\":\"9\",\"x\":1,\"y\":2,\"z\":4}]}")).get(1).id() == 9, "string ids tolerated");
        check(WorldMerge.PARAM.equals("&merge=1"), "merge parameter");
        System.out.println("World merge checks passed");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
