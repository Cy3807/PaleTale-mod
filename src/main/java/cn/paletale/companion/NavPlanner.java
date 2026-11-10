package cn.paletale.companion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Plans how to get to a coordinate: walk, or walk to a hub you can leave from, travel to a hub near the target (same line group, within the departure hub's reach) and walk on.
 * Two kinds of teleport come before all that: going home to one of the player's territories (20 s) and a warp to an RP point (15 s); from where they put you the plan continues
 * the same way. It is only a suggestion (the game decides what really works) and it is recomputed from wherever the player stands, so after a teleport the plan simply goes on from there.
 */
public final class NavPlanner {
    public static final double WALK = 5.0;        // blocks per second on foot
    public static final double TRAVEL = 20.0;     // seconds a hub trip costs (waiting included)
    public static final double HOME = 20.0;       // seconds the teleport home to a territory takes
    public static final double WARP = 15.0;       // seconds a warp to an RP point takes
    static final double RP_FALLBACK_GAIN = 60.0;  // borrowing an RP as an intermediate stop must save a full minute
    static final double RP_NEAR_TARGET = 256.0;  // an RP within this walking distance is a direct destination entry
    /** How close to a hub you count as there: the map says up to 20 blocks, 15 leaves room so the command still works. */
    static final double HUB_REACH = 15.0;
    /** Standing at a hub you can leave from, going home must beat leaving from right here by this many seconds (a teleport home is a detour from where the player is, not a free reset). */
    static final double HOME_DETOUR = 40.0;
    private NavPlanner() {}

    public enum Kind { WALK, TRAVEL, HOME, WARP, SPAWN, CITY_TRAVEL }
    /** WALK: go to ({@code x},{@code z}). TRAVEL: at the hub ({@code x},{@code z}) take the trip to {@code label}. HOME / WARP: teleport to the territory / RP point {@code label} at ({@code x},{@code z}). */
    public record Step(Kind kind, String label, int x, int z, double distance, String command, Integer y) {
        public Step(Kind kind, String label, int x, int z, double distance, String command) { this(kind, label, x, z, distance, command, null); }
        public Step(Kind kind, String label, int x, int z, double distance) { this(kind, label, x, z, distance, null); }
        /** A step you do by using something (a trip, going home, a warp) rather than by walking. */
        public boolean action() { return kind != Kind.WALK; }
    }
    public record Near(NavPoints.Hub hub, double distance) {}
    /**
     * A territory of the player with the ids of the hubs inside it. {@code spawn}: where /l spawn put players ({x, z}) when that is known, else null. {@code bounds}: the box around
     * its outline {minX, minZ, maxX, maxZ}, or null when unknown (used to check that a remembered spawn point still lies in it).
     */
    public record Land(String name, String nation, int x, int z, List<String> hubIds, int[] spawn, int[] bounds) {
        public Land(String name, String nation, int x, int z, List<String> hubIds) { this(name, nation, x, z, hubIds, null, null); }
        public Land(String name, String nation, int x, int z, List<String> hubIds, int[] spawn) { this(name, nation, x, z, hubIds, spawn, null); }
        public Land withSpawn(int[] known) { return new Land(name, nation, x, z, hubIds, known, bounds); }
        /** Whether a point lies in the box around the land, with a margin. */
        public boolean near(int px, int pz, int margin) { return bounds == null || px >= bounds[0] - margin && px <= bounds[2] + margin && pz >= bounds[1] - margin && pz <= bounds[3] + margin; }
    }
    public record Plan(List<Step> steps, double seconds, boolean direct, double directDistance, Near nearestPort, Near nearestStation, NavPoints.Warp nearestWarp, double warpDistance) {
        /** What to do right now: the first step that is not just a few blocks away. */
        public Step next() {
            for (int i = 0; i < steps.size(); i++) {
                Step step = steps.get(i);
                boolean beforeTrip = i + 1 < steps.size() && steps.get(i + 1).kind() == Kind.TRAVEL;
                boolean beforeCity = i + 1 < steps.size() && steps.get(i + 1).kind() == Kind.CITY_TRAVEL;
                if (step.action() || step.distance() > (beforeCity ? 3 : beforeTrip ? HUB_REACH : 6)) return step;
            }
            return steps.get(steps.size() - 1);
        }
        /** Where to head for on foot right now: the next step's place, or for a teleport step the walking that follows it (the last step as a fallback). */
        public Step guide() {
            var step = next();
            if (step.kind() != Kind.HOME && step.kind() != Kind.WARP && step.kind() != Kind.SPAWN) return step;
            for (Step later : steps.subList(steps.indexOf(step) + 1, steps.size())) if (later.kind() == Kind.WALK) return later;
            return steps.get(steps.size() - 1);
        }
    }
    private record Core(List<Step> steps, double seconds) {}

    static double dist(double ax, double az, double bx, double bz) { return Math.hypot(ax - bx, az - bz); }
    /** Small, medium or large: the three sizes the map gives (use distance 10 / 15 / 20). */
    public static String scale(NavPoints.Hub hub) { return hub.use() >= 20 ? "大型" : hub.use() >= 15 ? "中型" : "小型"; }

    public static Plan plan(double px, double pz, int tx, int tz, NavPoints points) { return plan(px, pz, tx, tz, points, List.of()); }

    /** Only a reported/observed spawn is a teleport endpoint. A land centre or its port is not a spawn. */
    static int[] homePoint(Land land) {
        return land.spawn();
    }

    public static Plan plan(double px, double pz, int tx, int tz, NavPoints points, List<Land> lands) {
        double direct = dist(px, pz, tx, tz);
        Core best = core(px, pz, tx, tz, points);
        boolean atHub = points.hubs().stream().anyMatch(h -> h.maintained() && dist(px, pz, h.x(), h.z()) <= HUB_REACH + 5);
        double detour = atHub && best.steps().stream().anyMatch(st -> st.kind() == Kind.TRAVEL) ? HOME_DETOUR : 0;
        // Going home to one of the player's territories, then on from there: the quickest of them.
        for (var land : lands) {
            int[] at = homePoint(land);
            if (at == null) continue;
            if (dist(px, pz, at[0], at[1]) < 60) continue;
            Core from = core(at[0], at[1], tx, tz, points);
            double seconds = HOME + from.seconds();
            if (seconds + detour + 0.001 < best.seconds()) best = prefixed(new Step(Kind.HOME, "领地 " + land.name(), at[0], at[1], dist(px, pz, at[0], at[1]), "l spawn " + land.name()), from, seconds);
        }
        best=withWarps(best,warpOptions(px,pz,tx,tz,points,false));
        Near port = nearest(points, tx, tz, true), station = nearest(points, tx, tz, false);
        NavPoints.Warp warp = points.warps().stream().min(Comparator.comparingDouble(w -> dist(tx, tz, w.x(), w.z()))).orElse(null);
        boolean isDirect = best.steps().size() == 1 && best.steps().get(0).kind() == Kind.WALK;
        return new Plan(best.steps(), best.seconds(), isDirect, direct, port, station, warp, warp == null ? 0 : dist(tx, tz, warp.x(), warp.z()));
    }
    /**
     * The plan from another world (the player's position there means nothing on the main map): only teleports can help, so a trip home or a warp comes first, then the way on from
     * where it lands. Null when there is neither a territory nor a warp to use.
     */
    public static Plan planElsewhere(int tx, int tz, NavPoints points, List<Land> lands) {
        Core best = null;
        for (var land : lands) {
            int[] at = homePoint(land);
            if (at == null) continue;
            Core from = core(at[0], at[1], tx, tz, points);
            double seconds = HOME + from.seconds();
            if (best == null || seconds + 0.001 < best.seconds()) best = prefixed(new Step(Kind.HOME, "领地 " + land.name(), at[0], at[1], 0, "l spawn " + land.name()), from, seconds);
        }
        best=withWarps(best,warpOptions(0,0,tx,tz,points,true));
        return best == null ? null : new Plan(best.steps(), best.seconds(), false, 0, null, null, null, 0);
    }
    private record WarpOptions(Core direct,Core fallback) {}
    private static WarpOptions warpOptions(double px,double pz,int tx,int tz,NavPoints points,boolean elsewhere) {
        Core direct=null,fallback=null;
        for(var warp:points.warps()) {
            double away=elsewhere?0:dist(px,pz,warp.x(),warp.z());
            if(!elsewhere&&away<60)continue;
            var step=new Step(Kind.WARP,"RP 传送点「"+warp.name()+"」",warp.x(),warp.z(),away,"warp "+warp.name());
            double walk=dist(warp.x(),warp.z(),tx,tz);
            if(walk<=RP_NEAR_TARGET) {
                double seconds=WARP+walk/WALK;
                if(direct==null||seconds<direct.seconds())direct=prefixed(step,new Core(List.of(new Step(Kind.WALK,"目标",tx,tz,walk)),walk/WALK),seconds);
            }
            var onward=core(warp.x(),warp.z(),tx,tz,points);
            double seconds=WARP+onward.seconds();
            if(fallback==null||seconds<fallback.seconds())fallback=prefixed(step,onward,seconds);
        }
        return new WarpOptions(direct,fallback);
    }
    /** Direct RP arrivals near the goal compete normally; borrowing an RP to transfer is a last resort. */
    private static Core withWarps(Core regular,WarpOptions warps) {
        if(warps.direct()!=null&&(regular==null||warps.direct().seconds()+.001<regular.seconds()))regular=warps.direct();
        var fallback=warps.fallback();
        // Apply the fallback margin once against the best regular route, never repeatedly against each RP candidate.
        return fallback!=null&&(regular==null||fallback.seconds()+RP_FALLBACK_GAIN<=regular.seconds()+.001)?fallback:regular;
    }
    private static Core prefixed(Step first, Core rest, double seconds) {
        List<Step> steps = new ArrayList<>();
        steps.add(first); steps.addAll(rest.steps());
        return new Core(steps, seconds);
    }
    /** Walking, or walking to a hub you can leave from, a trip, and walking on: whichever is quickest from this place. */
    private static Core core(double px, double pz, int tx, int tz, NavPoints points) {
        double direct = dist(px, pz, tx, tz);
        double bestSeconds=direct/WALK;
        Trip chosen=null;
        for(var trip:trips(points,tx,tz)) {
            var from=trip.from();
            double walkTo=Math.max(0,dist(px,pz,from.x(),from.z())-Math.min(from.use(),HUB_REACH));
            double seconds=walkTo/WALK+trip.onward();
            // Every option uses the same total-time metric; preference multipliers break progress after teleporting.
            if(seconds+0.001<bestSeconds){bestSeconds=seconds;chosen=trip;}
        }
        if(chosen==null)return new Core(List.of(new Step(Kind.WALK,"目标",tx,tz,direct)),bestSeconds);
        var from=chosen.from();var to=chosen.to();
        return new Core(List.of(new Step(Kind.WALK,from.kind()+" "+from.name(),from.x(),from.z(),dist(px,pz,from.x(),from.z())),
                new Step(Kind.TRAVEL,to.kind()+" "+to.name(),from.x(),from.z(),dist(from.x(),from.z(),to.x(),to.z()),from.sea()?"port travel "+from.id()+" "+to.id():null),
                new Step(Kind.WALK,"目标",tx,tz,dist(to.x(),to.z(),tx,tz))),bestSeconds);
    }
    private record Trip(NavPoints.Hub from,NavPoints.Hub to,double onward) {}
    private record TripCache(NavPoints points,int x,int z,List<Trip> trips) {}
    private static volatile TripCache tripCache;
    /** Evaluate every reachable arrival, once per map/target. Position-only replanning then examines one option per departure. */
    private static List<Trip> trips(NavPoints points,int tx,int tz) {
        var cached=tripCache;if(cached!=null&&cached.points()==points&&cached.x()==tx&&cached.z()==tz)return cached.trips();
        var trips=new ArrayList<Trip>();
        for(var from:points.hubs()) {
            if(!from.maintained())continue;
            NavPoints.Hub arrival=null;double best=Double.POSITIVE_INFINITY;
            for(var to:points.hubs()) {
                if(from.id().equals(to.id())||!from.group().equals(to.group())||dist(from.x(),from.z(),to.x(),to.z())>from.reach())continue;
                double onward=TRAVEL+dist(to.x(),to.z(),tx,tz)/WALK;
                if(onward<best){best=onward;arrival=to;}
            }
            if(arrival!=null)trips.add(new Trip(from,arrival,best));
        }
        var result=List.copyOf(trips);tripCache=new TripCache(points,tx,tz,result);return result;
    }
    /** Plain lines on how the plan was chosen, for the /pt nav why command. */
    public static List<String> explain(double px, double pz, int tx, int tz, NavPoints points, List<Land> lands) {
        List<String> lines = new ArrayList<>();
        double direct = dist(px, pz, tx, tz);
        Core base = core(px, pz, tx, tz, points);
        lines.add("从你这里：直接走 " + Math.round(direct / WALK) + " 秒；最快的港口驿站方案 " + Math.round(base.seconds()) + " 秒（" + base.steps().size() + " 步）。");
        lines.add("你的领地 " + lands.size() + " 块（回领地 " + (int) HOME + " 秒，只使用已记录落点；只有总耗时更短才推荐）：");
        lines.add("优先当前步行／港口与自己的领地；RP中转仅在无可用方案或至少省一分钟时兜底。目标距RP不超过256格时，可直接传送后步行。");
        for (var land : lands.stream().limit(10).toList()) {
            int[] at = homePoint(land);
            if(at==null){lines.add("· "+land.name()+"：尚无已记录传送落点，不代用领地中心或港口。");continue;}
            double away = dist(px, pz, at[0], at[1]);
            Core from = core(at[0], at[1], tx, tz, points);
            String hubs = land.hubIds().isEmpty() ? "领地内没有港口驿站" : "领地内：" + land.hubIds().stream().map(id -> points.hubs().stream().filter(h -> h.id().equals(id)).findFirst().map(h -> h.name() + (h.maintained() ? "（可出发）" : "（欠费，仅可到达）")).orElse(id)).reduce((a, b) -> a + "、" + b).orElse("");
            lines.add("· " + land.name() + "：距你 " + Math.round(away) + " 格；回去 " + (int) HOME + " 秒 + 之后 " + Math.round(from.seconds()) + " 秒 = " + Math.round(HOME + from.seconds()) + " 秒；" + hubs + (away < 60 ? "；你就在落点附近，不算回去" : ""));
        }
        Plan chosen = plan(px, pz, tx, tz, points, lands);
        lines.add("选中：" + chosen.steps().stream().map(st -> st.kind() + " " + st.label()).reduce((a, b) -> a + " → " + b).orElse("") + "，约 " + Math.round(chosen.seconds()) + " 秒。");
        return lines;
    }
    private static Near nearest(NavPoints points, int tx, int tz, boolean sea) {
        return points.hubs().stream().filter(h -> h.sea() == sea).map(h -> new Near(h, dist(tx, tz, h.x(), h.z()))).min(Comparator.comparingDouble(Near::distance)).orElse(null);
    }
}
