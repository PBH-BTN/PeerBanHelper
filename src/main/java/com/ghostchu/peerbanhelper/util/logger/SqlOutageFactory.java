package com.ghostchu.peerbanhelper.util.logger;

import com.p6spy.engine.common.ConnectionInformation;
import com.p6spy.engine.common.Loggable;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.JdbcEventListener;
import com.p6spy.engine.event.SimpleJdbcEventListener;
import com.p6spy.engine.outage.P6OutageDetector;
import com.p6spy.engine.outage.P6OutageFactory;
import com.p6spy.engine.outage.P6OutageOptions;

import java.sql.SQLException;

public final class SqlOutageFactory extends P6OutageFactory {
    @Override
    public JdbcEventListener getJdbcEventListener() {
        return new SimpleJdbcEventListener() {
            private void begin(Loggable invocation, String category) {
                if (P6OutageOptions.getActiveInstance().getOutageDetection()) {
                    String template = invocation.getSql();
                    String sql = SqlLogFactory.INCLUDE_VALUES ? invocation.getSqlWithValues() : template;
                    P6OutageDetector.INSTANCE.registerInvocation(invocation, System.nanoTime(), category,
                            sql, template, invocation.getConnectionInformation().getUrl());
                }
            }

            private void end(Loggable invocation) {
                if (P6OutageOptions.getActiveInstance().getOutageDetection()) {
                    P6OutageDetector.INSTANCE.unregisterInvocation(invocation);
                }
            }

            @Override
            public void onBeforeAnyExecute(StatementInformation statement) {
                begin(statement, "statement");
            }

            @Override
            public void onAfterAnyExecute(StatementInformation statement, long elapsed, SQLException error) {
                end(statement);
            }

            @Override
            public void onBeforeAnyAddBatch(StatementInformation statement) {
                begin(statement, "batch");
            }

            @Override
            public void onAfterAnyAddBatch(StatementInformation statement, long elapsed, SQLException error) {
                end(statement);
            }

            @Override
            public void onBeforeCommit(ConnectionInformation connection) {
                begin(connection, "commit");
            }

            @Override
            public void onAfterCommit(ConnectionInformation connection, long elapsed, SQLException error) {
                end(connection);
            }

            @Override
            public void onBeforeRollback(ConnectionInformation connection) {
                begin(connection, "rollback");
            }

            @Override
            public void onAfterRollback(ConnectionInformation connection, long elapsed, SQLException error) {
                end(connection);
            }
        };
    }
}
