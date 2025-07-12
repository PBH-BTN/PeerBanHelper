package com.ghostchu.peerbanhelper.util.push.impl;

import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.push.AbstractPushProvider;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import okhttp3.*;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.Map;

public final class BarkPushProvider extends AbstractPushProvider {

    private final Config config;
    private final String name;
    private final HTTPUtil httpUtil;

    public BarkPushProvider(String name, Config config, HTTPUtil httpUtil) {
        this.name = name;
        this.config = config;
        this.httpUtil = httpUtil;
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
        section.set("backend_url", config.getBackendUrl());
        section.set("device_key", config.getDeviceKey());
        section.set("message_group", config.getMessageGroup());
        return section;
    }

    public static BarkPushProvider loadFromJson(String name, JsonObject json, HTTPUtil httpUtil) {
        return new BarkPushProvider(name, JsonUtil.getGson().fromJson(json, Config.class), httpUtil);
    }

    public static BarkPushProvider loadFromYaml(String name, ConfigurationSection section, HTTPUtil httpUtil) {
        var backendUrl = section.getString("backend_url", "https://api.day.app/push");
        var sendKey = section.getString("device_key", "");
        var group = section.getString("message_group", "");
        Config config = new Config(backendUrl, sendKey, group);
        return new BarkPushProvider(name, config, httpUtil);
    }

    @Override
    public boolean push(String title, String content) {
        Map<String, Object> map = new HashMap<>();
        map.put("title", title);
        map.put("body", content);
        map.put("device_key", config.getDeviceKey());
        map.put("group", config.getMessageGroup());
        map.put("icon", "https://raw.githubusercontent.com/PBH-BTN/PeerBanHelper/refs/heads/master/src/main/resources/assets/icon.png");
        
        RequestBody requestBody = RequestBody.create(
                JsonUtil.getGson().toJson(map),
                MediaType.parse("application/json")
        );
        
        Request request = new Request.Builder()
                .url(config.getBackendUrl())
                .post(requestBody)
                .header("Content-Type", "application/json")
                .build();
        
        try (Response response = httpUtil.newBuilder().build().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("HTTP Failed while sending push messages to Bark: " + response.body().string());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send push message to Bark", e);
        }
        return true;
    }

    @AllArgsConstructor
    @Data
    public static class Config {
        @SerializedName("backend_url")
        private String backendUrl;
        @SerializedName("device_key")
        private String deviceKey;
        @SerializedName("message_group")
        private String messageGroup;
    }

}
