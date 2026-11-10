package cn.paletale.companion;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Places of the SMC world pages (a territory, a nation's territories, a teleport point) for the button at the top right of the page that opens Xaero's world map on them.
 * Coordinates come from the site's map data (the same /api/mcmod/nav/lands the navigation uses).
 */
final class MapPins {
    record Pin(String name, String symbol, int x, int z, String color) {}
    private MapPins() {}

    /** Opens Xaero's world map on the middle of the given places (one place: that place). False when the map could not be opened. */
    static boolean view(List<Pin> pins) {
        if (pins.isEmpty()) return false;
        long x = 0, z = 0;
        for (Pin pin : pins) { x += pin.x(); z += pin.z(); }
        return XaeroWorldMap.open((int) (x / pins.size()), (int) (z / pins.size()));
    }

    static Pin trafficPin(String name, int x, int z) { return new Pin("传送点 · " + name, "传", x, z, "LIGHT_PURPLE"); }

    private static String simple(String nation) { int dash = nation.lastIndexOf('-'); return dash >= 0 ? nation.substring(dash + 1) : nation; }

    /** The map coordinates of territories by name (spawn point when it is known, else the middle); only those of {@code nation} when one is given. */
    static CompletableFuture<List<Pin>> landPins(List<String> names, String nation, boolean withNation) {
        var wanted = names.stream().filter(n -> !n.isBlank() && !n.contains(",") && !n.contains("@") && n.length() <= 60).distinct().limit(60).toList();
        if (wanted.isEmpty()) return CompletableFuture.completedFuture(List.of());
        String path = "/api/mcmod/nav/lands?names=" + URLEncoder.encode(String.join(",", wanted), StandardCharsets.UTF_8);
        var request = HttpBodies.request(SiteConfig.current().resolve(path)).timeout(Duration.ofSeconds(20)).header("Accept", "application/json").GET().build();
        return HttpBodies.CLIENT.sendAsync(request, HttpBodies.text()).thenApply(response -> {
            if (response.statusCode() != 200 || response.body().length() > 2_000_000) throw new IllegalStateException("网站没有返回坐标");
            var pins = new ArrayList<Pin>();
            for (var land : NavLands.parse(response.body())) {
                if (!wanted.contains(land.name()) || nation != null && !nation.equals(land.nation())) continue;
                int[] at = land.spawn() != null ? land.spawn() : new int[]{ land.x(), land.z() };
                pins.add(new Pin((withNation ? simple(land.nation()) + " · " : "领地 · ") + land.name(), "领", at[0], at[1], withNation ? "GOLD" : "GREEN"));
            }
            return pins;
        });
    }

    /** The names of the territories listed on a nation's page. */
    static List<String> landNames(JsonArray lands) {
        var out = new ArrayList<String>();
        if (lands != null) for (var element : lands) if (element.isJsonObject()) { JsonObject row = element.getAsJsonObject(); if (row.has("name")) out.add(row.get("name").getAsString()); }
        return out;
    }
}
