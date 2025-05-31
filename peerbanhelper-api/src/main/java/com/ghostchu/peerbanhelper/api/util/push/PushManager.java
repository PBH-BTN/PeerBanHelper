package com.ghostchu.peerbanhelper.api.util.push;

import com.google.gson.JsonObject;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

import java.io.IOException;

public interface PushManager {
    PushProvider createPushProvider(String name, String type, ConfigurationSection section);

    PushProvider createPushProvider(String name, String type, JsonObject jsonObject);

    boolean addPushProvider(PushProvider provider);

    boolean removePushProvider(PushProvider provider);

    void savePushProviders() throws IOException;

    boolean pushMessage(String title, String description);

    java.util.List<PushProvider> getProviderList();
}
