package com.ghostchu.peerbanhelper.util.logger;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.event.NewLogEntryCreatedEvent;
import com.google.common.collect.EvictingQueue;
import org.slf4j.event.Level;

import javax.swing.*;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

public class JListAppender extends AppenderBase<ILoggingEvent> {

    public static final LinkedBlockingDeque<LogEntry> logEntryDeque = new LinkedBlockingDeque<>();
    public static final EvictingQueue<LogEntry> ringDeque = EvictingQueue.create(300);
    private static final AtomicInteger seq = new AtomicInteger(0);
    private PatternLayout layout;

    public JListAppender() {
    }

    public static AtomicInteger getSeq() {
        return seq;
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (layout == null) { // 我们至今不知道为什么这个要懒加载
            PatternLayout layout = new PatternLayout();
            layout.setPattern("[%d{HH:mm:ss}] [%t/%level]: %msg%n");
            layout.setContext(getContext());  // 必须设置上下文
            layout.start();  // 启动 layout
            this.layout = layout;
        }
        String formattedMessage = layout.doLayout(eventObject);
        // Ensure the update to the JList is done on the Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> {
            Level slf4jLevel = Level.INFO;
            if (eventObject.getLevel() == ch.qos.logback.classic.Level.WARN) {
                slf4jLevel = Level.WARN;
            } else if (eventObject.getLevel() == ch.qos.logback.classic.Level.ERROR) {
                slf4jLevel = Level.ERROR;
            } else if (eventObject.getLevel() == ch.qos.logback.classic.Level.DEBUG) {
                slf4jLevel = Level.DEBUG;
            } else if (eventObject.getLevel() == ch.qos.logback.classic.Level.TRACE) {
                slf4jLevel = Level.TRACE;
            } else if (eventObject.getLevel() == ch.qos.logback.classic.Level.OFF) {
                return;
            }
            var postAccessLog = new LogEntry(
                    eventObject.getTimeStamp(),
                    eventObject.getThreadName(),
                    slf4jLevel,
                    formattedMessage.trim(),
                    seq.incrementAndGet());
            logEntryDeque.add(postAccessLog);
            var rawLog = new LogEntry(
                    eventObject.getTimeStamp(),
                    eventObject.getThreadName(),
                    slf4jLevel,
                    eventObject.getFormattedMessage().trim(),
                    seq.incrementAndGet());
            ringDeque.add(rawLog);
            Main.getEventBus().post(new NewLogEntryCreatedEvent(rawLog));
        });
    }
}
