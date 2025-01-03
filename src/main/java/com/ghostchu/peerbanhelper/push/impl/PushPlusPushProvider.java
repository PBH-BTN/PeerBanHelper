package com.ghostchu.peerbanhelper.push.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.peerbanhelper.push.AbstractPushProvider;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public final class PushPlusPushProvider extends AbstractPushProvider {
    private final Config config;
    private final String name;

    public PushPlusPushProvider(String name, Config config) {
        this.name = name;
        this.config = config;
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

    public static PushPlusPushProvider loadFromJson(String name, JsonObject json) {
        return new PushPlusPushProvider(name, JsonUtil.getGson().fromJson(json, Config.class));
    }

    public static PushPlusPushProvider loadFromYaml(String name, ConfigurationSection section) {
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
        return new PushPlusPushProvider(name, config);

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
    public boolean push(String title, String content) throws IOException, InterruptedException {
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
        HttpResponse<String> resp = HTTPUtil.retryableSend(HTTPUtil.getHttpClient(false, null),
                MutableRequest.POST("https://www.pushplus.plus/send"
                                , HttpRequest.BodyPublishers.ofString(JsonUtil.getGson().toJson(args)))
                        .header("Content-Type", "application/json")
                , java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        ).join();
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("HTTP Failed while sending push messages to PushPlus: " + resp.body());
        } else {
            PushPlusResponse ppr = JsonUtil.getGson().fromJson(resp.body(), PushPlusResponse.class);
            if (ppr.getCode() != 200) {
                throw new IllegalStateException("HTTP Failed while sending push messages to PushPlus: " + ppr.getMsg());
            }
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