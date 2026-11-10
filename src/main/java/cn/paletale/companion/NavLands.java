package cn.paletale.companion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;

/** The territories (owned or belonged to) of the player that have a hub inside, as the site gives them: [name, nation, x, z, [hub ids], spawn x, spawn z, minX, minZ, maxX, maxZ]. */
public final class NavLands {
    private NavLands() {}
    public static List<NavPlanner.Land> parse(String json) {
        List<NavPlanner.Land> out = new ArrayList<>();
        JsonArray rows = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("lands");
        if (rows == null) return out;
        for (JsonElement element : rows) {
            JsonArray row = element.getAsJsonArray();
            if (row.size() < 5) continue;
            List<String> hubs = new ArrayList<>();
            for (JsonElement id : row.get(4).getAsJsonArray()) hubs.add(id.getAsString());
            // Rows 5 and 6 (x, z) are the spawn point other players reported for this land, when there is one.
            int[] spawn = row.size() >= 7 && !row.get(5).isJsonNull() && !row.get(6).isJsonNull() ? new int[]{ row.get(5).getAsInt(), row.get(6).getAsInt() } : null;
            int[] bounds = row.size() >= 11 ? new int[]{ row.get(7).getAsInt(), row.get(8).getAsInt(), row.get(9).getAsInt(), row.get(10).getAsInt() } : null;
            out.add(new NavPlanner.Land(row.get(0).getAsString(), row.get(1).getAsString(), row.get(2).getAsInt(), row.get(3).getAsInt(), List.copyOf(hubs), spawn, bounds));
        }
        return List.copyOf(out);
    }
}
