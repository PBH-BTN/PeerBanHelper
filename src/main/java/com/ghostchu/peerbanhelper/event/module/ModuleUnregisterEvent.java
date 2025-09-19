package com.ghostchu.peerbanhelper.event.module;

import com.ghostchu.peerbanhelper.module.FeatureModule;
import lombok.Getter;

public class ModuleUnregisterEvent {
    @Getter
    private final FeatureModule featureModule;

    public ModuleUnregisterEvent(FeatureModule featureModule) {
        this.featureModule = featureModule;
    }
}
