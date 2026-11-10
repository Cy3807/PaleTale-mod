package cn.paletale.companion;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the server's chat lines (and boss-bar texts) into ledger entries. The messages are the SIMMC server's own wording, e.g.
 * 「Energy_Kasa 从你的商店里购买了 1 圆石，你因此赚得了 $0.95」, 「你位于 6622, 71, 6516, 的商店的 圆石 脱销了。」, 「$10已被发送到Cy3807。」, 「Cy3807收到了$10。」 and
 * the tip bar 「Energy_Kasa 向 Cy3807 打赏了（¥9191.00）」. Lines that do not match are ignored (the client keeps a few to help add new wordings).
 */
public final class LedgerParser {
    private LedgerParser() {}
    private static final String MONEY = "([$¥￥])\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)";
    private static final Pattern SALE = Pattern.compile("^(\\S+)\\s*从你的商店里?购买了\\s*(\\d+)\\s*(.+?)\\s*[，,]\\s*你因此赚得了\\s*" + MONEY);
    /** Buyer side (wording assumed from the sale line; harmless when the server never sends it). */
    private static final Pattern PURCHASE = Pattern.compile("^你从\\s*(\\S+?)\\s*的商店里?购买了\\s*(\\d+)\\s*(.+?)\\s*[，,]\\s*你因此(?:花费|支付)了\\s*" + MONEY);
    private static final Pattern SOLD_TO_ME = Pattern.compile("^(\\S+)\\s*向你的商店里?出售了\\s*(\\d+)\\s*(.+?)\\s*[，,]\\s*你因此(?:花费|支付)了\\s*" + MONEY);
    /** A player sold into the player's buying shop: 「Qing_Wuu 以 $1 的价格向你的商店出售了 1 个 深板岩。」 The price in the line is the total, not per item. */
    private static final Pattern SOLD_TO_MY_SHOP = Pattern.compile("^(\\S+)\\s*以\\s*" + MONEY + "\\s*的价格向你的商店里?出售了\\s*(\\d+)\\s*个?\\s*(.+?)\\s*[。.]?$");
    private static final Pattern SOLD_OUT = Pattern.compile("^你位于\\s*(-?\\d+)\\s*[,，]\\s*(-?\\d+)\\s*[,，]\\s*(-?\\d+)\\s*[,，]?\\s*的商店的\\s*(.+?)\\s*脱销了");
    /** Territory bank: 「领土>> 你成功在 kp 领土的银行存入了 $1.00.余额: $3.00」 and 「领土>> 你成功从 kp 领土的银行里取出了 $1.00.银行中还有: $3.00」. */
    private static final Pattern BANK_IN = Pattern.compile("^领土\\s*>>\\s*你成功在\\s*(\\S+)\\s*领土的银行(?:里)?存入了\\s*" + "([$¥￥])\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)(?:\\.?\\s*余额\\s*[:：]\\s*([$¥￥]\\s*[0-9][0-9,]*(?:\\.[0-9]{1,2})?))?");
    private static final Pattern BANK_OUT = Pattern.compile("^领土\\s*>>\\s*你成功从\\s*(\\S+)\\s*领土的银行(?:里)?取出了\\s*" + "([$¥￥])\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)(?:\\.?\\s*银行中还有\\s*[:：]\\s*([$¥￥]\\s*[0-9][0-9,]*(?:\\.[0-9]{1,2})?))?");
    /** A transfer may carry a remark: 「$10已被发送到zqiu233。 备注：test」. */
    private static final String REMARK = "(?:\\s*备注\\s*[:：]\\s*(.*?))?\\s*$";
    private static final Pattern SENT = Pattern.compile("^" + MONEY + "\\s*已被发送到\\s*(\\S+?)\\s*[。.]?" + REMARK);
    /** Red packets: sending 「已发送 100.00 的红包，共 1 份，附近 9 人可以参与。」, grabbing 「你抢到了 zqiu233 的红包，获得 23.13！」. */
    private static final Pattern RED_PACKET_SENT = Pattern.compile("^已发送\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*的红包\\s*[，,]\\s*共\\s*(\\d+)\\s*份");
    private static final Pattern RED_PACKET_GRABBED = Pattern.compile("你抢到了\\s*(\\S+?)\\s*的红包\\s*[，,]\\s*获得\\s*[$¥￥]?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)");
    /** Not anchored: the line may start with an icon or tag; the name just before 「收到了」 must be this player's. */
    private static final Pattern RECEIVED = Pattern.compile("(\\S+?)\\s*收到了\\s*" + MONEY + "\\s*[。.]?" + REMARK);
    /** Current wording: 「从 zqiu233 收到了 $100,000。」 names the sender, not the receiver. */
    private static final Pattern RECEIVED_FROM = Pattern.compile("从\\s*([A-Za-z0-9_]{1,16})\\s*收到了\\s*" + MONEY + "\\s*[。.]?" + REMARK);
    /** Small tips: the receiver reads 「玩家 X 向你打赏了 $100.00」, the giver 「你已向 X 打赏 100.00」 (no symbol: same currency as the receiver's line, $). */
    private static final Pattern TIP_TO_ME = Pattern.compile("^玩家\\s*(\\S+)\\s*向你打赏了\\s*([$¥￥])?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)");
    private static final Pattern TIP_FROM_ME = Pattern.compile("^你已向\\s*(\\S+)\\s*打赏\\s*了?\\s*([$¥￥])?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)");
    private static final Pattern TIP = Pattern.compile("^(\\S+)\\s*向\\s*(\\S+)\\s*打赏了\\s*[（(]\\s*([$¥￥])?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*[）)]");

    /** Removes colour codes, player-head glyphs, stray control characters and the "[hh:mm:ss]" some chat-timestamp mods put in front of every message. */
    public static String clean(String text) {
        return text == null ? "" : text.replaceAll("§.", "").replaceAll("[\\p{Cntrl}\\p{Co}]", "").replace(' ', ' ').strip().replaceFirst("^\\[\\d{1,2}:\\d{2}(?::\\d{2})?]\\s*", "");
    }
    /** The remark shown in the item column; empty when the transfer carried none. */
    private static String remark(String text) { return text == null || text.isBlank() ? "" : "备注：" + text.strip(); }
    private static double amount(String raw) { return Double.parseDouble(raw.replace(",", "")); }
    /** $ and ¥ are the same money on this server (云币); one name keeps the totals together. */
    static final String MONEY_NAME = "云币";
    private static String currency(String symbol) { return MONEY_NAME; }
    private static LedgerEntry entry(long now, LedgerEntry.Kind kind, String currency, double amount, String counterparty, String item, int quantity, Integer x, Integer y, Integer z) {
        return new LedgerEntry(UUID.randomUUID().toString(), now, kind, currency, amount, counterparty, item, quantity, x, y, z, "", "", "");
    }

    /** {@code me} is this player's own name: a received transfer or tip only counts when it names them. */
    public static Optional<LedgerEntry> parse(String message, String me, long now) {
        return parseInner(message, me, now).map(entry -> entry.safeShopCounterparty().withRaw(clean(message)));
    }
    private static Optional<LedgerEntry> parseInner(String message, String me, long now) {
        var official = LedgerOfficial.parse(message, now, java.util.Map.of());
        if (official.isPresent()) return official;
        String text = clean(message);
        if (text.isEmpty() || text.length() > 400) return Optional.empty();
        Matcher m = SALE.matcher(text);
        if (m.find()) return Optional.of(entry(now, LedgerEntry.Kind.SHOP_SALE, currency(m.group(4)), amount(m.group(5)), m.group(1), m.group(3), Integer.parseInt(m.group(2)), null, null, null));
        m = PURCHASE.matcher(text);
        if (m.find()) return Optional.of(entry(now, LedgerEntry.Kind.SHOP_PURCHASE, currency(m.group(4)), amount(m.group(5)), m.group(1), m.group(3), Integer.parseInt(m.group(2)), null, null, null));
        m = SOLD_TO_ME.matcher(text);
        if (m.find()) return Optional.of(entry(now, LedgerEntry.Kind.SHOP_PURCHASE, currency(m.group(4)), amount(m.group(5)), m.group(1), m.group(3), Integer.parseInt(m.group(2)), null, null, null));
        m = SOLD_TO_MY_SHOP.matcher(text);
        if (m.find()) { int quantity = Integer.parseInt(m.group(4)); return Optional.of(entry(now, LedgerEntry.Kind.SHOP_PURCHASE, currency(m.group(2)), amount(m.group(3)), m.group(1), m.group(5), quantity, null, null, null)); }
        m = BANK_IN.matcher(text);
        if (m.find()) return Optional.of(entry(now, LedgerEntry.Kind.BANK_DEPOSIT, currency(m.group(2)), amount(m.group(3)), m.group(1), m.group(4) == null ? "" : "余额 " + m.group(4).replaceAll("\\s+", ""), 0, null, null, null));
        m = BANK_OUT.matcher(text);
        if (m.find()) return Optional.of(entry(now, LedgerEntry.Kind.BANK_WITHDRAW, currency(m.group(2)), amount(m.group(3)), m.group(1), m.group(4) == null ? "" : "余额 " + m.group(4).replaceAll("\\s+", ""), 0, null, null, null));
        m = SOLD_OUT.matcher(text);
        if (m.find()) return Optional.of(entry(now, LedgerEntry.Kind.SOLD_OUT, "$", 0, "", m.group(4), 0, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))));
        m = SENT.matcher(text);
        if (m.find()) return Optional.of(entry(now, LedgerEntry.Kind.TRANSFER_OUT, currency(m.group(1)), amount(m.group(2)), m.group(3), remark(m.group(4)), 0, null, null, null));
        m = RECEIVED_FROM.matcher(text);
        if (m.find()) return Optional.of(entry(now, LedgerEntry.Kind.TRANSFER_IN, currency(m.group(2)), amount(m.group(3)), m.group(1), remark(m.group(4)), 0, null, null, null));
        m = RECEIVED.matcher(text);
        if (m.find() && !me.isEmpty() && m.group(1).toLowerCase().endsWith(me.toLowerCase())) return Optional.of(entry(now, LedgerEntry.Kind.TRANSFER_IN, currency(m.group(2)), amount(m.group(3)), "", remark(m.group(4)), 0, null, null, null));
        m = RED_PACKET_SENT.matcher(text);
        if (m.find()) return Optional.of(entry(now, LedgerEntry.Kind.RED_PACKET_OUT, MONEY_NAME, amount(m.group(1)), "", "共 " + m.group(2) + " 份", 0, null, null, null));
        m = RED_PACKET_GRABBED.matcher(text);
        if (m.find()) return Optional.of(entry(now, LedgerEntry.Kind.RED_PACKET_IN, MONEY_NAME, amount(m.group(2)), m.group(1), "", 0, null, null, null));
        m = TIP_TO_ME.matcher(text);
        if (m.find()) return Optional.of(entry(now, LedgerEntry.Kind.TIP_IN, currency(m.group(2)), amount(m.group(3)), m.group(1), "", 0, null, null, null));
        m = TIP_FROM_ME.matcher(text);
        if (m.find()) return Optional.of(entry(now, LedgerEntry.Kind.TIP_OUT, currency(m.group(2)), amount(m.group(3)), m.group(1), "", 0, null, null, null));
        m = TIP.matcher(text);
        if (m.find()) {
            boolean toMe = m.group(2).equalsIgnoreCase(me), fromMe = m.group(1).equalsIgnoreCase(me);
            if (toMe == fromMe) return Optional.empty(); // someone else's tip (or a tip to oneself)
            return Optional.of(entry(now, toMe ? LedgerEntry.Kind.TIP_IN : LedgerEntry.Kind.TIP_OUT, currency(m.group(3)), amount(m.group(4)), toMe ? m.group(1) : m.group(2), "", 0, null, null, null));
        }
        return Optional.empty();
    }
    private static final Pattern[] SOURCE = {
            Pattern.compile("^(?:玩家)?\\s*(\\S+?)\\s*(?:向你|给你)\\s*(?:转账|转了|汇款|转来|发送了)"),
            Pattern.compile("你(?:收到|已收到)了?\\s*(\\S+?)\\s*(?:的|发来的)?\\s*(?:转账|汇款)"),
            Pattern.compile("^(\\S+?)\\s*(?:转账|汇款)给你"),
    };
    /** Best effort: who sent a transfer, from a line shown just before or after the 「收到了$X」 line (the server's own wording is not known, so several shapes are tried). */
    public static Optional<String> transferSource(String message, String me) {
        String text = clean(message);
        for (Pattern pattern : SOURCE) {
            Matcher m = pattern.matcher(text);
            if (m.find() && !m.group(1).equalsIgnoreCase(me)) return Optional.of(m.group(1));
        }
        return Optional.empty();
    }
    /** Lines worth keeping for later wording updates: money or shop words that no pattern recognised. */
    public static boolean looksFinancial(String message) {
        String text = clean(message);
        return text.length() <= 200 && (text.contains("商店") || text.contains("领土的银行") || text.contains("转账") || text.contains("打赏") || text.contains("红包") || text.matches(".*[$¥￥]\\s*\\d.*"));
    }
}
