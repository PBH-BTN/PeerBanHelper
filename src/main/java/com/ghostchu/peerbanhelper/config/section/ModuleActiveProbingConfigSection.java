package com.ghostchu.peerbanhelper.config.section;

import com.ghostchu.peerbanhelper.config.ConfigManager;
import com.ghostchu.peerbanhelper.config.ConfigPair;
import com.ghostchu.peerbanhelper.config.ModuleBaseConfigSection;
import com.ghostchu.peerbanhelper.module.impl.ActiveProbing;
import lombok.Getter;
import lombok.Setter;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

import java.util.List;

@Getter
@Setter
public class ModuleActiveProbingConfigSection extends ModuleBaseConfigSection {

    private int maxCachedEntry;
    private long expireAfterNoAccess;
    private int timeout;
    private List<String> probing;
    private String httpProbingUserAgent;

    public ModuleActiveProbingConfigSection(ConfigPair configPair) {
        super(configPair, "active-probing");
    }

    @Override
    public void load() {
        super.load();
        ConfigurationSection section = getConfigSection();
        this.maxCachedEntry = section.getInt("max-cached-entry");
        this.expireAfterNoAccess = section.getLong("expire-after-no-access");
        this.timeout = section.getInt("timeout");
        this.probing = section.getStringList("probing");
        this.httpProbingUserAgent = section.getString("http-probing-user-agent");
    }

    @Override
    public void save() {
        super.save();
        ConfigurationSection section = getConfigSection();
        section.set("max-cached-entry", maxCachedEntry);
        section.set("expire-after-no-access", expireAfterNoAccess);
        section.set("timeout", timeout);
        section.set("probing", probing);
        section.set("http-probing-user-agent", httpProbingUserAgent);
        super.callSave();
    }

    @Override
    public void reload() {
        super.reload();
        moduleManager().getModulesByClass(ActiveProbing.class).forEach(module -> moduleManager().unregisterModule(module));
        moduleManager().registerModule(new ActiveProbing(ConfigManager.Sections.moduleActiveProbing()));
    }
}
