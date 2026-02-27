package com.ghostchu.peerbanhelper.util.push.impl;

import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.push.AbstractPushProvider;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.util.Base64;
import java.nio.charset.StandardCharsets;

public final class NtfyPushProvider extends AbstractPushProvider {

    private final Config config;
    private final String name;
    private final HTTPUtil httpUtil;

    public NtfyPushProvider(String name, Config config, HTTPUtil httpUtil) {
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
        return "ntfy";
    }

    @Override
    public JsonObject saveJson() {
        return JsonUtil.readObject(JsonUtil.standard().toJson(config));
    }

    @Override
    public ConfigurationSection saveYaml() {
        YamlConfiguration section = new YamlConfiguration();
        section.set("type", "ntfy");
        section.set("server_url", config.getServerUrl());
        section.set("topic", config.getTopic());
        section.set("token", config.getToken());
        section.set("priority", config.getPriority());
        section.set("tags", config.getTags());
        return section;
    }

    public static NtfyPushProvider loadFromJson(String name, JsonObject json, HTTPUtil httpUtil) {
        return new NtfyPushProvider(name, JsonUtil.getGson().fromJson(json, Config.class), httpUtil);
    }

    public static NtfyPushProvider loadFromYaml(String name, ConfigurationSection section, HTTPUtil httpUtil) {
        var serverUrl = section.getString("server_url", "https://ntfy.sh");
        var topic = section.getString("topic", "");
        var token = section.getString("token", "");
        var priority = section.getInt("priority", 3);
        var tags = section.getString("tags", "");
        Config config = new Config(serverUrl, topic, token, priority, tags);
        return new NtfyPushProvider(name, config, httpUtil);
    }

    @Override
    public boolean push(String title, String content) {

        RequestBody requestBody = RequestBody.create(
                content,
                MediaType.parse("text/plain; charset=utf-8")
        );

        String encodedTitle = "=?UTF-8?B?" + Base64.getEncoder().encodeToString(title.getBytes(StandardCharsets.UTF_8)) + "?=";

        Request request = new Request.Builder()
                .url(config.getServerUrl() + "/" + config.getTopic())
                .post(requestBody)
                .header("Authorization", "Bearer " + config.getToken())
                .header("Title", encodedTitle)
                .header("Priority", String.valueOf(config.getPriority()))
                .header("Tags", config.getTags())
                .header("Icon", "https://raw.githubusercontent.com/PBH-BTN/PeerBanHelper/refs/heads/master/src/main/resources/assets/icon.png")
                .build();
        
        try (Response response = httpUtil.newBuilder().build().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("HTTP Failed while sending push messages to Ntfy: " + response.body().string());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send push message to Ntfy", e);
        }
        return true;
    }

    @AllArgsConstructor
    @Data
    public static class Config {
        @SerializedName("server_url")
        private String serverUrl;
        @SerializedName("topic")
        private String topic;
        @SerializedName("token")
        private String token;
        @SerializedName("priority")
        private int priority;
        @SerializedName("tags")
        private String tags;
    }

}
