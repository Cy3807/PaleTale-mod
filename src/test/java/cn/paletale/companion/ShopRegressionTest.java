package cn.paletale.companion;

import java.util.List;

final class ShopRegressionTest {
    private static int checks;
    static void run() {
        var both = ShopSigns.parse("minecraft:overworld", 1, 64, 2, List.of("§aShopkeeper", "16", "B 50 : S 40", "钻石")).orElseThrow();
        require(both.owner().equals("Shopkeeper") && both.quantity() == 16 && both.itemName().equals("钻石"), "Basic sign fields parsed");
        require(both.sellPrice() == 50 && both.buyPrice() == 40 && both.mode() == ShopSigns.Mode.BOTH, "B is the shop-sell price and S the shop-buy price");
        var real = ShopSigns.parse("minecraft:overworld", -1115, 112, 26906, List.of("Cy3807", "出售 30", "圆石", "单价：$100")).orElseThrow();
        require(real.owner().equals("Cy3807") && real.quantity() == 30 && real.itemName().equals("圆石"), "Real SIMMC sign fields parsed");
        require(real.mode() == ShopSigns.Mode.SELL && real.sellPrice() == 100 && real.buyPrice() == null, "Labeled quantity sets the trade direction, single price follows it");
        var thousands = ShopSigns.parse("minecraft:overworld", 6622, 69, 6513, List.of("Cy3807", "出售 1", "全职高手丨十年赴荣耀", "单价：$1,000,000")).orElseThrow();
        require(thousands.sellPrice() == 1_000_000.0 && thousands.quantity() == 1 && thousands.itemName().equals("全职高手丨十年赴荣耀"), "Thousands separators in the price are ignored");
        var empty = ShopSigns.parse("w", 0, 0, 0, List.of("FIVFI", "缺货", "【SH小课堂】", "单价：$5,999")).orElseThrow();
        require(empty.quantity() == 0 && empty.mode() == ShopSigns.Mode.SELL && empty.sellPrice() == 5999.0, "Out-of-stock selling sign parsed with quantity 0");
        var full = ShopSigns.parse("w", 0, 0, 0, List.of("店主", "空间已满", "圆石", "单价：$2")).orElseThrow();
        require(full.quantity() == 0 && full.mode() == ShopSigns.Mode.BUY && full.buyPrice() == 2.0, "Full buying sign parsed with quantity 0");
        var buying = ShopSigns.parse("w", 0, 0, 0, List.of("店主", "收购 16", "绿宝石", "单价：¥2.5")).orElseThrow();
        require(buying.mode() == ShopSigns.Mode.BUY && buying.buyPrice() == 2.5 && buying.quantity() == 16, "Buying sign parsed");
        require(ShopSigns.parse("w", 0, 0, 0, List.of("", "", "", "")).isEmpty() && ShopSigns.parse("w", 0, 0, 0, List.of("wd", "", "", "")).isEmpty(), "Empty and stray signs ignored");
        var buyOnly = ShopSigns.parse("w", 0, 0, 0, List.of("店主", "64", "S 2.5", "铁锭")).orElseThrow();
        require(buyOnly.mode() == ShopSigns.Mode.BUY && buyOnly.buyPrice() == 2.5 && buyOnly.sellPrice() == null, "Shop-buy-only sign parsed");
        var plain = ShopSigns.parse("w", 0, 0, 0, List.of("店主", "4", "10:5", "苹果")).orElseThrow();
        require(plain.sellPrice() == 10 && plain.buyPrice() == 5, "Unlabeled prices follow sell-then-buy order");
        require(ShopSigns.parse("w", 0, 0, 0, List.of("店主", "x", "B 5", "苹果")).isEmpty(), "Missing quantity rejected");
        require(ShopSigns.parse("w", 0, 0, 0, List.of("", "4", "B 5", "苹果")).isEmpty(), "Missing owner rejected");
        require(ShopSigns.parse("w", 0, 0, 0, List.of("店主", "4", "B 5", "")).isEmpty(), "Missing item rejected");
        require(both.sameTrade(both) && !both.sameTrade(buyOnly), "Trade comparison");
        var p=new ShopApi.Position("w",1,2,3);
        var lines=new java.util.ArrayList<>(List.of("ShopKeeper","64","B 10","Stone"));
        var sample=new ShopScanData.Sample(p,lines,"x+",null,0);lines.set(1,"0");
        var first=ShopScanData.changed(List.of(sample),1000);
        require(first.size()==1&&first.getFirst().quantity()==64&&first.getFirst().hasChest(),"Background parsing uses an immutable snapshot including chest direction");
        var cached=new ShopScanData.Sample(p,sample.lines(),"x+",first.getFirst(),1000);
        require(ShopScanData.changed(List.of(cached),2000).isEmpty(),"Unchanged snapshots do not upload each cycle");
        require(ShopScanData.changed(List.of(cached),302000).size()==1,"The five-minute heartbeat remains");
        require(ShopScanData.changed(List.of(new ShopScanData.Sample(p,List.of("ShopKeeper","0","B 10","Stone"),"x+",first.getFirst(),1000)),2000).size()==1,"Out of stock is still a change");
        require(ShopScanData.changed(List.of(new ShopScanData.Sample(p,List.of("bad"),null,null,0)),2000).isEmpty(),"A removed shop or malformed sign is ignored");
        require(ShopScanData.changed(java.util.Collections.nCopies(100,sample),1000).size()==64,"Background reports remain bounded to 64 signs");
        var near=new ShopApi.Position("w",0,0,0);var far=new ShopApi.Position("w",48,0,0);var high=new ShopApi.Position("w",0,49,0);
        require(ShopScanData.nearby(List.of(far,high,near,new ShopApi.Position("other",0,0,0),near),"w",.5,.5,.5).equals(List.of(near,far)),"Selection preserves the inclusive 48-block sphere, rejects another world and deduplicates bound positions");
        var dense=new java.util.ArrayList<ShopApi.Position>();for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)dense.add(new ShopApi.Position("w",x,0,z));
        require(ShopScanData.nearby(dense,"w",.5,.5,.5).size()==400,"Dense bound shops are capped before world reads");
        System.out.println("PaleTale shop regression checks passed: " + checks);
    }
    private static void require(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
