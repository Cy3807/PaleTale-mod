package cn.paletale.companion;

import java.util.List;

public final class ArchiveData {
    private ArchiveData() {}
    public enum Kind {
        ITEMS("物品档案", "items"), PLAYERS("玩家档案", "players"), ORGS("组织档案", "orgs");
        public final String label, id;
        Kind(String label, String id) { this.label = label; this.id = id; }
    }
    public record Owner(Integer playerId, String name, String mcName, boolean verified, String avatarPath) {}
    public record NativeAppearance(String itemModel, Integer customModelData) {}
    public record Entry(String kind, int id, String name, String subtitle, String description, String category,
                        String badge, boolean hasModel, String imagePath, Owner owner, NativeAppearance nativeAppearance,
                        /** Items only; -1 when the site sent no counter. */ int views, int favorites,
                        /** Wines only: effect key ("" = unknown or not a wine) and display text ("迅捷 II", "未知"; "" when not a wine). */ String effect, String effectText,
                        /** Brand wines only: the brand text (up to three lines, colour codes kept); "" otherwise. */ String brand,
                        /** Map art only: size and tags ("长 3 × 宽 2 · 二次元"); "" otherwise. */ String facts) {}
    /** Wine effect categories, same keys as the site. */
    public static final String[][] WINE_EFFECTS = { {"speed", "迅捷"}, {"haste", "急迫"}, {"strength", "力量"}, {"jump", "跳跃提升"}, {"resistance", "抗性提升"},
            {"fire_resistance", "抗火"}, {"hunger", "饥饿"}, {"absorption", "伤害吸收"}, {"glowing", "发光"}, {"luck", "幸运"}, {"unluck", "霉运"}, {"sober", "解酒"},
            {"summon", "生物召唤"}, {"transform", "变身"}, {"head", "获取头颅"}, {"brand", "烙印"}, {"enchant", "附魔"}, {"chat", "公屏酒"}, {"unknown", "未知"} };
    public static String effectLabel(String key) {
        for (String[] effect : WINE_EFFECTS) if (effect[0].equals(key)) return effect[1];
        return "全部效果";
    }
    public record Page(int page, int pageSize, int total, boolean hasMore, List<Entry> entries) {}
    public record Ad(String code, String title, String state) {}
    public record Member(int playerId, String name, String mcName, boolean leader, String avatarPath) {}
    public record Detail(Entry entry, String qq, boolean qqHidden, String joinUrl, int ownedTotal, List<Entry> ownedItems, int adsTotal, List<Ad> ads, List<Member> members,
                        /** Fish only: fishing facts as display lines and navigation actions (command already validated); empty otherwise. */ String fishCode, List<FishLine> fishLines) {}
    /** One fishing fact row: vanilla item ids of its icons (the website card's icons) and an optional validated navigation command ("" = none). */
    public record FishLine(String text, List<String> icons, String command) {}
}
