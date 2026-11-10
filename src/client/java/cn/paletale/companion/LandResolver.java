package cn.paletale.companion;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;

/**
 * A territory whose icon is a special item (a drink, say) can show the item's own name and lore in the /l menu instead of the territory's, so the menu alone does not give its name.
 * The site's map data lists the territories the player belongs to by their real names: when exactly one menu entry has no territory description and exactly one of the site's names
 * is left over after all the others are accounted for, that entry is that territory.
 */
final class LandResolver {
    private LandResolver() {}

    /** Menu name → real name, for the entries that could be resolved; empty when nothing is certain. */
    static CompletableFuture<Map<String, String>> aliases(List<String> menuNames, Set<String> unverified) {
        var user = Minecraft.getInstance().getUser();
        if (unverified.isEmpty() || user == null || user.getName() == null) return CompletableFuture.completedFuture(Map.of());
        String path = "/api/mcmod/nav/lands?player=" + URLEncoder.encode(user.getName(), StandardCharsets.UTF_8);
        var request = HttpBodies.request(SiteConfig.current().resolve(path)).timeout(Duration.ofSeconds(15)).header("Accept", "application/json").GET().build();
        return HttpBodies.CLIENT.sendAsync(request, HttpBodies.text()).thenApply(response -> {
            if (response.statusCode() != 200 || response.body().length() > 2_000_000) return Map.<String, String>of();
            var site = new ArrayList<String>();
            for (var land : NavLands.parse(response.body())) site.add(land.name());
            return resolve(menuNames, unverified, site);
        });
    }

    /** The decision itself, on plain lists. */
    static Map<String, String> resolve(List<String> menuNames, Set<String> unverified, List<String> site) {
        // An entry whose name the site knows is confirmed by it.
        var open = new ArrayList<String>();
        for (String name : menuNames) if (unverified.contains(name) && !site.contains(name)) open.add(name);
        if (open.size() != 1) return Map.of();
        var accounted = new ArrayList<>(menuNames); accounted.removeAll(open);
        var left = new ArrayList<>(site); left.removeAll(accounted);
        var out = new LinkedHashMap<String, String>();
        if (left.size() == 1) out.put(open.get(0), left.get(0));
        return out;
    }
}
