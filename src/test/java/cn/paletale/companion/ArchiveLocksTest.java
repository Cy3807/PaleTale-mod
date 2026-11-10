package cn.paletale.companion;

public final class ArchiveLocksTest {
    public static void main(String[] args) {
        var wine = new ArchiveData.Entry("item", 1, "酒", "", "", "wine", "", false, "", null, null, -1, -1, "", "", "", "");
        var dish = new ArchiveData.Entry("item", 2, "菜", "", "", "dish", "", false, "", null, null, -1, -1, "", "", "", "");
        var map = new ArchiveData.Entry("item", 3, "地图画", "", "", "map_art", "", false, "", null, null, -1, -1, "", "", "", "");
        var fish = new ArchiveData.Entry("item", 1_900_001_046, "合金机巧鱼", "", "", "fish", "鱼类", false, "", null, null, -1, -1, "", "", "", "");
        check(!ArchiveLocks.locked(fish) && !ArchiveLocks.locked("fish") && ArchiveLocks.OPEN_TYPES.equals("map_art,fish"), "fish are open in the mod");
        check(ArchiveLocks.locked(wine) && ArchiveLocks.locked(dish) && !ArchiveLocks.locked(map), "only wine and dish locked");
        check(ArchiveLocks.visible(ArchiveData.Kind.ITEMS, java.util.List.of(wine, dish, map, fish)).size() == 2, "locked entries filtered");
        check(ArchiveLocks.visible(ArchiveData.Kind.PLAYERS, java.util.List.of(wine)).size() == 1, "player pages unaffected");
        for (String ok : new String[] { "/pt nav world minecraft:overworld 1075 63 12955", "/pt nav world minecraft:overworld -6896 ~ -2586", "/pt nav expedition nether 1 2 3" })
            check(ArchiveApi.FISH_COMMAND.matcher(ok).matches(), ok);
        for (String bad : new String[] { "/pt nav world minecraft:overworld 1 2 3; /op x", "/op me", "/pt nav off", "/pt nav world minecraft:the_end 1 2 3", "/pt nav expedition x 1 2 3" })
            check(!ArchiveApi.FISH_COMMAND.matcher(bad).matches(), bad);
        for (String code : new String[] { "a-1046", "b-033", "event-20261107-03", "survey-20261107-4" }) {
            var root = com.google.gson.JsonParser.parseString("{\"fishing\":{\"code\":\"" + code + "\",\"lines\":[]}}").getAsJsonObject();
            check(ArchiveApi.fishCode(root).equals(code), "fish code accepted: " + code);
        }
        check(ArchiveApi.fishCode(com.google.gson.JsonParser.parseString("{\"fishing\":{\"code\":\"../x\"}}").getAsJsonObject()).isEmpty(), "bad fish code dropped");
        System.out.println("Archive content lock checks passed");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
