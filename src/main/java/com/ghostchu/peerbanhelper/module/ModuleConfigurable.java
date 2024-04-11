package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.config.ModuleBaseConfigSection;

public interface ModuleConfigurable<T extends ModuleBaseConfigSection> {

    public T getConfig();

}
