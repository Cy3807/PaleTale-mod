package cn.paletale.companion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The eight slots of the command wheel: a short label and the command it sends. Stored as "label<TAB>command" lines, one per slot. */
public final class RadialSlots {
    public static final int COUNT = 8, LABEL_MAX = 8, COMMAND_MAX = 120;
    public record Slot(String label, String command, String icon) {
        public Slot(String label, String command) { this(label, command, ""); }
        public boolean empty() { return command.isEmpty(); }
    }
    public static final Slot EMPTY = new Slot("", "");
    private static final java.util.Map<String, String> ICON_MOVES = java.util.Map.of(
            "@actions|minecraft:leather_boots", "minecraft:iron_sword", "@nav|minecraft:lodestone", "minecraft:compass", "@nav-go|minecraft:ender_eye", "minecraft:command_block",
            "@nav-off|minecraft:spyglass", "minecraft:lever", "@lands|minecraft:recovery_compass", "minecraft:ender_pearl", "bal|minecraft:emerald", "minecraft:gold_ingot");
    private RadialSlots() {}

    /** Actions the mod performs itself instead of sending a command; written "@name" in a slot. */
    public static final List<String> MACROS = List.of("cancel", "coords", "lands", "landedit", "mylands", "actions", "player", "nav", "nav-go", "nav-off", "recent", "transfers", "warps", "party", "signs");
    /**
     * A slot bound to one territory ("@land:白渚") or one player ("@player:Name"): letting go over it opens a small wheel of what to do with it. Returns {kind, name}, or null when
     * the text is not such a binding.
     */
    public static String[] bound(String command) {
        if (command == null) return null;
        for (String kind : new String[]{ "land", "player" }) {
            String prefix = "@" + kind + ":";
            if (!command.startsWith(prefix)) continue;
            String name = command.substring(prefix.length()).strip();
            boolean ok = kind.equals("player") ? name.matches("[A-Za-z0-9_]{1,16}") : !name.isEmpty() && name.length() <= 60 && name.chars().noneMatch(c -> Character.isISOControl(c) || c == ',' || c == '@' || c == '\t');
            return ok ? new String[]{ kind, name } : null;
        }
        return null;
    }
    public static boolean macro(String command) { return command != null && command.startsWith("@"); }

    /** A ready-made slot the editor offers. */
    public record Preset(String label, String command, String icon) {}
    public static final List<Preset> PRESETS = List.of(
            new Preset("我的领地", "@mylands", "minecraft:filled_map"), new Preset("传送领地", "@lands", "minecraft:ender_pearl"), new Preset("选择领地", "@landedit", "minecraft:oak_sign"),
            new Preset("玩家", "@player", "minecraft:player_head"), new Preset("最近聊天", "@recent", "minecraft:writable_book"), new Preset("转账对象", "@transfers", "minecraft:emerald"),
            new Preset("传送点", "@warps", "minecraft:ender_eye"), new Preset("组队", "@party", "minecraft:white_banner"), new Preset("告示牌", "@signs", "minecraft:spruce_sign"), new Preset("动作", "@actions", "minecraft:iron_sword"), new Preset("导航", "@nav", "minecraft:compass"),
            new Preset("一键导航", "@nav-go", "minecraft:command_block"), new Preset("停止导航", "@nav-off", "minecraft:lever"), new Preset("余额", "bal", "minecraft:gold_ingot"),
            new Preset("坐标", "@coords", "minecraft:compass"), new Preset("取消", "@cancel", "minecraft:barrier"));
    /** Plain vanilla items the editor's icon grid offers. */
    public static final List<String> ICONS = List.of(
            "compass", "recovery_compass", "clock", "map", "filled_map", "ender_pearl", "ender_eye", "emerald", "diamond", "gold_ingot",
            "iron_ingot", "copper_ingot", "netherite_ingot", "netherite_scrap", "redstone", "glowstone_dust", "charcoal", "prismarine_shard", "lapis_lazuli", "quartz", "amethyst_shard", "coal", "lever", "command_block",
            "iron_sword", "chest", "ender_chest", "barrel", "white_bed", "crafting_table", "anvil", "enchanting_table", "bell", "book",
            "writable_book", "name_tag", "paper", "oak_sign", "oak_door", "lantern", "torch", "campfire", "shield", "bow",
            "fishing_rod", "spyglass", "elytra", "totem_of_undying", "golden_apple", "bread", "experience_bottle", "potion", "player_head", "barrier");

    /** What a fresh install shows. Moving the mouse straight down cancels. Only commands the mod already knows the server has. */
    public static List<Slot> defaults() {
        // Clockwise from the top: the three straight directions go to what is used most, cancel sits at the bottom.
        List<Slot> slots = new ArrayList<>(List.of(new Slot("传送领地", "@lands", "minecraft:ender_pearl"), new Slot("我的领地", "@mylands", "minecraft:filled_map"), new Slot("玩家", "@player", "minecraft:player_head"),
                new Slot("告示牌", "@signs", "minecraft:spruce_sign"), new Slot("取消", "@cancel", "minecraft:barrier"), new Slot("余额", "bal", "minecraft:gold_ingot"),
                new Slot("导航", "@nav", "minecraft:compass"), new Slot("动作", "@actions", "minecraft:iron_sword")));
        return slots;
    }

    /** The command as the server expects it (no leading slash), or null when it is empty, too long or has line breaks / control characters. */
    public static String command(String raw) {
        if (raw == null) return null;
        String text = raw.strip();
        while (text.startsWith("/")) text = text.substring(1).stripLeading();
        if (text.startsWith("@")) return MACROS.contains(text.substring(1)) || bound(text) != null ? text : null;
        if (text.isEmpty() || text.length() > COMMAND_MAX || text.chars().anyMatch(Character::isISOControl)) return null;
        return text;
    }

    public static String label(String raw) {
        String text = raw == null ? "" : raw.replaceAll("\\p{Cntrl}", "").strip();
        return text.length() > LABEL_MAX ? text.substring(0, LABEL_MAX) : text;
    }

    /** A slot from what the player typed: an invalid or empty command gives an empty slot; a missing label falls back to the command. */
    public static Slot slot(String label, String command) { return slot(label, command, ""); }
    public static Slot slot(String label, String command, String icon) {
        String cmd = command(command);
        if (cmd == null) return EMPTY;
        String name = label(label);
        return new Slot(name.isEmpty() ? label(cmd) : name, cmd, icon(icon));
    }
    /** An item id such as minecraft:compass (a bare name means minecraft:), or "" when it is not one. */
    public static String icon(String raw) {
        String text = raw == null ? "" : raw.strip().toLowerCase(java.util.Locale.ROOT);
        if (text.isEmpty()) return "";
        if (!text.contains(":")) text = "minecraft:" + text;
        return text.length() <= 80 && text.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") ? text : "";
    }

    public static List<Slot> parse(String text) {
        List<Slot> slots = new ArrayList<>();
        for (String line : text.split("\\R", -1)) {
            if (slots.size() == COUNT) break;
            String[] part = line.split("\t", 3);
            // Rings that no longer exist: the favourites became part of the player search, the archive ring was dropped (H opens the pages).
            String saved = part.length < 2 ? "" : part[1].strip();
            var read = saved.equals("@favorites") ? new Slot("玩家", "@player", "minecraft:player_head") : saved.equals("@shops") ? new Slot("告示牌", "@signs", "minecraft:spruce_sign") : saved.equals("@archive") || part.length < 2 ? EMPTY : slot(part[0], part[1], part.length > 2 ? part[2] : "");
            // The warp slot of earlier versions became the player query; the territory menu is called "我的领地".
            if (read.command().equals("warp") && read.label().equals("传送点")) read = new Slot("查询玩家", "@player", "minecraft:player_head");
            else if (read.command().equals("@player") && read.label().equals("查询玩家")) read = new Slot("玩家", "@player", read.icon());
            else if (read.command().equals("@lands") && read.label().equals("回领地")) read = new Slot("传送领地", "@lands", read.icon());
            else if (read.command().equals("l") && (read.label().equals("领地") || read.label().equals("我的领地"))) read = new Slot("我的领地", "@mylands", read.icon());
            // Slots still wearing an earlier default icon take the current one.
            String moved = ICON_MOVES.get(read.command() + "|" + read.icon());
            if (moved != null) read = new Slot(read.label(), read.command(), moved);
            slots.add(read);
        }
        while (slots.size() < COUNT) slots.add(EMPTY);
        return slots;
    }

    public static String format(List<Slot> slots) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < COUNT; i++) {
            Slot slot = i < slots.size() ? slots.get(i) : EMPTY;
            out.append(slot.empty() ? "" : slot.label() + "\t" + slot.command() + (slot.icon().isEmpty() ? "" : "\t" + slot.icon())).append('\n');
        }
        return out.toString();
    }

    /** A missing or unreadable file gives the defaults. */
    public static List<Slot> load(Path file) {
        try {
            if (Files.isRegularFile(file)) return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException ignored) { /* defaults */ }
        return defaults();
    }

    public static void save(Path file, List<Slot> slots) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, format(slots), StandardCharsets.UTF_8);
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) { /* the wheel still works until the game closes */ }
    }
}
