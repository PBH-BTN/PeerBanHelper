package com.ghostchu.peerbanhelper.util.push.impl;

import com.ghostchu.peerbanhelper.alert.AlertLevel;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.TimeUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.push.AbstractPushProvider;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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
        section.set("content-type", config.getContentType());
        section.set("body-template", config.getBodyTemplate());
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
            config.setHeaders(Collections.emptyMap());
        }
        return new WebhookPushProvider(name, config, httpUtil);
    }

    public static WebhookPushProvider loadFromYaml(String name, ConfigurationSection section, HTTPUtil httpUtil) {
        var url = section.getString("url", "");
        var method = section.getString("method", DEFAULT_METHOD);
        var contentType = section.getString("content-type", DEFAULT_CONTENT_TYPE);
        var bodyTemplate = section.getString(
                "body-template",
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
    public boolean push(String title, String content, AlertLevel level) {
        if (config.getUrl() == null || config.getUrl().isBlank()) {
            throw new IllegalArgumentException("Webhook URL cannot be empty");
        }

        String method = normalizeMethod(config.getMethod());
        String contentType = normalizeContentType(config.getContentType());
        String bodyTemplate = config.getBodyTemplate() == null ? "" : config.getBodyTemplate();
        String renderedBody = renderTemplate(bodyTemplate, title, content, contentType, false, level);
        RequestBody requestBody = createRequestBody(method, contentType, renderedBody);

        String renderedUrl = renderTemplate(config.getUrl(), title, content, contentType, true, level);
        Request.Builder requestBuilder = new Request.Builder().url(renderedUrl).method(method, requestBody);  

        applyContentType(requestBuilder, method, requestBody);
        applyCustomHeaders(requestBuilder);
        Request request = requestBuilder.build();
        try (Response response = httpUtil.newBuilder().build().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "<empty>";
                throw new IllegalStateException("HTTP failed while sending push messages to Webhook: " + responseBody);
            }
        } catch (IllegalStateException e) {
            throw e;
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
        MediaType parsed = MediaType.parse(contentType.trim());
        return parsed != null ? parsed.toString() : DEFAULT_CONTENT_TYPE;
    }

    private RequestBody createRequestBody(String method, String contentType, String bodyContent) {
        if ("GET".equals(method)) {
            return null;
        }
        MediaType mediaType = MediaType.parse(contentType);
        if (mediaType == null) {
            mediaType = MediaType.parse(DEFAULT_CONTENT_TYPE);
        }
        if (bodyContent.isEmpty()) {
            if ("POST".equals(method)) {
                return RequestBody.create("", mediaType);
            }
            return null;
        }
        return RequestBody.create(bodyContent, mediaType);
    }

    private void applyContentType(Request.Builder requestBuilder, String method, RequestBody requestBody) {
        if (!"GET".equals(method) && requestBody != null) {
            MediaType mediaType = requestBody.contentType();
            if (mediaType != null) {
                requestBuilder.header("Content-Type", mediaType.toString());
            } else {
                requestBuilder.header("Content-Type", DEFAULT_CONTENT_TYPE);
            }
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

    private String renderTemplate(String template, String title, String content, String contentType, boolean urlEncode, AlertLevel level) {
        long currentTime = System.currentTimeMillis();
        String date = TimeUtil.formatDateOnly(currentTime);
        String time = TimeUtil.formatTimeOnly(currentTime);
        String datetime = TimeUtil.formatDateTime(currentTime);
        boolean json = contentType != null && contentType.toLowerCase(Locale.ROOT).contains("json");
        return template
            .replace("{title}", transformValue(title, urlEncode, json))
            .replace("{content}", transformValue(content, urlEncode, json))
            .replace("{level}", transformValue(level.name(), urlEncode, json))
            .replace("{date}", transformValue(date, urlEncode, json))
            .replace("{time}", transformValue(time, urlEncode, json))
            .replace("{datetime}", transformValue(datetime, urlEncode, json))
            .replace("{channelName}", transformValue(name, urlEncode, json));
    }

    private String transformValue(String value, boolean urlEncode, boolean json) {
        if (value == null) return "";
        if (urlEncode) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
        }
        if (!json) return value;
        String s = JsonUtil.standard().toJson(value);
        return s.substring(1, s.length() - 1);
    }

    @AllArgsConstructor
    @NoArgsConstructor
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