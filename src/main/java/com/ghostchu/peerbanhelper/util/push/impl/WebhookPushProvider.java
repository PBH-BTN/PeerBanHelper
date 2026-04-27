package com.ghostchu.peerbanhelper.util.push.impl;

import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.push.AbstractPushProvider;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WebhookPushProvider extends AbstractPushProvider {
    private static final String DEFAULT_METHOD = "POST";
    private static final String DEFAULT_CONTENT_TYPE = "application/json";
    private static final String DEFAULT_BODY_TEMPLATE = """
    {
        "title":"{title}",
        "content":"{content}",
        "level":"{level}",
        "date":"{date}",
        "time":"{time}",
        "datetime":"{datetime}",
        "channelName":"{channelName}"
    }
    """;
    private static final Pattern LEVEL_PATTERN = Pattern.compile("\\[PeerBanHelper/([^]]+)]");

    private final Config config;
    private final String name;
    private final HTTPUtil httpUtil;

    public WebhookPushProvider(String name, Config config, HTTPUtil httpUtil) {
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
        return "webhook";
    }

    @Override
    public JsonObject saveJson() {
        return JsonUtil.readObject(JsonUtil.standard().toJson(config));
    }

    @Override
    public ConfigurationSection saveYaml() {
        YamlConfiguration section = new YamlConfiguration();
        section.set("type", "webhook");
        section.set("url", config.getUrl());
        section.set("method", config.getMethod());
        section.set("content_type", config.getContentType());
        section.set("body_template", config.getBodyTemplate());
        section.set("headers", config.getHeaders());
        return section;
    }

    public static WebhookPushProvider loadFromJson(String name, JsonObject json, HTTPUtil httpUtil) {
        Config config = JsonUtil.getGson().fromJson(json, Config.class);
        if (config.getMethod() == null || config.getMethod().isBlank()) {
            config.setMethod(DEFAULT_METHOD);
        }
        if (config.getContentType() == null || config.getContentType().isBlank()) {
            config.setContentType(DEFAULT_CONTENT_TYPE);
        }
        if (config.getBodyTemplate() == null || config.getBodyTemplate().isBlank()) {
            config.setBodyTemplate(DEFAULT_BODY_TEMPLATE);
        }
        if (config.getHeaders() == null) {
            config.setHeaders(new HashMap<>());
        }
        return new WebhookPushProvider(name, config, httpUtil);
    }

    public static WebhookPushProvider loadFromYaml(String name, ConfigurationSection section, HTTPUtil httpUtil) {
        var url = section.getString("url", "");
        var method = section.getString("method", DEFAULT_METHOD);
        var contentType = section.getString("content_type", DEFAULT_CONTENT_TYPE);
        var bodyTemplate = section.getString(
                "body_template",
                DEFAULT_BODY_TEMPLATE
        );
        Map<String, String> headers = new HashMap<>();
        var headersSection = section.getConfigurationSection("headers");
        if (headersSection != null) {
            for (String key : headersSection.getKeys(false)) {
                headers.put(key, headersSection.getString(key, ""));
            }
        }
        Config config = new Config(url, method, contentType, bodyTemplate, headers);
        return new WebhookPushProvider(name, config, httpUtil);
    }

    @Override
    public boolean push(String title, String content) {
        if (config.getUrl() == null || config.getUrl().isBlank()) {
            throw new IllegalArgumentException("Webhook URL cannot be empty");
        }

        String method = normalizeMethod(config.getMethod());
        String contentType = normalizeContentType(config.getContentType());
        String bodyTemplate = config.getBodyTemplate() == null ? "" : config.getBodyTemplate();
        String renderedBody = renderTemplate(bodyTemplate, title, content);
        RequestBody requestBody = createRequestBody(method, contentType, renderedBody);

        Request.Builder requestBuilder = new Request.Builder().url(config.getUrl()).method(method, requestBody);
        applyContentType(requestBuilder, method, requestBody, contentType);
        applyCustomHeaders(requestBuilder);
        Request request = requestBuilder.build();
        try (Response response = httpUtil.newBuilder().build().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "<empty>";
                throw new IllegalStateException("HTTP failed while sending push messages to Webhook: " + responseBody);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send push message to Webhook", e);
        }
        return true;
    }

    private String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            return DEFAULT_METHOD;
        }
        String normalized = method.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "GET", "POST" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported webhook method: " + method);
        };
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return DEFAULT_CONTENT_TYPE;
        }
        return contentType;
    }

    private RequestBody createRequestBody(String method, String contentType, String bodyContent) {
        if ("GET".equals(method)) {
            return null;
        }
        if (bodyContent.isEmpty()) {
            if ("POST".equals(method)) {
                return RequestBody.create("", MediaType.parse(contentType));
            }
            return null;
        }
        return RequestBody.create(bodyContent, MediaType.parse(contentType));
    }

    private void applyContentType(Request.Builder requestBuilder, String method, RequestBody requestBody, String contentType) {
        if (!"GET".equals(method) && requestBody != null) {
            requestBuilder.header("Content-Type", contentType);
        }
    }

    private void applyCustomHeaders(Request.Builder requestBuilder) {
        if (config.getHeaders() == null) {
            return;
        }
        config.getHeaders().forEach((headerKey, headerValue) -> {
            if (headerKey != null && !headerKey.isBlank() && headerValue != null) {
                requestBuilder.header(headerKey, headerValue);
            }
        });
    }

    private String renderTemplate(String template, String title, String content) {
        OffsetDateTime now = OffsetDateTime.now();
        String date = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String time = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String datetime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return template
                .replace("{title}", title)
                .replace("{content}", content)
                .replace("{level}", extractLevel(title))
                .replace("{date}", date)
                .replace("{time}", time)
                .replace("{datetime}", datetime)
                .replace("{channelName}", name);
    }

    private String extractLevel(String title) {
        Matcher matcher = LEVEL_PATTERN.matcher(title);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "INFO";
    }

    @AllArgsConstructor
    @Data
    public static class Config {
        @SerializedName("url")
        private String url;
        @SerializedName("method")
        private String method;
        @SerializedName("content_type")
        private String contentType;
        @SerializedName("body_template")
        private String bodyTemplate;
        @SerializedName("headers")
        private Map<String, String> headers;
    }
}