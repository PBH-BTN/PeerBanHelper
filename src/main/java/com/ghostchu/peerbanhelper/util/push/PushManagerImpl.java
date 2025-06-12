package com.ghostchu.peerbanhelper.util.push;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.push.impl.*;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.configuration.MemoryConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;


@Component
@Slf4j
public final class PushManagerImpl implements Reloadable, PushManager {
    @Getter
    private final List<PushProvider> providerList = new ArrayList<>();
    @Autowired
    private HTTPUtil httpUtil;

    public PushManagerImpl() {
        reloadConfig();
    }

    @Override
    public PushProvider createPushProvider(String name, String type, ConfigurationSection section) {
        name = name.replace(".", "-");
        AbstractPushProvider provider = null;
        switch (type.toLowerCase(Locale.ROOT)) {
            case "pushplus" -> provider = PushPlusPushProvider.loadFromYaml(name, section, httpUtil);
            case "serverchan" -> provider = ServerChanPushProvider.loadFromYaml(name, section, httpUtil);
            case "smtp" -> provider = SmtpPushProvider.loadFromYaml(name, section);
            case "telegram" -> provider = TelegramPushProvider.loadFromYaml(name, section, httpUtil);
            case "bark" -> provider = BarkPushProvider.loadFromYaml(name, section, httpUtil);
        }
        return provider;
    }

    @Override
    public PushProvider createPushProvider(String name, String type, JsonObject jsonObject) {
        AbstractPushProvider provider = null;
        switch (type.toLowerCase(Locale.ROOT)) {
            case "pushplus" -> provider = PushPlusPushProvider.loadFromJson(name, jsonObject, httpUtil);
            case "serverchan" -> provider = ServerChanPushProvider.loadFromJson(name, jsonObject, httpUtil);
            case "smtp" -> provider = SmtpPushProvider.loadFromJson(name, jsonObject);
            case "telegram" -> provider = TelegramPushProvider.loadFromJson(name, jsonObject, httpUtil);
            case "bark" -> provider = BarkPushProvider.loadFromJson(name, jsonObject, httpUtil);
        }
        return provider;
    }

    private void reloadConfig() {
        List<PushProvider> registered = new ArrayList<>();
        var config = Main.getMainConfig().getConfigurationSection("push-notification");
        if(config == null) return;
        config.getKeys(false).forEach(provider -> {
            var section = config.getConfigurationSection(provider);
            registered.add(createPushProvider(provider, section.getString("type"), section));
        });
        providerList.clear();
        providerList.addAll(registered);
    }

    @Override
    public boolean addPushProvider(PushProvider provider) {
       return providerList.add(provider);
    }
    @Override
    public boolean removePushProvider(PushProvider provider) {
        return providerList.remove(provider);
    }

    @Override
    public void savePushProviders() throws IOException {
        ConfigurationSection clientSection = new MemoryConfiguration();
        for (var pushProvider : this.providerList) {
            clientSection.set(pushProvider.getName(), pushProvider.saveYaml());
        }
        Main.getMainConfig().set("push-notification", clientSection);
        Main.getMainConfig().save(Main.getMainConfigFile());
    }

    @Override
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