package cn.paletale.companion;

final class LedgerRegressionTest {
    private static int checks;
    private static LedgerEntry parse(String text, String me) { return LedgerParser.parse(text, me, 1L).orElse(null); }
    static void run() {
        official();
        var saleDollar = parse("商店 成功出售 蘑菇 ★★★，获得 4,194$!", "Cy3807");
        require(saleDollar != null && saleDollar.amount() == 4194 && saleDollar.quantity() == 18, "Actual dollar suffix in server sale receipt is accepted");
        require(parse("商店 成功出售 水晶番茄 ★★★，获得 4,203$!", "Cy3807").quantity() == 9 && parse("商店 成功出售 金锭，获得 2,000$!", "Cy3807").kind() == LedgerEntry.Kind.OFFICIAL_MINERAL, "Exact crop and ore dollar receipts");
        require(parse("商店 购买成功，花费 39!", "Cy3807").amount() == 39, "Purchases with hidden currency glyph still record spending");
        require(parse("商店 购买成功，花费 0!", "Cy3807").kind() == LedgerEntry.Kind.OFFICIAL_PURCHASE, "Confirmed free purchases are also separate receipts");
        var arcane=parse("§a已花费 100000 金币, 将 引力术 升级到 Lv5", "Cy3807");
        require(arcane!=null&&arcane.kind()==LedgerEntry.Kind.OFFICIAL_PURCHASE&&arcane.amount()==100000&&arcane.signed()==-100000&&arcane.item().contains("引力术"),"Arcane upgrade is an official expense");
        require(parse("<Bob> 已花费 100000 金币, 将 引力术 升级到 Lv5", "Cy3807")==null&&parse("已花费 100000 金币, 将 引力术 升级失败", "Cy3807")==null,"Player chat and failed upgrades do not become receipts");
        long t = java.time.Instant.parse("2026-10-21T15:59:59Z").toEpochMilli();
        var a = LedgerOfficial.parse("商店 成功出售 蘑菇 ★★★，获得 4,194$!", t, java.util.Map.of()).orElseThrow().withAccount("a", "Alice");
        var b = LedgerOfficial.parse("商店 成功出售 水稻 ★★★，获得 4,194$!", t - 1000, java.util.Map.of()).orElseThrow().withAccount("a", "Alice");
        var nextDay = LedgerOfficial.parse("商店 成功出售 蘑菇 ★★★，获得 4,194$!", t + 1000, java.util.Map.of()).orElseThrow().withAccount("a", "Alice");
        var buyA = LedgerOfficial.parse("商店 购买成功，花费 39!", t, java.util.Map.of()).orElseThrow();
        var buyB = LedgerOfficial.parse("商店 购买成功，花费 39!", t, java.util.Map.of()).orElseThrow();
        var view = LedgerOfficialDaily.group(java.util.List.of(a,b,nextDay,a.withAccount("b","Bob"),buyA,buyB));
        require(view.rows().size() == 5 && view.rows().getFirst().amount() == 8388 && view.members().get(view.rows().getFirst().id()).size() == 2, "Daily income groups by Beijing day, category and account while purchases remain separate");
        require(view.rows().stream().mapToDouble(LedgerEntry::signed).sum() == a.signed()+b.signed()+nextDay.signed()+a.signed()+buyA.signed()+buyB.signed(), "Daily view preserves exact totals and original members for deletion/details");
        try {
            String raw="商店 成功出售 蘑菇 ★★★，获得 4,194$!";
            String log="[01:00:00] [Render thread/INFO]: Setting user: Alice\n[01:00:01] [Render thread/INFO]: Connecting to play.simmc.cn, 25565\n[01:00:01] [Render thread/WARN]: Client disconnected with reason: 已转移至另一个服务器\n[01:00:01] [Render thread/INFO]: Connecting to 198.51.100.8, 7777\n[01:00:01] [Render thread/INFO]: [PlasmoVoice] Connecting to 198.51.100.8:37421\n[01:00:02] [Render thread/INFO]: [CHAT] "+raw+"\n[01:00:02] [Render thread/INFO]: [CHAT] "+raw+"\n";
            var recovered=LedgerReceiptRecovery.replay(new java.io.BufferedReader(new java.io.StringReader(log)),"2026-10-21-1.log.gz",java.time.LocalDate.of(2026,10,21),java.time.ZoneOffset.UTC,"a","Alice",java.util.Set.of(raw),java.util.List.of());
            require(recovered.size()==2 && !recovered.get(0).id().equals(recovered.get(1).id()), "Dated lost receipts recover distinct identical transactions without guessing from quotas");
            require(LedgerReceiptRecovery.replay(new java.io.BufferedReader(new java.io.StringReader(log)),"2026-10-21-1.log.gz",java.time.LocalDate.of(2026,10,21),java.time.ZoneOffset.UTC,"a","Bob",java.util.Set.of(raw),java.util.List.of()).isEmpty(), "Recovery refuses logs belonging to another account");
            require(LedgerReceiptRecovery.replay(new java.io.BufferedReader(new java.io.StringReader(log)),"2026-10-21-1.log.gz",java.time.LocalDate.of(2026,10,21),java.time.ZoneOffset.UTC,"a","Alice",java.util.Set.of(raw),recovered).isEmpty(), "Recovery is idempotent against persisted receipt IDs");
        } catch(java.io.IOException e) { throw new AssertionError(e); }
        var sale = parse("Energy_Kasa 从你的商店里购买了 1 圆石，你因此赚得了 $0.95", "Cy3807");
        require(sale != null && sale.kind() == LedgerEntry.Kind.SHOP_SALE && sale.counterparty().equals("Energy_Kasa") && sale.item().equals("圆石") && sale.quantity() == 1 && sale.amount() == 0.95 && sale.currency().equals("云币"), "Shop sale parsed (head glyph ignored)");
        require(sale.signed() == 0.95, "A sale is income");
        var big = parse("Energy_Kasa 从你的商店里购买了 12 全职高手丨十年赴荣耀，你因此赚得了 $1,000,000.50", "Cy3807");
        require(big != null && big.quantity() == 12 && big.item().equals("全职高手丨十年赴荣耀") && big.amount() == 1_000_000.5, "Thousands separators and decorated item names");
        var out = parse("你位于 6622, 71, 6516, 的商店的 圆石 脱销了。", "Cy3807");
        require(out != null && out.kind() == LedgerEntry.Kind.SOLD_OUT && out.x() == 6622 && out.y() == 71 && out.z() == 6516 && out.item().equals("圆石") && out.signed() == 0, "Out-of-stock notice carries the chest position");
        var sent = parse("$10已被发送到Cy3807。", "Bob");
        require(sent != null && sent.kind() == LedgerEntry.Kind.TRANSFER_OUT && sent.counterparty().equals("Cy3807") && sent.amount() == 10 && sent.signed() == -10, "Sent transfer");
        var received = parse("Cy3807收到了$10。", "Cy3807");
        require(received != null && received.kind() == LedgerEntry.Kind.TRANSFER_IN && received.amount() == 10 && received.signed() == 10, "Received transfer names the player");
        require(parse("Cy3807收到了$10。", "Bob") == null, "Somebody else's receipt is not mine");
        var fromSender = parse("\uE001 从zqiu233收到了$100,000。", "Cy3807");
        require(fromSender != null && fromSender.kind() == LedgerEntry.Kind.TRANSFER_IN && fromSender.amount() == 100000 && fromSender.counterparty().equals("zqiu233"), "「从X收到了」 is a transfer from X");
        var tagged = parse("\uE001 [转账] Cy3807收到了$10。", "Cy3807");
        require(tagged != null && tagged.kind() == LedgerEntry.Kind.TRANSFER_IN && tagged.amount() == 10, "A received transfer with an icon or tag in front still counts");
        var sentNote = parse("$10已被发送到zqiu233。 备注：test", "Bob");
        require(sentNote != null && sentNote.kind() == LedgerEntry.Kind.TRANSFER_OUT && sentNote.counterparty().equals("zqiu233") && sentNote.amount() == 10 && sentNote.item().equals("备注：test"), "A sent transfer with a remark is recorded with the remark");
        var gotNote = parse("\uE001 从zqiu233收到了$100。 备注：两份疯狂星期四", "Bob");
        require(gotNote != null && gotNote.kind() == LedgerEntry.Kind.TRANSFER_IN && gotNote.counterparty().equals("zqiu233") && gotNote.amount() == 100 && gotNote.item().equals("备注：两份疯狂星期四"), "A received transfer with a remark is recorded with the remark");
        var packetOut = parse("已发送 100.00 的红包，共 1 份，附近 9 人可以参与。", "Bob");
        require(packetOut != null && packetOut.kind() == LedgerEntry.Kind.RED_PACKET_OUT && packetOut.amount() == 100 && packetOut.signed() == -100 && packetOut.item().equals("共 1 份") && packetOut.kind().group().equals("player"), "Sending a red packet");
        var packetIn = parse("你抢到了 \uE002zqiu233 的红包，获得 23.13！", "Bob");
        require(packetIn != null && packetIn.kind() == LedgerEntry.Kind.RED_PACKET_IN && packetIn.amount() == 23.13 && packetIn.counterparty().equals("zqiu233") && packetIn.signed() == 23.13, "Grabbing a red packet");
        var tip = parse("Energy_Kasa 向 Cy3807 打赏了（¥9191.00）", "Cy3807");
        require(tip != null && tip.kind() == LedgerEntry.Kind.TIP_IN && tip.currency().equals("云币") && tip.amount() == 9191 && tip.counterparty().equals("Energy_Kasa"), "Received tip");
        var given = parse("Cy3807 向 Energy_Kasa 打赏了（¥50.00）", "Cy3807");
        require(given != null && given.kind() == LedgerEntry.Kind.TIP_OUT && given.counterparty().equals("Energy_Kasa") && given.signed() == -50, "Given tip");
        require(parse("A 向 B 打赏了（¥5.00）", "Cy3807") == null, "Other people's tips are ignored");
        var tips = new TipDedup();
        var chatTip = parse("你已向 Ccmi_ 打赏 50000.00", "Beibei10");
        var bossTip = parse("Beibei10 向 Ccmi_ 打赏了（¥50000.00）", "Beibei10");
        require(!tips.duplicate(chatTip, false, 1000) && tips.duplicate(bossTip, true, 1500), "A large tip seen as chat line and boss bar is one entry");
        require(!tips.duplicate(chatTip, false, 3000) && !tips.duplicate(chatTip, false, 3100) , "Repeated chat tips of the same amount are real repeats");
        require(!new TipDedup().duplicate(bossTip, true, 1) && !tips.duplicate(parse("你已向 Ccmi_ 打赏 100.00", "Beibei10"), false, 20000), "Different amounts or later tips are not merged");
        var small = parse("玩家 \uE002TipherеA 向你打赏了 $100.00", "Cy3807");
        require(small != null && small.kind() == LedgerEntry.Kind.TIP_IN && small.amount() == 100 && small.currency().equals("云币") && small.counterparty().startsWith("Tiph"), "Small tip received (chat line, head glyph ignored)");
        var smallOut = parse("你已向 \uE002TipherеA 打赏 100.00", "Cy3807");
        require(smallOut != null && smallOut.kind() == LedgerEntry.Kind.TIP_OUT && smallOut.amount() == 100 && smallOut.currency().equals("云币") && smallOut.signed() == -100, "Small tip given (action bar line without a symbol)");
        var deposit = parse("领土>>  你成功在 kp 领土的银行存入了 $1.00.余额: $3.00", "Cy3807");
        require(deposit != null && deposit.kind() == LedgerEntry.Kind.BANK_DEPOSIT && deposit.counterparty().equals("kp") && deposit.amount() == 1 && deposit.signed() == -1 && deposit.item().equals("余额 $3.00"), "Territory bank deposit (wallet money goes out)");
        var withdraw = parse("领土>>  你成功从 kp 领土的银行里取出了 $1.00.银行中还有: $3.00", "Cy3807");
        require(withdraw != null && withdraw.kind() == LedgerEntry.Kind.BANK_WITHDRAW && withdraw.counterparty().equals("kp") && withdraw.signed() == 1 && withdraw.item().equals("余额 $3.00"), "Territory bank withdrawal (money comes back)");
        require(parse("领土>> 你成功在 kp 领土的银行存入了 $1,250.50", "Cy3807").amount() == 1250.5, "Deposit without a balance tail and with separators");
        require(LedgerParser.transferSource("玩家 Bob 向你转账了 $5", "Cy3807").orElse("").equals("Bob") && LedgerParser.transferSource("你收到了 Bob 的转账", "Cy3807").orElse("").equals("Bob") && LedgerParser.transferSource("早上好", "Cy3807").isEmpty(), "A transfer's sender is found when a nearby line names them");
        require(parse("<Cy3807> 你好", "Cy3807") == null && parse("", "Cy3807") == null, "Ordinary chat is ignored");
        require(LedgerParser.looksFinancial("你的商店出现了新的变化 $5") && !LedgerParser.looksFinancial("早上好"), "Unrecognised money lines are worth keeping");
        // Trading in someone else's shop: the result line is attributed to the latest shop block, other chat in between does not matter.
        var talk = new LedgerShopTalk();
        String[] bought = { "┃ 商店信息：", "┃ 所有者： yishimi_ng_5", "┃ 物品：金胡萝卜    [物品预览]", "┃ 库存:40", "┃ 每个金胡萝卜价格$1", "┃ 这个商店现在正在出售物品。", "<Bob> hi", "┃ 支付成功：", "┃ 1 金胡萝卜 共 $1" };
        LedgerEntry made = null;
        for (String line : bought) { var got = talk.feed(line, 1000L); if (got.isPresent()) made = got.get(); }
        require(made != null && made.kind() == LedgerEntry.Kind.SHOP_PURCHASE && made.counterparty().equals("yishimi_ng_5") && made.item().equals("金胡萝卜") && made.quantity() == 1 && made.amount() == 1 && made.signed() == -1, "Bought from someone's shop (owner from the latest shop block)");
        var decoratedOwner=new LedgerShopTalk();decoratedOwner.feed("所有者：[Cy3807", 1000);var decoratedSale=decoratedOwner.feed("支付成功：1 圆石 共 $100", 1100).orElseThrow();require(decoratedSale.counterparty().equals("Cy3807"),"shop owner bracket safely removed");
        require(LedgerEntry.shopPlayer("[Cy_3807]").equals("Cy_3807")&&LedgerEntry.shopPlayer("[VIP]Bad").isEmpty()&&LedgerEntry.shopPlayer("a-b").isEmpty(),"only unambiguous valid player IDs accepted");
        var parsedShop=LedgerParser.parse("[Seller_1] 从你的商店里购买了 1 圆石，你因此赚得了 $0.95","Cy3807",1000).orElseThrow();require(parsedShop.counterparty().equals("Seller_1"),"direct live and log parser uses clean shop identity");
        var original=decoratedSale.withCounterparty("[Cy3807");require(original.safeShopCounterparty().counterparty().equals("Cy3807")&&original.counterparty().equals("[Cy3807"),"legacy correction does not mutate original entry");
        String[] sold = { "商店信息：", "所有者：yishimi_ng_5", "物品：金胡萝卜  [物品预览]", "空间:3417", "每个金胡萝卜价格$1", "这个商店现在正在收购物品。", "[系统] 欢迎", "出售成功：", "1 金胡萝卜 共 $1" };
        made = null;
        for (String line : sold) { var got = talk.feed(line, 2000L); if (got.isPresent()) made = got.get(); }
        require(made != null && made.kind() == LedgerEntry.Kind.SHOP_SELL_TO && made.counterparty().equals("yishimi_ng_5") && made.quantity() == 1 && made.signed() == 1, "Sold into a buying shop (income)");
        var oneLine = new LedgerShopTalk().feed("支付成功： 2 圆石 共 $3.50", 5000L);
        require(oneLine.isPresent() && oneLine.get().quantity() == 2 && oneLine.get().amount() == 3.5 && oneLine.get().counterparty().isEmpty(), "Result on one line, shop unknown");
        require(new LedgerShopTalk().feed("1 金胡萝卜 共 $1", 6000L).isEmpty(), "A result line without a 支付成功 / 出售成功 header is ignored");
        var soldIn = parse("Qing_Wuu 以 $1 的价格向你的商店出售了 1 个 深板岩。", "Cy3807");
        require(soldIn != null && soldIn.kind() == LedgerEntry.Kind.SHOP_PURCHASE && soldIn.counterparty().equals("Qing_Wuu") && soldIn.item().equals("深板岩") && soldIn.quantity() == 1 && soldIn.amount() == 1 && soldIn.signed() == -1 && soldIn.unitPrice() == 1, "A player sold into my buying shop (I pay the total)");
        var soldMany = parse("NanyuSama 以 $255 的价格向你的商店出售了 255 个 深板岩。", "Cy3807");
        require(soldMany != null && soldMany.amount() == 255 && soldMany.quantity() == 255 && soldMany.unitPrice() == 1, "Buying-shop line gives the total (255 for 255 items, unit 1)");
        long now = 40L * 86_400_000L, day = 86_400_000L;
        var soldOut = new LedgerEntry("o", now, LedgerEntry.Kind.SOLD_OUT, "云币", 0, "", "圆石", 0, 1, 2, 3, "", "", "");
        java.util.function.BiFunction<Long, Integer, LedgerEntry> saleAt = (age, count) -> new LedgerEntry("s" + age, now - age, LedgerEntry.Kind.SHOP_SALE, "云币", count, "Bob", "圆石", count, null, null, null, "", "", "");
        var pace = LedgerSales.of(java.util.List.of(saleAt.apply(day, 10), saleAt.apply(3 * day, 4), saleAt.apply(20 * day, 6), saleAt.apply(35 * day, 99)), soldOut, now);
        require(pace != null && pace.week() == 14 && pace.month() == 20 && Math.abs(pace.perDay() - 20 / 21.0) < 0.001, "Sales pace counts only the last 30 days (week 14, month 20)");
        require(LedgerSales.of(java.util.List.of(saleAt.apply(35 * day, 5)), soldOut, now) == null, "No pace without recent sales");
        var dailyDay = java.time.LocalDate.of(2026, 10, 19); var dailyZone = java.time.ZoneId.of("Asia/Shanghai");
        long dailyNoon = dailyDay.atTime(12, 0).atZone(dailyZone).toInstant().toEpochMilli();
        java.util.function.BiFunction<Integer, Double, LedgerEntry> dailySale = (qty, total) -> new LedgerEntry("d" + qty, dailyNoon, LedgerEntry.Kind.SHOP_SALE, "云币", total, "Bob", "圆石", qty, null, null, null, "", "", "");
        var other = new LedgerEntry("o", dailyNoon + 86_400_000L, LedgerEntry.Kind.SHOP_SALE, "云币", 99, "Bob", "金", 9, null, null, null, "", "", "");
        var paid = new LedgerEntry("p", dailyNoon, LedgerEntry.Kind.TRANSFER_OUT, "云币", 10, "Bob", "", 0, null, null, null, "", "", "");
        var daily = LedgerDaily.summarize(java.util.List.of(dailySale.apply(3, 3.0), dailySale.apply(5, 5.0), other, paid), dailyDay, dailyZone);
        require(daily != null && daily.income() == 8 && daily.spent() == 10 && daily.count() == 3 && daily.topItem().equals("圆石") && daily.topQuantity() == 8 && daily.net() == -2, "Daily summary counts only that dailyDay (Beijing time)");
        require(LedgerDaily.summarize(java.util.List.of(other), dailyDay, dailyZone) == null, "A dailyDay without records has no summary");
        var wines = WineEffectIndex.parse("{\"version\":1,\"wines\":[[\"维波里丝窖藏-永恒旷野\",\"迅捷 II\",\"speed\",\"\",412],[\"同名异料\",\"力量 III\",\"strength\",\"minecraft:potion\",413],[\"某酒\",\"未知\",\"unknown\",\"\",null],[\"自定义\",\"幸运\",\"luck\",\"srv:wine/a\",null],[\"烙印酒\",\"烙印\",\"brand\",\"\",555,\"&c玩家1\\n&l留言\"]]}");
        var byMaterial = wines.effectOf(true, "minecraft:potion", 412, "完全不同的名字");
        require(byMaterial != null && byMaterial.key().equals("speed"), "A potion is a wine by its custom model data (the real wine items are potions with only that number), not by its name");
        require(wines.effectOf(true, "minecraft:potion", 413, "").key().equals("strength") && wines.effectOf(true, "minecraft:potion", 999, "维波里丝窖藏—永恒旷野") == null, "Another number is another wine; a name does not override a recorded material");
        require(wines.effectOf(false, "minecraft:diamond_leggings", 412, "钻石护腿") == null && wines.effectOf(false, "minecraft:potion", 412, "") == null, "Only a potion can be a wine (a diamond leggings with the same number is not)");
        require(wines.effectOf(true, "minecraft:potion", null, "") == null && wines.effectOf(true, "minecraft:potion", 7, "") == null, "A plain potion, or one with an unknown number, is not a wine");
        require(wines.effectOf(true, "srv:wine/a", null, "").key().equals("luck") && wines.effectOf(true, "srv:wine/a", 7, "").key().equals("luck"), "A server item model identifies its wine");
        var branded = wines.effectOf(true, "minecraft:potion", 555, "");
        require(branded != null && branded.key().equals("brand") && branded.brand().equals("&c玩家1\n&l留言") && byMaterial.brand().isEmpty(), "The brand text comes with a 烙印 wine");
        var unnamed = wines.effectOf(true, "minecraft:potion", null, "某酒");
        require(unnamed != null && unnamed.text().equals("未知"), "A wine with no recorded material falls back to its name and shows 未知");
        require(java.util.Arrays.equals(NavTargets.parse("6622 / 71 / 6516"), new int[]{ 6622, 6516, 71 }) && java.util.Arrays.equals(NavTargets.parse("-346, -7309"), new int[]{ -346, -7309 }) && NavTargets.parse("没有坐标") == null && NavTargets.parse("") == null, "Coordinates in the site's format, with or without Y");
        var navPoints = NavPoints.parse("{\"version\":1,\"hubs\":[[\"a\",\"起点港\",10,0,\"sea\",1,10,80000],[\"b\",\"终点港\",19990,0,\"sea\",0,10,4500],[\"c\",\"别线驿站\",19980,0,\"east\",1,10,60000]],\"warps\":[[\"酒吧\",19000,0]]}");
        var far = NavPlanner.plan(0, 0, 20000, 0, navPoints);
        require(far.steps().get(1).command() != null && far.steps().get(1).command().startsWith("port travel ") && far.steps().get(1).command().split(" ").length == 4, "A trip step carries the /port travel command with the map ids of both ports");
        require(NavPlanner.HUB_REACH == 15.0, "A hub counts as reached within 15 blocks, inside the 20 the map allows");
        require(!far.direct() && far.steps().size() == 3 && far.steps().get(1).kind() == NavPlanner.Kind.TRAVEL && far.steps().get(1).label().contains("终点港") && far.next().kind() == NavPlanner.Kind.TRAVEL && NavPlanner.plan(-100, 0, 20000, 0, navPoints).next().label().contains("起点港"), "A far target is reached by walking to a hub (10 blocks from it already counts as there), travelling to the hub near it and walking on");
        require(far.seconds() < far.directDistance() / NavPlanner.WALK * 0.5 && far.nearestPort().hub().id().equals("b") && far.nearestStation().hub().id().equals("c") && far.nearestWarp().name().equals("酒吧"), "The plan is faster than walking and names the nearest port, station and warp");
        var after = NavPlanner.plan(19990, 0, 20000, 0, navPoints);
        require(after.direct() && after.next().kind() == NavPlanner.Kind.WALK && after.next().distance() == 10, "After arriving the plan is just the last walk");
        require(NavPlanner.plan(0, 0, 20000, 0, NavPoints.parse("{\"version\":1,\"hubs\":[[\"a\",\"起点港\",10,0,\"sea\",1,10,80000],[\"c\",\"别线驿站\",19980,0,\"east\",1,10,60000]],\"warps\":[]}")).direct(), "A hub of another line group is not a way there");
        // Going home to a territory that has a port: the hubs near the player cannot reach the target, the one inside the player's land can.
        var homePoints = NavPoints.parse("{\"version\":1,\"hubs\":[[\"d1\",\"近处驿站一\",20,0,\"east\",1,10,4000],[\"d2\",\"近处驿站二\",40,0,\"east\",1,10,4000],[\"d3\",\"近处驿站三\",60,0,\"east\",1,10,4000],[\"d4\",\"近处驿站四\",80,0,\"east\",1,10,4000],[\"d5\",\"近处驿站五\",100,0,\"east\",1,10,4000],[\"d6\",\"近处驿站六\",120,0,\"east\",1,10,4000],[\"d7\",\"近处驿站七\",140,0,\"east\",1,10,4000],[\"d8\",\"近处驿站八\",160,0,\"east\",1,10,4000],[\"home\",\"家港\",15000,0,\"sea\",1,10,80000],[\"far\",\"终点港\",19990,0,\"sea\",0,10,4500]],\"warps\":[]}");
        var withoutLand = NavPlanner.plan(0, 0, 20000, 0, homePoints);
        require(withoutLand.steps().stream().noneMatch(st->st.kind()==NavPlanner.Kind.HOME), "Without a territory, even a usable port beyond the eight closest departures is considered");
        var withLand = NavPlanner.plan(0, 0, 20000, 0, homePoints, java.util.List.of(new NavPlanner.Land("我的领地", "某国", 14950, 20, java.util.List.of("home")).withSpawn(new int[]{15000,0})));
        require(!withLand.direct() && withLand.steps().get(0).kind() == NavPlanner.Kind.HOME && withLand.steps().get(0).label().contains("我的领地") && withLand.next().kind() == NavPlanner.Kind.HOME
                && withLand.steps().get(2).kind() == NavPlanner.Kind.TRAVEL && withLand.steps().get(2).label().contains("终点港") && withLand.seconds() < 200 && withLand.guide().kind() == NavPlanner.Kind.WALK, "Going home to a territory with a port and leaving from there (the guide points at the walk after the teleport)");
        require(NavPlanner.plan(14960, 10, 20000, 0, homePoints, java.util.List.of(new NavPlanner.Land("我的领地", "某国", 14950, 20, java.util.List.of("home")).withSpawn(new int[]{15000,0}))).steps().stream().noneMatch(st -> st.kind() == NavPlanner.Kind.HOME), "No trip home when already there");
        // Ownership cannot make a slower teleport beat the faster RP route.
        var ownPoints = NavPoints.parse("{\"version\":1,\"hubs\":[[\"home\",\"家港\",-500,0,\"sea\",1,10,80000],[\"end\",\"终点港\",4990,0,\"sea\",0,10,4500]],\"warps\":[[\"别人家\",4960,0]]}");
        var ownLand = java.util.List.of(new NavPlanner.Land("我的领地", "某国", -500, 20, java.util.List.of("home")).withSpawn(new int[]{-500,0}));
        require(NavPlanner.plan(0, 0, 5000, 0, ownPoints, ownLand).next().kind() == NavPlanner.Kind.WARP, "A faster warp beats a slower home-plus-port route");
        // In another world only teleports help; choose the faster complete route.
        var elsewhere = NavPlanner.planElsewhere(5000, 0, ownPoints, ownLand);
        require(elsewhere != null && elsewhere.next().kind() == NavPlanner.Kind.WARP && elsewhere.next().command().equals("warp 别人家") && !elsewhere.direct(), "From another world, the faster complete teleport route wins");
        var elsewhereWarp = NavPlanner.planElsewhere(5000, 0, ownPoints, java.util.List.of());
        require(elsewhereWarp != null && elsewhereWarp.next().kind() == NavPlanner.Kind.WARP && NavPlanner.planElsewhere(5000, 0, NavPoints.EMPTY, java.util.List.of()) == null, "Without a territory a warp is used, and with nothing to use there is no plan");
        var warpPoints = NavPoints.parse("{\"version\":1,\"hubs\":[],\"warps\":[[\"远处酒吧\",29000,0],[\"近处酒吧\",50,0]]}");
        var warped = NavPlanner.plan(0, 0, 30000, 0, warpPoints);
        require(!warped.direct() && warped.steps().get(0).kind() == NavPlanner.Kind.WARP && warped.steps().get(0).label().contains("远处酒吧") && warped.seconds() < 15 + 1000 / NavPlanner.WALK + 1 && warped.next().action(), "A warp to an RP point near the target beats walking 30000 blocks");
        require(NavPlanner.plan(0, 0, 3000, 0, warpPoints).steps().stream().noneMatch(st -> st.kind() == NavPlanner.Kind.WARP) || NavPlanner.plan(0, 0, 3000, 0, warpPoints).seconds() < 3000 / NavPlanner.WALK * 0.8 + 1, "A warp is only suggested when it saves real time");
        require(NavPlanner.scale(new NavPoints.Hub("a", "x", 0, 0, "sea", true, 10, 4500)).equals("小型") && NavPlanner.scale(new NavPoints.Hub("a", "x", 0, 0, "sea", true, 15, 8500)).equals("中型") && NavPlanner.scale(new NavPoints.Hub("a", "x", 0, 0, "east", true, 20, 20000)).equals("大型"), "Hub sizes follow the use distance 10 / 15 / 20");
        var menuEntries = LandEntries.parse(java.util.List.of(
                java.util.List.of("灰色染色玻璃板"),
                java.util.List.of("kp", "你拥有这个领土.", "• 国家: 怀特墨斯自由联邦-苍白王国", "• 区块数: 25 / 120", "✖ 左键点击", "管理这个领土.", "✖ SHIFT + 左键点击", "选择为/lands edit 领土."),
                java.util.List.of("苍澜郡", "你在这个领土中被信任.", "• 国家: 怀特墨斯自由联邦-苍白王国", "• 认领区块: 1810 / 1810"),
                java.util.List.of("苍澜郡", "你在这个领土中被信任.", "• 国家: 怀特墨斯自由联邦-苍白王国"),
                java.util.List.of("下一页", "点击翻页")));
        require(menuEntries.size() == 2 && menuEntries.get(0).name().equals("kp") && menuEntries.get(0).owned() && menuEntries.get(0).nation().equals("怀特墨斯自由联邦-苍白王国")
                && menuEntries.get(1).name().equals("苍澜郡") && !menuEntries.get(1).owned(), "The /l menu items that are territories are found, with owner/trusted and nation; fillers and buttons are not");
        // Never prefer the slower home route, including when it has a port.
        var preferPoints = NavPoints.parse("{\"version\":1,\"hubs\":[[\"near\",\"近港\",25,0,\"sea\",1,10,80000],[\"homeport\",\"家港\",9000,0,\"sea\",1,10,80000],[\"end\",\"终点港\",19600,0,\"sea\",0,10,4500]],\"warps\":[]}");
        var withoutHome = NavPlanner.plan(0, 0, 20000, 0, preferPoints);
        var preferHome = NavPlanner.plan(0, 0, 20000, 0, preferPoints, java.util.List.of(new NavPlanner.Land("我的领地", "某国", 8950, 30, java.util.List.of("homeport")).withSpawn(new int[]{9000,0})));
        require(withoutHome.steps().get(0).kind() == NavPlanner.Kind.WALK && preferHome.steps().get(0).kind() == NavPlanner.Kind.WALK && preferHome.seconds() <= withoutHome.seconds(), "Adding a home option cannot replace a faster current route with a slower one");
        require(NavPlanner.plan(0, 0, 100, 0, preferPoints, java.util.List.of(new NavPlanner.Land("我的领地", "某国", 8950, 30, java.util.List.of("homeport")))).steps().get(0).kind() == NavPlanner.Kind.WALK, "A short trip is walked, not teleported home");
        var learned = NavPlanner.plan(0, 0, 20000, 0, preferPoints, java.util.List.of(new NavPlanner.Land("我的领地", "某国", 8950, 30, java.util.List.of("homeport")).withSpawn(new int[]{ 19650, 10 })));
        require(learned.steps().get(0).kind() == NavPlanner.Kind.HOME && learned.steps().get(0).x() == 19650 && learned.seconds() > 85 && learned.seconds() < 95, "A learned home spawn is used instead of the hub");
        var landList = NavLands.parse("{\"version\":1,\"lands\":[[\"天\",\"海间\",5150,24300,[\"fuermaniya\"]],[\"白渚\",\"苍白\",6776,6344,[\"baigang\"],6790,6350,6700,6300,6900,6400]]}");
        require(landList.size() == 2 && landList.get(0).spawn() == null && landList.get(1).spawn()[0] == 6790 && landList.get(1).spawn()[1] == 6350 && landList.get(1).near(6750, 6350, 0) && !landList.get(1).near(7500, 6350, 50) && landList.get(0).near(1, 1, 0), "A reported spawn point and the land's box come with it");
        require(landList.size() == 2 && landList.get(0).name().equals("天") && landList.get(0).hubIds().equals(java.util.List.of("fuermaniya")) && landList.get(0).x() == 5150, "The site's territories parse");
        var shopTarget = NavTargets.of(new MarketData.Ad("c", "标题", "", "active", java.util.List.of(), new MarketData.Author("a", "a", "", 1),
                java.util.List.of(new MarketData.Product("圆石", "", "", "active", 0, null, "", "", "", "", false, java.util.List.of(new MarketData.Shop(new ShopSigns.Reading("overworld", 100, 64, 200, "o", "圆石", ShopSigns.Mode.SELL, 5, null, 1.0, "x+"), "")))),
                java.util.List.of(new MarketData.Location("主城", "10 / 70 / 20", "")), "", "", "", 0, 0)).stream().toList();
        require(shopTarget.size() == 2 && shopTarget.get(0).name().equals("标题") && shopTarget.get(0).marks().size() == 1 && shopTarget.get(0).marks().get(0)[1] == 70
                && shopTarget.get(1).name().equals("标题 · 圆石") && shopTarget.get(1).marks().size() == 2 && shopTarget.get(1).marks().get(1)[0] == 101, "A shop target lights up its sign and chest");
        require(NavTargets.fit("苍白王国大市场开业 · 圆石", 14).equals("苍白王国大市场开业 · 圆石") && NavTargets.fit("苍白王国大市场开业一二三四 · 圆石", 14).equals("苍白王国大市场开业一二三... · 圆石") && NavTargets.fit("一二三四五六七八九十甲乙丙丁戊己 · 一二三四五六七八九十", 14).equals("一二三四五六七... · 一二三四五六七...") && NavTargets.fit("苍白王国大市场开业一二三", 14).equals("苍白王国大市场开业一二三") && NavTargets.cut("abc苍", 3).equals("abc苍") && NavTargets.cut("abc苍", 2).equals("abc..."), "A name is cut to the room it has: the shorter part keeps what it needs");
        require(NavTargets.ofAd(new MarketData.Ad("c", "标题", "", "active", java.util.List.of(), new MarketData.Author("a", "a", "", 1), java.util.List.of(), java.util.List.of(new MarketData.Location("无坐标", "没有", ""), new MarketData.Location("主城", "10 / 70 / 20", ""), new MarketData.Location("次", "1, 2", "")), "", "", "", 0, 0)).x() == 10, "An ad goes to the first address with coordinates");
        System.out.println("PaleTale ledger regression checks passed: " + checks);
    }
    private static void official() {
        var soy = parse("商店 成功出售 大豆 \uE001\uE001\uE002，获得 186\uE010", "Cy3807");
        require(soy != null && soy.kind() == LedgerEntry.Kind.OFFICIAL_CROP && soy.amount() == 186 && soy.quantity() == 3 && soy.unitPrice() == 62 && soy.item().equals("大豆 · 2星"), "Official soybean screenshot: 186 is three at 62");
        var gold = parse("商店 成功出售 金锭，获得 2,000\uE010", "Cy3807");
        require(gold != null && gold.kind() == LedgerEntry.Kind.OFFICIAL_MINERAL && gold.quantity() == 20 && gold.unitPrice() == 100, "Official gold screenshot: 20 ingots at 100");
        var buy = parse("商店 购买成功，花费 10,000金币!", "Cy3807");
        require(buy != null && buy.kind() == LedgerEntry.Kind.OFFICIAL_PURCHASE && buy.signed() == -10000 && buy.item().isEmpty() && buy.quantity() == 0, "Official purchase records spending without inventing an item");
        String fishing = "您从鱼中赚了600.00金！今天您还可以从市场中\n获得9400.00金";
        var fish = parse(fishing, "Cy3807");
        var remaining = LedgerOfficial.fishing("a", fishing, 1).orElseThrow();
        require(fish != null && fish.kind() == LedgerEntry.Kind.OFFICIAL_FISH && fish.amount() == 600 && remaining.used() == 9400 && remaining.limit() == 10000, "Fish transaction and total value quota stay distinct");
        require(parse("<Bob> 商店 购买成功，花费 10,000金币!", "Cy3807") == null && parse("商店 购买失败，花费 10,000金币!", "Cy3807") == null, "Chat and failed purchases cannot become official entries");
        require(parse("商店 成功出售 大豆，获得 496", "Cy3807").quantity() == 0, "Ambiguous soybean total never invents quantity or tier");
        int[][] prices = { {8,62,156}, {12,93,233}, {23,187,467} };
        int[][] limits = { {108,54,27}, {72,36,18}, {36,18,9} };
        String[] items = { "大豆", "蘑菇", "水晶番茄" };
        for (int i = 0; i < items.length; i++) for (int tier = 1; tier <= 3; tier++) {
            var product = LedgerOfficial.product(items[i] + " · " + tier + "星");
            require(product.price() == prices[i][tier-1] && product.limit() == limits[i][tier-1], "Screenshot crop price/quota " + product.key());
        }
        for (String item : java.util.List.of("玉米", "茄子", "水稻")) for (int tier = 1; tier <= 3; tier++) {
            var product = LedgerOfficial.product(item + " · " + tier + "星");
            require(product.price() == (item.equals("水稻") ? prices[1] : prices[0])[tier-1] && product.limit() == (item.equals("水稻") ? limits[1] : limits[0])[tier-1], "Shared crop price and quota " + product.key());
        }
        require(LedgerOfficial.product("铁锭").price() == 25 && LedgerOfficial.product("铁锭").limit() == 999, "Iron screenshot price and quota");
        String title = "大豆 \uE001\uE001\uE002";
        var reading = LedgerOfficial.tooltip("a", title, java.util.List.of("出售: 62$", "玩家出售限制: 3/54"), 1).orElseThrow();
        var exact = LedgerOfficial.parse("商店 成功出售 " + title + "，获得496\uE010", 1, java.util.Map.of(reading.name(), reading.product())).orElseThrow();
        require(exact.quantity() == 8 && exact.item().equals("大豆 · 2星"), "Learned shop glyphs resolve a total that was ambiguous");
        require(LedgerOfficial.tooltip("a", "大豆", java.util.List.of("出售: 62$", "玩家出售限制: 3/9999999999999999999999"), 1).isEmpty(), "Malformed quota cannot crash a menu scan");
        long before = java.time.Instant.parse("2026-10-21T15:59:59Z").toEpochMilli(), after = before + 2000;
        var entries = java.util.List.of(exact.withAccount("a", "A"), new LedgerEntry("b", after, gold.kind(), "云币", 2000, "官方", "金锭", 20, null, null, null, "", "", "").withAccount("a", "A"));
        require(LedgerOfficial.recorded(entries, "a", "金锭", LedgerOfficial.quotaDay(before)) == 0 && LedgerOfficial.recorded(entries, "a", "金锭", LedgerOfficial.quotaDay(after)) == 20 && LedgerOfficial.recorded(entries, "b", "金锭", LedgerOfficial.quotaDay(after)) == 0, "Daily quantities reset at Beijing midnight and stay separated by account");
        var explicit = parse("商店 成功出售 玉米 ★★✰，获得 496", "Cy3807");
        require(explicit.quantity() == 8 && explicit.item().equals("玉米 · 2星"), "Exported filled and hollow stars disambiguate the exact crop quality");
        long today = java.time.Instant.parse("2026-10-21T10:00:00Z").toEpochMilli();
        var capped = new LedgerEntry("full", today, LedgerEntry.Kind.OFFICIAL_CROP, "云币", 864, "官方", "大豆 · 1星", 108, null, null, null, "", "", "").withAccount("a", "A");
        var quotas = LedgerOfficial.quotas(java.util.List.of(capped), java.util.List.of(new LedgerOfficial.Reading("a", today, "", "钓鱼", 0, 10000), new LedgerOfficial.Reading("b", today, "", "金锭", 999, 999)), "a", LedgerOfficial.quotaDay(today));
        require(quotas.stream().filter(q -> q.key().equals("大豆 · 1星")).findFirst().orElseThrow().full() && quotas.stream().filter(q -> q.key().equals("钓鱼")).findFirst().orElseThrow().full(), "A full crop quantity and zero remaining fish value are both complete");
        require(!quotas.stream().filter(q -> q.key().equals("金锭")).findFirst().orElseThrow().full(), "Other accounts cannot turn a quota green");
        var reset = LedgerOfficial.quotas(java.util.List.of(capped), java.util.List.of(new LedgerOfficial.Reading("a", today, "", "钓鱼", 0, 10000)), "a", LedgerOfficial.quotaDay(today).plusDays(1));
        require(reset.stream().noneMatch(LedgerOfficial.Quota::full), "The next Beijing midnight resets full-quota status");
        require(LedgerOfficial.quotaDay(java.time.Instant.parse("2026-10-21T03:59:59Z").toEpochMilli()).equals(LedgerOfficial.quotaDay(java.time.Instant.parse("2026-10-21T04:00:01Z").toEpochMilli())), "Noon does not reset the midnight-based official quota");
        require(!LedgerOfficial.quotaDay(before).equals(LedgerOfficial.quotaDay(after)), "The exact 00:00 Beijing boundary starts a new quota period");
        require(buy.matchesFlow(2) && !buy.matchesFlow(1) && soy.matchesFlow(1) && !soy.matchesFlow(2) && soy.matchesFlow(0), "Income and spending remain independently filterable, including official purchases");
        require(!new LedgerEntry("event", today, LedgerEntry.Kind.SOLD_OUT, "云币", 0, "", "", 0, null, null, null, "", "", "").matchesFlow(1), "Non-money events do not enter the income filter");
        var gson = new com.google.gson.Gson();
        require(gson.fromJson(gson.toJson(exact), LedgerEntry.class).normalized().kind() == LedgerEntry.Kind.OFFICIAL_CROP && gson.fromJson(gson.toJson(reading), LedgerOfficial.Reading.class).product().equals(reading.product()), "Official entries and quota snapshots round-trip through local JSON");
    }
    private static void require(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
