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

public final class GotifyPushProvider extends AbstractPushProvider {

    private final Config config;
    private final String name;
    private final HTTPUtil httpUtil;

    public GotifyPushProvider(String name, Config config, HTTPUtil httpUtil) {
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
        return "gotify";
    }

    @Override
    public JsonObject saveJson() {
        return JsonUtil.readObject(JsonUtil.standard().toJson(config));
    }

    @Override
    public ConfigurationSection saveYaml() {
        YamlConfiguration section = new YamlConfiguration();
        section.set("type", "gotify");
        section.set("server_url", config.getServerUrl());
        section.set("token", config.getToken());
        section.set("priority", config.getPriority());
        return section;
    }

    public static GotifyPushProvider loadFromJson(String name, JsonObject json, HTTPUtil httpUtil) {
        return new GotifyPushProvider(name, JsonUtil.getGson().fromJson(json, Config.class), httpUtil);
    }

    public static GotifyPushProvider loadFromYaml(String name, ConfigurationSection section, HTTPUtil httpUtil) {
        var serverUrl = section.getString("server_url", "");
        var token = section.getString("token", "");
        var priority = section.getInt("priority", 5);
        Config config = new Config(serverUrl, token, priority);
        return new GotifyPushProvider(name, config, httpUtil);
    }

    @Override
    public boolean push(String title, String content) {
        Map<String, Object> map = new HashMap<>();
        map.put("title", title);
        map.put("message", stripMarkdown(content));
        map.put("priority", config.getPriority());
        
        RequestBody requestBody = RequestBody.create(
                JsonUtil.getGson().toJson(map),
                MediaType.parse("application/json")
        );
        
        String url = config.getServerUrl();
        if (!url.endsWith("/")) {
            url += "/";
        }
        url += "message?token=" + config.getToken();
        
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .build();
        
        try (Response response = httpUtil.newBuilder().build().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("HTTP Failed while sending push messages to Gotify: " + response.body().string());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send push message to Gotify", e);
        }
        return true;
    }

    @AllArgsConstructor
    @Data
    public static class Config {
        @SerializedName("server_url")
        private String serverUrl;
        @SerializedName("token")
        private String token;
        @SerializedName("priority")
        private int priority;
    }
}