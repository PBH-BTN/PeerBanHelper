package com.ghostchu.peerbanhelper.configuration.pf4j;

import com.ghostchu.peerbanhelper.BuildMeta;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.module.ModuleManagerImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author leichen
 */
@Configuration
public class Pf4jConfiguration {

    @Bean
    public PBHSpringPluginManager pluginManager(BuildMeta build, ModuleManagerImpl modules) {
        return new PBHSpringPluginManager(Main.getPluginDirectory().toPath(), build.getVersion(), modules);
    }

}
