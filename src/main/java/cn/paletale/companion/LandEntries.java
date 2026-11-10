package cn.paletale.companion;

import java.util.ArrayList;
import java.util.List;

/** The territories listed by the server's /l menu: every item of the menu whose description says 「你拥有这个领土」 or 「你在这个领土中被信任」 is one (the rest are fillers and buttons). */
public final class LandEntries {
    private LandEntries() {}
    /** {@code owned}: the player owns it (otherwise they are trusted in it); {@code nation}: the nation line of the description, "" when there is none. */
    public record Entry(String name, String nation, boolean owned) {}

    /** Each item is its name followed by its description lines (colour codes already removed). */
    public static List<Entry> parse(List<List<String>> items) {
        List<Entry> out = new ArrayList<>();
        for (List<String> item : items) {
            if (item.size() < 2) continue;
            String name = item.get(0).strip();
            boolean owned = false, land = false; String nation = "";
            for (String line : item.subList(1, item.size())) {
                if (line.contains("你拥有这个领土")) { owned = true; land = true; }
                else if (line.contains("你在这个领土中被信任") || line.contains("这个领土中被信任") || line.contains("你已将这个领土设置为")) land = true;
                int at = line.indexOf("国家");
                if (at >= 0 && nation.isEmpty()) {
                    int colon = Math.max(line.indexOf(':', at), line.indexOf('：', at));
                    if (colon >= 0) nation = line.substring(colon + 1).strip();
                }
            }
            final String nationOf = nation, nameOf = name;
            if (land && !nameOf.isEmpty() && nameOf.length() <= 60 && out.stream().noneMatch(e -> e.name().equals(nameOf) && e.nation().equals(nationOf))) out.add(new Entry(nameOf, nationOf, owned));
        }
        return List.copyOf(out);
    }

    /**
     * Every item of a menu known to be the territory list, whatever its description says: the player's /l menu lists only territories they can teleport to. Items that are plainly buttons
     * (page turners, symbols) are left out. Used only when the described form above finds nothing.
     */
    public static List<Entry> lenient(List<List<String>> items) {
        List<Entry> out = new ArrayList<>();
        for (List<String> item : items) {
            if (item.isEmpty()) continue;
            String name = item.get(0).strip();
            if (name.isEmpty() || name.length() > 60 || name.matches("[\\p{P}\\p{S}\\p{Z}\\p{C}]*") || name.matches(".*(玻璃板|上一页|下一页|翻页|返回|关闭|排序|搜索|筛选|新建|创建).*")) continue;
            if (out.stream().noneMatch(e -> e.name().equals(name))) out.add(new Entry(name, "", false));
        }
        return List.copyOf(out);
    }

    /** The menu's own order, each territory with the nation and ownership the described form found for it when it found one. */
    public static List<Entry> merge(List<Entry> described, List<Entry> all) {
        List<Entry> out = new ArrayList<>();
        for (Entry entry : all) out.add(described.stream().filter(e -> e.name().equals(entry.name())).findFirst().orElse(entry));
        return List.copyOf(out);
    }
}
