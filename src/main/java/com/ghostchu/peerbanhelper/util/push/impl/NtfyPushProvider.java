package com.ghostchu.peerbanhelper.util.push.impl;

import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.push.AbstractPushProvider;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

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
        return section;
    }

    public static NtfyPushProvider loadFromJson(String name, JsonObject json, HTTPUtil httpUtil) {
        return new NtfyPushProvider(name, JsonUtil.getGson().fromJson(json, Config.class), httpUtil);
    }

    public static NtfyPushProvider loadFromYaml(String name, ConfigurationSection section, HTTPUtil httpUtil) {
        var serverUrl = section.getString("server_url", "https://ntfy.sh");
        var topic = section.getString("topic", "");
        var token = section.getString("token", "");
        Config config = new Config(serverUrl, topic, token);
        return new NtfyPushProvider(name, config, httpUtil);
    }

    @Override
    public boolean push(String title, String content) {
        var serverUrl = config.getServerUrl().replaceAll("/+$", "");
        var parsedUrl = HttpUrl.parse(serverUrl);
        if (parsedUrl == null) {
            throw new IllegalStateException("Invalid ntfy server URL: " + serverUrl);
        }
        var url = parsedUrl.newBuilder().addPathSegment(config.getTopic()).build();

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(stripMarkdown(content), MediaType.parse("text/plain")))
                .header("Title", title);

        if (config.getToken() != null && !config.getToken().isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + config.getToken());
        }

        try (Response response = httpUtil.newBuilder().build().newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                var body = response.body();
                var bodyStr = body != null ? body.string() : "(empty)";
                throw new IllegalStateException("HTTP Failed while sending push messages to Ntfy: " + bodyStr);
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
    }

}
