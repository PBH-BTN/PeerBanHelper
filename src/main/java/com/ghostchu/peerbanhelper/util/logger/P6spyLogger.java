package com.ghostchu.peerbanhelper.util.logger;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.ghostchu.peerbanhelper.util.SentryUtils;
import com.google.common.collect.EvictingQueue;
import com.p6spy.engine.logging.Category;
import com.p6spy.engine.spy.appender.FormattedLogger;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.protocol.Message;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class P6spyLogger extends FormattedLogger {
    public static final EvictingQueue<@NotNull String> sqlRingQueue = EvictingQueue.create(ExternalSwitch.parseInt("pbh.p6spy.ringqueue.size", 50));
    public static final EvictingQueue<@NotNull String> writeSqlRingQueue = EvictingQueue.create(ExternalSwitch.parseInt("pbh.p6spy.writeringqueue.size", 30));

    @Override
    public void logException(Exception e) {
        log.error("P6Spy error logging", e);
    }

    @Override
    public void logText(String text) {
        log.debug("[P6Spy] {}", text);
    }

    @Override
    public void logSQL(int connectionId, String now, long elapsed, Category category, @Nullable String prepared, String sql, String url) {
        String loggingSQL = "[" + category.getName() + "] " + prepared + " | " + elapsed + " ms";
        sqlRingQueue.add(loggingSQL);
        if(prepared != null) {
            if (!prepared.startsWith("SELECT") && (prepared.startsWith("INSERT") // 这里检查一个 SELECT 以便快速命中绝大部分查询，避免遍历所有条件
                    || prepared.startsWith("UPDATE")
                    || prepared.startsWith("DELETE")
                    || prepared.startsWith("ALTER")
                    || prepared.startsWith("CREATE")
                    || prepared.startsWith("DROP")
                    || prepared.startsWith("TRUNCATE"))) {
                writeSqlRingQueue.add(loggingSQL);
            }
        }
        //super.logSQL(connectionId, now, elapsed, category, prepared, sql, url);
        if (category == Category.OUTAGE) {
            log.warn("[P6Spy] >>! OUTAGE/SLOW !<< {} | {} | took {} ms | {}\n{}", now, category.getName(), elapsed, prepared, MiscUtil.getAllThreadTrace());
            SentryEvent event = new SentryEvent();
            Message msg = new Message();
            msg.setMessage("SQL Outage/Slow Query: " + prepared);
            event.setMessage(msg);
            event.setTag("category", category.getName());
            event.setExtra("elapsed_ms", elapsed);
            event.setExtra("timestamp", now);
            event.setExtra("prepared", prepared);
            event.setExtra("stacktrace", MiscUtil.getAllThreadTrace());
            if (ExternalSwitch.parseBoolean("pbh.p6spy.logsqlwithsentry", false)) { // do not upload sql by default
                event.setExtra("sql", sql);
                event.setExtra("recent_write_queries", String.join("\n", writeSqlRingQueue));
                event.setExtra("recent_all_queries", String.join("\n", sqlRingQueue));
            }
            event.setThreads(SentryUtils.getSentryThreads());
            Sentry.captureEvent(event);
        }
        log.debug("[P6Spy] {} | {} | took {} ms | {}", now, category.getName(), elapsed, sql);
    }

    @Override
    public boolean isCategoryEnabled(Category category) {
        return true;
    }
}
