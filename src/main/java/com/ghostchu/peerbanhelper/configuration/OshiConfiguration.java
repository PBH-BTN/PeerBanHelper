package com.ghostchu.peerbanhelper.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import oshi.SystemInfo;
import oshi.spi.SystemInfoFactory;
import oshi.spi.SystemInfoProvider;

@Configuration
public class OshiConfiguration {
    @Bean
    public SystemInfoProvider systemInfo() {
        return SystemInfoFactory.create();
    }
}
