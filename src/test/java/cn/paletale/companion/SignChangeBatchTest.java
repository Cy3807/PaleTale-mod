package cn.paletale.companion;

import cn.paletale.companion.ShopTouchPolicy.Spot;

/** Changed shop signs are reported in calm batches, never one request per packet. */
final class SignChangeBatchTest {
    private SignChangeBatchTest() {}
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    static void run() {
        var batch = new SignChangeBatch();
        var a = new Spot(1, 64, 1); var b = new Spot(2, 64, 1);
        require(batch.take(1_000).isEmpty(), "Nothing changed, nothing to report");
        batch.add(a, 10_000); batch.add(b, 10_500); batch.add(a, 11_000);
        require(batch.take(12_900).isEmpty(), "A change waits a moment so that neighbouring changes arrive together");
        var first = batch.take(13_000);
        require(first.size() == 2 && first.get(0).equals(a) && first.get(1).equals(b), "The same sign twice counts once, in the order of arrival");
        require(batch.isEmpty() && batch.take(20_000).isEmpty(), "A batch is handed over once");
        batch.add(b, 14_000);
        require(batch.take(16_999).isEmpty() && batch.take(20_999).isEmpty(), "At most one batch every 8 seconds, however busy it is");
        require(batch.take(21_000).size() == 1, "The next batch comes when the pause is over");
        batch.add(a, 100_000);
        require(batch.take(103_000).size() == 1, "After a quiet time a change is reported after the short wait only");
        for (int i = 0; i < 1_000; i++) batch.add(new Spot(i, 64, 0), 200_000);
        require(batch.take(300_000).size() == 300, "A flood is capped");
        batch.add(a, 400_000); batch.clear();
        require(batch.isEmpty() && batch.take(500_000).isEmpty(), "Clearing (new world or account) drops what was waiting");
    }
}
