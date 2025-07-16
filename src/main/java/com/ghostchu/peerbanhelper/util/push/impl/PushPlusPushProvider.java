package com.ghostchu.peerbanhelper.util.push.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.push.AbstractPushProvider;
import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public final class PushPlusPushProvider extends AbstractPushProvider {
    private final Config config;
    private final String name;
    private final HTTPUtil httpUtil;

    public PushPlusPushProvider(String name, Config config, HTTPUtil httpUtil) {
        this.name = name;
        this.config = config;
        this.httpUtil = httpUtil;
    }

    @Override
    public JsonObject saveJson() {
        return JsonUtil.readObject(JsonUtil.standard().toJson(config));
    }

    @Override
    public ConfigurationSection saveYaml() {
        YamlConfiguration section = new YamlConfiguration();
        section.set("type", "pushplus");
        section.set("token", config.getToken());
        section.set("topic", config.getTopic());
        section.set("channel", config.getChannel());
        return section;
    }

    public static PushPlusPushProvider loadFromJson(String name, JsonObject json, HTTPUtil httpUtil) {
        return new PushPlusPushProvider(name, JsonUtil.getGson().fromJson(json, Config.class), httpUtil);
    }

    public static PushPlusPushProvider loadFromYaml(String name, ConfigurationSection section, HTTPUtil httpUtil) {
        var token = section.getString("token", "");
        var topic = section.getString("topic", "");
        var channel = section.getString("channel", "");
        if (topic.isBlank()) {
            topic = null;
        }
        if (channel.isBlank()) {
            channel = null;
        }
        Config config = new Config(token, topic, channel);
        return new PushPlusPushProvider(name, config, httpUtil);

    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getConfigType() {
        return "pushplus";
    }

    @Override
    public boolean push(String title, String content) {
        Map<String, Object> args = new HashMap<>() {{
            put("token", config.getToken());
            if (config.getTopic() != null) {
                put("topic", config.getTopic());
            }
            if (config.getChannel() != null) {
                put("channel", config.getChannel());
            }
            put("title", title);
            put("content", content);
            put("template", "markdown");
        }};
        
        RequestBody requestBody = RequestBody.create(
                JsonUtil.getGson().toJson(args),
                MediaType.parse("application/json")
        );
        
        Request request = new Request.Builder()
                .url("https://www.pushplus.plus/send")
                .post(requestBody)
                .header("Content-Type", "application/json")
                .build();
        
        try (Response response = httpUtil.newBuilder().build().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("HTTP Failed while sending push messages to PushPlus: " + response.body().string());
            }
            String responseBody = response.body().string();
            PushPlusResponse ppr = JsonUtil.getGson().fromJson(responseBody, PushPlusResponse.class);
            if (ppr.getCode() != 200) {
                throw new IllegalStateException("HTTP Failed while sending push messages to PushPlus: " + ppr.getMsg());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send push message to PushPlus", e);
        }
        return true;
    }

    @AllArgsConstructor
    @Data
    public static class Config {
        private String token;
        private String topic;
        private String channel;
    }

    @NoArgsConstructor
    @Data
    public static class PushPlusResponse {

        @JsonProperty("code")
        private Integer code;
        @JsonProperty("msg")
        private String msg;
    }
}