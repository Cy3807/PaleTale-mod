package cn.paletale.companion;

import java.util.concurrent.CompletionException;

/** The game log gets a plain reason only: no address, path, class name or other internals. */
final class ModLogTest {
    private ModLogTest() {}
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    static void run() {
        require(ModLog.reason(new CompletionException(new java.net.http.HttpTimeoutException("request to https://www.paletale.cn timed out"))).equals("网络连接超时"), "Timeouts are named, the address is not");
        require(ModLog.reason(new java.net.ConnectException("Connection refused: /203.0.113.7:443")).equals("无法连接网站"), "An IP address never reaches the log");
        require(ModLog.reason(new java.io.IOException("GOAWAY received")).equals("网络连接中断"), "Protocol details become a plain category");
        require(ModLog.reason(new IllegalStateException("登录已过期，请重新验证正版账号。")).equals("登录已过期，请重新验证正版账号。"), "Messages written for players are kept");
        require(ModLog.reason(new IllegalStateException("连接 http://127.0.0.1:3000 失败")).equals("未知错误"), "A player message carrying an address is replaced");
        require(ModLog.reason(new IllegalStateException("连接 10.0.0.5 失败")).equals("未知错误"), "A bare IP inside a Chinese message is replaced");
        require(ModLog.reason(new NullPointerException("Cannot invoke \"cn.paletale.companion.X.y()\"")).equals("未知错误"), "Programming errors never expose internals");
        require(ModLog.reason(new RuntimeException("服务器返回了一段非常长的说明" + "很".repeat(100))).equals("未知错误"), "Overlong messages are not copied");
        HttpBodies.identify("1.0.0-beta.1", "1.21.11");
        require(HttpBodies.agent().equals("PaleTale/1.0.0-beta.1 Minecraft/1.21.11"), "Requests name the mod and game version: " + HttpBodies.agent());
        require(HttpBodies.request(java.net.URI.create("https://www.paletale.cn/api/mcmod/version")).build().headers().firstValue("User-Agent").orElse("").equals(HttpBodies.agent()), "Every request carries it");
        HttpBodies.identify("1.0\r\nX-Evil: 1", "");
        require(HttpBodies.agent().equals("PaleTale/1.0X-Evil1"), "Odd version text cannot inject headers: " + HttpBodies.agent());
    }
}
