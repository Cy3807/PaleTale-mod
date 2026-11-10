package cn.paletale.companion;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The mod's only setting: which entry of the PaleTale server to talk to. Needs Cloth Config. */
final class SiteSettingsScreen {
    private SiteSettingsScreen() {}
    enum Choice {
        MAIN("主站（www.paletale.cn）"), BACKUP("备用站（pt.cy3807.com）");
        final String label;
        Choice(String label) { this.label = label; }
    }
    static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create().setParentScreen(parent).setTitle(Component.literal("PaleTale · 设置"));
        ConfigCategory general = builder.getOrCreateCategory(Component.literal("常规"));
        ConfigEntryBuilder entries = builder.entryBuilder();
        general.addEntry(entries.startEnumSelector(Component.literal("站点"), Choice.class, SiteConfig.backup() ? Choice.BACKUP : Choice.MAIN)
                .setDefaultValue(Choice.MAIN)
                .setEnumNameProvider(value -> Component.literal(((Choice) value).label))
                .setTooltip(Component.literal("主站无法访问时改用备用站，两者是同一台服务器，账号与数据相同。\n游戏内也可输入 /pt net 来回切换。"))
                .setSaveConsumer(choice -> {
                    java.net.URI target = choice == Choice.BACKUP ? SiteConfig.BACKUP : SiteConfig.MAIN;
                    // A developer on the local test site keeps it unless they really pick the other entry.
                    if (!target.equals(SiteConfig.current()) && !(SiteConfig.local() && choice == Choice.MAIN)) PaleTaleClient.useSite(target);
                })
                .build());
        return builder.build();
    }
}
