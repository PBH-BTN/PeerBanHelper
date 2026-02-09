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

public final class ServerChanPushProvider extends AbstractPushProvider {

    private final Config config;
    private final String name;
    private final HTTPUtil httpUtil;

    public ServerChanPushProvider(String name, Config config, HTTPUtil httpUtil) {
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
        return "serverchan";
    }

    @Override
    public JsonObject saveJson() {
        return JsonUtil.readObject(JsonUtil.standard().toJson(config));
    }

    @Override
    public ConfigurationSection saveYaml() {
        YamlConfiguration section = new YamlConfiguration();
        section.set("type", "serverchan");
        section.set("sendkey", config.getSendKey());
        section.set("channel", config.getChannel());
        section.set("openid", config.getOpenId());
        return section;
    }

    public static ServerChanPushProvider loadFromJson(String name, JsonObject json, HTTPUtil httpUtil) {
        return new ServerChanPushProvider(name, JsonUtil.getGson().fromJson(json, Config.class), httpUtil);
    }

    public static ServerChanPushProvider loadFromYaml(String name, ConfigurationSection section, HTTPUtil httpUtil) {
        var sendKey = section.getString("sendkey", "");
        var channel = section.getString("channel", "");
        var openid = section.getString("openid", "");
        if (channel.isBlank()) {
            channel = null;
        }
        if (openid.isBlank()) {
            openid = null;
        }
        Config config = new Config(sendKey, channel, openid);
        return new ServerChanPushProvider(name, config, httpUtil);
    }

    @Override
    public boolean push(String title, String content) {
        Map<String, Object> map = new HashMap<>();
        map.put("title", title);
        map.put("desp", content);
        map.put("text", title);
        if (config.getChannel() != null) {
            map.put("channel", config.getChannel());
        }
        if (config.getOpenId() != null) {
            map.put("openid", config.getOpenId());
        }
        
        RequestBody requestBody = RequestBody.create(
                JsonUtil.getGson().toJson(map),
                MediaType.parse("application/json")
        );
        
        Request request = new Request.Builder()
                .url("https://sctapi.ftqq.com/" + config.getSendKey() + ".send")
                .post(requestBody)
                .header("Content-Type", "application/json")
                .build();
        
        try (Response response = httpUtil.newBuilder().build().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body().string();
                ServerChanResponse scr = JsonUtil.getGson().fromJson(responseBody, ServerChanResponse.class);
                throw new IllegalStateException("HTTP Failed while sending push messages to ServerChan: " + scr.getMessage());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send push message to ServerChan", e);
        }
        return true;
    }
    @AllArgsConstructor
    @Data
    public static class Config{
        private String sendKey;
        private String channel;
        private String openId;
    }

    @NoArgsConstructor
    @Data
    public static class ServerChanResponse {

        @JsonProperty("message")
        private String message;
        @JsonProperty("code")
        private Integer code;
        @JsonProperty("info")
        private String info;
        @JsonProperty("scode")
        private Integer scode;
    }
}
