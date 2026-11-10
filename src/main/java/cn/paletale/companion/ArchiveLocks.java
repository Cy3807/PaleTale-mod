package cn.paletale.companion;

import java.util.List;

/**
 * Temporary content lock for the item archive's wine and dish categories (2026-11-07).
 *
 * <p>Wine and dish entries are not listed, cannot be opened and are left out of search and favourites
 * while the content is being reworked with the management team. Other item categories stay available.
 * Set {@link #ACTIVE} to false to lift the lock.
 */
public final class ArchiveLocks {
    public static final boolean ACTIVE = true;
    /** 锁定期“全部”只请求仍开放的类别：地图画与鱼类（2026-11-08 起鱼类在模组开放，官网仍锁定）。 */
    public static final String OPEN_TYPES = "map_art,fish";
    public static final String LOCK_ICON = "❌";
    public static final String TITLE = "酒、菜档案整改中";
    public static final String MESSAGE = "正在与管理组进行内容整改协商，暂不可查看。";

    private ArchiveLocks() {}

    public static boolean locked(String category) {
        return ACTIVE && ("wine".equals(category) || "dish".equals(category));
    }

    public static boolean locked(ArchiveData.Entry entry) {
        return entry != null && locked(entry.category());
    }

    /** The entries the archive may show; non-item pages are returned unchanged. */
    public static List<ArchiveData.Entry> visible(ArchiveData.Kind kind, List<ArchiveData.Entry> entries) {
        if (entries == null) return List.of();
        if (!ACTIVE || kind != ArchiveData.Kind.ITEMS) return entries;
        return entries.stream().filter(entry -> !locked(entry)).toList();
    }
}
