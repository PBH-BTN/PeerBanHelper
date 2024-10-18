package com.ghostchu.peerbanhelper.push;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.push.impl.PushPlusPushProvider;
import com.ghostchu.peerbanhelper.push.impl.ServerChanPushProvider;
import com.ghostchu.peerbanhelper.push.impl.SmtpPushProvider;
import com.ghostchu.peerbanhelper.push.impl.TelegramPushProvider;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
                switch (provider) {
                    case "smtp" -> registered.add(new SmtpPushProvider(section));
                    case "pushplus" -> registered.add(new PushPlusPushProvider(section));
                    case "serverchan" -> registered.add(new ServerChanPushProvider(section));
                    case "telegram" -> registered.add(new TelegramPushProvider(section));
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
                log.error("Unable to push message via {}", pushProvider.getClass().getName(), e);
            }
        });
        return anySuccess.get();
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        reloadConfig();
        return Reloadable.super.reloadModule();
    }
}