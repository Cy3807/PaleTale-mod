package cn.paletale.companion;

import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;

/** A real integrated server changes a sign; the real update packet must reach the sign-update hook with the new text already applied on the client. No production account or data. */
final class SignUpdateClientGameTest {
    private SignUpdateClientGameTest() {}
    private static void require(boolean ok, String label) { if (!ok) throw new AssertionError(label); }
    private static String quantityLine(Object sign) { return ((SignBlockEntity) sign).getFrontText().getMessage(1, false).getString(); }

    static void run(ClientGameTestContext context) {
        var seen = new CopyOnWriteArrayList<BlockPos>();
        var textAtNotification = new CopyOnWriteArrayList<String>();
        try (var world = context.worldBuilder().create()) {
            BlockPos[] pos = new BlockPos[1];
            context.runOnClient(client -> {
                pos[0] = client.player.blockPosition().offset(3, 0, 3);
                SignUpdates.listen(changed -> {
                    seen.add(changed);
                    if (client.level.getBlockEntity(changed) instanceof SignBlockEntity sign) textAtNotification.add(quantityLine(sign));
                });
            });
            world.getServer().runOnServer(server -> {
                var level = server.overworld();
                level.setBlockAndUpdate(pos[0], Blocks.OAK_SIGN.defaultBlockState());
                var sign = (SignBlockEntity) level.getBlockEntity(pos[0]);
                sign.setText(sign.getFrontText().setMessage(0, Component.literal("ShopKeeper")).setMessage(1, Component.literal("64")).setMessage(2, Component.literal("S 10")).setMessage(3, Component.literal("Stone")), true);
            });
            context.waitTicks(40);
            seen.clear(); textAtNotification.clear();
            world.getServer().runOnServer(server -> {
                var sign = (SignBlockEntity) server.overworld().getBlockEntity(pos[0]);
                sign.setText(sign.getFrontText().setMessage(1, Component.literal("0")), true);
            });
            context.waitFor(client -> !seen.isEmpty(), 200);
            require(seen.contains(pos[0]), "The server changing a sign reaches the sign-update hook with that sign's position");
            require(!textAtNotification.isEmpty() && textAtNotification.getLast().equals("0"), "The hook runs after the client applied the new text, so reading the sign gives the new stock");
            System.out.println("PaleTale sign update hook: real update packet delivered in-game, new text readable at notification");
        } finally {
            SignUpdates.listen(changed -> {});
        }
    }
}
