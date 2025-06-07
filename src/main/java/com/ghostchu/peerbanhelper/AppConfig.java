package com.ghostchu.peerbanhelper;

import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.simplereloadlib.ReloadManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;

@Configuration
@ComponentScan(value = "com.ghostchu.peerbanhelper", excludeFilters = @ComponentScan.Filter(IgnoreScan.class))
@ComponentScan(value = "com.ghostchu.lib.jni", excludeFilters = @ComponentScan.Filter(IgnoreScan.class))
@Slf4j
@EnableScheduling
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

    public ReloadManager reloadManager() {
        return Main.getReloadManager();
    }
}
