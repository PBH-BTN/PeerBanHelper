package com.ghostchu.peerbanhelper.util.push;

import com.ghostchu.peerbanhelper.alert.AlertLevel;
import com.google.gson.JsonObject;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

import java.io.IOException;

public interface PushManager {
    PushProvider createPushProvider(String name, String type, ConfigurationSection section);

    PushProvider createPushProvider(String name, String type, JsonObject jsonObject);

    boolean addPushProvider(PushProvider provider);

    boolean removePushProvider(PushProvider provider);

    void savePushProviders() throws IOException;

    boolean pushMessage(String title, String description, AlertLevel level);

    java.util.List<PushProvider> getProviderList();
}
