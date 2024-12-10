package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import io.javalin.http.Context;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.locks.ReentrantLock;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
public abstract class AbstractFeatureModule implements FeatureModule {
    @Getter
    private final ReentrantLock lock = new ReentrantLock();
    @Getter
    @Autowired
    private PeerBanHelperServer server;
    @Getter
    private boolean register;
    @Autowired
    private JavalinWebContainer javalinWebContainer;

    @Override
    public boolean isModuleEnabled() {
        try {
            return !isConfigurable() || getConfig().getBoolean("enabled");
        } catch (Exception e) {
            log.warn(tlUI(Lang.CONFIGURATION_OUTDATED_MODULE_DISABLED, getName()));
            return false;
        }
    }

    @Override
    public ReentrantLock getThreadLock() {
        return lock;
    }

    /**
     * 如果返回 false，则不初始化任何配置文件相关对象
     *
     * @return 是否支持使用配置文件进行配置
     */
    public abstract boolean isConfigurable();

    @Override
    public ConfigurationSection getConfig() {
        if (!isConfigurable()) return null;
        ConfigurationSection section = Objects.requireNonNull(Main.getProfileConfig().getConfigurationSection("module")).getConfigurationSection(getConfigName());
        if (section == null) {
            log.warn(tlUI(Lang.CONFIGURATION_OUTDATED_MODULE_DISABLED, getName()));
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.set("enabled", false);
            return configuration;
        }
        return section;
    }

    @Override
    public void disable() {
        if (register) {
            onDisable();
            cleanupResources();
            log.info(tlUI(Lang.MODULE_UNREGISTER, getName()));
        }
    }

    private void cleanupResources() {

    }

    @Override
    public void enable() {
        register = true;
        onEnable();
        log.info(tlUI(Lang.MODULE_REGISTER, getName()));
    }

    @Override
    public void saveConfig() throws IOException {
        if (!isConfigurable()) return;
        Main.getProfileConfig().set("module." + getConfigName(), getConfig());
        Main.getProfileConfig().save(new File(Main.getConfigDirectory(), "profile.yml"));
    }

    public String locale(Context ctx) {
        return javalinWebContainer.reqLocale(ctx);
    }

    public TimeZone timezone(Context ctx) {
        var tz = TimeZone.getDefault();
        if (ctx.header("X-Timezone") != null) {
            tz = TimeZone.getTimeZone(ctx.header("X-Timezone"));
        }
        return tz;
    }

    public String userIp(Context context) {
        return CommonUtil.userIp(context);
    }
}
