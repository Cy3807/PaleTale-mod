package cn.paletale.companion;

/**
 * One line of the local ledger. Stored only on this computer (never uploaded). {@code account} is the game account (UUID, no dashes) it was recorded on and
 * {@code accountName} its name then; {@code raw} is the server line it came from, kept for checking disputes.
 */
public record LedgerEntry(String id, long time, Kind kind, String currency, double amount, String counterparty, String item, int quantity,
                          Integer x, Integer y, Integer z, String adCode, String adTitle, String productName, String account, String accountName, String raw) {
    public boolean recordable() { return kind == Kind.SOLD_OUT || amount != 0.0; }
    public enum Kind {
        SHOP_SALE("商店出售", 1), SHOP_PURCHASE("商店购买", -1), SHOP_SELL_TO("卖给商店", 1), SOLD_OUT("商店脱销", 0), TRANSFER_OUT("转出", -1), TRANSFER_IN("转入", 1), TIP_IN("收到打赏", 1), TIP_OUT("打赏他人", -1), RED_PACKET_OUT("发红包", -1), RED_PACKET_IN("抢红包", 1),
        BANK_DEPOSIT("领地存入", -1), BANK_WITHDRAW("领地取出", 1),
        OFFICIAL_CROP("官方农作物", 1), OFFICIAL_MINERAL("官方矿物", 1), OFFICIAL_FISH("官方钓鱼", 1), OFFICIAL_OTHER("官方出售", 1), OFFICIAL_PURCHASE("官方购买", -1);
        public final String label; public final int sign;
        Kind(String label, int sign) { this.label = label; this.sign = sign; }
        /** 商店 / 玩家（转账、打赏、红包）/ 领地 group used by the screen's filter. */
        public String group() { return switch (this) { case SHOP_SALE, SHOP_PURCHASE, SHOP_SELL_TO, SOLD_OUT -> "shop"; case TRANSFER_OUT, TRANSFER_IN, TIP_IN, TIP_OUT, RED_PACKET_OUT, RED_PACKET_IN -> "player"; case BANK_DEPOSIT, BANK_WITHDRAW -> "bank"; case OFFICIAL_CROP, OFFICIAL_MINERAL, OFFICIAL_FISH, OFFICIAL_OTHER, OFFICIAL_PURCHASE -> "official"; }; }
    }
    /** Entries made by the parsers: no account or raw text yet (the store stamps them). */
    public LedgerEntry(String id, long time, Kind kind, String currency, double amount, String counterparty, String item, int quantity, Integer x, Integer y, Integer z, String adCode, String adTitle, String productName) {
        this(id, time, kind, currency, amount, counterparty, item, quantity, x, y, z, adCode, adTitle, productName, "", "", "");
    }
    /** Ledgers written by older versions lack some fields (Gson leaves them null). */
    public LedgerEntry normalized() {
        return new LedgerEntry(id, time, kind, currency == null ? "云币" : currency, amount, counterparty == null ? "" : counterparty, item == null ? "" : item, quantity, x, y, z,
                adCode == null ? "" : adCode, adTitle == null ? "" : adTitle, productName == null ? "" : productName, account == null ? "" : account, accountName == null ? "" : accountName, raw == null ? "" : raw);
    }
    public LedgerEntry withAd(String code, String title, String product) { return new LedgerEntry(id, time, kind, currency, amount, counterparty, item, quantity, x, y, z, code == null ? "" : code, title == null ? "" : title, product == null ? "" : product, account, accountName, raw); }
    public LedgerEntry withCounterparty(String name) { return new LedgerEntry(id, time, kind, currency, amount, name, item, quantity, x, y, z, adCode, adTitle, productName, account, accountName, raw); }
    public LedgerEntry withRaw(String text) { return new LedgerEntry(id, time, kind, currency, amount, counterparty, item, quantity, x, y, z, adCode, adTitle, productName, account, accountName, text == null ? "" : (text.length() > 200 ? text.substring(0, 200) : text)); }
    public LedgerEntry withAccount(String uuid, String name) { return new LedgerEntry(id, time, kind, currency, amount, counterparty, item, quantity, x, y, z, adCode, adTitle, productName, uuid, name, raw); }
    /** Remove only surrounding brackets when they leave one unambiguous Minecraft ID. */
    public static String shopPlayer(String value) {
        if(value==null)return "";String text=value.strip();int start=0,end=text.length();
        while(start<end&&"[【（(".indexOf(text.charAt(start))>=0)start++;
        while(end>start&&"]】）)".indexOf(text.charAt(end-1))>=0)end--;
        if(end-start<1||end-start>16)return "";
        for(int i=start;i<end;i++){char c=text.charAt(i);if(!(c>='A'&&c<='Z'||c>='a'&&c<='z'||c>='0'&&c<='9'||c=='_'))return "";}
        return start==0&&end==text.length()?text:text.substring(start,end);
    }
    public LedgerEntry safeShopCounterparty(){
        if(kind!=Kind.SHOP_SALE&&kind!=Kind.SHOP_PURCHASE&&kind!=Kind.SHOP_SELL_TO)return this;
        String safe=shopPlayer(counterparty);return java.util.Objects.equals(safe,counterparty)?this:withCounterparty(safe);
    }
    /** Signed amount: income positive, spending negative, 0 for events without money. */
    public double signed() { return amount * kind.sign; }
    /** Income and spending filters exclude non-money events such as sold-out notices. */
    public boolean matchesFlow(int flow) { return flow == 0 || flow == 1 && kind.sign > 0 || flow == 2 && kind.sign < 0; }
    /** Price of one item for shop trades (0 when there is no quantity). */
    public double unitPrice() { return quantity > 0 ? amount / quantity : 0; }
}
