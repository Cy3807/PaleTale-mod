package cn.paletale.companion;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/** Narrow, multi-line information panel using the user's scoreboard display rules. */
public final class PaleScoreboard {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("paletale", "scoreboard");
    private PaleScoreboard() {}
    static void register() { HudElementRegistry.addLast(ID, (g, tracker) -> render(g)); }
    private static Component text(String value, ChatFormatting color) { return Component.literal(value).withStyle(color); }
    private static Component heading(String icon, String label, boolean emphasis) {
        return emphasis ? Component.literal(icon + " " + label).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
                : text(icon + " " + label, ChatFormatting.WHITE);
    }
    private static Component detail(String label, String value, ChatFormatting color, String suffix) {
        return text("  " + label, ChatFormatting.GRAY).copy().append(text(value, color)).append(text(suffix, ChatFormatting.GRAY));
    }
    private static void separator(ArrayList<Component> lines, boolean compact) { if (!compact && !lines.isEmpty()) lines.add(Component.empty()); }
    /** Compact duration for the single next-time line: 5h, 5h20m, 35m, <1m. */
    private static String compact(long millis) {
        long minutes = Math.max(0, (millis + 59_999L) / 60_000L);
        if (minutes < 1) return "<1m";
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60, rest = minutes % 60;
        return hours + "h" + (rest == 0 ? "" : rest + "m");
    }
    private static String compactSpan(long minMillis, long maxMillis) {
        if (maxMillis <= 0) return "<1m";
        if (minMillis <= 0) return "≤" + compact(maxMillis);
        String min = compact(minMillis), max = compact(maxMillis);
        return min.equals(max) ? min : min + "-" + max;
    }
    private static String monthDay(int month, int day) { return month + "." + (day < 10 ? "0" : "") + day; }
    private static void monsoon(ArrayList<Component> lines, Minecraft client) {
        lines.add(heading("❋", "季风", true));
        var tab = client.gui.getTabList();
        String text = (tab.header == null ? "" : tab.header.getString()) + "\n" + (tab.footer == null ? "" : tab.footer.getString());
        var date = YunMonsoon.parse(text);
        if (date.isEmpty()) {
            lines.add(detail("⌛ ", "等待 Yun 历", ChatFormatting.GRAY, ""));
            return;
        }
        long now = System.currentTimeMillis();
        long elapsed = YunWatch.elapsed(date.get(), now);
        long dayStart = elapsed < 0 ? -1 : now - elapsed;
        var estimate = YunMonsoon.estimate(date.get(), now, dayStart);
        boolean running = estimate.state() == YunMonsoon.State.IN_PROGRESS;
        String status = running ? "正在进行" : "未开始";
        lines.add(text("  ⌛ ", ChatFormatting.GRAY).copy().append(text("目前 ", ChatFormatting.GRAY))
                .append(text(status, running ? ChatFormatting.GREEN : ChatFormatting.RED)));
        // While an event is running, keep showing that event and its remaining time; the next event
        // only replaces it after this one ends.
        YunMonsoon.Slot shown;
        String time;
        if (running) {
            shown = estimate.slot();
            time = estimate.event().durationKnown() ? "剩余 " + compactSpan(estimate.minRemainingMillis(), estimate.maxRemainingMillis()) : "剩余待确认";
        } else {
            shown = YunMonsoon.next(date.get());
            var window = YunMonsoon.until(shown, now, dayStart);
            time = compactSpan(window.minMillis(), window.maxMillis());
        }
        lines.add(detail("⌚ ", monthDay(shown.event().month(), shown.event().day()) + " (" + time + ")", ChatFormatting.WHITE, ""));
        for (String port : shown.event().ports().split("、")) if (!port.isBlank()) lines.add(detail("⌂ ", port, ChatFormatting.WHITE, ""));
    }
    /** One row of the panel: left text, an optional right-aligned value (server score), optionally centred. */
    private record Row(FormattedCharSequence left, FormattedCharSequence right, boolean centered) {
        static final Row BLANK = new Row(FormattedCharSequence.EMPTY, null, false);
    }
    /** The objective vanilla would draw as the sidebar: the player's team-colour slot first, then the plain sidebar slot. */
    private static Objective serverObjective(Minecraft client) {
        if (client.level == null || client.player == null) return null;
        var scoreboard = client.level.getScoreboard();
        var team = scoreboard.getPlayersTeam(client.player.getScoreboardName());
        if (team != null) {
            var slot = DisplaySlot.teamColorToSlot(team.getColor());
            if (slot != null) { var objective = scoreboard.getDisplayObjective(slot); if (objective != null) return objective; }
        }
        return scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
    }
    /** True when the server sidebar is currently folded into the PaleTale panel (vanilla then skips drawing it). */
    public static boolean mergesServerSidebar() {
        var client = Minecraft.getInstance();
        return !client.options.hideGui && LocalRoster.trustedServer(client) && ModSettings.scoreboard("enabled") && ModSettings.mergeServerScoreboard() && serverObjective(client) != null;
    }
    /** True when the text has something a player can read: a letter, digit, punctuation or symbol. Spaces, zero-width and private-use spacer glyphs do not count. */
    private static boolean visible(String text) {
        // Servers pad their sidebar with rows made only of stray section signs (the colour-code lead character).
        return text.replaceAll("\u00a7[0-9a-fk-orA-FK-OR]?", "").codePoints().anyMatch(c -> c != 0xA7 && Character.isLetterOrDigit(c) || switch (Character.getType(c)) {
            case Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION, Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION,
                 Character.FINAL_QUOTE_PUNCTUATION, Character.OTHER_PUNCTUATION, Character.MATH_SYMBOL, Character.CURRENCY_SYMBOL, Character.MODIFIER_SYMBOL, Character.OTHER_SYMBOL -> true;
            default -> false; });
    }
    /** Server rows as vanilla lists them: highest score first, hidden entries dropped, at most 15, team prefixes and suffixes applied. */
    private static List<Row> serverRows(Minecraft client, Objective objective) {
        var scoreboard = objective.getScoreboard();
        var format = objective.numberFormatOrDefault(net.minecraft.network.chat.numbers.StyledFormat.SIDEBAR_DEFAULT);
        var rows = new ArrayList<Row>();
        // The server's own title is replaced by a fixed yellow SIMMC; one blank row separates it from the content.
        rows.add(new Row(text("SIMMC", ChatFormatting.YELLOW).getVisualOrderText(), null, true));
        rows.add(Row.BLANK);
        scoreboard.listPlayerScores(objective).stream().filter(entry -> !entry.isHidden())
                .sorted(java.util.Comparator.comparingInt(net.minecraft.world.scores.PlayerScoreEntry::value).reversed()
                        .thenComparing(net.minecraft.world.scores.PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER.reversed()))
                .limit(15).forEach(entry -> {
                    var team = scoreboard.getPlayersTeam(entry.owner());
                    Component name = net.minecraft.world.scores.PlayerTeam.formatNameForTeam(team, entry.ownerName());
                    Component score = entry.formatValue(format);
                    // Servers pad their sidebar with empty spacer lines; the merged panel drops them to stay compact.
                    if (!visible(name.getString()) && !visible(score.getString())) return;
                    rows.add(new Row(name.getVisualOrderText(), score.getString().isEmpty() ? null : score.getVisualOrderText(), false));
                });
        return rows;
    }
    private static void render(GuiGraphics g) {
        var client = Minecraft.getInstance();
        if (client.options.hideGui || !LocalRoster.trustedServer(client) || !ModSettings.scoreboard("enabled")) return;
        Objective server = ModSettings.mergeServerScoreboard() ? serverObjective(client) : null;
        var serverRows = server == null ? List.<Row>of() : serverRows(client, server);
        // With server content on the panel PaleTale's own block is packed without spacer rows.
        boolean compact = !serverRows.isEmpty();
        var lines = new ArrayList<Component>();
        if (ModSettings.scoreboard("monsoon")) monsoon(lines, client);
        if (ModSettings.scoreboard("favorites")) {
            separator(lines, compact); lines.add(heading("★", "收藏", false));
            int online = PaleTaleClient.FAVORITE_PRESENCE.onlineCount(), shops = NoticeWatch.shopPreviewCount();
            lines.add(online < 0 ? detail("玩家在线  ", "待获取", ChatFormatting.GRAY, "") : detail("玩家在线  ", String.valueOf(online), ChatFormatting.GREEN, " 人"));
            lines.add(shops < 0 ? detail("广告动态  ", "待获取", ChatFormatting.GRAY, "")
                    : shops == 0 ? detail("广告动态  ", "预览无动态", ChatFormatting.GRAY, "")
                    : detail("广告动态  至少 ", String.valueOf(shops), ChatFormatting.WHITE, " 条"));
        }
        if (ModSettings.scoreboard("social")) {
            separator(lines, compact); lines.add(heading("●", "社交", false));
            int unread = PrivateChatStore.totalUnread();
            lines.add(unread < 0 ? detail("未读消息  ", "待获取", ChatFormatting.GRAY, "")
                    : detail("未读消息  ", String.valueOf(unread), unread > 0 ? ChatFormatting.RED : ChatFormatting.WHITE, " 条"));
        }
        if (lines.isEmpty() && serverRows.isEmpty()) return;
        int maxWidth = Math.max(40, Math.min(140, g.guiWidth() - 20));
        var rows = new ArrayList<Row>();
        // Server section first, unchanged; PaleTale's own block follows under its title.
        if (!serverRows.isEmpty()) {
            rows.addAll(serverRows);
            if (!lines.isEmpty()) rows.add(Row.BLANK);
        }
        var title = Component.literal("PaleTale").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
        if (!lines.isEmpty()) {
            rows.add(new Row(title.getVisualOrderText(), null, true));
            if (!compact) rows.add(Row.BLANK); // One blank line after the title.
            for (var line : lines) {
                if (line.getString().isEmpty()) rows.add(Row.BLANK);
                else for (var part : client.font.split(line, maxWidth)) rows.add(new Row(part, null, false));
            }
        }
        rows.add(Row.BLANK); // One blank row under the last module.
        int width = 0, gap = client.font.width(": ");
        for (var row : rows) width = Math.max(width, client.font.width(row.left()) + (row.right() == null ? 0 : gap + client.font.width(row.right())));
        int lineHeight = 11, totalHeight = rows.size() * lineHeight;
        int x = Math.max(4, g.guiWidth() - width - 10), y = Math.max(2, (g.guiHeight() - totalHeight) / 2);
        g.fill(x - 4, y - 3, g.guiWidth() - 4, Math.min(g.guiHeight(), y + totalHeight), 0x70000000);
        for (var row : rows) {
            int left = row.centered() ? x + (width - client.font.width(row.left())) / 2 : x;
            g.drawString(client.font, row.left(), left, y, 0xFFFFFFFF, true);
            if (row.right() != null) g.drawString(client.font, row.right(), x + width - client.font.width(row.right()), y, 0xFFFFFFFF, true);
            y += lineHeight;
        }
    }
}
