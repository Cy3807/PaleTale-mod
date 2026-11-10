package cn.paletale.companion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Command wheel storage: command normalisation, label fallback, round trip. */
final class RadialSlotsTest {
    static void run() {
        try { body(); } catch (java.io.IOException failure) { throw new AssertionError(failure); }
    }
    private static void body() throws java.io.IOException {
        check("/l spawn x".equals("/" + RadialSlots.command("//  l spawn x ")), "leading slashes and blanks are removed");
        check(RadialSlots.command("  ") == null && RadialSlots.command("/") == null, "empty commands are refused");
        check(RadialSlots.command("l\nop me") == null, "line breaks are refused");
        check(RadialSlots.command("x".repeat(121)) == null && RadialSlots.command("x".repeat(120)) != null, "length limit is 120");
        var slot = RadialSlots.slot("", "warp spawn");
        check(slot.label().equals("warp spa") && slot.command().equals("warp spawn"), "label falls back to the command, cut to 8");
        check(RadialSlots.slot("名字", "  ").empty(), "no command means an empty slot");
        check(RadialSlots.icon("compass").equals("minecraft:compass") && RadialSlots.icon("a b").isEmpty() && RadialSlots.icon("x:Y/z").equals("x:y/z"), "icon ids are normalised or refused");
        check("@cancel".equals(RadialSlots.command("@cancel")) && RadialSlots.command("@rm") == null && RadialSlots.command("/@lands") != null, "only known macros are accepted");
        check(RadialSlots.defaults().get(4).command().equals("@cancel") && RadialSlots.defaults().get(4).icon().equals("minecraft:barrier"), "straight down cancels by default");
        check(RadialSlots.ICONS.stream().allMatch(i -> !RadialSlots.icon(i).isEmpty()) && RadialSlots.PRESETS.stream().allMatch(p -> RadialSlots.command(p.command()) != null), "presets and icons are valid");
        var lenient = LandEntries.lenient(List.of(List.of("白渚", "点击传送"), List.of("下一页"), List.of("＝"), List.of("白渚"), List.of("云樱 酒馆")));
        check(lenient.size() == 2 && lenient.get(0).name().equals("白渚") && lenient.get(1).name().equals("云樱 酒馆"), "lenient territory list keeps names, drops buttons and duplicates");
        check(RadialSlots.parse("传送点\twarp\tminecraft:ender_pearl\n领地\tl\n").get(0).command().equals("@player") && RadialSlots.parse("领地\tl\n").get(0).label().equals("我的领地"), "old default slots migrate");
        check("传送领地".equals(RadialSlots.parse("回领地\t@lands\n").get(0).label()) && RadialSlots.defaults().get(0).label().equals("传送领地"), "the territory slot is called 传送领地");
        var menu = List.of("testi", "白港", "★屯屯★", "我是猴"); var site = List.of("testi", "林泉-建筑俱乐部", "我是猴");
        check(LandEntries.parse(List.of(List.of("我是猴", "你已将这个领土设置为你的/lands edit 领土."))).size() == 1, "a territory selected for /lands edit is still recognised");
        check(java.util.Map.of("★屯屯★", "林泉-建筑俱乐部").equals(LandResolver.resolve(menu, java.util.Set.of("★屯屯★", "我是猴"), site)), "a drink-named territory is resolved by elimination");
        check(LandResolver.resolve(menu, java.util.Set.of("★屯屯★", "白港"), List.of("testi", "林泉", "乙")).isEmpty(), "two unknown entries are never guessed");
        check(RadialSlots.bound("@land:白渚")[1].equals("白渚") && RadialSlots.bound("@player:Cy_3807")[0].equals("player") && RadialSlots.bound("@player:带空格 x") == null && RadialSlots.bound("@land:") == null && RadialSlots.command("@land:a,b") == null, "bound slots are validated");
        check(!ServerAccess.isSimmc("203.0.113.5:7777"), "a direct address is unknown until the site lists it");
        ServerAccess.trustRemote(java.util.List.of("203.0.113.5:7777"));
        check(ServerAccess.isSimmc("203.0.113.5:7777") && ServerAccess.isSimmc("PLAY.simmc.cn") && !ServerAccess.isSimmc("203.0.113.6:7777") && !ServerAccess.isSimmc("evil.example"), "the direct address the site lists is recognised, others are not");
        ServerAccess.trustRemote(java.util.List.of());
        ServerAccess.trust(java.util.List.of("Example.org:25570", "bad host"));
        check(ServerAccess.isSimmc("example.org:25570") && !ServerAccess.isSimmc("bad host"), "extra entries from the settings file count, malformed ones do not");
        ServerAccess.trust(java.util.List.of());
        ServerAccess.trustRemote(ServerEntriesSync.parse("{\"kind\":\"server-entries\",\"version\":1,\"entries\":[\"203.0.113.9:25570\",\"Alt.Example.org\",\"bad host\"]}"));
        check(ServerAccess.isSimmc("203.0.113.9:25570") && ServerAccess.isSimmc("alt.example.org") && !ServerAccess.isSimmc("bad host") && !ServerAccess.isSimmc("203.0.113.10:25570"), "addresses listed by the site count, malformed ones do not");
        boolean refused = false; try { ServerEntriesSync.parse("{\"kind\":\"chat-rules\",\"version\":1,\"entries\":[]}"); } catch (RuntimeException e) { refused = true; }
        check(refused, "a file that is not a server-entries file is refused");
        ServerAccess.trustRemote(java.util.List.of());
        check(RadialSlots.parse("动作\t@actions\tminecraft:leather_boots\n").get(0).icon().equals("minecraft:iron_sword") && RadialSlots.PRESETS.stream().noneMatch(p -> p.command().equals("l")), "old default icons move to the new ones; the plain territory menu is no preset");
        var slots = new java.util.ArrayList<>(RadialSlots.defaults());
        slots.set(5, RadialSlots.slot("家", "/home", "bed"));
        var back = RadialSlots.parse(RadialSlots.format(slots));
        check(back.size() == 8 && back.equals(slots), "format and parse round trip");
        check(RadialSlots.parse("").size() == 8 && RadialSlots.parse("a\tb\n".repeat(20)).size() == 8, "always eight slots");
        Path dir = Files.createTempDirectory("radial"); Path file = dir.resolve("a/radial.txt");
        check(RadialSlots.load(file).equals(RadialSlots.defaults()), "missing file gives defaults");
        RadialSlots.save(file, slots);
        check(RadialSlots.load(file).equals(slots), "saved file loads back");
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
