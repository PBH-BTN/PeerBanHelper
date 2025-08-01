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

public final class PushDeerPushProvider extends AbstractPushProvider {

    private final Config config;
    private final String name;
    private final HTTPUtil httpUtil;

    public PushDeerPushProvider(String name, Config config, HTTPUtil httpUtil) {
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
        return "pushdeer";
    }

    @Override
    public JsonObject saveJson() {
        return JsonUtil.readObject(JsonUtil.standard().toJson(config));
    }

    @Override
    public ConfigurationSection saveYaml() {
        YamlConfiguration section = new YamlConfiguration();
        section.set("type", "pushdeer");
        section.set("endpoint", config.getEndpoint());
        section.set("pushkey", config.getPushKey());
        return section;
    }

    public static PushDeerPushProvider loadFromJson(String name, JsonObject json, HTTPUtil httpUtil) {
        return new PushDeerPushProvider(name, JsonUtil.getGson().fromJson(json, Config.class), httpUtil);
    }

    public static PushDeerPushProvider loadFromYaml(String name, ConfigurationSection section, HTTPUtil httpUtil) {
        var endpoint = section.getString("endpoint", "https://api2.pushdeer.com/message/push");
        var pushKey = section.getString("pushkey", "");
        Config config = new Config(endpoint, pushKey);
        return new PushDeerPushProvider(name, config, httpUtil);
    }

    @Override
    public boolean push(String title, String content) {
        Map<String, Object> map = new HashMap<>();
        map.put("pushkey", config.getPushKey());
        map.put("text", title);
        map.put("desp", content);
        map.put("type", "text");

        FormBody.Builder builder = new FormBody.Builder();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            builder.add(entry.getKey(), String.valueOf(entry.getValue()));
        }

        Request request = new Request.Builder()
                .url(config.getEndpoint())
                .post(builder.build())
                .build();

        try (Response response = httpUtil.retryableSend(request).join()) {
            if (response.isSuccessful()) {
                String responseBody = response.body().string();
                JsonObject json = JsonUtil.readObject(responseBody);
                return json.getAsJsonPrimitive("code").getAsInt() == 0;
            }
        } catch (Exception e) {
            logger.error("Error sending push notification", e);
        }
        return false;
    }

    @Data
    @AllArgsConstructor
    public static class Config {
        @SerializedName("endpoint")
        private String endpoint;
        @SerializedName("pushkey")
        private String pushKey;
    }
}