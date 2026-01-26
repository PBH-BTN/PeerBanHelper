package com.ghostchu.peerbanhelper.databasent.p6spy;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.event.database.SlowQueryEvent;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.SimpleJdbcEventListener;
import com.p6spy.engine.logging.P6LogOptions;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

public class SlowQueryListener extends SimpleJdbcEventListener {
    @Override
    public void onAfterAnyExecute(StatementInformation statementInformation, long timeElapsedNanos, SQLException e) {
        long millis = TimeUnit.NANOSECONDS.toMillis(timeElapsedNanos);
        long threshold = P6LogOptions.getActiveInstance().getExecutionThreshold();
        if (millis > threshold) {
            Main.getEventBus().post(new SlowQueryEvent(statementInformation.getSql(), millis)); // 不要使用 SqlWithValues，会泄露用户隐私信息
            SentryEvent sentryEvent = new SentryEvent(new SlowQueryException("Slow query detected: " + millis + " ms: " + statementInformation.getSql()));
            sentryEvent.setLevel(SentryLevel.WARNING);
            sentryEvent.setTag("cost_millis", String.valueOf(millis));
            sentryEvent.setTag("threshold", String.valueOf(threshold));
            sentryEvent.setTag("sql", statementInformation.getSql());
            Sentry.captureEvent(sentryEvent);
        }
    }

    public static class SlowQueryException extends Exception {
        public SlowQueryException(String message) {
            super(message);
        }
    }
}
