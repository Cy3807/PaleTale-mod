package cn.paletale.companion;

import java.util.ArrayList;
import java.util.List;

/** The shops this player bound (kept in memory after login): lets the ledger tell which ad a shop message belongs to. */
final class ShopIndex {
    private static volatile List<ShopApi.MyShop> shops = List.of();
    private ShopIndex() {}
    static void set(List<ShopApi.MyShop> next) { shops = List.copyOf(next); }
    static List<ShopApi.MyShop> all() { return shops; }
    private static String name(String value) { return value == null ? "" : value.replaceAll("[\\s_]+", "").toLowerCase(); }

    /** The shop whose chest (or sign) is at this position; chat gives the chest position, the sign sits next to it. */
    static ShopApi.MyShop at(int x, int y, int z) {
        for (var shop : shops) {
            var chest = shop.chest();
            if (chest != null && chest.x() == x && chest.y() == y && chest.z() == z) return shop;
        }
        for (var shop : shops) {
            var sign = shop.sign();
            if (Math.abs(sign.x() - x) <= 1 && Math.abs(sign.y() - y) <= 1 && Math.abs(sign.z() - z) <= 1 && shop.chest() == null) return shop;
        }
        return null;
    }
    /** Distinct ad products that trade this item (a sale message names only the item). */
    static List<ShopApi.MyShop> forItem(String item) {
        String wanted = name(item);
        List<ShopApi.MyShop> found = new ArrayList<>();
        if (wanted.isEmpty()) return found;
        for (var shop : shops) {
            String own = name(shop.itemName());
            if (own.isEmpty() || !(own.equals(wanted) || own.length() >= 2 && wanted.length() >= 2 && (own.contains(wanted) || wanted.contains(own)))) continue;
            boolean seen = false;
            for (var other : found) if (other.adCode().equals(shop.adCode()) && other.productIndex() == shop.productIndex()) { seen = true; break; }
            if (!seen) found.add(shop);
        }
        return found;
    }
}
