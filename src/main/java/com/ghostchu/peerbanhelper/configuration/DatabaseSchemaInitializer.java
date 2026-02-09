package com.ghostchu.peerbanhelper.configuration;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.databasent.DatabaseDriver;
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
        log.info(tlUI(Lang.DBNT_FLYWAY_VALIDATING_SCHEMA, databaseDriver.getType().name()));
        try {
            String migrationType = databaseDriver.getType().getMigrationType();
            String repeatType = databaseDriver.getType().getRepeatType();
            // 1. Run Flyway Migration
            Flyway flyway = Flyway.configure()
                    .dataSource(databaseDriver.getReadDataSource())
                    .locations("classpath:db/migration/" + migrationType)
                    .baselineOnMigrate(true)
                    .validateOnMigrate(ExternalSwitch.parseBoolean("dbnt.flyway.validateOnMigrate", true))
                    .load();

            flyway.migrate();
            // 2. Run Repeat Scripts
            log.debug("Running repeat scripts for {}", migrationType);
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:db/repeat/" + repeatType + "/*.sql");

            // Sort by filename to ensure deterministic execution order
            Arrays.sort(resources, Comparator.comparing(Resource::getFilename));

            try (Connection conn = databaseDriver.getReadDataSource().getConnection()) {
                for (Resource resource : resources) {
                    log.debug("Executing repeat script: {}", resource.getFilename());
                    ScriptUtils.executeSqlScript(conn, resource);
                }
            }

        } catch (Exception e) {
            log.error(tlUI(Lang.DBNT_FLYWAY_ERROR), e);
            throw new RuntimeException(tlUI(Lang.DBNT_FLYWAY_ERROR), e);
        }
    }
}
