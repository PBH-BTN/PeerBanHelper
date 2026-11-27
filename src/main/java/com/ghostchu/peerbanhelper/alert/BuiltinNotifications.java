package com.ghostchu.peerbanhelper.alert;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class BuiltinNotifications {
    public static final NotificationCategory DOWNLOADER_NETWORK =
            new NotificationCategory("downloader.network", "downloader", "downloader.network", true, true);
    public static final NotificationCategory DOWNLOADER_CONNECTION =
            new NotificationCategory("downloader.connection", "downloader", "downloader.connection", true, true);
    public static final NotificationCategory TRANSMISSION_BLOCKLIST =
            new NotificationCategory("downloader.transmission-blocklist", "downloader", "downloader.transmission-blocklist", true, true);
    public static final NotificationCategory TRAFFIC_LIMIT =
            new NotificationCategory("monitor.traffic-limit", "monitor", "monitor.traffic-limit", true, true);
    public static final NotificationCategory LOW_MEMORY =
            new NotificationCategory("system.low-memory", "system", "system.low-memory", true, true);
    public static final NotificationCategory CRASH_RECOVERY =
            new NotificationCategory("system.crash-recovery", "system", "system.crash-recovery", true, true);
    public static final NotificationCategory WATCHDOG =
            new NotificationCategory("system.watchdog", "system", "system.watchdog", false, false);
    public static final NotificationCategory JAVA_COMPATIBILITY =
            new NotificationCategory("system.java-compatibility", "system", "system.java-compatibility", true, false);
    public static final NotificationCategory BITNESS =
            new NotificationCategory("system.bitness", "system", "system.bitness", true, false);
    public static final NotificationCategory SNAPSHOT =
            new NotificationCategory("system.snapshot", "system", "system.snapshot", true, false);
    public static final NotificationCategory BTN_UNBAN =
            new NotificationCategory("btn.allowlist-unban", "btn", "btn.allowlist-unban", true, false);
    public static final NotificationCategory WEB_LOGIN =
            new NotificationCategory("web.login", "web", "web.login", false, false);

    @Bean
    NotificationCategory downloaderNetworkNotificationCategory() {
        return DOWNLOADER_NETWORK;
    }

    @Bean
    NotificationCategory downloaderConnectionNotificationCategory() {
        return DOWNLOADER_CONNECTION;
    }

    @Bean
    NotificationCategory transmissionBlocklistNotificationCategory() {
        return TRANSMISSION_BLOCKLIST;
    }

    @Bean
    NotificationCategory trafficLimitNotificationCategory() {
        return TRAFFIC_LIMIT;
    }

    @Bean
    NotificationCategory lowMemoryNotificationCategory() {
        return LOW_MEMORY;
    }

    @Bean
    NotificationCategory crashRecoveryNotificationCategory() {
        return CRASH_RECOVERY;
    }

    @Bean
    NotificationCategory watchdogNotificationCategory() {
        return WATCHDOG;
    }

    @Bean
    NotificationCategory javaCompatibilityNotificationCategory() {
        return JAVA_COMPATIBILITY;
    }

    @Bean
    NotificationCategory bitnessNotificationCategory() {
        return BITNESS;
    }

    @Bean
    NotificationCategory snapshotNotificationCategory() {
        return SNAPSHOT;
    }

    @Bean
    NotificationCategory btnUnbanNotificationCategory() {
        return BTN_UNBAN;
    }

    @Bean
    NotificationCategory webLoginNotificationCategory() {
        return WEB_LOGIN;
    }
}
