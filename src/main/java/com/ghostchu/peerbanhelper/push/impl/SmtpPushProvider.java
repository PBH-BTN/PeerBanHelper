package com.ghostchu.peerbanhelper.push.impl;

import com.ghostchu.peerbanhelper.push.AbstractPushProvider;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Properties;

@Slf4j
public class SmtpPushProvider extends AbstractPushProvider {
    private final Properties props;
    private final String username;
    private final String password;
    private final String sender;
    private final String senderName;
    private final @NotNull List<String> receivers;

    public SmtpPushProvider(ConfigurationSection section) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", true);
        props.put("mail.smtp.starttls.enable", section.getBoolean("ssl"));
        props.put("mail.smtp.host", section.getString("host"));
        props.put("mail.smtp.port", section.getString("port"));
        this.props = props;
        this.username = section.getString("username");
        this.password = section.getString("password");
        this.sender = section.getString("sender");
        this.senderName = section.getString("name");
        this.receivers = section.getStringList("receiver");
    }

    public void sendMail(String email, String subject, String text) throws MessagingException, UnsupportedEncodingException {
        var session = Session.getDefaultInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(sender, senderName, "UTF-8"));
        msg.setRecipient(MimeMessage.RecipientType.TO, new InternetAddress(email));
        msg.setSubject(stripMarkdown(subject));
        msg.setContent(markdown2Html(text), "text/html;charset=utf-8");
        Transport.send(msg);
    }

    @Override
    public boolean push(String title, String content) {
        boolean anySuccess = false;
        for (String receiver : receivers) {
            try {
                sendMail(receiver, title, content);
                anySuccess = true;
            } catch (Exception e) {
                log.warn("Failed to send mail to {}", receiver, e);
            }
        }
        return anySuccess;

    }
}