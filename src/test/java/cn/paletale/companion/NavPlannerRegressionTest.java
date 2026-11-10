package cn.paletale.companion;

import java.util.List;

/** Route regressions derived from the reported mainland teleport loop and public map coordinates. */
final class NavPlannerRegressionTest {
    private static int checks;
    static void run() {
        var points = new NavPoints(List.of(
                new NavPoints.Hub("baigang", "白港", 6600, 6476, "sea", true, 20, 80000),
                new NavPoints.Hub("wuruiyawangguo", "乌瑞亚鼠鱼港", -7429, 13515, "sea", true, 20, 80000),
                new NavPoints.Hub("hanmuhuiguan", "寒木会馆", 6409, 15400, "sea", true, 20, 80000)), List.of());
        var lands = List.of(
                new NavPlanner.Land("白渚", "", 6776, 6344, List.of("baigang"), new int[]{6615,6476}),
                new NavPlanner.Land("乌瑞亚王国", "", -7568, 13456, List.of("wuruiyawangguo"), new int[]{-7431,13491}));
        int tx=6467,tz=15620;
        var white=NavPlanner.plan(6615,6476,tx,tz,points,lands);
        require(white.next().kind()==NavPlanner.Kind.TRAVEL&&white.next().label().contains("寒木会馆"),"At 白渚's actual spawn, use its port instead of teleporting to 乌瑞亚");
        var uria=NavPlanner.plan(-7431,13491,tx,tz,points,lands);
        require(uria.steps().stream().noneMatch(s->s.kind()==NavPlanner.Kind.HOME),"At 乌瑞亚's spawn, do not teleport back to 白渚");
        var arrived=NavPlanner.plan(6409,15354,tx,tz,points,lands);
        require(arrived.direct()&&arrived.directDistance()>270&&arrived.directDistance()<274,"The last 272 blocks near the destination port are walked, without a new home teleport");
        var city=NavPlanner.planElsewhere(tx,tz,points,lands);
        require(city!=null&&city.next().kind()==NavPlanner.Kind.HOME,"From the city, a known home spawn still provides a valid route into the mainland");
        var first=city.next();
        var landed=NavPlanner.plan(first.x(),first.z(),tx,tz,points,lands);
        require(landed.steps().stream().noneMatch(s->s.kind()==NavPlanner.Kind.HOME)&&landed.seconds()<city.seconds(),"Following the first home teleport advances the route instead of starting a home loop");
        var unknown=new NavPlanner.Land("未知落点", "", tx,tz,List.of("hanmuhuiguan"));
        require(NavPlanner.plan(0,0,tx,tz,points,List.of(unknown)).steps().stream().noneMatch(s->s.kind()==NavPlanner.Kind.HOME),"A port or land centre cannot stand in for an unobserved home spawn");
        require(NavPlanner.planElsewhere(tx,tz,NavPoints.EMPTY,List.of(unknown))==null,"Unknown land spawns cannot invent a route between worlds");
        var rpPoints=new NavPoints(List.of(new NavPoints.Hub("own","自家港口",-500,0,"sea",true,10,80000),new NavPoints.Hub("arrival","目的港",4990,0,"sea",false,10,80000)),List.of(new NavPoints.Warp("别人家",-490,0)));
        var own=List.of(new NavPlanner.Land("我的领地","",-500,0,List.of("own"),new int[]{-500,0}));
        require(NavPlanner.planElsewhere(5000,0,rpPoints,own).next().kind()==NavPlanner.Kind.HOME,"A small RP time advantage cannot displace the player's own home-plus-port entry");
        require(NavPlanner.plan(0,0,5000,0,rpPoints,own).next().kind()==NavPlanner.Kind.HOME,"The mainland also prefers a usable home entry over visiting another player's RP for a small gain");
        var atHome=NavPlanner.plan(-500,0,5000,0,rpPoints,own);
        require(atHome.next().kind()==NavPlanner.Kind.TRAVEL&&atHome.next().command().equals("port travel own arrival"),"After using the preferred home entry, continue from its port instead of switching to RP");
        var nearRP=new NavPoints(rpPoints.hubs(),List.of(new NavPoints.Warp("目的地RP",4960,0)));
        require(NavPlanner.planElsewhere(5000,0,nearRP,own).next().kind()==NavPlanner.Kind.WARP,"An RP near the actual destination can be used directly even when its advantage over home is small");
        require(NavPlanner.plan(0,0,5000,0,nearRP,own).steps().stream().noneMatch(s->s.kind()==NavPlanner.Kind.TRAVEL),"The near-target exception walks directly from RP to the goal instead of borrowing a port");
        require(NavPlanner.planElsewhere(5000,0,rpPoints,List.of()).next().kind()==NavPlanner.Kind.WARP,"Without a usable home entry, RP remains an available cross-world fallback");
        var slowHome=List.of(new NavPlanner.Land("偏远领地","",-20000,0,List.of(),new int[]{-20000,0}));
        require(NavPlanner.planElsewhere(5000,0,rpPoints,slowHome).next().kind()==NavPlanner.Kind.WARP,"RP is used when the regular home route is over a minute slower");
        var currentPort=new NavPoints(List.of(new NavPoints.Hub("current","当前港口",0,0,"sea",true,10,80000),new NavPoints.Hub("arrival","目的港",4990,0,"sea",false,10,80000)),List.of(new NavPoints.Warp("借道RP",0,0)));
        require(NavPlanner.plan(100,0,5000,0,currentPort,List.of()).next().kind()==NavPlanner.Kind.WALK,"Already approaching a usable port does not detour to RP merely to save three seconds");
        var boundaryRP=new NavPoints(List.of(),List.of(new NavPoints.Warp("边界RP",0,0)));
        require(NavPlanner.planElsewhere(256,0,boundaryRP,List.of(new NavPlanner.Land("家","",756,0,List.of(),new int[]{756,0}))).next().kind()==NavPlanner.Kind.WARP,"An RP exactly 256 blocks from the target qualifies for direct arrival");
        require(NavPlanner.planElsewhere(257,0,boundaryRP,List.of(new NavPlanner.Land("家","",757,0,List.of(),new int[]{757,0}))).next().kind()==NavPlanner.Kind.HOME,"An RP beyond 256 blocks must satisfy the fallback savings rule");
        var minuteRP=new NavPoints(List.of(),List.of(new NavPoints.Warp("省一分钟",375,0)));
        require(NavPlanner.plan(0,0,5000,0,minuteRP).next().kind()==NavPlanner.Kind.WARP,"Saving exactly sixty seconds is enough for the fallback");
        require(NavPlanner.plan(0,0,5000,0,new NavPoints(List.of(),List.of(new NavPoints.Warp("不足一分钟",374,0)))).direct(),"A fallback saving less than sixty seconds does not replace the regular route");
        var rpOrder=List.of(new NavPoints.Warp("较慢兜底",400,0),new NavPoints.Warp("较快兜底",500,0));
        require(NavPlanner.plan(0,0,5000,0,new NavPoints(List.of(),rpOrder)).next().label().contains("较快兜底")&&NavPlanner.plan(0,0,5000,0,new NavPoints(List.of(),rpOrder.reversed())).next().label().contains("较快兜底"),"The fallback margin is applied once and never hides the fastest RP due to iteration order");
        require(NavPlanner.plan(4990,0,5000,0,nearRP,own).direct(),"The near-target RP exception cannot replace a shorter final walk");
        var many=new java.util.ArrayList<NavPoints.Hub>();
        for(int i=0;i<8;i++) {
            many.add(new NavPoints.Hub("near"+i,"近处异线",i,0,"east",true,10,100));
            many.add(new NavPoints.Hub("end"+i,"终点异线",20000-i,0,"east",false,10,100));
        }
        many.add(new NavPoints.Hub("from","可用出发港",1000,0,"sea",true,10,80000));
        many.add(new NavPoints.Hub("to","可用到达港",19800,0,"sea",false,10,80000));
        var all=NavPlanner.plan(0,0,20000,0,new NavPoints(List.copyOf(many),List.of()));
        require(!all.direct()&&all.steps().get(1).command().equals("port travel from to"),"Usable departures and arrivals beyond the eight closest points are still considered");
        java.util.Collections.reverse(many);
        var reversed=NavPlanner.plan(0,0,20000,0,new NavPoints(List.copyOf(many),List.of()));
        require(reversed.seconds()==all.seconds()&&reversed.steps().get(1).command().equals(all.steps().get(1).command()),"Map iteration order cannot change which route is fastest");
        var warps=new java.util.ArrayList<NavPoints.Warp>();
        for(int i=0;i<6;i++)warps.add(new NavPoints.Warp("近处RP"+i,18000-i*50,0));
        warps.add(new NavPoints.Warp("有港口的RP",1000,0));
        require(NavPlanner.plan(-30000,0,20000,0,new NavPoints(List.copyOf(many),List.copyOf(warps))).next().label().contains("有港口的RP"),"A farther warp with a better onward route cannot be discarded by a nearest-six cutoff");
        // Following HOME/WARP actions must lower the remaining cost; exercise many positions and land orders.
        var withRP=new NavPoints(points.hubs(),List.of(new NavPoints.Warp("借道RP",6600,6476),new NavPoints.Warp("目标附近RP",6409,15400)));
        for(var layout:List.of(points,withRP))for(int x=-12000;x<=12000;x+=1500)for(int z=0;z<=18000;z+=1500) {
            var route=NavPlanner.plan(x,z,tx,tz,layout,lands);
            var step=route.next();
            if(step.kind()!=NavPlanner.Kind.HOME&&step.kind()!=NavPlanner.Kind.WARP)continue;
            var next=NavPlanner.plan(step.x(),step.z(),tx,tz,layout,lands);
            double entry=step.kind()==NavPlanner.Kind.HOME?NavPlanner.HOME:NavPlanner.WARP;
            require(next.seconds()<route.seconds()-entry+.01&&next.next().kind()!=NavPlanner.Kind.HOME&&next.next().kind()!=NavPlanner.Kind.WARP,"After the chosen home/RP entry the remaining route is cheaper and does not restart another entry");
        }
        System.out.println("PaleTale mainland navigation regression checks passed: "+checks);
    }
    private static void require(boolean ok,String message) { checks++;if(!ok)throw new AssertionError(message); }
}
