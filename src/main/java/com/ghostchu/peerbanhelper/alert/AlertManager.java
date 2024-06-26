package com.ghostchu.peerbanhelper.alert;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.event.NewAlertCreated;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AlertManager {
    private final Map<String, Alert> alerts = new ConcurrentHashMap<>();

    public void addAlert(@NotNull Alert alert) {
        if (alerts.containsKey(alert.id())) {
            return;
        }
        if (alerts.put(alert.id(), alert) == null) {
            Main.getEventBus().register(new NewAlertCreated(alert));
        }
    }

    public boolean removeAlert(@NotNull String id) {
        return alerts.remove(id) != null;
    }

    public boolean removeAlert(@NotNull Alert alert) {
        return alerts.remove(alert.id()) != null;
    }

    @NotNull
    public List<Alert> getAlerts() {
        return ImmutableList.copyOf(alerts.values());
    }
}
