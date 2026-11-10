package cn.paletale.companion;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static cn.paletale.companion.ChatTokens.*;
/** Deterministic, independent 32-way partitions. Changed partitions replace their old contents, including deletions. */
public final class ChatSync {
    public static final int SHARDS=32;
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    public static String sha(byte[] bytes){return HexFormat.of().formatHex(digest().digest(bytes));}
    private static String type(Target t){return t.kind().name().toLowerCase(Locale.ROOT);}
    public static int shard(Target t){byte[] key=(type(t)+"\0"+t.key()).getBytes(StandardCharsets.UTF_8);return (digest().digest(key)[0]&255)%SHARDS;}
    public static String[] hashes(Collection<Target> input){
        var rows=new ArrayList<>(input);rows.sort(Comparator.comparing(ChatSync::type).thenComparing(Target::key));
        var hashes=new MessageDigest[SHARDS];for(int i=0;i<SHARDS;i++)hashes[i]=digest();
        for(var row:rows){var d=hashes[shard(row)];d.update((type(row)+"\0"+row.name()+"\0"+row.key()+"\n").getBytes(StandardCharsets.UTF_8));}
        String[] result=new String[SHARDS];for(int i=0;i<SHARDS;i++)result[i]=HexFormat.of().formatHex(hashes[i].digest());return result;
    }
    public static List<Target> merge(Collection<Target> old,Collection<Target> incoming,Set<Integer> changed,String[] expected){
        if(expected.length!=SHARDS||changed.isEmpty())throw new IllegalArgumentException("Invalid manifest");
        if(changed.stream().anyMatch(i->i<0||i>=SHARDS))throw new IllegalArgumentException("Invalid shard");
        var next=new LinkedHashSet<Target>();for(var row:old)if(!changed.contains(shard(row)))next.add(row);
        for(var row:incoming){if(!changed.contains(shard(row)))throw new IllegalArgumentException("Unexpected shard");next.add(row);}
        if(next.size()>ChatTokens.MAX_NAMES||!Arrays.equals(hashes(next),expected))throw new IllegalArgumentException("Names changed during refresh");
        return List.copyOf(next);
    }
}
