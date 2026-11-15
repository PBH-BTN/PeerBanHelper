package com.ghostchu.peerbanhelper.alert;

public enum BuiltinNotification {
    DOWNLOADER_NETWORK(new NotificationCategory("downloader.network", "downloader", "downloader.network", true, true)),
    DOWNLOADER_CONNECTION(new NotificationCategory("downloader.connection", "downloader", "downloader.connection", true, true)),
    TRANSMISSION_BLOCKLIST(new NotificationCategory("downloader.transmission-blocklist", "downloader", "downloader.transmission-blocklist", true, true)),
    TRAFFIC_LIMIT(new NotificationCategory("monitor.traffic-limit", "monitor", "monitor.traffic-limit", true, true)),
    LOW_MEMORY(new NotificationCategory("system.low-memory", "system", "system.low-memory", true, true)),
    CRASH_RECOVERY(new NotificationCategory("system.crash-recovery", "system", "system.crash-recovery", true, true)),
    WATCHDOG(new NotificationCategory("system.watchdog", "system", "system.watchdog", false, false)),
    JAVA_COMPATIBILITY(new NotificationCategory("system.java-compatibility", "system", "system.java-compatibility", true, false)),
    BITNESS(new NotificationCategory("system.bitness", "system", "system.bitness", true, false)),
    SNAPSHOT(new NotificationCategory("system.snapshot", "system", "system.snapshot", true, false)),
    BTN_UNBAN(new NotificationCategory("btn.allowlist-unban", "btn", "btn.allowlist-unban", true, false)),
    WEB_LOGIN(new NotificationCategory("web.login", "web", "web.login", false, false));

    private final NotificationCategory category;

    BuiltinNotification(NotificationCategory category) {
        this.category = category;
    }

    public String id() {
        return category.id();
    }

    public NotificationCategory category() {
        return category;
    }
}
