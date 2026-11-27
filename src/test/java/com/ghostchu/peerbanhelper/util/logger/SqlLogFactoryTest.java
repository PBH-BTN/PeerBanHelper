package com.ghostchu.peerbanhelper.util.logger;

import com.p6spy.engine.common.ConnectionInformation;
import com.p6spy.engine.common.PreparedStatementInformation;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SqlLogFactoryTest {
    @Test
    void templatesReachBothRingsWithoutExpandingParameters() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:p6spy:sqlite::memory:")) {
            try (var create = connection.createStatement()) {
                create.execute("CREATE TABLE perf_log_test (value TEXT)");
            }
            try (var insert = connection.prepareStatement("INSERT INTO perf_log_test VALUES (?)")) {
                insert.setString(1, "bound-value");
                insert.executeUpdate();
            }
            assertTrue(P6spyLogger.sqlRingQueue.stream().anyMatch(line -> line.contains("INSERT INTO perf_log_test VALUES (?)")));
            assertTrue(P6spyLogger.writeSqlRingQueue.stream().anyMatch(line -> line.contains("INSERT INTO perf_log_test VALUES (?)")));

            var info = ConnectionInformation.fromTestConnection(connection);
            var statement = spy(new PreparedStatementInformation(info, "SELECT ?"));
            var outage = new SqlOutageFactory().getJdbcEventListener();
            outage.onBeforeExecute(statement);
            outage.onAfterExecute(statement, 1000, null);
            new SqlLogFactory().getJdbcEventListener().onAfterExecute(statement, 1000, null);
            verify(statement, never()).getSqlWithValues();
        }
    }
}
