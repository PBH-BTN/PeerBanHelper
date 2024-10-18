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

public class TelegramPushProvider implements PushProvider {

    private final String token;
    private final String chatid;

    public TelegramPushProvider(ConfigurationSection section) {
        this.token = section.getString("token", "");
        this.chatid = section.getString("chat-id", "");
    }

    @Override
    public boolean push(String title, String content) throws Exception {
        String markdown = "*" + title + "*\n" + content;
        Map<String, Object> map = new HashMap<>();
        map.put("chat_id", chatid);
        map.put("caption", markdown);
        map.put("text", markdown);
        map.put("photo", "https://raw.githubusercontent.com/PBH-BTN/PeerBanHelper/refs/heads/master/src/main/resources/assets/icon.png");
        map.put("parse_mode", "Markdown");
        try (Response resp = HTTPUtil.retryableSend(HTTPUtil.getHttpClient(false, null),
                new Request.Builder().url("https://api.telegram.org/bot" + token + "/sendPhoto")
                        .post(RequestBody.create(JsonUtil.getGson().toJson(map),MEDIA_TYPE_JSON))
                        .header("Content-Type", "application/json")
                        .build()
        ).join()) {
            if (resp.code() != 200) {
                TelegramErrResponse tgr = JsonUtil.getGson().fromJson(resp.body().string(), TelegramErrResponse.class);
                throw new IllegalStateException("HTTP Failed while sending push messages to Telegram: " + tgr.getDescription());
            }
        }
        return true;
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
