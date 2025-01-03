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
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class TelegramPushProvider extends AbstractPushProvider {

    private final Config config;
    private final String name;

    public TelegramPushProvider(String name, Config config) {
        this.name = name;
        this.config = config;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getConfigType() {
        return "telegram";
    }

    public static TelegramPushProvider loadFromJson(String name, JsonObject json) {
        return new TelegramPushProvider(name, JsonUtil.getGson().fromJson(json, Config.class));
    }

    public static TelegramPushProvider loadFromYaml(String name, ConfigurationSection section) {
        var token = section.getString("token", "");
        var chatid = section.getString("chatid", "");
        Config config = new Config(token, chatid);
        return new TelegramPushProvider(name, config);
    }

    @Override
    public JsonObject saveJson() {
        return JsonUtil.readObject(JsonUtil.standard().toJson(config));
    }

    @Override
    public ConfigurationSection saveYaml() {
        YamlConfiguration section = new YamlConfiguration();
        section.set("type", "telegram");
        section.set("token", config.getToken());
        section.set("chatid", config.getChatId());
        return section;
    }

    @Override
    public boolean push(String title, String content) throws Exception {
        String markdown = "*" + title + "*\n" + content;
        Map<String, Object> map = new HashMap<>();
        map.put("chat_id", config.getChatId());
        map.put("caption", markdown);
        map.put("text", markdown);
        map.put("photo", "https://raw.githubusercontent.com/PBH-BTN/PeerBanHelper/refs/heads/master/src/main/resources/assets/icon.png");
        map.put("parse_mode", "Markdown");
        HttpResponse<String> resp = HTTPUtil.retryableSend(HTTPUtil.getHttpClient(false, null),
                MutableRequest.POST("https://api.telegram.org/bot" + config.getToken() + "/sendPhoto"
                                , HttpRequest.BodyPublishers.ofString(JsonUtil.getGson().toJson(map)))
                        .header("Content-Type", "application/json")
                , java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        ).join();
        if (resp.statusCode() != 200) {
            TelegramErrResponse tgr = JsonUtil.getGson().fromJson(resp.body(), TelegramErrResponse.class);
            throw new IllegalStateException("HTTP Failed while sending push messages to Telegram: " + tgr.getDescription());
        }
        return true;
    }
    @AllArgsConstructor
    @Data
    public static class Config{
        private String token;
        private String chatId;
    }

    @NoArgsConstructor
    @Data
    public static class TelegramErrResponse {

        @JsonProperty("ok")
        private Boolean ok;
        @JsonProperty("error_code")
        private Integer error_code;
        @JsonProperty("description")
        private String description;
    }
}
