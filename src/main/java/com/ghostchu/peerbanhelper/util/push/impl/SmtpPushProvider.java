package com.ghostchu.peerbanhelper.util.push.impl;

import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.push.AbstractPushProvider;
import com.google.gson.JsonObject;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

@Slf4j
public final class SmtpPushProvider extends AbstractPushProvider {
    private final Config config;
    private final String name;

    public SmtpPushProvider(String name, Config config) {
        this.name = name;
        this.config = config;
    }

    public static SmtpPushProvider loadFromJson(String name, JsonObject json) {
        return new SmtpPushProvider(name, JsonUtil.getGson().fromJson(json, Config.class));
    }

    public static SmtpPushProvider loadFromYaml(String name, ConfigurationSection section) {
        var auth = section.getBoolean("auth");
        var username = section.getString("username");
        var password = section.getString("password");
        var sender = section.getString("sender");
        var senderName = section.getString("name", "PeerBanHelper");
        var receivers = section.getStringList("receiver");
        var encryption = section.getString("encryption", "SSLTLS");
        var sendPartial = section.getBoolean("sendPartial", true);
        Config config = new Config(section.getString("host"),
                section.getInt("port"), auth, username, password,
                sender, senderName, receivers, encryption, sendPartial
        );
        return new SmtpPushProvider(name, config);
    }

    @Override
    public JsonObject saveJson() {
        return JsonUtil.readObject(JsonUtil.standard().toJson(config));
    }

    @Override
    public ConfigurationSection saveYaml() {
        YamlConfiguration section = new YamlConfiguration();
        section.set("type", "smtp");
        section.set("host", config.getHost());
        section.set("port", config.getPort());
        section.set("auth", config.isAuth());
        section.set("username", config.getUsername());
        section.set("password", config.getPassword());
        section.set("sender", config.getSender());
        section.set("name", config.getSenderName());
        section.set("receiver", config.getReceivers());
        section.set("encryption", config.getEncryption());
        section.set("sendPartial", config.isSendPartial());
        return section;
    }

    public String sendMail(List<String> email, String subject, String text) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.smtp.host", config.getHost());
        props.put("mail.smtp.port", String.valueOf(config.getPort()));
        props.put("mail.smtp.auth", String.valueOf(config.isAuth()));
        props.put("mail.smtp.sendpartial", String.valueOf(config.isSendPartial()));

        try {
            Encryption enc = Encryption.valueOf(config.getEncryption());
            switch (enc) {
                case STARTTLS -> props.put("mail.smtp.starttls.enable", "true");
                case ENFORCE_STARTTLS -> {
                    props.put("mail.smtp.starttls.enable", "true");
                    props.put("mail.smtp.starttls.required", "true");
                }
                case SSLTLS -> props.put("mail.smtp.ssl.enable", "true");
            }
        } catch (Exception e) {
            log.error("Unable to load mail encryption type: {}, it's valid?", config.getEncryption(), e);
        }

        Session session;
        if (config.isAuth()) {
            session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(config.getUsername(), config.getPassword());
                }
            });
        } else {
            session = Session.getInstance(props);
        }

        MimeMessage message = new MimeMessage(session);

        try {
            message.setFrom(new InternetAddress(config.getSender(), config.getSenderName(), "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            message.setFrom(new InternetAddress(config.getSender()));
        }

        InternetAddress[] recipients = email.stream()
                .map(str -> {
                    try {
                        return new InternetAddress(str);
                    } catch (Exception exception) {
                        log.warn("The email address [{}] is invalid", str, exception);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toArray(InternetAddress[]::new);

        message.setRecipients(Message.RecipientType.TO, recipients);
        message.setSubject(subject, "UTF-8");
        message.setContent(markdown2Html(text), "text/html; charset=UTF-8");

        Transport.send(message);
        return message.getMessageID();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getConfigType() {
        return "smtp";
    }

    @Override
    public boolean push(String title, String content) {
        try {
            sendMail(config.getReceivers(), title, content);
            return true;
        } catch (MessagingException e) {
            log.warn("Unable to push message via SMTP", e);
            return false;
        }
    }

    public enum Encryption {
        NONE,
        STARTTLS,
        ENFORCE_STARTTLS,
        SSLTLS
    }

    @AllArgsConstructor
    @Data
    public static class Config {
        // private Properties props;
        private String host;
        private int port;
        private boolean auth;
        private String username;
        private String password;
        private String sender;
        private String senderName;
        private @NotNull List<String> receivers;
        private String encryption;
        private boolean sendPartial;
    }
}