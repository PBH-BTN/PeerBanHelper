package com.ghostchu.peerbanhelper.util.logger;

import com.baomidou.mybatisplus.extension.p6spy.MybatisPlusLogFactory;
import com.baomidou.mybatisplus.extension.p6spy.MybatisPlusLoggingEventListener;
import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.p6spy.engine.common.Loggable;
import com.p6spy.engine.common.PreparedStatementInformation;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.JdbcEventListener;
import com.p6spy.engine.logging.Category;

import java.sql.SQLException;

public final class SqlLogFactory extends MybatisPlusLogFactory {
    static final boolean INCLUDE_VALUES = ExternalSwitch.parseBoolean("pbh.p6spy.enable", false)
            || ExternalSwitch.parseBoolean("pbh.p6spy.logsqlwithsentry", false);

    @Override
    public JdbcEventListener getJdbcEventListener() {
        if (INCLUDE_VALUES) return super.getJdbcEventListener();
        return new MybatisPlusLoggingEventListener() {
            @Override
            protected void logElapsed(Loggable data, long elapsed, Category category, SQLException error) {
                if (data instanceof PreparedStatementInformation statement) {
                    var template = new StatementInformation(statement.getConnectionInformation());
                    template.setStatementQuery(statement.getSql());
                    data = template;
                }
                super.logElapsed(data, elapsed, category, error);
            }
        };
    }
}
