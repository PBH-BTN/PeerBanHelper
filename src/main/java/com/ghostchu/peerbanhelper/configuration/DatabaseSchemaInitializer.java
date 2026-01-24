package com.ghostchu.peerbanhelper.configuration;

import com.ghostchu.peerbanhelper.databasent.DatabaseDriver;
import com.ghostchu.peerbanhelper.text.Lang;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DatabaseSchemaInitializer {
    private final DatabaseDriver databaseDriver;

    @PostConstruct
    public void init() {
        log.info(tlUI(Lang.SPRING_CONTEXT_LOADING)); // Or some other meaningful log
        log.info("Check and upgrading database schema for {}", databaseDriver.getType());
        try {
            // TODO: Implement table creation and upgrade logic here
            // You can access databaseDriver.getDataSource() here

            // Example:
            // try (Connection conn = databaseDriver.getDataSource().getConnection()) {
            //     ...
            // }

        } catch (Exception e) {
            log.error("Failed to initialize database schema", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }
}
