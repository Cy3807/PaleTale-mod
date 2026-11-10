package cn.paletale.companion;

import java.net.URI;

/** Holds the notification site; the v2 inbox and unread watcher talk to it through {@link NoticeV2Api}. */
public final class NoticeApi {
    private volatile URI site;
    public URI site() { return site; }
    public void retarget(URI next) { if (MarketApi.isAllowedSite(next)) site = next; }
    public NoticeApi(URI site) { this.site = site; }
}
