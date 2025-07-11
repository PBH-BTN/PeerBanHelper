package com.ghostchu.peerbanhelper.configuration;

import com.ghostchu.peerbanhelper.Main;
import org.pf4j.PluginManager;
import org.pf4j.spring.SpringPluginManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author leichen
 */
@Configuration
public class Pf4jConfiguration {

    @Bean
    public PluginManager pluginManager() {
        return new SpringPluginManager(Main.getPluginDirectory().toPath());
    }

}