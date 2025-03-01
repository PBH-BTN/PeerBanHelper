package com.ghostchu.peerbanhelper.push;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.push.impl.*;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.configuration.MemoryConfiguration;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
public final class PushManager implements Reloadable {
    @Getter
    private final List<PushProvider> providerList = new ArrayList<>();

    public PushManager() {
        reloadConfig();
    }

    public PushProvider createPushProvider(String name, String type, ConfigurationSection section) {
        name = name.replace(".", "-");
        AbstractPushProvider provider = null;
        switch (type.toLowerCase(Locale.ROOT)) {
            case "pushplus" -> provider = PushPlusPushProvider.loadFromYaml(name, section);
            case "serverchan" -> provider = ServerChanPushProvider.loadFromYaml(name, section);
            case "smtp" -> provider = SmtpPushProvider.loadFromYaml(name, section);
            case "telegram" -> provider = TelegramPushProvider.loadFromYaml(name, section);
            case "bark" -> provider = BarkPushProvider.loadFromYaml(name, section);
        }
        return provider;
    }

    public PushProvider createPushProvider(String name, String type, JsonObject jsonObject) {
        AbstractPushProvider provider = null;
        switch (type.toLowerCase(Locale.ROOT)) {
            case "pushplus" -> provider = PushPlusPushProvider.loadFromJson(name, jsonObject);
            case "serverchan" -> provider = ServerChanPushProvider.loadFromJson(name, jsonObject);
            case "smtp" -> provider = SmtpPushProvider.loadFromJson(name, jsonObject);
            case "telegram" -> provider = TelegramPushProvider.loadFromJson(name, jsonObject);
            case "bark" -> provider = BarkPushProvider.loadFromJson(name, jsonObject);
        }
        return provider;
    }

    private void reloadConfig() {
        List<com.ghostchu.peerbanhelper.push.PushProvider> registered = new ArrayList<>();
        var config = Main.getMainConfig().getConfigurationSection("push-notification");
        if(config == null) return;
        config.getKeys(false).forEach(provider -> {
            var section = config.getConfigurationSection(provider);
            registered.add(createPushProvider(provider, section.getString("type"), section));
        });
        providerList.clear();
        providerList.addAll(registered);
    }

    public boolean addPushProvider(PushProvider provider) {
       return providerList.add(provider);
    }
    public boolean removePushProvider(PushProvider provider) {
        return providerList.remove(provider);
    }

    public void savePushProviders() throws IOException {
        ConfigurationSection clientSection = new MemoryConfiguration();
        for (var pushProvider : this.providerList) {
            clientSection.set(pushProvider.getName(), pushProvider.saveYaml());
        }
        Main.getMainConfig().set("push-notification", clientSection);
        Main.getMainConfig().save(Main.getMainConfigFile());
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

    @Override
    public ReloadResult reloadModule() throws Exception {
        reloadConfig();
        return Reloadable.super.reloadModule();
    }
}