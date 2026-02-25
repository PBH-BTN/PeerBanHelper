package com.ghostchu.peerbanhelper.util.logger;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.ghostchu.peerbanhelper.util.SentryUtils;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.Queues;
import com.p6spy.engine.logging.Category;
import com.p6spy.engine.spy.appender.FormattedLogger;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.protocol.Message;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Queue;

@Slf4j
public class P6spyLogger extends FormattedLogger {
    public static final Queue<@NotNull String> sqlRingQueue = Queues.synchronizedQueue(EvictingQueue.create(ExternalSwitch.parseInt("pbh.p6spy.ringqueue.size", 50)));
    public static final Queue<@NotNull String> writeSqlRingQueue = Queues.synchronizedQueue(EvictingQueue.create(ExternalSwitch.parseInt("pbh.p6spy.writeringqueue.size", 30)));

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
        try {
            String loggingSQL = "[" + category.getName() + "] " + prepared + " | " + elapsed + " ms";
            sqlRingQueue.add(loggingSQL);
            if (prepared != null) {
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
                    event.setExtra("recent_write_queries", pollSqls(writeSqlRingQueue));
                    event.setExtra("recent_all_queries", pollSqls(sqlRingQueue));
                }
                event.setThreads(SentryUtils.getSentryThreads());
                Sentry.captureEvent(event);
            }
            log.debug("[P6Spy] {} | {} | took {} ms | {}", now, category.getName(), elapsed, sql);
        } catch (Throwable th) { // make sure SQL can execute even exception fired
            Sentry.captureException(th);
        }
    }

    public String pollSqls(Queue<String> queue) { // 这个方法其实也有可能并发，获取到不完整的队列内容。但是无伤大雅，比起上个锁直接堵住 logSQL，不完整反而更容易接受，因为是出于调试目的
        StringBuilder sb = new StringBuilder();
        do {
            String sql = queue.poll();
            if (sql == null) break;
            sb.append(sql).append("\n");
        } while (true);
        return sb.toString();
    }

    @Override
    public boolean isCategoryEnabled(Category category) {
        return true;
    }
}
