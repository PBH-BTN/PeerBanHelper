package com.ghostchu.peerbanhelper.push.impl;

import com.ghostchu.peerbanhelper.push.PushProvider;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.ghostchu.peerbanhelper.util.HTTPUtil.MEDIA_TYPE_JSON;

@Slf4j
public class PushPlusPushProvider implements PushProvider {
    private final String token;
    private String topic;
    private String template;
    private String channel;

    public PushPlusPushProvider(ConfigurationSection section) {
        this.token = section.getString("token", "");
        this.topic = section.getString("topic", "");
        this.template = section.getString("template", "");
        this.channel = section.getString("channel", "");
        if (topic.isBlank()) {
            topic = null;
        }
        if (template.isBlank()) {
            template = null;
        }
        if (channel.isBlank()) {
            channel = null;
        }
    }

    @Override
    public boolean push(String title, String content) throws IOException, InterruptedException {
        Map<String, Object> args = new HashMap<>() {{
            put("token", token);
            if (topic != null) {
                put("topic", topic);
            }
            if (template != null) {
                put("template", template);
            }
            if (channel != null) {
                put("channel", channel);
            }
            put("title", title);
            put("content", content);
        }};
        try (Response resp = HTTPUtil.retryableSend(HTTPUtil.getHttpClient(false, null),
                new Request.Builder().url("https://www.pushplus.plus/send")
                        .post(RequestBody.create(JsonUtil.getGson().toJson(args),MEDIA_TYPE_JSON))
                        .header("Content-Type", "application/json")
                        .build()
                ).join()) {
            if (resp.code() != 200) {
                throw new IllegalStateException("HTTP Failed while sending push messages to PushPlus: " + resp.body().string());
            }
        }
        return true;
    }
}