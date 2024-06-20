package com.ghostchu.peerbanhelper.alert;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.event.NewAlertCreated;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AlertManager {
    private Map<String, Alert> alerts = new ConcurrentHashMap<>();

    public void addAlert(Alert alert) {
        if (alerts.put(alert.id(), alert) == null) {
            Main.getEventBus().register(new NewAlertCreated(alert));
        }
    }

    public boolean removeAlert(String id) {
        return alerts.remove(id) != null;
    }

    public boolean removeAlert(Alert alert) {
        return alerts.remove(alert.id()) != null;
    }

    public List<Alert> getAlerts() {
        return ImmutableList.copyOf(alerts.values());
    }
}
