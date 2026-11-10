package cn.paletale.companion;
import java.util.ArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
/** Online player names in supported chat turn aqua; mentions of you turn green and ring once. */
final class MentionAlerts {
    private static final int MAX_TEXT=4096;
    private static long lastBell;
    private MentionAlerts(){}
    static Component apply(Component source){
        if(!ModSettings.chatMentions())return source;
        record Slice(String text,Style style){}
        var slices=new ArrayList<Slice>();var plain=new StringBuilder();var piece=new StringBuilder();Style[] current={null};boolean[] overflow={false};
        net.minecraft.util.StringDecomposer.iterateFormatted(source,Style.EMPTY,(offset,style,codepoint)->{
            if(plain.length()+Character.charCount(codepoint)>MAX_TEXT){overflow[0]=true;return false;}
            if(current[0]!=null&&!current[0].equals(style)){slices.add(new Slice(piece.toString(),current[0]));piece.setLength(0);}
            current[0]=style;piece.appendCodePoint(codepoint);plain.appendCodePoint(codepoint);return true;
        });
        if(overflow[0])return source;
        if(!piece.isEmpty())slices.add(new Slice(piece.toString(),current[0]));
        String me=LedgerStore.accountName();var client=Minecraft.getInstance();var online=LocalRoster.onlineNames(client);
        var spans=ChatMentions.findPlayers(plain.toString(),me,online);if(spans.isEmpty())return source;var output=Component.empty();int position=0,spanIndex=0;
        for(var slice:slices){String text=slice.text();int local=0;while(local<text.length()){
            var span=spanIndex<spans.size()?spans.get(spanIndex):null;
            if(span!=null&&position>=span.end()){spanIndex++;continue;}
            if(span!=null&&position>=span.start()){
                // The mention takes the style of its first character, so click/insertion metadata survives.
                if(position==span.start())output.append(Component.literal("@"+span.name()).setStyle(slice.style().withColor(span.self()?ChatFormatting.GREEN:ChatFormatting.AQUA)));
                int take=Math.min(text.length()-local,span.end()-position);local+=take;position+=take;continue;
            }
            int end=span==null?text.length()-local:Math.min(text.length()-local,span.start()-position);
            output.append(Component.literal(text.substring(local,local+end)).setStyle(slice.style()));local+=end;position+=end;
        }}
        if(spans.stream().anyMatch(ChatMentions.NamedSpan::self))ChatMentions.line(plain.toString()).ifPresent(MentionAlerts::ring);
        return output;
    }
    private static void ring(ChatMentions.Line line){
        if(ModSettings.chatDoNotDisturb())return;
        String conversation=switch(line.channel()){case "land"->"领地:"+line.scope();case "nation"->"国家:"+line.scope();default->line.sender();};
        if(PrivateChatStore.muted(conversation)||PrivateChatStore.muted(line.sender()))return;
        lastBell=System.currentTimeMillis();
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL,1.5f));
    }
}
