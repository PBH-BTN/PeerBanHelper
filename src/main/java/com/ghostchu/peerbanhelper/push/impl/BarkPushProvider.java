package com.ghostchu.peerbanhelper.push.impl;

import com.ghostchu.peerbanhelper.push.AbstractPushProvider;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class BarkPushProvider extends AbstractPushProvider {

    private final Config config;
    private final String name;

    public BarkPushProvider(String name, Config config) {
        this.name = name;
        this.config = config;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getConfigType() {
        return "bark";
    }

    @Override
    public JsonObject saveJson() {
        return JsonUtil.readObject(JsonUtil.standard().toJson(config));
    }

    @Override
    public ConfigurationSection saveYaml() {
        YamlConfiguration section = new YamlConfiguration();
        section.set("type", "bark");
        section.set("device_key", config.getDeviceKey());
        return section;
    }

    public static BarkPushProvider loadFromJson(String name, JsonObject json) {
        return new BarkPushProvider(name, JsonUtil.getGson().fromJson(json, Config.class));
    }

    public static BarkPushProvider loadFromYaml(String name, ConfigurationSection section) {
        var sendKey = section.getString("device_key", "");
        Config config = new Config(sendKey);
        return new BarkPushProvider(name, config);
    }

    @Override
    public boolean push(String title, String content) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("title", title);
        map.put("body", content);
        map.put("device_key", config.getDeviceKey());
        HttpResponse<String> resp = HTTPUtil.retryableSend(HTTPUtil.getHttpClient(false, null),
                MutableRequest.POST("https://api.day.app/push"
                                , HttpRequest.BodyPublishers.ofString(JsonUtil.getGson().toJson(map)))
                        .header("Content-Type", "application/json")
                , HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        ).join();
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("HTTP Failed while sending push messages to Bark: " + resp.body());
        }
        return true;
    }

    @AllArgsConstructor
    @Data
    public static class Config {
        @SerializedName("device_key")
        private String deviceKey;
    }

}
