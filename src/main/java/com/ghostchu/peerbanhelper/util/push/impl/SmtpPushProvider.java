package com.ghostchu.peerbanhelper.util.push.impl;

import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.push.AbstractPushProvider;
import com.google.gson.JsonObject;
import jakarta.mail.Message;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.simplejavamail.api.email.Email;
import org.simplejavamail.api.email.Recipient;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.api.mailer.config.TransportStrategy;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.MailerBuilder;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public final class SmtpPushProvider extends AbstractPushProvider {
    private final Config config;
    private final String name;
    private final Mailer mailer;

    public SmtpPushProvider(String name, Config config) {
        this.name = name;
        this.config = config;
        var encrypt = Encryption.valueOf(config.getEncryption());
        var mailerBuilder = MailerBuilder
                .withSMTPServer(config.getHost(), config.getPort(), config.getUsername(), config.getPassword())
                .withTransportStrategy(TransportStrategy.SMTP_TLS);
        TransportStrategy strateg = TransportStrategy.SMTP;

        switch (encrypt) {
            case STARTTLS -> {
                strateg = TransportStrategy.SMTP_TLS;
                mailerBuilder.verifyingServerIdentity(false);
            }
            case ENFORCE_STARTTLS -> {
                strateg = TransportStrategy.SMTP_TLS;
                mailerBuilder.verifyingServerIdentity(true);
            }
            case SSLTLS -> {
                mailerBuilder.verifyingServerIdentity(false);
                strateg = TransportStrategy.SMTPS;
            }
        }
        mailerBuilder = mailerBuilder.withTransportStrategy(strateg);
        this.mailer = mailerBuilder.buildMailer();
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

    public String sendMail(List<String> email, String subject, String text) {
        List<Recipient> recipients = new ArrayList<>();
        for (String addr : email) {
            recipients.add(new Recipient(null, addr, Message.RecipientType.TO));
        }

        Email mail = EmailBuilder.startingBlank()
                .from(config.getSenderName(), config.getSender())
                .to(recipients)
                .withSubject(subject)
                .withHTMLText(text)
                .buildEmail();

        mailer.sendMail(mail).join();
        return null;
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
        } catch (Exception e) {
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