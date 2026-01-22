package com.ghostchu.peerbanhelper.configuration;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.databasent.DatabaseDriver;
import com.ghostchu.peerbanhelper.databasent.driver.SQLiteDatabaseDriver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class DatabaseDriverConfig {
    @Bean
    public DatabaseDriver loadDriver() throws Exception {
        log.info("Please wait, loading database driver...");
        var section = Main.getMainConfig().getConfigurationSection("database");
        if (section == null) throw new IllegalStateException("Database configuration section is missing!");
        int databaseTypeId = section.getInt("type");
        var driver = switch (databaseTypeId) {
            // case 1 -> new PostgresDatabaseDriver(section);
            // case 2 -> new MySQLDatabaseDriver(section);
            default -> new SQLiteDatabaseDriver(section);
        };
        log.info("Database driver loaded: {}", driver.getType().name());
        return driver;
    }
}
