package cn.paletale.companion;
import com.google.gson.*;
import java.io.Reader;
import java.util.*;
import static cn.paletale.companion.ChatTokens.*;
/** Strict compact names-only contract. No map geometry, item detail, coordinates or player observations. */
public final class ChatCatalog {
    public static List<ChatExclusions.Rule> exclusions(JsonObject root){
        if(!root.has("exclusions"))return ChatExclusions.defaults();var values=root.getAsJsonArray("exclusions");if(values.size()>1000)throw new IllegalArgumentException("Too many exclusions");
        var out=new LinkedHashSet<ChatExclusions.Rule>();for(var value:values){var row=value.getAsJsonArray();if(row.size()!=2)throw new IllegalArgumentException("Invalid exclusion");var rule=new ChatExclusions.Rule(row.get(0).getAsString(),row.get(1).getAsString());if(!ChatExclusions.valid(rule))throw new IllegalArgumentException("Invalid exclusion");out.add(rule);}return List.copyOf(out);
    }
    public static List<String> nations(JsonObject root){
        if(!root.has("nations"))return List.of();var values=root.getAsJsonArray("nations");if(values.size()>3000)throw new IllegalArgumentException("Too many nations");
        var out=new LinkedHashSet<String>();for(var value:values){String name=value.getAsString();if(name.isBlank()||name.length()>MAX_NAME||name.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid nation");out.add(name);}return List.copyOf(out);
    }
    public static List<Target> names(Reader input){
        var root=JsonParser.parseReader(input).getAsJsonObject();
        if(root.get("version").getAsInt()!=1)throw new IllegalArgumentException("Unknown names contract");
        var rows=root.getAsJsonArray("names");if(rows.size()>MAX_NAMES)throw new IllegalArgumentException("Too many names");
        var result=new LinkedHashSet<Target>();
        for(var value:rows){var row=value.getAsJsonArray();if(row.size()!=3)throw new IllegalArgumentException("Name row must have three fields");
            Kind kind=switch(row.get(0).getAsString()){case "player"->Kind.PLAYER;case "land"->Kind.LAND;case "traffic"->Kind.TRAFFIC;case "item"->Kind.ITEM;default->throw new IllegalArgumentException("Invalid name kind");};
            String name=row.get(1).getAsString(),key=row.get(2).getAsString();
            if(name.length()<2||name.length()>MAX_NAME||key.isEmpty()||key.length()>160||name.chars().anyMatch(Character::isISOControl)||key.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid name");
            if(kind==Kind.PLAYER&&!key.matches("[A-Za-z0-9_]{2,32}")||kind==Kind.ITEM&&!key.matches("[1-9][0-9]{0,8}")||kind==Kind.LAND&&!key.contains("|")||kind==Kind.TRAFFIC&&!key.startsWith("hub:")&&!key.startsWith("warp:"))throw new IllegalArgumentException("Invalid entity key");
            result.add(new Target(kind,name,key,null,null,null));
        }return List.copyOf(result);
    }
}
