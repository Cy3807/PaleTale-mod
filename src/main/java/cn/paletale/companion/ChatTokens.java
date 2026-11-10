package cn.paletale.companion;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

/** Bounded, Minecraft-independent recognition. No I/O and no retained chat history. */
public final class ChatTokens {
    public static final int MAX_TEXT = 8192, MAX_MATCHES = 128, MAX_NAMES = 30000, MAX_NAME = 100;
    public enum Kind { COORDINATE, LINK, PLAYER, LAND, NATION, TRAFFIC, ITEM }
    public record Target(Kind kind, String name, String key, Integer x, Integer y, Integer z) {}
    public record Match(int start, int end, List<Target> targets) {}
    public record Options(boolean coordinates, boolean places, boolean players, boolean links, boolean items) {
        public boolean accepts(Kind kind) { return switch(kind) {
            case COORDINATE -> coordinates; case LINK -> links; case PLAYER -> players;
            case LAND, NATION, TRAFFIC -> places; case ITEM -> items;
        }; }
    }
    private static final Pattern URL = Pattern.compile("(?i)https?://[^\\s<>\\\"\\u0000-\\u001f]+|(?i)www\\.[^\\s<>\\\"\\u0000-\\u001f]+");
    private static final Pattern COORD = Pattern.compile("(?:(?<=\\p{IsHan})(?=-)|(?<![\\p{L}\\p{N}_.+/-]))-?\\d++(?:(?:[ \\t]++-?\\d++)++|(?:[ \\t]*+/[ \\t]*+-?\\d++)++)(?![\\p{L}\\p{N}_./])");
    private static final class Node { final Map<Character,Node> next = new HashMap<>(); final List<Target> targets = new ArrayList<>(); }
    /** Trie has at most MAX_NAMES * MAX_NAME nodes; longest match wins, at most 8 choices per name. */
    public static final class Index {
        private final Node root = new Node(); private int size,nodes=1;
        public Index(Collection<Target> targets) {this(targets,List.of());}
        public Index(Collection<Target> targets,Collection<String> additionalNations) {this(targets,additionalNations,ChatExclusions.defaults());}
        public Index(Collection<Target> targets,Collection<String> additionalNations,Collection<ChatExclusions.Rule> exclusions) {
            var blocked=ChatExclusions.keys(exclusions);
            var all=new ArrayList<Target>();var nations=new LinkedHashSet<String>();
            for(var target:targets){if(all.size()>=MAX_NAMES)break;if(target==null)continue;all.add(target);if(target.kind()==Kind.LAND&&target.key()!=null){int split=target.key().indexOf('|');if(split>0){String nation=target.key().substring(0,split);if(!nation.isBlank())nations.add(nation);}}}
            for(String nation:additionalNations)if(nation!=null&&!nation.isBlank()&&nation.length()<=MAX_NAME&&nations.size()<3000)nations.add(nation);
            for(String nation:nations){if(all.size()>=MAX_NAMES)break;all.add(new Target(Kind.NATION,nation,nation,null,null,null));}
            for (var target : all) {
                if(target==null||target.kind()==null||target.key()==null||target.key().length()>256)continue;
                String name=target.name();
                if(size>=MAX_NAMES)break;
                if(name==null||name.length()<(target.kind()==Kind.NATION?1:2)||name.length()>MAX_NAME||name.chars().anyMatch(c->Character.isISOControl(c))||ChatExclusions.blocked(blocked,target.kind(),name))continue;
                Node node=root;for(int i=0;i<name.length();i++){char ch=Character.toLowerCase(name.charAt(i));Node next=node.next.get(ch);if(next==null){if(++nodes>150000)throw new IllegalArgumentException("Chat index exceeds node budget");next=new Node();node.next.put(ch,next);}node=next;}
                if(node.targets.size()<8&&!node.targets.contains(target)){node.targets.add(target);size++;}
            }
        }
        public int size(){return size;}
        private Match at(String text,int start,Options options) {
            Node node=root;Match best=null;
            for(int end=start;end<Math.min(text.length(),start+MAX_NAME);end++) {
                node=node.next.get(Character.toLowerCase(text.charAt(end)));if(node==null)break;
                if(node.targets.isEmpty())continue;
                int stop=end+1;var selected=new ArrayList<Target>(2);
                for(var target:node.targets)if(options.accepts(target.kind())&&boundary(text,start,stop,target.kind()))selected.add(target);
                if(!selected.isEmpty())best=new Match(start,stop,List.copyOf(selected));
            }
            return best;
        }
    }
    private static boolean word(char ch){return ch<128&&(Character.isLetterOrDigit(ch)||ch=='_');}
    private static boolean boundary(String text,int from,int to,Kind kind){
        if(kind==Kind.PLAYER)return (from==0||!word(text.charAt(from-1)))&&(to==text.length()||!word(text.charAt(to)));
        if(kind==Kind.NATION&&to-from==1)return (from==0||!Character.isLetterOrDigit(text.charAt(from-1)))&&(to==text.length()||!Character.isLetterOrDigit(text.charAt(to)));
        return !(word(text.charAt(from))&&from>0&&word(text.charAt(from-1)))&&!(word(text.charAt(to-1))&&to<text.length()&&word(text.charAt(to)));
    }
    public static List<Match> parse(String text,Index index,Options options) {
        if(text.isEmpty()||text.length()>MAX_TEXT)return List.of();
        var reserved=new TreeMap<Integer,Match>();
        var links=URL.matcher(text);
        while(links.find()&&reserved.size()<MAX_MATCHES){
            int end=links.end();while(end>links.start()&&"。，；！？、.,;!?）】》".indexOf(text.charAt(end-1))>=0)end--;
            // A trailing bracket closes surrounding prose; balanced parentheses inside paths remain intact.
            int open=0,close=0;for(int i=links.start();i<end;i++){if(text.charAt(i)=='(')open++;else if(text.charAt(i)==')')close++;}
            while(end>links.start()&&text.charAt(end-1)==')'&&close>open){end--;close--;}
            String value=text.substring(links.start(),end);
            String url=value.regionMatches(true,0,"www.",0,4)?"https://"+value:value;
            try{var uri=URI.create(url);if(uri.getHost()!=null&&uri.getUserInfo()==null)reserved.put(links.start(),new Match(links.start(),end,options.links()?List.of(new Target(Kind.LINK,value,url,null,null,null)):List.of()));}catch(IllegalArgumentException ignored){}
        }
        if(options.coordinates()){
            var coords=COORD.matcher(text);
            while(coords.find()&&reserved.size()<MAX_MATCHES){
                if(overlaps(reserved,coords.start(),coords.end()))continue;
                try{String[] numbers=coords.group().strip().split("[ \t]*/[ \t]*|[ \t]+");if(numbers.length!=2&&numbers.length!=3)continue;int x=Integer.parseInt(numbers[0]),second=Integer.parseInt(numbers[1]);Integer y=numbers.length==2?null:second;int z=numbers.length==2?second:Integer.parseInt(numbers[2]);
                    if(Math.abs((long)x)>1000000||Math.abs((long)z)>1000000||y!=null&&Math.abs((long)y)>2048)continue;
                    reserved.put(coords.start(),new Match(coords.start(),coords.end(),List.of(new Target(Kind.COORDINATE,coords.group(),"",x,y,z))));
                }catch(NumberFormatException ignored){}
            }
        }
        var result=new ArrayList<Match>();
        for(int i=0;i<text.length()&&result.size()<MAX_MATCHES;){
            var span=reserved.get(i);if(span!=null){if(!span.targets().isEmpty())result.add(span);i=span.end();continue;}
            Match match=index.at(text,i,options);
            if(match!=null&&!overlaps(reserved,i,match.end())){result.add(match);i=match.end();}else i++;
        }
        return List.copyOf(result);
    }
    private static boolean overlaps(TreeMap<Integer,Match> spans,int start,int end){var before=spans.floorEntry(start);if(before!=null&&before.getValue().end()>start)return true;var after=spans.ceilingEntry(start);return after!=null&&after.getKey()<end;}
}
