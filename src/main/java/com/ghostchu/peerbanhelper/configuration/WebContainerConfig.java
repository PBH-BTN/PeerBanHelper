package com.ghostchu.peerbanhelper.configuration;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebContainerConfig {
    @Bean
    public JavalinWebContainer webContainer(){
        return Main.getWebContainer();
    }
}
