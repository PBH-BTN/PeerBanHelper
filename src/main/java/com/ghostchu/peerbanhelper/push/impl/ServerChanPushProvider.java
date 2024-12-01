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

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ServerChanPushProvider extends AbstractPushProvider {

    private Config config;

    public ServerChanPushProvider(Config config) {
        this.config = config;
    }

    @Override
    public String getConfigType() {
        return "serverchan";
    }

    @Override
    public JsonObject saveJson() {
        return JsonUtil.readObject(JsonUtil.standard().toJson(config));
    }

    public static ServerChanPushProvider loadFromJson(JsonObject json) {
        return new ServerChanPushProvider(JsonUtil.getGson().fromJson(json, Config.class));
    }

    public static ServerChanPushProvider loadFromYaml(ConfigurationSection section) {
        var sendKey = section.getString("send-key", "");
        var channel = section.getString("channel", "");
        var openid = section.getString("openid", "");
        if (channel.isBlank()) {
            channel = null;
        }
        if (openid.isBlank()) {
            openid = null;
        }
        Config config = new Config(sendKey, channel, openid);
        return new ServerChanPushProvider(config);
    }

    @Override
    public boolean push(String title, String content) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("title", title);
        map.put("desp", content);
        map.put("text", title);
        if (config.getChannel() != null) {
            map.put("channel", config.getChannel());
        }
        if (config.getOpenid() != null) {
            map.put("openid", config.getOpenid());
        }
        HttpResponse<String> resp = HTTPUtil.retryableSend(HTTPUtil.getHttpClient(false, null),
                MutableRequest.POST("https://sctapi.ftqq.com/" + config.getSendKey() + ".send"
                                , HttpRequest.BodyPublishers.ofString(JsonUtil.getGson().toJson(map)))
                        .header("Content-Type", "application/json")
                , java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        ).join();
        if (resp.statusCode() != 200) {
            ServerChanResponse scr = JsonUtil.getGson().fromJson(resp.body(), ServerChanResponse.class);
            throw new IllegalStateException("HTTP Failed while sending push messages to ServerChan: " + scr.getMessage());
        }
        return true;
    }
    @AllArgsConstructor
    @Data
    public static class Config{
        private String sendKey;
        private String channel;
        private String openid;
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
