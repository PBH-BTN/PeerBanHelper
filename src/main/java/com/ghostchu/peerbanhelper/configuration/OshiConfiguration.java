package com.ghostchu.peerbanhelper.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import oshi.SystemInfoFFM;

@Configuration
public class OshiConfiguration {
    @Bean
    public SystemInfoFFM systemInfo() {
        return new SystemInfoFFM();
    }
}
