package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.config.ModuleBaseConfigSection;
import com.ghostchu.peerbanhelper.text.Lang;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractFeatureModule<T extends ModuleBaseConfigSection> implements FeatureModule, ModuleConfigurable<T> {
    private final ModuleBaseConfigSection section;

    public AbstractFeatureModule(ModuleBaseConfigSection section) {
        this.section = section;
    }

    @Override
    public boolean isModuleEnabled() {
        return getConfig().isEnabled();
    }

    private boolean register;

    @Override
    public void stop() {
        if (register) {
            log.info(Lang.MODULE_UNREGISTER, getName());
        }
    }

    @Override
    public void register() {
        register = true;
        log.info(Lang.MODULE_REGISTER, getName());
    }

    @Override
    public T getConfig() {
        return (T) section;
    }

}
