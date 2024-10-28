package com.ghostchu.peerbanhelper.push;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.push.impl.PushPlusPushProvider;
import com.ghostchu.peerbanhelper.push.impl.ServerChanPushProvider;
import com.ghostchu.peerbanhelper.push.impl.SmtpPushProvider;
import com.ghostchu.peerbanhelper.push.impl.TelegramPushProvider;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
public class PushManager implements Reloadable {
    private final List<PushProvider> providerList = new ArrayList<>();

    public PushManager() {
        reloadConfig();
    }

    private void reloadConfig() {
        List<PushProvider> registered = new ArrayList<>();
        var config = Main.getMainConfig().getConfigurationSection("push-notification");
        config.getKeys(false).forEach(provider -> {
            var section = config.getConfigurationSection(provider);
            if (section.getBoolean("enabled")) {
                switch (section.getString("type")) {
                    case "smtp" -> registered.add(new SmtpPushProvider(section));
                    case "pushplus" -> registered.add(new PushPlusPushProvider(section));
                    case "serverchan" -> registered.add(new ServerChanPushProvider(section));
                    case "telegram" -> registered.add(new TelegramPushProvider(section));
                    default -> log.warn(tlUI(Lang.UNKNOWN_PUSH_PROVIDER, provider, section.getString("type")));
                }
            }
        });
        providerList.clear();
        providerList.addAll(registered);
    }

    public boolean pushMessage(String title, String description) {
        AtomicBoolean anySuccess = new AtomicBoolean(false);
        providerList.forEach(pushProvider -> {
            try {
                if (pushProvider.push(title, description)) {
                    anySuccess.set(true);
                }
            } catch (Exception e) {
                log.error(tlUI(Lang.UNABLE_TO_PUSH_ALERT_VIA, pushProvider.getClass().getName()), e);
            }
        });
        return anySuccess.get();
    }

    @Nullable
    public PushProvider getPushProvider(String name) {
        if (name == null) {
            return null;
        }
        var config = Main.getMainConfig().getConfigurationSection("push-notification");
        var section = config.getConfigurationSection(name);
        if (section == null) {
            return null;
        }
        switch (section.getString("type")) {
            case "smtp" -> {
                return new SmtpPushProvider(section);
            }
            case "pushplus" -> {
                return new PushPlusPushProvider(section);
            }
            case "serverchan" -> {
                return new ServerChanPushProvider(section);
            }
            case "telegram" -> {
                return new TelegramPushProvider(section);
            }
        }
        return null;
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        reloadConfig();
        return Reloadable.super.reloadModule();
    }
}