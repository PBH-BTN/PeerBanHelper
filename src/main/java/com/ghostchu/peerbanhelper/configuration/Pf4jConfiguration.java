package com.ghostchu.peerbanhelper.configuration;

import com.ghostchu.peerbanhelper.Main;
import org.pf4j.PluginManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author leichen
 */
@Configuration
@EnableConfigurationProperties(Pf4jManagerProperties.class)
@ConditionalOnProperty(value = "spring.pf4j.enabled", havingValue = "true", matchIfMissing = true)
public class Pf4jConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PluginManager pluginManager() {
        return new org.pf4j.spring.SpringPluginManager(Main.getPluginDirectory().toPath());
    }

}