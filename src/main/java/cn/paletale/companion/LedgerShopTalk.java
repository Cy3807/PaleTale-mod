package cn.paletale.companion;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trading in someone else's chest shop: the server prints a 「商店信息」 block (owner, item, stock or space, unit price, whether the shop sells or buys), the player types an amount,
 * and later 「支付成功：1 金胡萝卜 共 $1」 (bought) or 「出售成功：…」 (sold into a buying chest) appears. Other chat may come in between, so the latest shop block is remembered and
 * the result line is attributed to it. Feed it line by line.
 */
public final class LedgerShopTalk {
    private static final Pattern OWNER = Pattern.compile("^所有者\\s*[:：]\\s*(\\S+)");
    private static final Pattern ITEM = Pattern.compile("^物品\\s*[:：]\\s*(.+?)\\s*(?:\\[物品预览\\])?\\s*$");
    private static final Pattern MODE = Pattern.compile("^这个商店现在正在(出售|收购)物品");
    private static final Pattern RESULT_HEAD = Pattern.compile("^(支付成功|出售成功)\\s*[:：]?\\s*(.*)$");
    private static final Pattern RESULT = Pattern.compile("^(\\d+)\\s*(.+?)\\s*共\\s*[$¥￥]\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)");
    private static final long INFO_KEEP_MS = 10 * 60_000L, RESULT_WAIT_MS = 8_000L;

    private String owner = "", item = "";
    private long infoAt;
    private boolean pendingBuy, hasPending;
    private long pendingAt;

    /** Removes the decoration glyphs the server puts in front of its lines (frame bars) and squeezes spaces. */
    static String strip(String raw) {
        String text = LedgerParser.clean(raw).replaceAll("^[^\\p{L}\\p{N}$¥￥]+", "").replaceAll("\\s+", " ").strip();
        return text;
    }

    public Optional<LedgerEntry> feed(String raw, long now) {
        String line = strip(raw);
        if (line.isEmpty()) return Optional.empty();
        if (line.startsWith("商店信息")) { owner = ""; item = ""; infoAt = now; return Optional.empty(); }
        Matcher m = OWNER.matcher(line);
        if (m.find()) { owner = LedgerEntry.shopPlayer(m.group(1)); infoAt = now; return Optional.empty(); }
        m = ITEM.matcher(line);
        if (m.find() && line.startsWith("物品")) { item = m.group(1); infoAt = now; return Optional.empty(); }
        m = MODE.matcher(line);
        if (m.find()) { infoAt = now; return Optional.empty(); }
        m = RESULT_HEAD.matcher(line);
        if (m.find()) {
            pendingBuy = m.group(1).equals("支付成功"); hasPending = true; pendingAt = now;
            String rest = m.group(2).strip();
            return rest.isEmpty() ? Optional.empty() : result(rest, now);
        }
        if (hasPending && now - pendingAt < RESULT_WAIT_MS) return result(line, now);
        return Optional.empty();
    }

    private Optional<LedgerEntry> result(String line, long now) {
        Matcher m = RESULT.matcher(line);
        if (!m.find()) return Optional.empty();
        hasPending = false;
        String who = now - infoAt < INFO_KEEP_MS ? owner : "";
        double amount = Double.parseDouble(m.group(3).replace(",", ""));
        return Optional.of(new LedgerEntry(UUID.randomUUID().toString(), now, pendingBuy ? LedgerEntry.Kind.SHOP_PURCHASE : LedgerEntry.Kind.SHOP_SELL_TO, LedgerParser.MONEY_NAME, amount, who, m.group(2), Integer.parseInt(m.group(1)), null, null, null, "", "", "").withRaw((pendingBuy ? "支付成功：" : "出售成功：") + line));
    }
}
