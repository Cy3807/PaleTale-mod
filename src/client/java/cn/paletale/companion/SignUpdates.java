package cn.paletale.companion;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;

/** Where the sign-update mixin hands over the position of a block entity the server has just changed (set once by the client at start-up). */
public final class SignUpdates {
    private static volatile Consumer<BlockPos> sink = pos -> {};
    private SignUpdates() {}
    static void listen(Consumer<BlockPos> next) { sink = next; }
    public static void changed(BlockPos pos) { sink.accept(pos); }
}
