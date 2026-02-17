package com.ghostchu.peerbanhelper.databasent.driver.h2;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.databasent.DatabaseType;
import com.ghostchu.peerbanhelper.databasent.driver.AbstractDatabaseDriver;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.stone.beecp.BeeDataSource;
import org.stone.beecp.BeeDataSourceConfig;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ghostchu.peerbanhelper.util.MiscUtil.removeBeeCPShutdownHook;

public class H2DatabaseDriver extends AbstractDatabaseDriver {
    private final AtomicBoolean requestCompactOnShutdown = new AtomicBoolean(false);
    private final BeeDataSource dataSource;

    public H2DatabaseDriver(@NotNull ConfigurationSection section) throws IOException {
        super();
        BeeDataSourceConfig config = new BeeDataSourceConfig();
        File persistDir = new File(Main.getDataDirectory(), "persist");
        if (!persistDir.exists()) {
            if (!persistDir.mkdirs()) {
                throw new IOException("Unable to create persist directory at " + persistDir.getAbsolutePath() + ", permission denied?");
            }
        }
        File dbFile = new File(persistDir, "peerbanhelper-nt");
        String dbPath = dbFile.getAbsolutePath();

        config.setJdbcUrl("jdbc:p6spy:h2:" + dbPath + ";MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=60000;RETENTION_TIME=5000;MAX_LOG_SIZE=8");
        //config.setDriverClassName("org.h2.Driver");
        config.setDriverClassName("com.p6spy.engine.spy.P6SpyDriver");
        config.setMaxActive(10);
        config.setMaxWait(30000);
        config.setIntervalOfClearTimeout(600000L);

        // 连接池验证配置
        config.setAliveTestSql("SELECT 1");

        // 启用公平排队 (FIFO)
        config.setFairMode(true);

        this.dataSource = new BeeDataSource(config);
        removeBeeCPShutdownHook(dataSource);
    }

    @Override
    public @NotNull DatabaseType getType() {
        return DatabaseType.H2;
    }

    @Override
    public @NotNull DataSource createDataSource() {
        return dataSource;
    }

    @Override
    public void close() throws Exception {
        try (Connection connection = getDataSource().getConnection()) {
            if (requestCompactOnShutdown.get()) {
                connection.createStatement().execute("SHUTDOWN COMPACT");
                return;
            }
        }
        super.close();
    }
}
