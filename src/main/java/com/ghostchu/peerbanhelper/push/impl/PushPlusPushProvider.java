package com.ghostchu.peerbanhelper.push.impl;

import com.ghostchu.peerbanhelper.push.PushProvider;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.github.mizosoft.methanol.MutableRequest;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

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
        HttpResponse<String> resp = HTTPUtil.getHttpClient(false, null).send(
                MutableRequest.POST("https://www.pushplus.plus/send"
                                , HttpRequest.BodyPublishers.ofString(JsonUtil.getGson().toJson(args)))
                        .header("Content-Type", "application/json")
                , java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("HTTP Failed while sending push messages to PushPlus: " + resp.body());
        }
        return false;
    }
}
