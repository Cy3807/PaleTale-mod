package cn.paletale.companion;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Website receives a one-use challenge, never the Minecraft access token. */
public final class ModAuthApi {
    /** The verified Minecraft account has no website account yet; carries the site's registration path. */
    public static final class Unregistered extends IllegalStateException {
        private final String path;
        Unregistered(String message, String path) { super(message); this.path = path; }
        public String path() { return path; }
    }
    /** The site refused the request; the status separates a rejected sign-in (401/403) from a busy or broken site (429/5xx). */
    public static final class Refused extends IllegalStateException {
        private final int status;
        Refused(String message, int status) { super(message); this.status = status; }
        public int status() { return status; }
    }
    public record Challenge(String challenge, String serverId) {}
    public record Citizen(long playerId, String mcName, String displayName, String uuid) {}
    /** {@code renewal} (when the site offers it) extends the login later without a new Mojang join; it is kept in memory only. */
    public record Login(String token, Citizen citizen, long expiresAt, String renewal) {}
    private volatile URI site;
    public void retarget(URI next) { if (MarketApi.isAllowedSite(next)) site = next; }
    private final HttpClient http = HttpBodies.CONTROL;
    private static final Gson JSON = new Gson();

    public ModAuthApi(URI site) { new MarketApi(site); this.site = site; }
    public URI register() { return site.resolve("/register"); }
    public Challenge challenge(String uuid, String name) throws Exception {
        JsonObject data = new JsonObject(); data.addProperty("uuid", uuid); data.addProperty("name", name);
        Challenge challenge = JSON.fromJson(request("challenge", "POST", data.toString(), ""), Challenge.class);
        // The server id is handed to Mojang's join call; only the exact random shapes the site issues are ever signed.
        if (challenge == null || challenge.challenge() == null || !challenge.challenge().matches("[a-f0-9]{64}")
                || challenge.serverId() == null || !challenge.serverId().matches("[a-f0-9]{40}")) throw new IllegalStateException("登录验证数据不正确，请稍后重试。");
        return challenge;
    }
    public Login complete(Challenge challenge) throws Exception { return complete(challenge, null); }
    /** With a password, an unregistered but Mojang-verified player is registered on the spot; the password only ever travels over HTTPS to the site. */
    public Login complete(Challenge challenge, String password) throws Exception {
        JsonObject data = new JsonObject(); data.addProperty("challenge", challenge.challenge());
        if (password != null) data.addProperty("password", password);
        return JSON.fromJson(request("complete", "POST", data.toString(), ""), Login.class);
    }
    /** Exchanges a renewal for a fresh login (and the next renewal). A rejected renewal throws IllegalStateException; a network problem throws IOException. */
    public Login renew(String renewal) throws Exception {
        JsonObject data = new JsonObject(); data.addProperty("renewal", renewal);
        return JSON.fromJson(request("renew", "POST", data.toString(), ""), Login.class);
    }
    public void logout(String token) throws Exception { request("session", "DELETE", "", token); }
    public Citizen session(String token) throws Exception { return JSON.fromJson(request("session", "GET", "", token).getAsJsonObject("citizen"), Citizen.class); }

    private JsonObject request(String path, String method, String body, String token) throws Exception {
        HttpRequest.Builder request = HttpBodies.request(site.resolve("/api/mcmod/auth/" + path))
                .timeout(Duration.ofSeconds(15)).header("Accept", "application/json");
        if (!token.isEmpty()) request.header("Authorization", "Bearer " + token);
        if (method.equals("POST")) request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        else request.method(method, HttpRequest.BodyPublishers.noBody());
        long started = System.nanoTime();
        HttpResponse<String> response;
        try { response = http.send(request.build(), HttpBodies.text()); }
        catch (Exception failed) { LoginDebug.failure("site " + method + " /auth/" + path + " after " + (System.nanoTime() - started) / 1_000_000 + " ms", failed); throw failed; }
        LoginDebug.log("site " + method + " /auth/" + path + " -> " + response.statusCode() + " in " + (System.nanoTime() - started) / 1_000_000 + " ms, " + response.headers().firstValue("Content-Type").orElse("no content type") + ", " + response.body().length() + " bytes"
                + (response.statusCode() == 200 ? "" : ", body " + (response.body().length() > 200 ? response.body().substring(0, 200) : response.body())));
        if (!response.headers().firstValue("Content-Type").orElse("").contains("application/json") || response.body().length() > 16384)
            throw new Refused("登录接口暂时不可用，请稍后重试。", response.statusCode());
        JsonObject data = JsonParser.parseString(response.body()).getAsJsonObject();
        if (response.statusCode() == 403 && data.has("code") && data.get("code").getAsString().equals("unregistered")) {
            throw new Unregistered(MarketData.text(data.get("message").getAsString()), "/register");
        }
        if (response.statusCode() != 200) throw new Refused(data.has("message") ? MarketData.text(data.get("message").getAsString()) : "登录验证失败，请重试。", response.statusCode());
        return data;
    }
}
