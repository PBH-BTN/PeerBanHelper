package com.ghostchu.peerbanhelper.util.logger;

import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.ghostchu.peerbanhelper.util.SentryUtils;
import com.p6spy.engine.logging.Category;
import com.p6spy.engine.spy.appender.FormattedLogger;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.protocol.Message;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class P6spyLogger extends FormattedLogger {
    @Override
    public void logException(Exception e) {
        log.error("P6Spy error logging", e);
    }

    @Override
    public void logText(String text) {
        log.debug("[P6Spy] {}", text);
    }

    @Override
    public void logSQL(int connectionId, String now, long elapsed, Category category, String prepared, String sql, String url) {
        //super.logSQL(connectionId, now, elapsed, category, prepared, sql, url);
        if(category == Category.OUTAGE){
            log.warn("[P6Spy] >>! OUTAGE/SLOW !<< {} | {} | took {} ms | {}\n{}", now, category.getName(), elapsed, prepared, MiscUtil.getAllThreadTrace());
            SentryEvent event = new SentryEvent();
            Message msg = new Message();
            msg.setMessage("SQL Outage/Slow Query: "+sql);
            event.setMessage(msg);
            event.setTag("category", category.getName());
            event.setExtra("elapsed_ms", elapsed);
            event.setExtra("timestamp", now);
            event.setExtra("prepared", prepared);
            event.setExtra("stacktrace", MiscUtil.getAllThreadTrace());
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