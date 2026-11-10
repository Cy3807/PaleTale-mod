package cn.paletale.companion;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.chat.Component;

final class MentionSoundClientTest {
    static void run(ClientGameTestContext context) {
        context.runOnClient(client -> {
            try {
                String me = LedgerStore.accountName();
                ModSettings.setChat("chatMentions", true);
                ModSettings.setChat("chatDoNotDisturb", false);
                PrivateChatClientTest.resetBell();
                var own = ChatAssist.decorate(Component.literal("[G] " + me + " : " + me));
                if (!own.getString().contains("@" + me) || PrivateChatClientTest.bell() == 0) throw new AssertionError("self mention must highlight and ring");
                PrivateChatClientTest.resetBell();
                ChatAssist.decorate(Component.literal("[G] Other : @" + me));
                if (PrivateChatClientTest.bell() == 0) throw new AssertionError("global mention must ring");
                PrivateChatClientTest.resetBell();
                var nation = ChatAssist.decorate(Component.literal("国家 测试王国 | ￼Other » @" + me));
                if (!nation.getString().contains("@" + me) || PrivateChatClientTest.bell() == 0) throw new AssertionError("nation mention immediately following global must ring");
            } catch (Exception e) { throw new AssertionError(e); }
        });
    }
}
