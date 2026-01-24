package com.ghostchu.peerbanhelper.configuration;

import com.ghostchu.peerbanhelper.databasent.DatabaseDriver;
import com.ghostchu.peerbanhelper.databasent.DatabaseType;
import com.ghostchu.peerbanhelper.text.Lang;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.util.Arrays;
import java.util.Comparator;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DatabaseSchemaInitializer {
    private final DatabaseDriver databaseDriver;

    @PostConstruct
    public void init() {
        log.info(tlUI(Lang.SPRING_CONTEXT_LOADING));
        log.info("Check and upgrading database schema for {}", databaseDriver.getType());
        try {
            String dbType = "mysql";
            if (databaseDriver.getType() == DatabaseType.POSTGRES) {
                dbType = "postgres";
            }

            // 1. Run Flyway Migration
            log.info("Running Flyway migration for {}", dbType);
            Flyway flyway = Flyway.configure()
                    .dataSource(databaseDriver.getDataSource())
                    .locations("classpath:db/migration/" + dbType)
                    .baselineOnMigrate(true)
                    .load();
            flyway.migrate();

            // 2. Run Repeat Scripts
            log.info("Running repeat scripts for {}", dbType);
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:db/repeat/" + dbType + "/*.sql");

            // Sort by filename to ensure deterministic execution order
            Arrays.sort(resources, Comparator.comparing(Resource::getFilename));

            try (Connection conn = databaseDriver.getDataSource().getConnection()) {
                for (Resource resource : resources) {
                    log.info("Executing repeat script: {}", resource.getFilename());
                    ScriptUtils.executeSqlScript(conn, resource);
                }
            }

        } catch (Exception e) {
            log.error("Failed to initialize database schema", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }
}
