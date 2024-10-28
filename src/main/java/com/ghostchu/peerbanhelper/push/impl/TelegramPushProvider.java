package com.ghostchu.peerbanhelper.push.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.peerbanhelper.push.AbstractPushProvider;
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

public class TelegramPushProvider extends AbstractPushProvider {

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
        HttpResponse<String> resp = HTTPUtil.retryableSend(HTTPUtil.getHttpClient(false, null),
                MutableRequest.POST("https://api.telegram.org/bot" + token + "/sendPhoto"
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
