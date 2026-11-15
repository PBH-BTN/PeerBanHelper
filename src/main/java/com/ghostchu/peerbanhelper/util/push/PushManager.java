package com.ghostchu.peerbanhelper.util.push;

import com.google.gson.JsonObject;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

import java.util.List;

public interface PushManager {
    PushProvider createPushProvider(String name, String type, ConfigurationSection section);

    PushProvider createPushProvider(String name, String type, JsonObject jsonObject);

    List<PushProvider> getProviderList();
}
