package cn.paletale.companion;


final class CityNavigationRegressionTest {
    private static int checks;
    static void run() throws Exception {
        require(new NavCoordinates(120,64,-350).equals(NavCoordinates.parse("120 64 -350")), "Manual XYZ coordinates retain axis order");
        require(new NavCoordinates(-120,null,350).equals(NavCoordinates.parse(" -120   +350 ")), "Manual XZ coordinates omit height");
        for(String invalid:java.util.List.of("", "1", "1 2 3 4", "1.5 2", "主城 1 2", "1 / 2", "30000001 2", "1 2048 2", "999999999999 2"))
            require(NavCoordinates.parse(invalid)==null,"Invalid or out-of-world manual coordinates are rejected: "+invalid);
        var crop = CityNavigation.shop("农作物");
        var fish = CityNavigation.shop("钓鱼");
        var ore = CityNavigation.shop("矿物");
        require(crop.x() == 42.151946097197985 && crop.y() == 54 && crop.z() == 9.021311834525957, "Crop now uses the exact captured NPC position");
        require(fish.x() == 56.13090907532497 && fish.y() == 54.0625 && fish.z() == 2.370101288133334 && ore.x() == 28.645573604984737 && ore.y() == 43 && ore.z() == 28.914960669715146, "Fish and mineral approach positions preserve XYZ order and precision");
        var wrong = CityNavigation.plan(CityNavigation.MAIN_WORLD, crop.x(), crop.y(), crop.z(), crop.blockX(), crop.blockY(), crop.blockZ());
        require(!wrong.direct() && wrong.steps().getFirst().kind() == NavPlanner.Kind.SPAWN && wrong.next().command().equals("spawn"), "Same coordinates in the mainland still require /spawn before city navigation");
        var here = CityNavigation.plan(CityNavigation.WORLD, crop.x(), crop.y(), crop.z(), crop.blockX(), crop.blockY(), crop.blockZ());
        require(here.direct() && here.directDistance() < 1 && here.steps().stream().allMatch(s -> s.kind() == NavPlanner.Kind.WALK), "Inside the city, its shop uses a local walking route without mainland ports");
        require(CityNavigation.plan(CityNavigation.WORLD, crop.x(), 90, crop.z(), crop.blockX(), crop.blockY(), crop.blockZ()).directDistance() > 30, "Another floor cannot count as arrival");
        require(!CityNavigation.sameWorld("worlds:spawned", CityNavigation.WORLD) && !CityNavigation.sameWorld("minecraft:the_nether", CityNavigation.WORLD), "World identity is exact, not a coordinate or prefix guess");
        require(CityCatalog.npcs().size() == 14 && CityCatalog.shortcuts().size() == 8 && CityCatalog.destinations().size() == 11, "All captured NPCs, unique shortcut entities and real menu destinations are included");
        require(CityCatalog.spawn().x() == 111.5 && CityCatalog.spawn().y() == 54 && CityCatalog.spawn().z() == -15.5, "Spawn landing comes from its player record");
        var smith = CityCatalog.destinations().stream().filter(p -> p.name().equals("铁匠铺")).findFirst().orElseThrow();
        require(smith.x() == 123.5 && smith.y() == 60 && smith.z() == 153.5, "Blacksmith destination uses player source despite swapped filenames");
        require(CityCatalog.shortcuts().stream().anyMatch(p -> p.x() == 118.54835223496043 && p.y() == 60.5), "Blacksmith NPC uses its entity capture");
        require(CityCatalog.shortcuts().stream().anyMatch(p -> p.areas().contains("主城") && p.areas().contains("飞空艇") && p.areas().contains("活动传送点")), "Three region labels share one physical shortcut NPC");
        var route = CityNavigation.plan(CityNavigation.WORLD, 121.8, 54, -9.7, smith.blockX(), smith.blockY(), smith.blockZ());
        require(!route.direct() && route.next().kind() == NavPlanner.Kind.CITY_TRAVEL && route.next().label().equals("铁匠铺快捷传送") && route.next().command() == null, "Nearby shortcut routes through the actual menu without inventing commands");
        require(route.guide().y() == 55, "Shortcut guidance retains the NPC floor");
        var arrived = CityNavigation.plan(CityNavigation.WORLD, smith.x(), smith.y(), smith.z(), smith.blockX(), smith.blockY(), smith.blockZ());
        require(arrived.direct() && arrived.directDistance() < 1, "After a real teleport the route ends at its recorded arrival");
        var far = CityNavigation.plan(CityNavigation.WORLD, 100, 54, -20, smith.blockX(), smith.blockY(), smith.blockZ());
        require(far.next().kind() == NavPlanner.Kind.WALK && far.next().label().contains("NPC") && far.next().y() != null, "Walk to the departure NPC before choosing the menu");
        var npc = CityCatalog.shop("农作物商店");
        require(CityCatalog.matches(npc, "minecraft:player", npc.x(), npc.y(), npc.z()) && !CityCatalog.matches(npc, "minecraft:vex", npc.x(), npc.y(), npc.z()) && !CityCatalog.matches(npc, "minecraft:player", npc.x(), npc.y() + 5, npc.z()), "Scanning verifies captured type and 3D position");
        require(CityCatalog.matchesName(npc, "德马克(农作物商店)", "") && !CityCatalog.matchesName(npc, "SomePlayer", "SomePlayer"), "Players at NPC coordinates must still match a registered NPC name");
        require(CityCatalog.destinations().stream().noneMatch(p -> p.menu().equals("返回主菜单") || p.name().equals("主城")), "Back and spawn are not fabricated menu destinations");
        String hash = "a".repeat(64);
        String property = java.util.Base64.getEncoder().encodeToString(("{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/"+hash+"\"}}}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        require(hash.equals(NpcProfile.skinHash(property)) && NpcProfile.skinHash("invalid!") == null, "Skin upload extracts only a valid Minecraft texture hash");
        require(hash.equals(NpcProfile.textureHash("http://textures.minecraft.net/texture/"+hash)) && NpcProfile.textureHash("http://localhost/texture/"+hash)==null, "The downloaded Minecraft texture supplies its real URL hash, not its SHA1 cache filename");
        require(NpcProfile.of(npc,hash).valid() && !NpcProfile.of(npc,null).valid(), "A player NPC is not learned with an unready/default skin");
        require(NpcProfile.of(npc,null,true).valid() && NpcProfile.of(npc,hash,true).skinHash()==null, "Personalized NPCs store a marker without uploading the viewer skin hash");
        require(!CityCatalog.accept("{\"world\":\"minecraft:overworld\"}"), "Wrong-world downloaded catalogs are rejected without replacing defaults");
        System.out.println("PaleTale city navigation checks passed: " + checks);
    }
    private static void require(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
