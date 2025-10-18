package com.ghostchu.peerbanhelper.configuration.pf4j;

import com.ghostchu.peerbanhelper.Main;
import org.pf4j.PluginManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author leichen
 */
@Configuration
public class Pf4jConfiguration {

    @Bean
    public PluginManager pluginManager() {
        return new PBHSpringPluginManager(Main.getPluginDirectory().toPath());
    }

}