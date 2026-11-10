package cn.paletale.companion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;

/** The server's hubs (港口 = group sea, 驿站 = the other groups) and RP warps, from the site's compact copy of the server map. */
public record NavPoints(List<Hub> hubs, List<Warp> warps) {
    /** {@code maintained}: can be left from as well as arrived at (otherwise arrival only). {@code use}: how close you must stand to use it; {@code reach}: how far it sends you. */
    public record Hub(String id, String name, int x, int z, String group, boolean maintained, int use, int reach) {
        public boolean sea() { return group.equals("sea"); }
        public String kind() { return sea() ? "港口" : "驿站"; }
    }
    public record Warp(String name, int x, int z) {}
    public static final NavPoints EMPTY = new NavPoints(List.of(), List.of());

    public static NavPoints parse(String json) {
        var root = JsonParser.parseString(json).getAsJsonObject();
        List<Hub> hubs = new ArrayList<>(); List<Warp> warps = new ArrayList<>();
        JsonArray hubRows = root.getAsJsonArray("hubs"), warpRows = root.getAsJsonArray("warps");
        if (hubRows != null) for (JsonElement element : hubRows) {
            JsonArray row = element.getAsJsonArray();
            if (row.size() < 8) continue;
            hubs.add(new Hub(row.get(0).getAsString(), row.get(1).getAsString(), row.get(2).getAsInt(), row.get(3).getAsInt(), row.get(4).getAsString(), row.get(5).getAsInt() == 1, row.get(6).getAsInt(), row.get(7).getAsInt()));
        }
        if (warpRows != null) for (JsonElement element : warpRows) {
            JsonArray row = element.getAsJsonArray();
            if (row.size() >= 3) warps.add(new Warp(row.get(0).getAsString(), row.get(1).getAsInt(), row.get(2).getAsInt()));
        }
        return new NavPoints(List.copyOf(hubs), List.copyOf(warps));
    }
}
