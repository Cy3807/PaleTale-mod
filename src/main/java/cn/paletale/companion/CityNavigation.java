package cn.paletale.companion;

import java.util.List;

/** The SIMMC city is worlds:spawn, a different world from the public mainland map (minecraft:overworld). */
public final class CityNavigation {
    public static final String WORLD = "worlds:spawn", MAIN_WORLD = "minecraft:overworld";
    public record Shop(String category, String name, double x, double y, double z) {
        public int blockX() { return (int) Math.floor(x); }
        public int blockY() { return (int) Math.floor(y); }
        public int blockZ() { return (int) Math.floor(z); }
    }
    /** Exact NPC captures replace the older player approach positions. */
    public static final List<Shop> SHOPS = List.of(shopFrom("农作物", "农作物商店"), shopFrom("矿物", "矿物商店"), shopFrom("钓鱼", "鱼类商店"));
    private static Shop shopFrom(String category, String role) {
        var p = CityCatalog.shop(role); return new Shop(category, p.name(), p.x(), p.y(), p.z());
    }
    private CityNavigation() {}
    public static Shop shop(String category) { return shopFrom(category, switch (category) { case "农作物" -> "农作物商店"; case "矿物" -> "矿物商店"; case "钓鱼" -> "鱼类商店"; default -> throw new IllegalArgumentException(category); }); }
    public static boolean sameWorld(String current, String destination) { return current != null && current.equals(destination); }
    /** City-only alternatives: walk, or walk to a captured NPC and choose a real menu destination. */
    public static NavPlanner.Plan plan(String currentWorld, double px, double py, double pz, int tx, Integer ty, int tz) {
        if (!sameWorld(currentWorld, WORLD)) return new NavPlanner.Plan(List.of(new NavPlanner.Step(NavPlanner.Kind.SPAWN, "主城", tx, tz, 0, "spawn")), 0, false, 0, null, null, null, 0);
        return local(px, py, pz, tx, ty, tz);
    }
    private static NavPlanner.Plan local(double px, double py, double pz, int tx, Integer ty, int tz) {
        double gy = ty == null ? py : ty;
        double direct = CityCatalog.distance(px, py, pz, tx + .5, gy, tz + .5);
        var best = new NavPlanner.Plan(List.of(walk("目标", tx, ty, tz, direct)), direct / NavPlanner.WALK, true, direct, null, null, null, 0);
        for (var npc : CityCatalog.shortcuts()) for (var arrival : CityCatalog.destinations()) {
            double before = CityCatalog.distance(px, py, pz, npc.x(), npc.y(), npc.z());
            double after = CityCatalog.distance(arrival.x(), arrival.y(), arrival.z(), tx + .5, gy, tz + .5);
            double seconds = before / NavPlanner.WALK + 6 + after / NavPlanner.WALK;
            // A menu trip must save at least four seconds and 20% over walking; small distance changes cannot flip the route.
            if (seconds + 4 >= direct / NavPlanner.WALK || seconds >= best.seconds() || seconds >= direct / NavPlanner.WALK * .8) continue;
            best = new NavPlanner.Plan(List.of(walk("快捷传送 NPC", npc.blockX(), npc.blockY(), npc.blockZ(), before),
                    new NavPlanner.Step(NavPlanner.Kind.CITY_TRAVEL, arrival.menu(), npc.blockX(), npc.blockZ(), before, null, npc.blockY()),
                    walk("目标", tx, ty, tz, after)), seconds, false, direct, null, null, null, 0);
        }
        return best;
    }
    private static NavPlanner.Step walk(String name, int x, Integer y, int z, double distance) { return new NavPlanner.Step(NavPlanner.Kind.WALK, name, x, z, distance, null, y); }
}
