package com.ghostchu.peerbanhelper.util.push.impl;

import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.push.AbstractPushProvider;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;
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
        section.set("endpoint", config.getEndpoint());
        section.set("priority", config.getPriority());
        return section;
    }

    public static GotifyPushProvider loadFromJson(String name, JsonObject json, HTTPUtil httpUtil) {
        return new GotifyPushProvider(name, JsonUtil.getGson().fromJson(json, Config.class), httpUtil);
    }

    public static GotifyPushProvider loadFromYaml(String name, ConfigurationSection section, HTTPUtil httpUtil) {
        var backendUrl = section.getString("endpoint", "https://push.example.de/message?token=<apptoken>");
        var priority = section.getInt("priority", 5);
        Config config = new Config(backendUrl, priority);
        return new GotifyPushProvider(name, config, httpUtil);
    }

    @Override
    public boolean push(String title, String content) {
        Map<String, Object> map = new HashMap<>();
        map.put("title", title);
        map.put("message", stripMarkdown(content));

        FormBody.Builder formBody = new FormBody.Builder();

        var form = formBody.add("title", title)
                .add("message", stripMarkdown(content))
                .add("priority", String.valueOf(config.getPriority())).build();

        Request request = new Request.Builder()
                .url(config.getEndpoint())
                .post(form)
                .header("Content-Type", "application/x-www-form-urlencoded")
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
        @SerializedName("endpoint")
        private String endpoint;
        @SerializedName("priority")
        private int priority;
    }

}
