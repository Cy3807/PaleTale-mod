package cn.paletale.companion;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

/** Immutable sign snapshots only: safe to filter and parse without touching a Minecraft world. */
final class ShopScanData {
    record Sample(ShopApi.Position position, List<String> lines, String chestDir, ShopSigns.Reading previous, long sentAt) {
        Sample { lines = List.copyOf(lines); }
    }
    private ShopScanData() {}
    static double distanceSquared(ShopApi.Position p, double x, double y, double z) {
        double dx=p.x()+.5-x,dy=p.y()+.5-y,dz=p.z()+.5-z;return dx*dx+dy*dy+dz*dz;
    }
    static List<ShopApi.Position> nearby(List<ShopApi.Position> positions,String world,double x,double y,double z) {
        return positions.stream().filter(p->p.world().equals(world)&&distanceSquared(p,x,y,z)<=48*48)
                .distinct().sorted(Comparator.comparingDouble(p->distanceSquared(p,x,y,z))).limit(400).toList();
    }
    static List<ShopSigns.Reading> changed(List<Sample> samples,long now) {
        var result=new ArrayList<ShopSigns.Reading>();
        for(var sample:samples) {
            var p=sample.position();var parsed=ShopSigns.parse(p.world(),p.x(),p.y(),p.z(),sample.lines());
            if(parsed.isEmpty())continue;
            var reading=parsed.get().withChestDir(sample.chestDir());
            if(sample.previous()==null||!sample.previous().sameTrade(reading)||now-sample.sentAt()>300_000)result.add(reading);
            if(result.size()==64)break;
        }
        return List.copyOf(result);
    }
}
