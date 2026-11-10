package cn.paletale.companion;
import java.util.*;
import static cn.paletale.companion.ChatTokens.*;
public final class ChatAssistRegressionTest {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void run()throws Exception{
        var publicHit=ChatMentions.find("[G] Someone : hi Tester 在吗","Tester").orElseThrow();check(publicHit.line().sender().equals("Someone")&&publicHit.spans().size()==1&&"[G] Someone : hi Tester 在吗".substring(publicHit.spans().getFirst().start(),publicHit.spans().getFirst().end()).equals("Tester"),"public mention span");
        String at="[地狱远征] [G] [红] L_longl: @tester 来";var atHit=ChatMentions.find(at,"Tester").orElseThrow();check(at.substring(atHit.spans().getFirst().start(),atHit.spans().getFirst().end()).equals("@tester"),"@ mention includes the at sign, any case");
        check(ChatMentions.find("高塔 | 牧羊人 \uFFFCCo1dMoon » Tester 过来","Tester").orElseThrow().line().scope().equals("高塔")&&ChatMentions.find("国家 斯芙尔 | \uFFFCCo1dMoon » Tester","Tester").orElseThrow().line().channel().equals("nation"),"land and nation mentions");
        for(String[] example : new String[][] {{"[太空] [G]  [✡奥术大师]  A___Cat : @Tester", "A___Cat"}, {"[G]  ￼举  li_xia_jun : Tester", "li_xia_jun"}, {"[G]  ￼[∰睡梦眷属〄]  bailingjiu_san ☽: Tester", "bailingjiu_san"}, {" [G]  ￼襅  baiciwei : Tester", "baiciwei"}}) check(ChatMentions.find(example[0],"Tester").orElseThrow().line().sender().equals(example[1]), "real server decorations: "+example[0]);
        check(ChatMentions.find("[G] Tester : Tester","Tester").isPresent(),"own message can mention self");check(ChatMentions.find("Cy3807 悄悄的对 我 说: Tester","Tester").isEmpty()&&ChatMentions.find("Tester 加入了游戏","Tester").isEmpty(),"whispers and system lines ignored");
        check(ChatMentions.find("[G] A : Tester2 hi","Tester").isEmpty()&&ChatMentions.find("[G] A : xTester","Tester").isEmpty()&&ChatMentions.find("[Tester] [G] A : hi","Tester").isEmpty(),"whole-word body mentions only");
        var rp = ChatMentions.findPlayers("[RP]  [专心经营自己的糖氏生活ঌ]  陈留香 说 ￼Cy3807 和 @Other", "Cy3807", Set.of("other"));
        check(rp.size()==2 && rp.get(0).self() && !rp.get(1).self() && rp.get(1).name().equals("Other"), "RP body recognises self and online players");
        check(ChatMentions.findPlayers("[G] Person : Tester2 @Other OtherX", "Tester", Set.of("other")).size()==1,"online mention matches complete ID only");
        check(ChatMentions.findPlayers("[G] Person : @Offline", "Tester", Set.of("other")).isEmpty(),"offline names not highlighted");
        check(ChatMentions.findPlayers("[G] Tester : Tester Other", "Tester", Set.of("other")).stream().anyMatch(ChatMentions.NamedSpan::self),"own chat can alert self");
        check(ChatMentions.findPlayers("国家 怀特墨斯自由联邦-苍白王国 | ￼Beibei10 » @Cy3807 叫爸爸", "Cy3807", Set.of()).stream().anyMatch(ChatMentions.NamedSpan::self), "actual nation chat mention");
        var customLand=PrivateChat.parse("高塔 | 牧羊人 \uFFFCCo1dMoon » 高塔","Tester",1000,"a").orElseThrow();check(customLand.peer().equals("领地:高塔")&&customLand.senderId().equals("Co1dMoon")&&customLand.text().equals("高塔"),"custom land role title parsed");check(PrivateChat.parse("ch1 | 领主 \uFFFCExample » x","Tester",1000,"a").orElseThrow().scope().equals("ch1"),"built-in land role parsed");check(PrivateChat.parse("高塔 | Co1dMoon » 1","Tester",1000,"a").orElseThrow().channelKind().equals("land"),"land chat without role parsed");check(PrivateChat.parse("[地狱远征] [G] [红] L_longl: 1","Tester",1000,"a").isEmpty()&&PrivateChat.parse("<Someone> 高塔 | 牧羊人 Co1dMoon » x","Tester",1000,"a").isEmpty(),"public chat cannot become land chat");
        String live=System.getenv("PALETALE_CHAT_NAMES_LIVE");if(live!=null){var response=HttpBodies.CLIENT.send(HttpBodies.request(java.net.URI.create(live)).timeout(java.time.Duration.ofSeconds(20)).GET().build(),HttpBodies.limited(3_000_000));check(response.statusCode()==200,"live names HTTP 200");var names=ChatCatalog.names(new java.io.StringReader(new String(response.body(),java.nio.charset.StandardCharsets.UTF_8)));var liveIndex=new Index(names);check(names.stream().anyMatch(t->t.kind()==Kind.ITEM),"live items present");check(liveIndex.size()>1000,"live directory index");System.out.println("LIVE NAMES: "+names.size()+" records / "+response.body().length+" bytes / no coordinates");}

        var gold=java.util.List.of(new Target(Kind.PLAYER,"Cy3807","Cy3807",null,null,null),new Target(Kind.LAND,"测试领地","国家|测试领地",null,null,null),new Target(Kind.ITEM,"测试 & 酒","3",null,null,null));var goldHashes=ChatSync.hashes(gold);
        check(ChatSync.shard(gold.get(0))==4&&goldHashes[4].equals("ca309762d3fed309fa5e74007bae7da80167a26051b4b61666a4ac115e60484f"),"shared UTF-8 player shard fixture");
        check(ChatSync.shard(gold.get(1))==12&&goldHashes[12].equals("9753e33cca54b71cffae5dc7e83f284fcca9e55233e2c03917e16d380720e357"),"shared UTF-8 land shard fixture");
        check(ChatSync.shard(gold.get(2))==25&&goldHashes[25].equals("ed452a1b41f782b86f5a6c8f11481524c0d8b2b01e371ee20fd96d65860f8b18"),"shared UTF-8 item shard fixture");
        var countryIndex=new Index(List.of(new Target(Kind.LAND,"测试领地","测试国家|测试领地",null,null,null)),List.of("独立国家","国"));
        var countryOptions=new Options(false,true,false,false,false);
        check(ChatTokens.parse("去测试国家",countryIndex,countryOptions).stream().anyMatch(m->m.targets().stream().anyMatch(t->t.kind()==Kind.NATION&&t.key().equals("测试国家"))),"nation inferred from land key");
        check(ChatTokens.parse("独立国家",countryIndex,countryOptions).stream().anyMatch(m->m.targets().stream().anyMatch(t->t.kind()==Kind.NATION)),"country metadata included");
        check(ChatTokens.parse("治疗炎症",new Index(List.of(),List.of("炎")),countryOptions).isEmpty(),"single-character nation does not match inside a word");
        check(ChatTokens.parse("国家：炎",new Index(List.of(),List.of("炎")),countryOptions).size()==1,"single-character nation still matches when isolated");
        check(ChatTokens.parse("国",countryIndex,countryOptions).stream().anyMatch(m->m.targets().stream().anyMatch(t->t.kind()==Kind.NATION)),"single-character nation supported");
        check(ChatTokens.parse("测试国家",countryIndex,new Options(false,false,false,false,false)).isEmpty(),"place switch disables country recognition");
        var capital=com.google.gson.JsonParser.parseString("{\"version\":1,\"nation\":\"测试国家\",\"capital\":\"测试首都\",\"x\":4,\"z\":2}").getAsJsonObject();
        check(CapitalDestination.parse(capital,"测试国家")!=null,"valid exact capital");check(CapitalDestination.parse(capital,"其他国家")==null,"wrong nation rejected");capital.addProperty("x",4.2);check(CapitalDestination.parse(capital,"测试国家")==null,"fractional capital rejected");capital.addProperty("x",1000001);check(CapitalDestination.parse(capital,"测试国家")==null,"out-of-bounds capital rejected");
        var blockedIndex=new Index(List.of(new Target(Kind.LAND,"Esc","国家|Esc",null,null,null),new Target(Kind.LAND,"故事","国家|故事",null,null,null),new Target(Kind.ITEM,"Ｅｓｃ","7",null,null,null)));
        check(ChatTokens.parse("按 ESC 或读故事",blockedIndex,new Options(false,true,false,false,true)).isEmpty(),"default exclusions block common false positives with case folding");
        var allowedIndex=new Index(List.of(new Target(Kind.LAND,"Esc","国家|Esc",null,null,null)),List.of(),List.of());check(!ChatTokens.parse("Esc",allowedIndex,new Options(false,true,false,false,false)).isEmpty(),"explicit empty admin list can restore a default name");
        var selective=new Index(List.of(new Target(Kind.LAND,"故事","国家|故事",null,null,null),new Target(Kind.PLAYER,"故事","Story",null,null,null)),List.of(),List.of(new ChatExclusions.Rule("land","故事")));var match=ChatTokens.parse("故事",selective,new Options(false,true,true,false,false));check(match.size()==1&&match.getFirst().targets().getFirst().kind()==Kind.PLAYER,"type-specific exclusion does not remove other types");
        var absent=new com.google.gson.JsonObject();check(ChatCatalog.exclusions(absent).size()==2,"old servers use safe defaults");absent.add("exclusions",new com.google.gson.JsonArray());check(ChatCatalog.exclusions(absent).isEmpty(),"server empty list is authoritative");
        var all=new Options(true,true,true,true,true);var empty=new Index(List.of());
        for(String line:List.of("12 -34","(12 64 -34)","目的地：-12\t-34","12 64 -34(xyz)","12/-34","12/64/-34","12 / 64 / -34","-12 / -34"))check(ChatTokens.parse(line,empty,all).size()==1,"coordinates: "+line);
        var slash=ChatTokens.parse("12/64/-34",empty,all).getFirst().targets().getFirst();check(slash.x()==12&&slash.y()==64&&slash.z()==-34,"slash XYZ values");var slashPair=ChatTokens.parse("12/-34",empty,all).getFirst().targets().getFirst();check(slashPair.x()==12&&slashPair.y()==null&&slashPair.z()==-34,"slash XZ values");check(ChatTokens.parse("https://example.org/12/64/34",empty,all).size()==1,"numeric URL remains one link");
        var attached=ChatTokens.parse("欢迎抢购 购买地址 自由市场南卫-2717 6118 四号兑换机",empty,all);check(attached.size()==1&&attached.getFirst().targets().getFirst().x()==-2717&&attached.getFirst().targets().getFirst().z()==6118,"negative coordinate directly follows Chinese text");
        check(ChatTokens.parse("player-2717 6118",empty,all).isEmpty(),"negative coordinate is not embedded in an ASCII identifier");
        var xyz=ChatTokens.parse("12 64 -34",empty,all).getFirst().targets().getFirst();check(xyz.x()==12&&xyz.y()==64&&xyz.z()==-34,"XYZ order");
        for(String bad:List.of("1 2 3 4","1000001 64 2","12 2049 34","1234567890123456 2","1.5 3","v1 2","abc12 34","12 34px","1/2/3/4","abc12/34","12/34px"))check(ChatTokens.parse(bad,empty,all).isEmpty(),"not coordinates: "+bad);
        String link="https://docs.qq.com/aio/02Xd2eVDY0lzUUJ0VS";var linked=ChatTokens.parse("看这里 "+link+"。",empty,all);check(linked.size()==1&&linked.getFirst().targets().getFirst().key().equals(link),"full URL without punctuation");
        check(ChatTokens.parse("WWW.EXAMPLE.COM/path",empty,all).getFirst().targets().getFirst().key().equals("https://WWW.EXAMPLE.COM/path"),"case-insensitive www links");
        String pathological="https://example.org/path"+")".repeat(7000);long punctuationStart=System.nanoTime();for(int i=0;i<100;i++)check(ChatTokens.parse(pathological,empty,all).getFirst().targets().getFirst().key().equals("https://example.org/path"),"long trailing punctuation");check(System.nanoTime()-punctuationStart<2_000_000_000L,"URL punctuation remains linear");
        var name=new Target(Kind.ITEM,"测试酒","1",null,null,null);var other=new Target(Kind.ITEM,"测试酒","2",null,null,null);
        var index=new Index(List.of(name,other,new Target(Kind.ITEM,"测试酒馆","3",null,null,null),new Target(Kind.PLAYER,"Cy3807","Cy3807",null,null,null)));
        check(ChatTokens.parse("测试酒馆",index,all).getFirst().targets().getFirst().key().equals("3"),"longest name");
        var overlapIndex=new Index(List.of(new Target(Kind.LAND,"曼波","|曼波",null,null,null),new Target(Kind.LAND,"曼波居酒屋","|曼波居酒屋",null,null,null),new Target(Kind.TRAFFIC,"曼波居酒屋","hub:fixture",null,null,null)));
        var overlap=ChatTokens.parse("区域：曼波居酒屋 领土：自由市场",overlapIndex,all);
        check(overlap.size()==1&&overlap.getFirst().end()-overlap.getFirst().start()=="曼波居酒屋".length()&&overlap.getFirst().targets().size()==2,"longest full name wins and all same-name types remain");
        check(ChatTokens.parse("曼波",overlapIndex,all).getFirst().targets().getFirst().name().equals("曼波"),"short name remains available on its own");
        var missingLong=new Index(List.of(new Target(Kind.LAND,"曼波","|曼波",null,null,null)));
        check(ChatTokens.parse("曼波居酒屋",missingLong,all).getFirst().targets().getFirst().name().equals("曼波"),"longest match cannot invent a name absent from the index");
        check(ChatTokens.parse("测试酒",index,all).getFirst().targets().size()==2,"ambiguous names retained");
        check(ChatTokens.parse("Cy3807_extra Cy3807",index,all).size()==1,"player boundaries");
        check(ChatTokens.parse("https://example.org/Cy3807",index,all).size()==1,"URL wins over player");
        check(ChatTokens.parse("https://example.org/Cy3807",index,new Options(true,true,true,false,true)).isEmpty(),"disabled URL still reserved");
        check(ChatTokens.parse("Cy3807 测试酒 12 34",index,new Options(false,false,false,false,false)).isEmpty(),"all recognition switches disabled");
        check(ChatTokens.parse("x".repeat(MAX_TEXT+1),index,all).isEmpty(),"oversized text bypass");
        check(ChatTokens.parse("测试酒 ".repeat(1000),index,all).size()==MAX_MATCHES,"match count bounded");
        var history=new ChatHistory<String>();long old=history.put("old",10);for(int i=0;i<100000;i++)history.put("message "+i,20000);
        check(history.size()<=50&&history.weight()<=ChatHistory.MAX_WEIGHT&&history.get(old)==null,"100k messages plateau at weight cap");history.clear();check(history.size()==0&&history.weight()==0,"disconnect releases retained messages");
        String names="{\"version\":1,\"names\":[[\"land\",\"测试领地\",\"测试国|测试领地\"],[\"player\",\"Cy3807\",\"Cy3807\"],[\"item\",\"测试酒\",\"3\"]]}";
        var parsed=ChatCatalog.names(new java.io.StringReader(names));check(parsed.size()==3&&parsed.stream().allMatch(t->t.x()==null&&t.y()==null&&t.z()==null),"names-only payload never includes coordinates or geometry");
        try{ChatCatalog.names(new java.io.StringReader(names.replace("\\\"3\\\"","\\\"bad\\\"")));}catch(IllegalArgumentException expected){}
        var many=new ArrayList<Target>();for(int i=0;i<20000;i++)many.add(new Target(Kind.ITEM,"压测物品"+i,Integer.toString(i),null,null,null));var large=new Index(many);check(large.size()==20000,"20k-name index");
        String chat="<Cy3807> 来压测物品19999 看看，位置 -123 64 456 https://docs.qq.com/aio/02Xd2eVDY0lzUUJ0VS";
        for(int i=0;i<10000;i++)ChatTokens.parse(chat,large,all);
        long[] nanos=new long[20000];for(int i=0;i<nanos.length;i++){long start=System.nanoTime();ChatTokens.parse(chat,large,all);nanos[i]=System.nanoTime()-start;}Arrays.sort(nanos);
        System.out.printf(java.util.Locale.ROOT,"CHAT PERF 20k names / 20k messages: median %.3f ms p95 %.3f ms p99 %.3f ms; history <= %d / %d chars%n",nanos[10000]/1e6,nanos[19000]/1e6,nanos[19800]/1e6,ChatHistory.CAPACITY,ChatHistory.MAX_WEIGHT);
        // A wide guard catches accidental full-catalog scanning, without claiming a universal FPS bound.
        check(nanos[19000]<10_000_000,"p95 parser under 10ms regression budget");
        System.out.println("Chat assistance regression: "+checks+" checks passed");
    }
}
