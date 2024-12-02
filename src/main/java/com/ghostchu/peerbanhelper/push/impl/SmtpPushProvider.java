package com.ghostchu.peerbanhelper.push.impl;

import com.ghostchu.peerbanhelper.push.AbstractPushProvider;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Slf4j
public class SmtpPushProvider extends AbstractPushProvider {
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
        var senderName = section.getString("name");
        var receivers = section.getStringList("receiver");
        var encryption = Encryption.valueOf(section.getString("encryption").toUpperCase(Locale.ROOT));
        var sendPartial = section.getBoolean("sendPartial");
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

    public String sendMail(List<String> email, String subject, String text) throws EmailException {
        Email mail = new HtmlEmail();
        mail.setAuthentication(config.getUsername(), config.getPassword());
        mail.setCharset("UTF-8");
        mail.setHostName(config.getHost());
        mail.setSmtpPort(config.getPort());
        switch (config.getEncryption()) {
            case STARTTLS -> mail.setStartTLSEnabled(true);
            case ENFORCE_STARTTLS -> {
                mail.setStartTLSEnabled(true);
                mail.setStartTLSRequired(true);
            }
            case SSLTLS -> mail.setSSLOnConnect(true);
        }
        mail.setSendPartial(config.isSendPartial());
        mail.setSubject(subject);
        mail.setContent(text, "text/html");
        mail.setFrom(config.getSender(), config.getSenderName(), "UTF-8");
        mail.setTo(email.stream().map(str -> {
            try {
                return new InternetAddress(str);
            } catch (AddressException exception) {
                log.warn("The email address [{}] is invalid", str, exception);
                return null;
            }
        }).filter(Objects::nonNull).toList());
        return mail.send();
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
        } catch (EmailException e) {
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
        private Encryption encryption;
        private boolean sendPartial;
    }
}