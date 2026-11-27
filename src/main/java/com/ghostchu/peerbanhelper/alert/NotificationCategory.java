package com.ghostchu.peerbanhelper.alert;

/** A module owns a stable, namespaced ID and the defaults for its events. */
public record NotificationCategory(String id, String group, String name, boolean keepHistory,
                                   boolean defaultExternal) {
}
