package cn.paletale.companion;

import java.util.List;

public final class MarketData {
    private MarketData() {}
    public record Author(String name, String mcName, String avatarPath, int playerId) {}
    public record Shop(ShopSigns.Reading reading, String observedAt) {}
    public record Product(String name, String description, String price, String state, int itemId, ArchiveData.NativeAppearance nativeAppearance, String imagePath,
                          String itemName, String vanillaItem, String vanillaItemName, boolean itemHasModel, List<Shop> shops, String bindingKey, String bindingWarning,
                          /** Labels as on the website (已认证 / 原版 / 未关联档案, then 地图画, 2D 酒 …) and the owner's own category path. */ List<String> tags, List<String> categories) {
        public Product(String name,String description,String price,String state,int itemId,ArchiveData.NativeAppearance nativeAppearance,String imagePath,String itemName,String vanillaItem,String vanillaItemName,boolean itemHasModel,List<Shop> shops){this(name,description,price,state,itemId,nativeAppearance,imagePath,itemName,vanillaItem,vanillaItemName,itemHasModel,shops,"","",List.of(),List.of());}
        public Product(String name,String description,String price,String state,int itemId,ArchiveData.NativeAppearance nativeAppearance,String imagePath,String itemName,String vanillaItem,String vanillaItemName,boolean itemHasModel,List<Shop> shops,String bindingKey,String bindingWarning){this(name,description,price,state,itemId,nativeAppearance,imagePath,itemName,vanillaItem,vanillaItemName,itemHasModel,shops,bindingKey,bindingWarning,List.of(),List.of());}
    }
    public record Location(String name, String coordinates, String directions) {}
    public record Ad(String code, String title, String summary, String state, List<String> tags,
                     Author author, List<Product> products, List<Location> locations, String updatedAt,
                     String publishedAt, String iconPath, /** -1 when the site sent no counter. */ int views, int favorites,
                     /** Detail only (empty in lists): the full text, the author's public QQ and the ad's own contact note. */ String body, String contactQq, String contactNote) {
        public Ad(String code,String title,String summary,String state,List<String> tags,Author author,List<Product> products,List<Location> locations,String updatedAt,String publishedAt,String iconPath,int views,int favorites){this(code,title,summary,state,tags,author,products,locations,updatedAt,publishedAt,iconPath,views,favorites,"","","");}
        /** The wide cover the site renders from the ad's first picture (falls back to the icon there). */
        public String coverPath() { return "/api/mcmod/market/" + code + "/cover"; }
    }
    public record Page(int page, int pageSize, int total, boolean hasMore, List<Ad> ads) {}

    public enum AdState {
        ACTIVE("有效", "active"), PAUSED("暂停", "paused"), ENDED("已结束", "ended"), ALL("全部", "all");
        public final String label;
        public final String id;
        AdState(String label, String id) { this.label = label; this.id = id; }
        public AdState next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public enum Category {
        ALL("全部类别", ""), WINE("酒品售卖", "wine"), DISH("菜品售卖", "dish"),
        MAP_ART("地图画售卖", "map_art"), MATERIALS("建材售卖", "materials"), BUYING("材料收购", "buying"),
        MODELING("模型制作", "modeling"), SERVICE("其他服务", "service"),
        WHOLESALE("批发", "wholesale"), RETAIL("零售", "retail"), EVENT("活动", "event"),
        RECRUITMENT("招募", "recruitment"), OTHER("未知", "other");
        public final String label;
        public final String id;
        Category(String label, String id) { this.label = label; this.id = id; }
        public Category next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public static String stateLabel(String state) {
        return switch (state) {
            case "active" -> "有效";
            case "paused" -> "暂停";
            case "ended" -> "已结束";
            case "available" -> "在售";
            case "sold_out" -> "售罄";
            case "accepting" -> "接单中";
            case "closed" -> "暂停接单";
            default -> "状态未知";
        };
    }

    /** Display website text literally: do not allow legacy formatting/control injection. */
    public static String text(String value) {
        return value == null ? "" : value.replaceAll("§.", "").replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "");
    }
}
