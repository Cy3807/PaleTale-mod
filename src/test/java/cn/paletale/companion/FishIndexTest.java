package cn.paletale.companion;

import java.nio.charset.StandardCharsets;

public final class FishIndexTest {
    private static byte[] b(String json) { return json.getBytes(StandardCharsets.UTF_8); }
    private static String body(String rows) { return "{\"version\":1,\"fields\":[\"id\",\"code\",\"customId\",\"name\"],\"fish\":[" + rows + "]}"; }
    public static void run() {
        var index = FishIndex.decode(b(body("[2063,\"a-1046\",1046,\"合金机巧鱼\"],[2201,\"event-20261107-01\",null,\"凌霄领主\"],[2150,\"b-033\",3033.0,\"恋心胭脂鱼\"]")));
        check(index.size() == 3, "all rows read");
        check(index.idOfCustom(1046) == 2063 && index.idOfCustom(3033) == 2150, "custom model data maps to the archive id");
        check(index.idOfCustom(999) == null && index.idOfCustom(null) == null, "unknown or missing custom model data is ignored");
        check("凌霄领主".equals(index.name(2201)), "name by id");
        var twins = FishIndex.decode(b(body("[2001,\"a-1\",7,\"甲\"],[2002,\"a-2\",7,\"乙\"],[2003,\"a-3\",8,\"丙\"]")));
        check(twins.idOfCustom(7) == null && twins.idOfCustom(8) == 2003, "a shared model number matches neither fish");
        check(FishIndex.decode(b(body(""))).size() == 0, "an empty table is valid");
        String[] bad = {
            body("[0,\"a-1\",1,\"x\"]"), body("[1900000000,\"a-1\",1,\"x\"]"), body("[2001,\"A-1\",1,\"x\"]"), body("[2001,\"a_1\",1,\"x\"]"),
            body("[2001,\"a-1\",-1,\"x\"]"), body("[2001,\"a-1\",1.5,\"x\"]"), body("[2001,\"a-1\",1,\"\"]"), body("[2001,\"a-1\",1,\"x\"],[2001,\"a-2\",2,\"y\"]"),
            body("[2001,\"a-1\",1]"), "{\"version\":2,\"fields\":[\"id\",\"code\",\"customId\",\"name\"],\"fish\":[]}", "{\"version\":1,\"fields\":[\"id\",\"name\"],\"fish\":[]}", "not json" };
        for (String text : bad) {
            boolean rejected = false;
            try { FishIndex.decode(b(text)); } catch (RuntimeException expected) { rejected = true; }
            check(rejected, "rejected: " + text);
        }
        boolean big = false;
        try { FishIndex.decode(new byte[FishIndex.MAX_BYTES + 1]); } catch (RuntimeException expected) { big = true; }
        check(big, "oversized response rejected");
        FishIndex.use(index); check(FishIndex.current().size() == 3, "index applied");
        FishIndex.resetForTest(); check(FishIndex.current().size() == 0, "reset");
        System.out.println("Fish index checks passed");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
