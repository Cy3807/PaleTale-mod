package cn.paletale.companion;

import java.util.Optional;
import java.util.regex.Pattern;
/** Explicit primary-server labels only. A proxy total or tab-list size is never a primary population. */
public final class PrimaryPopulation {
    public record Sample(int count,String evidence) {}
    private static final Pattern LABEL=Pattern.compile("(?:主服(?:务器)?[ \\t]*(?:在线(?:人数|玩家)?|玩家(?:人数)?)[ \\t]*[:：]?[ \\t]*([0-9]{1,5})(?![0-9,.])|主服[ \\t]*[:：][ \\t]*([0-9]{1,5})(?![0-9,.]))");
    private PrimaryPopulation() {}
    public static Optional<Sample> parse(String raw) {
        if(raw==null||raw.length()>8192)return Optional.empty();
        String text=raw.replaceAll("§[0-9a-fk-orxA-FK-ORX]","");
        var matcher=LABEL.matcher(text); Sample found=null;
        while(matcher.find()) {
            int count=Integer.parseInt(matcher.group(1)!=null?matcher.group(1):matcher.group(2));
            if(count>10000||found!=null&&found.count()!=count)return Optional.empty();
            found=new Sample(count,matcher.group());
        }
        return Optional.ofNullable(found);
    }
}
