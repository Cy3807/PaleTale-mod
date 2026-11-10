package cn.paletale.companion;

import cn.paletale.companion.ShopTouchPolicy.Kind;
import cn.paletale.companion.ShopTouchPolicy.Spot;

/** Which chat lines mean "a shop sign was just used", and which sign they are about. */
final class ShopTouchPolicyTest {
    private ShopTouchPolicyTest() {}
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    static void run() {
        require(ShopTouchPolicy.classify("┃ 商店信息：") == Kind.INFO, "The shop block header is a click");
        require(ShopTouchPolicy.classify("商店信息：") == Kind.INFO, "The header without decoration too");
        require(ShopTouchPolicy.classify("支付成功：1 金胡萝卜 共 $1") == Kind.RESULT && ShopTouchPolicy.classify("出售成功：2 圆石 共 $1") == Kind.RESULT, "A finished trade is a result");
        require(ShopTouchPolicy.classify("┃ 库存:0") == Kind.NONE && ShopTouchPolicy.classify("<Bob> 支付成功") == Kind.NONE && ShopTouchPolicy.classify("") == Kind.NONE && ShopTouchPolicy.classify(null) == Kind.NONE, "Other lines, and a player typing the words, are ignored");

        var policy = new ShopTouchPolicy();
        var sign = new Spot(9787, 70, 6541);
        require(policy.choose(Kind.INFO, sign, 1_000).equals(sign), "A click reads the sign under the crosshair");
        require(policy.choose(Kind.INFO, null, 2_000).equals(sign), "The next lines of the same block stay with that sign");
        require(policy.choose(Kind.INFO, null, 9_000) == null, "A block with no sign in sight a while later is not guessed");
        require(policy.choose(Kind.RESULT, null, 20_000).equals(sign), "A payment belongs to the shop of the last block even when the player looked away");
        require(policy.choose(Kind.RESULT, null, 200_000) == null, "An old block is not blamed for a much later payment");
        var other = new Spot(1, 2, 3);
        require(policy.choose(Kind.RESULT, other, 300_000).equals(other), "With no earlier block the sign in sight is used");
        require(policy.choose(Kind.NONE, sign, 1) == null, "Other lines choose nothing");

        require(java.util.Arrays.equals(ShopTouchPolicy.delays(Kind.INFO), new long[] { 0, 1_500 }) && java.util.Arrays.equals(ShopTouchPolicy.delays(Kind.RESULT), new long[] { 1_200, 4_000 }) && ShopTouchPolicy.delays(Kind.NONE).length == 0, "Click: read now and again; trade: after the server updated the sign");
        require(policy.mayRead(sign, 1_000) && !policy.mayRead(sign, 1_500) && policy.mayRead(sign, 2_100) && policy.mayRead(other, 2_100), "One read per sign per second");
    }
}
