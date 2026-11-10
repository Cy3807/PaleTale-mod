package cn.paletale.companion;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chest-shop sign text → trade data. Line 1 is the shop owner, a bare integer line is the quantity,
 * the price line holds "B 10 : S 5"-style tokens and the remaining text line is the item name.
 * B / 买 / 出售 = the shop sells (player buys); S / 卖 / 收购 = the shop buys (player sells).
 */
public final class ShopSigns {
    private ShopSigns() {}
    public enum Mode { BUY, SELL, BOTH; public String id() { return name().toLowerCase(); } }
    /** A sign reading; {@code chestX/Y/Z} is the chest the sign is attached to (null when it could not be found). */
    public record Reading(String world, int x, int y, int z, String owner, String itemName, Mode mode, int quantity, Double buyPrice, Double sellPrice, String chestDir) {
        public Reading(String world, int x, int y, int z, String owner, String itemName, Mode mode, int quantity, Double buyPrice, Double sellPrice) {
            this(world, x, y, z, owner, itemName, mode, quantity, buyPrice, sellPrice, null);
        }
        public Reading withChestDir(String dir) { return new Reading(world, x, y, z, owner, itemName, mode, quantity, buyPrice, sellPrice, dir); }
        public boolean hasChest() { return chestDir != null; }
        /** Where the chest is: one block from the sign in the stored direction (null when unknown). */
        public int[] chest() { return chestAt(x, y, z, chestDir); }
        public String key() { return world + "|" + x + "|" + y + "|" + z; }
        public boolean sameTrade(Reading other) {
            return owner.equals(other.owner) && itemName.equals(other.itemName) && mode == other.mode && quantity == other.quantity
                    && java.util.Objects.equals(buyPrice, other.buyPrice) && java.util.Objects.equals(sellPrice, other.sellPrice);
        }
        public JsonObject json() {
            JsonObject row = new JsonObject();
            row.addProperty("world", world); row.addProperty("x", x); row.addProperty("y", y); row.addProperty("z", z);
            row.addProperty("owner", owner); row.addProperty("itemName", itemName); row.addProperty("mode", mode.id()); row.addProperty("quantity", quantity);
            if (buyPrice != null) row.addProperty("buyPrice", buyPrice); else row.add("buyPrice", com.google.gson.JsonNull.INSTANCE);
            if (sellPrice != null) row.addProperty("sellPrice", sellPrice); else row.add("sellPrice", com.google.gson.JsonNull.INSTANCE);
            if (hasChest()) row.addProperty("chestDir", chestDir);
            return row;
        }
    }
    /** The chest sits one block behind its sign, so only the sign position and a direction (x+, x-, z+ or z-) are stored. */
    public static int[] chestAt(int x, int y, int z, String dir) {
        if (dir == null) return null;
        return switch (dir) { case "x+" -> new int[]{ x + 1, y, z }; case "x-" -> new int[]{ x - 1, y, z }; case "z+" -> new int[]{ x, y, z + 1 }; case "z-" -> new int[]{ x, y, z - 1 }; default -> null; };
    }
    private static final Pattern PRICE = Pattern.compile("(?i)(出售|收购|买入|卖出|买|卖|收|售|[bs])?\\s*[:：]?\\s*(\\d+(?:\\.\\d+)?)");
    private static final Pattern WHOLE = Pattern.compile("(?:\\s*(?:出售|收购|买入|卖出|买|卖|收|售|[bsBS])?\\s*[:：]?\\s*\\d+(?:\\.\\d+)?\\s*[:：/|]?)+");
    /** "出售 30" / "收购：16": quantity and trade direction on one line. */
    private static final Pattern LABELED_QUANTITY = Pattern.compile("(出售|收购|买入|卖出)\\s*[:：]?\\s*(\\d{1,6})");
    /** A sign whose quantity line says the chest is empty (selling) or full (buying): quantity 0, direction from the word. */
    private static final Pattern EMPTY = Pattern.compile("(缺货|售罄|无货|已售空)");
    private static final Pattern FULL = Pattern.compile("(已满|空间已满|仓库已满|满仓|收满)");
    /** "单价：$100" / "价格 ¥2.5" / "$100": one price whose direction comes from the quantity line. */
    private static final Pattern SIMPLE_PRICE = Pattern.compile("(?:单价|价格|价|price)?\\s*[:：]?\\s*[$¥￥]?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:云币|钻石|金币|元)?", Pattern.CASE_INSENSITIVE);
    private static boolean shopSellsLabel(String label) { return label.equals("出售") || label.equals("买入"); }

    public static String plain(String line) { return line == null ? "" : line.replaceAll("§.", "").replaceAll("[\\p{Cntrl}]", "").trim(); }

    public static Optional<Reading> parse(String world, int x, int y, int z, List<String> raw) {
        if (raw.size() < 4) return Optional.empty();
        String[] lines = raw.stream().limit(4).map(ShopSigns::plain).toArray(String[]::new);
        // "$1,000,000": drop thousands separators before reading numbers (the owner line is left alone).
        for (int i = 1; i < lines.length; i++) lines[i] = lines[i].replaceAll("(?<=\\d),(?=\\d{3}(?!\\d))", "");
        String owner = lines[0];
        if (owner.isEmpty() || owner.length() > 32) return Optional.empty();
        int quantity = -1, quantityLine = -1, priceLine = -1;
        Boolean labeledSell = null;
        for (int i = 1; i < 4; i++) {
            Matcher labeled = LABELED_QUANTITY.matcher(lines[i]);
            if (quantityLine < 0 && labeled.matches()) { quantity = Integer.parseInt(labeled.group(2)); quantityLine = i; labeledSell = shopSellsLabel(labeled.group(1)); }
            else if (quantityLine < 0 && lines[i].matches("\\d{1,6}")) { quantity = Integer.parseInt(lines[i]); quantityLine = i; }
            else if (quantityLine < 0 && EMPTY.matcher(lines[i]).matches()) { quantity = 0; quantityLine = i; labeledSell = true; }
            else if (quantityLine < 0 && FULL.matcher(lines[i]).matches()) { quantity = 0; quantityLine = i; labeledSell = false; }
        }
        if (quantity < 0 || quantity > 100_000) return Optional.empty();
        for (int i = 1; i < 4 && priceLine < 0; i++) {
            if (i == quantityLine || lines[i].isEmpty()) continue;
            boolean multi = WHOLE.matcher(lines[i]).matches() && PRICE.matcher(lines[i]).find() && !lines[i].matches("\\d+");
            if (multi || SIMPLE_PRICE.matcher(lines[i]).matches() && lines[i].matches(".*(单价|价格|价|price|[$¥￥]).*")) priceLine = i;
        }
        if (priceLine < 0) return Optional.empty();
        String item = "";
        for (int i = 1; i < 4; i++) if (i != quantityLine && i != priceLine && !lines[i].isEmpty()) { item = lines[i]; break; }
        if (item.isEmpty() || item.length() > 100) return Optional.empty();
        Double buy = null, sell = null; int unlabeled = 0;
        Matcher simple = SIMPLE_PRICE.matcher(lines[priceLine]);
        if (simple.matches() && !lines[priceLine].matches("(?i).*\\b[bs]\\b.*") && lines[priceLine].matches(".*(单价|价格|价|price|[$¥￥]).*")) {
            double value = Double.parseDouble(simple.group(1));
            if (labeledSell == null || labeledSell) sell = value; else buy = value;
        } else {
            Matcher matcher = PRICE.matcher(lines[priceLine]);
            while (matcher.find()) {
                double value = Double.parseDouble(matcher.group(2));
                String label = matcher.group(1) == null ? "" : matcher.group(1).toLowerCase();
                boolean shopSells = label.equals("b") || label.equals("买") || label.equals("买入") || label.equals("出售") || label.equals("售");
                boolean shopBuys = label.equals("s") || label.equals("卖") || label.equals("卖出") || label.equals("收购") || label.equals("收");
                // Unlabeled "a : b" follows ChestShop order: first the shop-sells price, then the shop-buys price.
                if (label.isEmpty()) { if (unlabeled++ == 0) sell = value; else buy = value; }
                else if (shopSells) sell = value; else if (shopBuys) buy = value;
            }
        }
        if (buy == null && sell == null || (buy != null && buy > 1e9) || (sell != null && sell > 1e9)) return Optional.empty();
        Mode mode = buy != null && sell != null ? Mode.BOTH : sell != null ? Mode.SELL : Mode.BUY;
        return Optional.of(new Reading(world, x, y, z, owner, item, mode, quantity, buy, sell));
    }
    public static JsonArray jsonList(List<Reading> readings) { JsonArray list = new JsonArray(); readings.forEach(row -> list.add(row.json())); return list; }
    public static String modeLabel(Mode mode) { return switch (mode) { case SELL -> "出售"; case BUY -> "收购"; case BOTH -> "出售/收购"; }; }
    public static String priceLabel(Reading r) {
        String sell = r.sellPrice() == null ? "" : "出售 " + trim(r.sellPrice()), buy = r.buyPrice() == null ? "" : "收购 " + trim(r.buyPrice());
        return sell.isEmpty() ? buy : buy.isEmpty() ? sell : sell + " · " + buy;
    }
    private static String trim(double value) { return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value); }
}
