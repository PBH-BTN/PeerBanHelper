package com.ghostchu.peerbanhelper.util.push.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.push.AbstractPushProvider;
import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.Map;

public final class TelegramPushProvider extends AbstractPushProvider {

    private final Config config;
    private final String name;
    private final HTTPUtil httpUtil;

    public TelegramPushProvider(String name, Config config, HTTPUtil httpUtil) {
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
        return "telegram";
    }

    public static TelegramPushProvider loadFromJson(String name, JsonObject json, HTTPUtil httpUtil) {
        return new TelegramPushProvider(name, JsonUtil.getGson().fromJson(json, Config.class), httpUtil);
    }

    public static TelegramPushProvider loadFromYaml(String name, ConfigurationSection section, HTTPUtil httpUtil) {
        var token = section.getString("token", "");
        var chatid = section.getString("chatid", "");
        Config config = new Config(token, chatid);
        return new TelegramPushProvider(name, config, httpUtil);
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
    public boolean push(String title, String content) {
        String markdown = "*" + title + "*\n" + content;
        Map<String, Object> map = new HashMap<>();
        map.put("chat_id", config.getChatId());
        map.put("caption", markdown);
        map.put("text", markdown);
        map.put("photo", "https://raw.githubusercontent.com/PBH-BTN/PeerBanHelper/refs/heads/master/src/main/resources/assets/icon.png");
        map.put("parse_mode", "Markdown");
        
        RequestBody requestBody = RequestBody.create(
                JsonUtil.getGson().toJson(map),
                MediaType.parse("application/json")
        );
        
        Request request = new Request.Builder()
                .url("https://api.telegram.org/bot" + config.getToken() + "/sendPhoto")
                .post(requestBody)
                .header("Content-Type", "application/json")
                .build();
        
        try (Response response = httpUtil.newBuilder().build().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body().string();
                TelegramErrResponse tgr = JsonUtil.getGson().fromJson(responseBody, TelegramErrResponse.class);
                throw new IllegalStateException("HTTP Failed while sending push messages to Telegram: " + tgr.getDescription());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send push message to Telegram", e);
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
