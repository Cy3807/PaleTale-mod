package cn.paletale.companion;
/** Retargetable critical damping: preserves both position and velocity during bursts. */
public final class ChatMotion {
 private double displacement,velocity;private long at;
 public double value(long now){double t=Math.max(0,now-at)/1e9,k=15;return (displacement+(velocity+k*displacement)*t)*Math.exp(-k*t);}
 private double speed(long now){double t=Math.max(0,now-at)/1e9,k=15;return (velocity-k*(velocity+k*displacement)*t)*Math.exp(-k*t);}
 public void shift(double distance,long now){double current=value(now),v=speed(now);displacement=current+distance;velocity=v;at=now;}
 public void reset(){displacement=velocity=0;at=0;}
}
