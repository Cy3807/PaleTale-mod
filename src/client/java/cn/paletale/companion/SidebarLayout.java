package cn.paletale.companion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.components.events.GuiEventListener;
import static cn.paletale.companion.MarketTheme.Action;

/**
 * The ledger's left column is the reference for every page: its buttons are 14 tall at a 15 pitch (11 and 11 in a short window),
 * the first one at y=86 right under the search box (a page that starts lower, such as the archive lists, is moved up to it), and the bottom pair is 17 tall at an 18 pitch with the last one at height-34.
 * Pages lay their full-width sidebar buttons out however they like; this restacks them to that size and spacing, so the column
 * looks the same everywhere. Only size and spacing change, never the labels.
 */
final class SidebarLayout {
    private SidebarLayout() {}
    static final int FIRST_Y = 86;

    /** Restacks the full-width sidebar buttons in {@code children} that are not in {@code done} yet; call after the page has laid out its widgets. */
    static void normalize(List<? extends GuiEventListener> children, int width, int height, java.util.Set<Object> done) {
        var layout = ArchiveLayout.of(width);
        List<Action> fresh = new ArrayList<>();
        for (var child : children) {
            if (child instanceof Action action && !done.contains(action) && action.getX() == layout.left() && action.getWidth() == layout.sidebar()
                    && action.getY() >= 60 && action.getHeight() >= 14 && action.getHeight() <= 22) fresh.add(action);
        }
        if (fresh.isEmpty()) return;
        fresh.sort(Comparator.comparingInt(Action::getY));
        boolean compact = height < 320;
        int topHeight = compact ? 11 : 14, topPitch = compact ? 11 : 15;
        List<Action> top = new ArrayList<>(), bottom = new ArrayList<>();
        for (var action : fresh) (action.getY() < height / 2 ? top : bottom).add(action);
        int previousOld = Integer.MIN_VALUE, next = 0; boolean first = true;
        for (var action : top) {
            int old = action.getY();
            if (first || old - previousOld > 26) next = first && old >= 84 && old <= 120 ? FIRST_Y : old;
            first = false; previousOld = old;
            action.setY(next); action.setHeight(topHeight); next += topPitch;
        }
        int y = height - 34;
        for (int i = bottom.size() - 1; i >= 0; i--) { bottom.get(i).setY(y); bottom.get(i).setHeight(17); y -= 18; }
        done.addAll(fresh);
    }
}
