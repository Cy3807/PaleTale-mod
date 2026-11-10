package cn.paletale.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ClientAccount {
    private final ModAuthApi api;
    private ModAuthApi.Login login;
    private ClientPacketListener connection;
    private UUID profile;
    private int generation;
    private boolean busy;
    private boolean checking;
    private long nextCheck;
    private long nextRenew;
    private String message = "";
    private boolean autoTried;
    private boolean registerPrompt, unregistered, promptOnFailure = true;
    /** The verified Minecraft account has no website account yet: the registration screen keeps coming back until it is registered. */
    private boolean mustRegister;
    private long joinedAt;
    private ClientPacketListener seenConnection;
    private long retryAt;
    private int silentFailures;
    /** Lets the login be extended while still on the server even after the launcher's Mojang session ran out (memory only, this connection and profile only). */
    private String renewal;
    private UUID renewalProfile;

    public ClientAccount(ModAuthApi api) { this.api = api; }
    /** Which of the conditions for signing in hold right now, for the login log. */
    private static String gate(Minecraft client) {
        var server = client.getCurrentServer(); var user = client.getUser();
        boolean uuid = client.player != null && user != null && client.player.getUUID().equals(user.getProfileId());
        return "player=" + (client.player != null) + " level=" + (client.level != null) + " connection=" + (client.getConnection() != null) + " singleplayer=" + client.hasSingleplayerServer()
                + " server=" + (server == null ? "none" : server.ip) + " simmc=" + (server != null && ServerAccess.isSimmc(server.ip)) + " uuidMatchesAccount=" + uuid
                + (client.player != null && user != null && !uuid ? " (player " + client.player.getUUID() + ", launcher account " + user.getProfileId() + ")" : "");
    }

    public boolean canLogin(Minecraft client) {
        return client.player != null && client.level != null && client.getConnection() != null
                && !client.hasSingleplayerServer() && client.getCurrentServer() != null
                && ServerAccess.isSimmc(client.getCurrentServer().ip)
                && client.player.getUUID().equals(client.getUser().getProfileId());
    }
    /** Called when a PaleTale screen opens: one automatic attempt per server connection. */
    public void autoLogin(Minecraft client) {
        if (login != null || busy || !canLogin(client)) return;
        if (autoTried) { if (unregistered) { unregistered = false; registerPrompt = true; } return; }
        autoTried = true; start(client);
    }
    /** True once after a verified Minecraft account turned out to have no website account. */
    public boolean mustRegister() { return mustRegister; }
    public boolean takeRegisterPrompt() { boolean value = registerPrompt; registerPrompt = false; return value; }
    public java.net.URI registerUri() { return api.register(); }
    public void start(Minecraft client) { promptOnFailure = true; begin(client, null); }
    /** Registers the website account for this verified Minecraft account (chosen password), then signs in. */
    public void register(Minecraft client, String password) { promptOnFailure = false; unregistered = false; begin(client, password); }
    private void begin(Minecraft client) { begin(client, null); }
    private void begin(Minecraft client, String password) {
        if (busy || !canLogin(client)) { LoginDebug.log("begin refused: busy=" + busy + " " + gate(client)); message = "请先使用正版账号进入 play.simmc.cn。"; return; }
        int run = ++generation;
        connection = client.getConnection();
        profile = client.getUser().getProfileId();
        UUID expected = profile;
        String name = client.getUser().getName();
        busy = true; message = "正在验证正版账号…";
        LoginDebug.log("begin run=" + run + " register=" + (password != null) + " renewalOffered=" + (expected.equals(renewalProfile) && renewal != null) + " name=" + name + " account=" + expected + " " + gate(client));
        String held = password == null && expected.equals(renewalProfile) ? renewal : null;
        CompletableFuture.supplyAsync(() -> {
            try {
                long t0 = System.nanoTime();
                if (held != null) {
                    LoginDebug.log("stage renew");
                    try { var renewed = api.renew(held); LoginDebug.log("stage renew ok"); return renewed; }
                    // Refused (expired, used or account changed): forget it and prove the account to Mojang below.
                    catch (IllegalStateException refused) { LoginDebug.log("stage renew refused, proving the account again"); client.execute(() -> { if (held.equals(renewal)) renewal = null; }); }
                    // The one-use renewal may already be consumed server-side while the answer was lost on the way back. Replaying it would only fail,
                    // so drop it immediately and prove the account again in this same attempt instead of waiting for the next cycle.
                    catch (java.io.IOException lost) { LoginDebug.failure("stage renew response lost, proving the account again", lost); client.execute(() -> { if (held.equals(renewal)) renewal = null; }); }
                }
                LoginDebug.log("stage challenge");
                ModAuthApi.Challenge challenge = api.challenge(expected.toString(), name);
                LoginDebug.log("stage challenge ok");
                // Authlib performs the official join call locally. The access token never goes to PaleTale.
                LoginDebug.log("stage mojang join");
                long j0 = System.nanoTime();
                try { client.services().sessionService().joinServer(expected, client.getUser().getAccessToken(), challenge.serverId()); }
                catch (Exception mojang) { LoginDebug.failure("stage mojang join failed after " + (System.nanoTime() - j0) / 1_000_000 + " ms", mojang); throw mojang; }
                LoginDebug.log("stage mojang join ok in " + (System.nanoTime() - j0) / 1_000_000 + " ms");
                var done = api.complete(challenge, password);
                LoginDebug.log("stage complete ok, whole sign-in " + (System.nanoTime() - t0) / 1_000_000 + " ms");
                return done;
            } catch (Exception e) { LoginDebug.failure("sign-in attempt failed", e); throw new java.util.concurrent.CompletionException(e); }
        }).whenComplete((result, error) -> client.execute(() -> {
            if (run != generation || !canLogin(client) || client.getConnection() != connection || !client.getUser().getProfileId().equals(expected)) {
                LoginDebug.log("result dropped: runCurrent=" + (run == generation) + " " + gate(client));
                if (result != null) discard(result.token());
                return;
            }
            busy = false;
            if (error != null) {
                Throwable cause = error.getCause();
                ModLog.failed("模组登录", cause == null ? error : cause);
                LoginDebug.failure("result: failed", cause == null ? error : cause);
                if (cause instanceof ModAuthApi.Unregistered && password == null) mustRegister = true;
                if (cause instanceof ModAuthApi.Unregistered) { if (promptOnFailure) registerPrompt = true; else if (password == null) unregistered = true; }
                message = cause instanceof IllegalStateException ? cause.getMessage() : "正版会话验证失败。请在启动器里重新登录正版账号（或重启游戏）后再试。";
                // Network/session hiccups retry silently with growing delays; an unregistered account never retries.
                if (!(cause instanceof ModAuthApi.Unregistered) && silentFailures < 5) {
                    autoTried = false; promptOnFailure = false;
                    // Growing delays with jitter: a network wobble must not turn into every client retrying at the same second.
                    retryAt = System.currentTimeMillis() + 15_000L * (1L << silentFailures++)
                            + java.util.concurrent.ThreadLocalRandom.current().nextLong(0, 5_000);
                }
                return;
            }
            if (result == null || result.citizen() == null || !result.citizen().uuid().equals(expected.toString().replace("-", ""))) {
                if (result != null) discard(result.token());
                LoginDebug.log("result: site account does not match the game account (site " + (result == null || result.citizen() == null ? "none" : result.citizen().uuid()) + " vs " + expected + ")");
                message = "网站账号与当前游戏账号不一致。"; return;
            }
            if (login != null) discard(login.token());
            login = result; mustRegister = false;
            if (result.renewal() != null && result.renewal().matches("[A-Za-z0-9_-]{43}")) { renewal = result.renewal(); renewalProfile = expected; } silentFailures = 0; retryAt = 0;
            nextCheck = System.currentTimeMillis() + 60_000;
            nextRenew = result.expiresAt() - 60_000;
            message = "";
            LoginDebug.log("result: signed in as " + result.citizen().mcName() + " (player " + result.citizen().playerId() + "), expires in " + (result.expiresAt() - System.currentTimeMillis()) / 1000 + " s, renewalOffered=" + (renewal != null));
        }));
    }

    /** Client tests sign in by setting the session directly in a singleplayer world, where nothing could keep it: they pin it so the next tick does not drop it. */
    static boolean sessionPinned;
    public void tick(Minecraft client) {
        LoginDebug.watch(gate(client) + " login=" + (login != null) + " busy=" + busy);
        if (client.getConnection() != seenConnection) { seenConnection = client.getConnection(); forgetRenewal(); autoTried = false; unregistered = false; mustRegister = false; silentFailures = 0; retryAt = 0; joinedAt = System.currentTimeMillis(); }
        // Silent login a few seconds after joining SIMMC; the same Mojang proof applies as for the manual button.
        if (login == null && !busy && !autoTried && canLogin(client) && System.currentTimeMillis() - joinedAt > 4000 && System.currentTimeMillis() >= retryAt) {
            autoTried = true; promptOnFailure = false; begin(client);
        }
        if (!sessionPinned && (busy || login != null) && (!canLogin(client) || client.getConnection() != connection || !client.getUser().getProfileId().equals(profile))) { drop(); forgetRenewal(); }
        if (login == null) return;
        long now = System.currentTimeMillis();
        if (now >= login.expiresAt()) { drop(); relogin(); return; }
        if (now >= nextRenew && !busy) { nextRenew = now + 60_000; start(client); return; }
        if (now < nextCheck || checking) return;
        checking = true; nextCheck = now + 60_000;
        String token = login.token(); int run = generation;
        CompletableFuture.supplyAsync(() -> check(token)).thenAccept(check -> client.execute(() -> {
            checking = false;
            if (run != generation || login == null) return;
            if (check.citizen() != null) {
                if (check.citizen().playerId() != login.citizen().playerId()) { drop(); relogin(); }
                else nextCheck = System.currentTimeMillis() + 60_000;
                return;
            }
            if (check.rejected()) { LoginDebug.log("session rejected, signing in again"); drop(); relogin(); return; }
            // The site was unreachable or busy, which says nothing about the session: keep it and look again soon.
            nextCheck = System.currentTimeMillis() + 20_000 + java.util.concurrent.ThreadLocalRandom.current().nextLong(0, 15_000);
            LoginDebug.log("session check unavailable, keeping the login: " + gate(client));
        }));
    }
    /** Session expired or was rejected while still on the server: sign in again silently. */
    private void relogin() { autoTried = false; promptOnFailure = false; retryAt = 0; joinedAt = 0; }
    /** Manual sign-out: stays signed out until the player logs in again or reconnects. */
    public void logout() { drop(); forgetRenewal(); autoTried = true; }
    private void forgetRenewal() { renewal = null; renewalProfile = null; }
    /** Different backend process: do not forward an old session or reuse its pending login result. */
    void changeSite() {
        generation++;login=null;connection=null;profile=null;busy=false;checking=false;forgetRenewal();
        autoTried=false;unregistered=false;mustRegister=false;registerPrompt=false;silentFailures=0;
        joinedAt=0;retryAt=0;nextCheck=0;nextRenew=0;message="";
    }
    /** Clicking the account button: verify the current session against the site (toast with the result), or sign in when not logged in. */
    public void checkStatus(Minecraft client) {
        if (login == null) { start(client); return; }
        if (busy || checking) return;
        String token = login.token(), name = login.citizen().mcName(); int run = generation;
        checking = true;
        CompletableFuture.supplyAsync(() -> check(token))
                .thenAccept(check -> client.execute(() -> {
                    checking = false;
                    if (run != generation || login == null) return;
                    boolean ok = check.citizen() != null && check.citizen().playerId() == login.citizen().playerId();
                    // An unreachable site is not a rejected login: keep the session and say so instead of signing in again.
                    boolean offline = !ok && check.citizen() == null && !check.rejected();
                    client.getToastManager().addToast(net.minecraft.client.gui.components.toasts.SystemToast.multiline(client,
                            new net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId(),
                            net.minecraft.network.chat.Component.literal(ok ? "登录状态正常" : offline ? "网站暂时连不上" : "登录已失效"),
                            net.minecraft.network.chat.Component.literal(ok ? name + " · 正版账号已验证并匹配网站账号"
                                    : offline ? "已保留登录，稍后自动复查" : "正在重新验证…")));
                    if (!ok && !offline) { drop(); relogin(); }
                }));
    }
    /** One session check: a refusal (401/403) invalidates the login, while a timeout, broken connection or 429/5xx only means the site was not heard. */
    private record Check(ModAuthApi.Citizen citizen, boolean rejected) {}
    private Check check(String token) {
        try { return new Check(api.session(token), false); }
        catch (ModAuthApi.Refused refused) { return new Check(null, refused.status() < 500 && refused.status() != 429); }
        catch (Exception offline) { return new Check(null, false); }
    }
    private void drop() {
        generation++; busy = false; checking = false;
        if (login != null) discard(login.token());
        login = null; connection = null; profile = null; message = "";
    }
    private void discard(String token) {
        CompletableFuture.runAsync(() -> { try { api.logout(token); } catch (Exception ignored) { /* Short session also expires server-side. */ } });
    }
    public String label() { return busy ? "验证中…" : login == null ? "正版账号登录" : login.citizen().mcName(); }
    public String message() { return message; }
    public boolean busy() { return busy; }
    public String token() { return login == null ? null : login.token(); }
    public boolean loggedIn() { return login != null; }
    public ModAuthApi.Citizen citizen() { return login == null ? null : login.citizen(); }
}
