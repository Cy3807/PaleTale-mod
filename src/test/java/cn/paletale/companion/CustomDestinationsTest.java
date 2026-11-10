package cn.paletale.companion;

import java.util.List;

public final class CustomDestinationsTest {
    public static void run() {
        List<CustomDestinations.Entry> list = List.of();
        list = CustomDestinations.add(list, "  我的家  ", "地下室\n入口", 10, 64, -20, "minecraft:overworld", "", "白港", 1L);
        check(list != null && list.get(0).name().equals("我的家") && !list.get(0).note().contains("\n"), "text trimmed and cleaned");
        check(list.get(0).land().equals("白港") && CustomDestinations.decode("{\"items\":[{\"name\":\"a\",\"x\":1,\"y\":2,\"z\":3}]}").get(0).land().isEmpty(), "land kept, old files read without it");
        check(CustomDestinations.add(list, "   ", "x", 0, 0, 0, "", "", "", 2L) == null, "blank name refused");
        list = CustomDestinations.add(list, "x".repeat(50), "y".repeat(90), 1, 2, 3, "minecraft:the_nether", "srv", "", 3L);
        check(list.size() == 2 && list.get(0).name().length() == 24 && list.get(0).note().length() == 60 && list.get(0).server().equals("srv"), "limits and newest first");
        var back = CustomDestinations.decode(CustomDestinations.encode(list));
        check(back.equals(list), "json round trip");
        check(CustomDestinations.decode("not json").isEmpty() && CustomDestinations.decode("{\"items\":[{\"name\":\"a\"},{\"name\":\"b\",\"x\":1,\"y\":2,\"z\":3}]}").size() == 1, "broken file or rows skipped");
        check(CustomDestinations.remove(list, list.get(0)).size() == 1, "remove");
        var full = new java.util.ArrayList<CustomDestinations.Entry>();
        for (int i = 0; i < CustomDestinations.MAX; i++) full.add(new CustomDestinations.Entry("n" + i, "", i, 0, 0, "", "", "", 0));
        check(CustomDestinations.add(full, "more", "", 0, 0, 0, "", "", "", 0) == null, "full list refused");
        System.out.println("PaleTale custom destination checks passed");
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
