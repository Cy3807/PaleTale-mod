package cn.paletale.companion;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.network.chat.*;
import static cn.paletale.companion.ChatTokens.*;

/** Display-only metadata. Never sends a command, rewrites a signature or retains a Screen/world. */
public final class ChatAssist {
    private static final String PREFIX="paletale-chat:"+UUID.randomUUID()+":";
    private static final java.util.regex.Pattern DIGITS=java.util.regex.Pattern.compile("[0-9]++");
    private record Part(List<Target> targets,String insertion,boolean nativeClick,String value,String number) {}
    private record NumberSpan(int start,int end,String value) {}
    private record Message(String text,List<Part> parts,String privatePeer) {Message(String text,List<Part> parts){this(text,parts,null);}}
    private record Row(String label,String operation,Target target) {}
    private record Menu(int x,int y,int width,String text,List<Row> rows) {}
    private static final ChatHistory<Message> HISTORY=new ChatHistory<>();
    private static Runnable deleteAction,saveAction;private static Menu menu;private static long blockHudUntil;
    private static Object connection;private static int joinedTicks;
    private static java.util.concurrent.CompletableFuture<?> landRequest;private static long landSerial;
    private ChatAssist(){}
    static Options options(){return new Options(ModSettings.chatCoordinates(),ModSettings.chatPlaces(),ModSettings.chatPlayers(),ModSettings.chatLinks(),ModSettings.chatItems());}
    public static void opened(){ChatDirectory.ensure();}
    public static void closed(){menu=null;deleteAction=null;saveAction=null;}
    public static void clear(){HISTORY.clear();menu=null;landSerial++;if(landRequest!=null)landRequest.cancel(true);landRequest=null;}
    public static boolean blocksHud(){return menu!=null||System.nanoTime()<blockHudUntil;}
    static void settingsChanged(){menu=null;ChatDirectory.clear();}
    static void tick(Minecraft client){ChatRulesSync.refresh(SiteConfig.current(),client.gameDirectory.toPath().resolve("paletale/cache/chat-rules"));Object current=client.getConnection();if(connection!=current){connection=current;joinedTicks=0;clear();ChatDirectory.clear();}if(current!=null&&joinedTicks<200&&++joinedTicks==200)ChatDirectory.ensure();}
    static int retainedMessages(){return HISTORY.size();}
    static int retainedWeight(){return HISTORY.weight();}
    static void useIndex(Index index){ChatDirectory.index=index;}
    public static Component decorate(Component source){return MentionAlerts.apply(decorate(source,true));}
    static Component content(Component source){return decorate(source,false);}
    private static Component decorate(Component source,boolean envelope){
        var whisper=PrivateChat.parse(source.getString(),LedgerStore.accountName(),System.currentTimeMillis(),LedgerStore.account());if(envelope&&whisper.isPresent()){String peer=whisper.get().peer();long id=HISTORY.put(new Message(source.getString(),List.of(new Part(List.of(),null,false,null,null)),peer),source.getString().length()+128);if(id>=0)return source.copy().withStyle(style->style.withInsertion(PREFIX+id+":0").withHoverEvent(new HoverEvent.ShowText(Component.literal("打开 "+peer+" 的聊天"))));}

        Options options=options();if(!ModSettings.chatMenu()&&!options.coordinates()&&!options.places()&&!options.players()&&!options.links()&&!options.items())return source;
        // Match the exact text and styles Minecraft lays out. Unstyled getString() expands embedded
        // object descriptions (e.g. a player head); the styled renderer uses one U+FFFC glyph instead.
        // Formatting codes must likewise be resolved before splitting spans, or styles shift on redraw.
        record Slice(String text,Style style){}
        var slices=new ArrayList<Slice>();var plainBuilder=new StringBuilder();
        var piece=new StringBuilder();var currentStyle=new Style[]{null};boolean[] overflow={false};
        net.minecraft.util.StringDecomposer.iterateFormatted(source,Style.EMPTY,(offset,style,codepoint)->{
            if(plainBuilder.length()+Character.charCount(codepoint)>MAX_TEXT){overflow[0]=true;return false;}
            if(currentStyle[0]!=null&&!currentStyle[0].equals(style)){
                slices.add(new Slice(piece.toString(),currentStyle[0]));piece.setLength(0);
                if(slices.size()>=512){overflow[0]=true;return false;}
            }
            currentStyle[0]=style;piece.appendCodePoint(codepoint);plainBuilder.appendCodePoint(codepoint);return true;
        });
        if(overflow[0])return source;
        if(!piece.isEmpty())slices.add(new Slice(piece.toString(),currentStyle[0]));
        String plain=plainBuilder.toString();if(plain.isEmpty())return source;
        var matches=ChatTokens.parse(plain,ChatDirectory.index,options);
        var numbers=new ArrayList<NumberSpan>();
        if(ModSettings.chatMenu()){
            var digits=DIGITS.matcher(plain);
            while(digits.find()&&numbers.size()<MAX_MATCHES)if(digits.end()-digits.start()>6)numbers.add(new NumberSpan(digits.start(),digits.end(),digits.group()));
        }
        if(matches.isEmpty()&&!ModSettings.chatMenu())return source;
        var output=Component.empty();var parts=new ArrayList<Part>();var rendered=new ArrayList<Slice>();var values=new IdentityHashMap<Match,String>();int position=0,matchIndex=0,numberIndex=0;
        for(var slice:slices){int local=0;while(local<slice.text().length()){
            while(matchIndex<matches.size()&&matches.get(matchIndex).end()<=position)matchIndex++;
            Match match=matchIndex<matches.size()?matches.get(matchIndex):null;
            boolean inside=match!=null&&match.start()<=position;
            while(numberIndex<numbers.size()&&numbers.get(numberIndex).end()<=position)numberIndex++;
            NumberSpan number=numberIndex<numbers.size()?numbers.get(numberIndex):null;boolean insideNumber=number!=null&&number.start()<=position;
            int end=Math.min(slice.text().length()-local,match==null?plain.length()-position:(inside?match.end():match.start())-position);
            if(number!=null)end=Math.min(end,(insideNumber?number.end():number.start())-position);
            List<Target> targets=inside?match.targets():List.of();Style original=slice.style();
            String insertion=original.getInsertion();if(insertion!=null&&insertion.length()>MAX_TEXT)insertion=null;
            parts.add(new Part(targets,insertion,original.getClickEvent()!=null&&targets.stream().noneMatch(t->t.kind()==Kind.PLAYER),inside?values.computeIfAbsent(match,m->plain.substring(m.start(),m.end())):null,insideNumber?number.value():null));
            Style style=original;
            if(!targets.isEmpty()){
                if(original.getClickEvent()==null&&targets.stream().anyMatch(t->t.kind()!=Kind.PLAYER))style=style.withUnderlined(true);
                var labels=new LinkedHashSet<String>();for(var target:targets)labels.add(kindLabel(target.kind()));
                style=style.withHoverEvent(new HoverEvent.ShowText(Component.literal(String.join("、",labels))));
            }
            rendered.add(new Slice(slice.text().substring(local,local+end),style));position+=end;local+=end;
            if(parts.size()>768)return source;
        }}
        long id=HISTORY.put(new Message(plain.replace("\uFFFC", ""),List.copyOf(parts)),plain.length()+parts.size()*128);
        if(id<0)return source;
        for(int i=0;i<rendered.size();i++){var slice=rendered.get(i);output.append(Component.literal(slice.text()).setStyle(slice.style().withInsertion(PREFIX+id+":"+i)));}
        return output;
    }
    public static Style hit(double x,double y){
        var client=Minecraft.getInstance();
        var finder=new net.minecraft.client.gui.ActiveTextCollector.ClickableStyleFinder(client.font,(int)x,(int)y).includeInsertions(true);
        client.gui.getChat().captureClickableText(finder,client.getWindow().getGuiScaledHeight(),client.gui.getGuiTicks(),true);
        return finder.result();
    }
    private static boolean shift(){long window=Minecraft.getInstance().getWindow().handle();return org.lwjgl.glfw.GLFW.glfwGetKey(window,340)==1||org.lwjgl.glfw.GLFW.glfwGetKey(window,344)==1;}
    public static boolean click(ChatScreen screen,double x,double y,int button){
        if(menuClick(screen,x,y,button))return true;
        if(button!=0&&button!=1)return false;
        String favorite=PaleTaleClient.FAVORITE_PRESENCE.playerAt(x,y);if(favorite!=null&&favorite.matches("[A-Za-z0-9_]{1,16}")){var target=new Target(Kind.PLAYER,favorite,favorite,null,null,null);if(button==0)act(screen,new Row("","open",target),favorite);else show(screen,x,y,favorite,List.of(new Row("查看玩家","open",target),new Row("私聊玩家","msg",target),new Row("转账玩家","pay",target),new Row("复制此条","copy-target",target)));return true;}
        Style style=hit(x,y);if(style==null)return false;
        return clickStyle(screen,style,x,y,button);
    }
    static boolean menuClick(Screen screen,double x,double y,int button){
        if(menu!=null){var selected=menu;menu=null;blockHudUntil=System.nanoTime()+250_000_000L;
            int row=(int)(y-selected.y())/18;
            if(button==0&&x>=selected.x()&&x<selected.x()+selected.width()&&y>=selected.y()&&row>=0&&row<selected.rows().size())act(screen,selected.rows().get(row),selected.text());
            return true;
        }
        return false;
    }
    static boolean clickStyle(Screen screen,Style style,double x,double y,int button){
        if(style==null)return false;
        boolean tell=button==0&&!shift()&&isTell(style.getClickEvent());
        if(style.getInsertion()==null||!style.getInsertion().startsWith(PREFIX))return tell;
        Message message=null;Part part=null;
        try{String[] key=style.getInsertion().substring(PREFIX.length()).split(":");message=HISTORY.get(Long.parseLong(key[0]));if(message!=null)part=message.parts().get(Integer.parseInt(key[1]));}catch(RuntimeException ignored){}
        if(part==null)return tell||button==0&&shift();
        if(button==0&&!shift()&&message.privatePeer()!=null){Minecraft.getInstance().setScreen(PrivateChatScreen.conversation(screen,PaleTaleClient.MARKET,message.privatePeer()));return true;} // never insert an expired private marker
        if(button==0&&shift()){if(part.insertion()!=null)insert(screen,part.insertion(),false);return true;}
        var current=options();var targets=part.targets().stream().filter(t->current.accepts(t.kind())).toList();
        if(button==1){if(!ModSettings.chatMenu())return false;var rows=new ArrayList<Row>();
            for(var target:targets){String suffix=targets.size()>1?" · "+humanTarget(target):"";
                switch(target.kind()){
                    case PLAYER->{rows.add(new Row("私聊玩家"+suffix,"msg",target));rows.add(new Row("转账玩家"+suffix,"pay",target));}
                    case LAND,NATION,TRAFFIC->{rows.add(new Row("快捷导航"+suffix,"navigate",target));if(ChatMap.available())rows.add(new Row("地图上查看"+suffix,"map",target));}
                    case COORDINATE->{if(ChatMap.available())rows.add(new Row("地图上查看","map",target));}
                    default->{}
                }
            }
            if(!targets.isEmpty()&&part.value()!=null){Kind kind=targets.getFirst().kind();String label=kind==Kind.COORDINATE?"复制坐标":"复制此条";rows.add(new Row(label,"copy-target",new Target(kind,part.value(),"",null,null,null)));}
            if(part.number()!=null)rows.add(new Row("复制数字","copy-target",new Target(Kind.ITEM,part.number(),"",null,null,null)));
            rows.add(new Row("收藏信息","save",null));rows.add(new Row("复制消息","copy",null));show(screen,x,y,message.text(),rows);return true;
        }
        if(part.nativeClick())return tell;
        if(targets.isEmpty())return tell;
        if(targets.size()==1){act(screen,new Row("",defaultOperation(targets.getFirst().kind()),targets.getFirst()),message.text());}
        else{var rows=new ArrayList<Row>();var counts=new HashMap<String,Integer>();for(var target:targets){String label=humanTarget(target);int n=counts.merge(label,1,Integer::sum);rows.add(new Row(label+(n>1?"（候选 "+n+"）":""),"open",target));}show(screen,x,y,message.text(),rows);}
        return true;
    }
    /** What a plain click on a name of this kind does: the player's choice in the chat settings (the SMC page unless chosen otherwise). */
    static String defaultOperation(Kind kind){String value=ModSettings.chatClickAction(kind.name());return choices(kind).contains(value)?value:"open";}
    static List<String> choices(Kind kind){return switch(kind){case PLAYER->List.of("open","msg","pay");case LAND,NATION,TRAFFIC->ChatMap.available()?List.of("open","navigate","map"):List.of("open","navigate");default->List.of("open");};}
    static String choiceLabel(String operation){return switch(operation){case "msg"->"私聊";case "pay"->"转账";case "navigate"->"快捷导航";case "map"->"地图上查看";default->"查看 SMC 页面";};}
    static void messageMenu(Screen screen,String message,double x,double y){show(screen,x,y,message,List.of(new Row("收藏信息","save",null),new Row("复制消息","copy",null)));}
    static void playerMenu(Screen screen,String name,String message,double x,double y){var target=new Target(Kind.PLAYER,name,name,null,null,null);show(screen,x,y,message,List.of(new Row("查看玩家","open",target),new Row("私聊玩家","msg",target),new Row("转账玩家","pay",target),new Row("复制此条","copy-target",target),new Row("收藏信息","save",null),new Row("复制消息","copy",null)));}
    /** The open menu's star row runs this instead of saving the plain line (chat screen rows know their time and sender). */
    static void offerSave(String label,Runnable action){if(menu==null)return;saveAction=action;menu=new Menu(menu.x(),menu.y(),Math.max(menu.width(),Minecraft.getInstance().font.width(label)+12),menu.text(),menu.rows().stream().map(row->row.operation().equals("save")?new Row(label,"save",null):row).toList());}
    static void offerDelete(Runnable action){if(menu==null)return;deleteAction=action;var rows=new ArrayList<>(menu.rows());rows.add(new Row("删除消息","delete",null));menu=new Menu(menu.x(),Math.min(menu.y(),Minecraft.getInstance().screen.height-rows.size()*18-22),menu.width(),menu.text(),List.copyOf(rows));}
    private static boolean isTell(ClickEvent event){String command=event instanceof ClickEvent.SuggestCommand s?s.command():event instanceof ClickEvent.RunCommand r?r.command():"";return command.strip().matches("(?i)^/tell\\s+[A-Za-z0-9_]{1,32}\\s*$");}
    private static String humanTarget(Target target){String suffix="";if(target.kind()==Kind.LAND){int split=target.key().indexOf('|');if(split>0)suffix="（"+target.key().substring(0,split)+"）";}return kindLabel(target.kind())+" · "+target.name()+suffix;}
    private static String kindLabel(Kind kind){return switch(kind){case PLAYER->"玩家";case LAND->"领地";case NATION->"国家";case TRAFFIC->"传送点";case ITEM->"物品";case COORDINATE->"坐标";case LINK->"链接";};}
    private static void show(Screen screen,double x,double y,String text,List<Row> rows){
        int maxRows=Math.max(1,(screen.height-24)/18);if(rows.size()>maxRows){if(rows.getLast().operation().equals("copy")){int tail=1;while(tail<maxRows&&tail<rows.size()&&rows.get(rows.size()-1-tail).operation().equals("copy-target"))tail++;var limited=new ArrayList<Row>(rows.subList(0,maxRows-tail));limited.addAll(rows.subList(rows.size()-tail,rows.size()));rows=limited;}else rows=new ArrayList<>(rows.subList(0,maxRows));}
        int width=Math.min(screen.width-8,Math.max(120,rows.stream().mapToInt(row->Minecraft.getInstance().font.width(row.label())+12).max().orElse(120)));
        saveAction=null;deleteAction=null;menu=new Menu(Math.max(0,Math.min((int)x,screen.width-width-4)),Math.max(0,Math.min((int)y,screen.height-rows.size()*18-22)),width,text,List.copyOf(rows));
    }
    public static boolean escape(int key){if(key==256&&menu!=null){menu=null;return true;}return false;}
    public static boolean scroll(){if(menu==null)return false;menu=null;return true;}
    public static void render(GuiGraphics g,int mx,int my){var selected=menu;if(selected==null)return;var font=Minecraft.getInstance().font;
        int height=selected.rows().size()*18;
        g.fill(selected.x()+2,selected.y()+2,selected.x()+selected.width()+2,selected.y()+height+2,0x55000000);
        g.fill(selected.x(),selected.y(),selected.x()+selected.width(),selected.y()+height,MarketTheme.PAPER);
        g.renderOutline(selected.x(),selected.y(),selected.width(),height,MarketTheme.LINE);
        for(int i=0;i<selected.rows().size();i++){int y=selected.y()+i*18;boolean hover=mx>=selected.x()&&mx<selected.x()+selected.width()&&my>=y&&my<y+18;
            if(hover)g.fill(selected.x()+1,y+1,selected.x()+selected.width()-1,y+17,MarketTheme.c(0xFFE2E8DE));
            if(i>0)g.hLine(selected.x()+5,selected.x()+selected.width()-6,y,MarketTheme.LINE);
            MarketTheme.text(g,selected.rows().get(i).label(),selected.x()+6,y+5,selected.width()-12,MarketTheme.INK);
        }
    }
    private static void insert(Screen screen,String text,boolean replace){if(screen instanceof ChatScreen chat)chat.insertText(text,replace);else if(text.startsWith("/pay "))Minecraft.getInstance().setScreen(new ChatScreen(text,false));}
    private static void act(Screen screen,Row row,String message){
        var client=Minecraft.getInstance();Target target=row.target();
        switch(row.operation()){
            case "delete"->{var action=deleteAction;deleteAction=null;if(action!=null)action.run();}
            case "save"->{var action=saveAction;saveAction=null;if(action!=null)action.run();else saveLine(message);}
            case "copy"->client.keyboardHandler.setClipboard(message);
            case "msg"->client.setScreen(PrivateChatScreen.conversation(screen,PaleTaleClient.MARKET,target.key()));
            case "pay"->{if(target.key().matches("[A-Za-z0-9_]{2,32}"))insert(screen,"/pay "+target.key()+" ",true);}
            case "copy-target"->client.keyboardHandler.setClipboard(target.name());
            case "map"->resolve(target,true);
            case "navigate"->navigate(target);
            case "open"->{switch(target.kind()){
                case COORDINATE->navigate(target);
                case LINK->ConfirmLinkScreen.confirmLinkNow(screen,target.key());
                case PLAYER,LAND,NATION,TRAFFIC->client.setScreen(new WorldEntityScreen(screen,PaleTaleClient.MARKET,target.kind()==Kind.PLAYER?"player":target.kind()==Kind.LAND?"land":target.kind()==Kind.NATION?"nation":"traffic",target.key()));
                case ITEM->client.setScreen(new ArchiveDetailScreen(screen,PaleTaleClient.MARKET,ArchiveData.Kind.ITEMS,Integer.parseInt(target.key())));
            }}
            default->{}
        }
    }
    /** Stars a line from the vanilla chat box, splitting sender and body when it is a known chat format. */
    private static void saveLine(String line){String me=LedgerStore.accountName(),sender="",body=line,source="聊天框";long now=System.currentTimeMillis();var whisper=PrivateChat.parse(line,me,now,LedgerStore.account());var parsed=ChatMentions.line(line);
        if(whisper.isPresent()){var e=whisper.get();sender=e.outgoing()?me:e.senderId();body=e.text();source=e.channelKind().equals("land")?"领地 · "+e.scope():e.channelKind().equals("nation")?"国家 · "+e.scope():"私聊";}
        else if(parsed.isPresent()){var p=parsed.get();sender=p.sender();body=line.substring(p.bodyStart());source=switch(p.channel()){case "land"->"领地 · "+p.scope();case "nation"->"国家 · "+p.scope();default->p.scope().equals("G")?"全球":p.scope();};}
        notifySaved(SavedChatStore.add(body,now,sender,source));}
    static void notifySaved(boolean added){Minecraft.getInstance().gui.setOverlayMessage(Component.literal(added?"已收藏，可在 聊天 › ★信息 查看":"这条消息已在 ★信息 中"),false);}
    private static void navigate(Target target){resolve(target,false);}
    private static void destination(Target target,int x,Integer y,int z,boolean map,String kind){
        if(map){var client=Minecraft.getInstance();if(target.kind()!=Kind.COORDINATE&&!CityNavigation.MAIN_WORLD.equals(Navigation.world(client))){client.gui.getChat().addMessage(Component.literal("请先前往主世界，再在地图上查看该地点。"));return;}if(!ChatMap.open(client.screen,x,z))client.gui.getChat().addMessage(Component.literal("Xaero 世界地图暂不可用，请稍后重试。"));}
        else Navigation.start(new Navigation.Target(target.name(),x,y,z,kind,List.of(),CityNavigation.MAIN_WORLD));
    }
    private static void resolve(Target target,boolean map){
        if(target.x()!=null&&target.z()!=null){destination(target,target.x(),target.y(),target.z(),map,"聊天");return;}
        if(map){var addon=ChatMap.addon(target);if(addon!=null){destination(target,addon.x(),null,addon.z(),true,"地图扩展");return;}}
        if(target.kind()!=Kind.LAND&&target.kind()!=Kind.NATION&&target.kind()!=Kind.TRAFFIC||landRequest!=null&&!landRequest.isDone())return;
        // Explicit click only; a territory without cached coordinates is resolved against the existing public API.
        var client=Minecraft.getInstance();var site=SiteConfig.current();var joined=client.getConnection();var initiatingScreen=client.screen;
        String[] key=target.key().split("\\|",2);if(target.kind()==Kind.LAND&&key.length!=2)return;
        String names=java.net.URLEncoder.encode(target.kind()==Kind.LAND?key[1]+"@"+key[0]:target.key(),java.nio.charset.StandardCharsets.UTF_8);
        String path=target.kind()==Kind.LAND?"/api/mcmod/nav/lands?names="+names:target.kind()==Kind.NATION?"/api/mcmod/chat/names?capital="+names:"/api/mcmod/world/catalog?traffic="+names;
        long run=++landSerial;landRequest=new WorldApi().call(site,path,null,null).whenComplete((root,error)->client.execute(()->{
            if(run!=landSerial||client.getConnection()!=joined||!SiteConfig.current().equals(site)||(map&&client.screen!=initiatingScreen))return;
            if(error==null&&target.kind()==Kind.NATION){var capital=CapitalDestination.parse(root,target.key());if(capital!=null){destination(new Target(target.kind(),target.name()+" · 首都 "+capital.capital(),target.key(),null,null,null),capital.x(),null,capital.z(),map,"首都几何中心");return;}}
            if(error==null&&target.kind()==Kind.TRAFFIC&&root.has("scope")&&root.get("scope").isJsonObject()){
                var scope=root.getAsJsonObject("scope");if(scope.has("point")&&scope.get("point").isJsonObject()){var point=scope.getAsJsonObject("point");int x=point.get("x").getAsInt(),z=point.get("z").getAsInt();if(Math.abs((long)x)<=1000000&&Math.abs((long)z)<=1000000){destination(target,x,null,z,map,"传送点");return;}}
            }
            if(error==null&&target.kind()==Kind.LAND&&root.has("lands"))for(var entry:root.getAsJsonArray("lands")){var land=entry.getAsJsonArray();if(land.size()>=4&&land.get(0).getAsString().equals(key[1])&&land.get(1).getAsString().equals(key[0])){int x=land.get(2).getAsInt(),z=land.get(3).getAsInt();if(Math.abs((long)x)<=1000000&&Math.abs((long)z)<=1000000){destination(target,x,null,z,map,"领地");return;}}}
            client.gui.getChat().addMessage(Component.literal(target.kind()==Kind.NATION?"未能确认该国家的首都几何中心，请稍后重试。":"目的地坐标暂不可用，请稍后重试。"));
        }));
    }
}
