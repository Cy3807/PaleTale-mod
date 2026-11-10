package cn.paletale.companion;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/** The network summary must count, bucket and describe exchanges without the raw log being read by hand. */
final class NetworkTraceTest {
    private NetworkTraceTest() {}
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static void add(long at, String path, int status, long total, String error) {
        NetworkTrace.record(new NetworkTrace.Exchange(at, "www.paletale.cn", "GET", path, status,
                status == 0 ? "-" : "h2", total, Math.max(1, total / 3), 128, status == 0 ? 2 : 1, error));
    }

    static void run() {
        NetworkTrace.reset();
        NetworkTrace.begin("1.0.7-beta.test", "1.21.11", "21", "test", "主站（www.paletale.cn）");
        long now = System.currentTimeMillis();
        add(now - 5_000, "/api/mcmod/presence", 200, 120, "");
        add(now - 4_000, "/api/mcmod/presence", 200, 300, "");
        add(now - 3_000, "/api/mcmod/market/123456", 200, 4_000, "");
        add(now - 2_000, "/api/mcmod/market/654321", 0, 15_000, "timeout");
        add(now - 1_000, "/api/mcmod/items/9", 503, 900, "");

        String summary = NetworkTrace.summary();
        require(summary.contains("请求 5 次"), "All attempts are counted:\n" + summary);
        require(summary.contains("成功 3（60.0%）"), "Success rate excludes the timeout and the 503:\n" + summary);
        require(summary.contains("超时 1"), "A timeout is named as such:\n" + summary);
        require(summary.contains("/api/mcmod/presence 2 次 （100.0%）"), "Repeated endpoints are grouped:\n" + summary);
        require(summary.contains("/api/mcmod/market/{id} 2 次 （50.0%）"), "Identifiers are bucketed:\n" + summary);
        require(summary.contains("超过 2 秒 2"), "Slow exchanges are flagged:\n" + summary);
        require(summary.contains("HTTP 503"), "The recent failure keeps its status:\n" + summary);
        require(summary.contains("第 2 次"), "A retry is visible:\n" + summary);

        require(NetworkTrace.bucket("/api/mcmod/market/123456").equals("/api/mcmod/market/{id}"), "Six-digit codes are bucketed");
        require(NetworkTrace.bucket("/api/mcmod/players/uuids").equals("/api/mcmod/players/uuids"), "Words stay readable");
        require(NetworkTrace.bucket(URI.create("https://www.paletale.cn/api/mcmod/items/1/image").getPath()).equals("/api/mcmod/items/{id}/image"), "Item pictures keep their shape");
        require(NetworkTrace.category(new HttpTimeoutException("timed out")).equals("timeout"), "Timeouts are classified");
        require(NetworkTrace.category(new java.net.UnknownHostException("no dns")).equals("dns"), "DNS failures are classified");
        require(NetworkTrace.category(new java.net.ConnectException("refused")).equals("connect"), "Refused connections are classified");
        require(NetworkTrace.category(new java.io.IOException("Connection reset")).equals("reset"), "Resets are classified");

        require(HttpBodies.CONTROL != HttpBodies.CLIENT, "Control and content traffic use separate connection pools");
        NetworkTrace.probe("www.paletale.cn", true, 210, "");
        require(NetworkTrace.summary().contains("停顿探针：1 次 · 新连接可通 1 次"), "A probe is reported apart from requests:\n" + NetworkTrace.summary());
        HttpRequest shortRead = HttpBodies.request(URI.create("https://www.paletale.cn/api/mcmod/version")).timeout(Duration.ofSeconds(5)).GET().build();
        long watched = StallProbe.begin(shortRead);
        require(watched != 0, "A short request is watched for a stall");
        StallProbe.end(watched);
        HttpRequest download = HttpBodies.request(URI.create("https://www.paletale.cn/api/mod/download?mc=1")).timeout(Duration.ofMinutes(5)).GET().build();
        require(StallProbe.begin(download) == 0, "A long download is never watched for a stall");
        NetworkTrace.reset();
        System.out.println("Network trace regression: 19 checks passed");
    }
}
