package com.ghostchu.peerbanhelper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
@ComponentScan("com.ghostchu.peerbanhelper")
@Slf4j
public class AppConfig {
    @Bean
    public BuildMeta buildMeta() {
        return Main.getMeta();
    }

    @Bean("banListFile")
    public File banListFile() {
        return new File(Main.getDataDirectory(), "banlist.dump");
    }

    @Bean("userAgent")
    public String userAgent() {
        return Main.getUserAgent();
    }

}
