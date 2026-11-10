package cn.paletale.companion;
import java.util.*;
/** Exact display names, with an optional type. Does not alter public archives or server chat. */
public final class ChatExclusions {
 public record Rule(String kind,String name){}
 private static final Set<String> KINDS=Set.of("all","player","land","nation","traffic","item");
 private static final List<Rule> DEFAULTS=List.of(new Rule("all","Esc"),new Rule("all","故事"));
 public static List<Rule> defaults(){return DEFAULTS;}
 public static boolean valid(Rule rule){return rule!=null&&rule.kind()!=null&&KINDS.contains(rule.kind())&&rule.name()!=null&&!rule.name().isBlank()&&rule.name().length()<=100&&!rule.name().chars().anyMatch(Character::isISOControl);}
 public static String normal(String value){return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT);}
 public static Set<String> keys(Collection<Rule> rules){var keys=new HashSet<String>();for(var rule:rules)if(valid(rule))keys.add(rule.kind()+"\0"+normal(rule.name()));return Set.copyOf(keys);}
 public static boolean blocked(Set<String> keys,ChatTokens.Kind kind,String name){String value=normal(name);return keys.contains("all\0"+value)||keys.contains(kind.name().toLowerCase(Locale.ROOT)+"\0"+value);}
}
