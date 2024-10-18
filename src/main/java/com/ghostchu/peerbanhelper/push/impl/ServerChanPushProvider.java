package com.ghostchu.peerbanhelper.push.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.peerbanhelper.push.PushProvider;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.github.mizosoft.methanol.MutableRequest;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ServerChanPushProvider implements PushProvider {

    private final String sendKey;
    private String channel;
    private String openid;

    public ServerChanPushProvider(ConfigurationSection section) {
        this.sendKey = section.getString("send-key", "");
        this.channel = section.getString("channel", "");
        this.openid = section.getString("openid", "");
        if (channel.isBlank()) {
            channel = null;
        }
        if (openid.isBlank()) {
            openid = null;
        }
    }

    @Override
    public boolean push(String title, String content) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("title", title);
        map.put("desp", content);
        map.put("text", title);
        if (channel != null) {
            map.put("channel", channel);
        }
        if (openid != null) {
            map.put("openid", openid);
        }
        HttpResponse<String> resp = HTTPUtil.retryableSend(HTTPUtil.getHttpClient(false, null),
                MutableRequest.POST("https://sctapi.ftqq.com/" + sendKey + ".send"
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
