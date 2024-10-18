package com.ghostchu.peerbanhelper.push.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.peerbanhelper.push.PushProvider;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import lombok.Data;
import lombok.NoArgsConstructor;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

import static com.ghostchu.peerbanhelper.util.HTTPUtil.MEDIA_TYPE_JSON;

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
        try (Response resp = HTTPUtil.retryableSend(HTTPUtil.getHttpClient(false, null),
                new Request.Builder().url("https://sctapi.ftqq.com/" + sendKey + ".send")
                        .post(RequestBody.create(JsonUtil.getGson().toJson(map),MEDIA_TYPE_JSON))
                        .header("Content-Type", "application/json")
                        .build()
        ).join()) {
            if (resp.code() != 200) {
                ServerChanResponse scr = JsonUtil.getGson().fromJson(resp.body().string(), ServerChanResponse.class);
                throw new IllegalStateException("HTTP Failed while sending push messages to ServerChan: " + scr.getMessage());
            }
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
