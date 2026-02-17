package com.ghostchu.peerbanhelper.util.logger;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.event.program.logger.NewLogEntryCreatedEvent;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.Queues;
import lombok.Getter;
import org.slf4j.event.Level;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class JListAppender extends AppenderBase<ILoggingEvent> {

    public static final Queue<LogEntry> logEntryDeque = Queues.synchronizedQueue(EvictingQueue.create(ExternalSwitch.parseInt("pbh.logger.logEntryDeque.size", 200)));
    public static final AtomicBoolean allowWriteLogEntryDeque = new AtomicBoolean(true);
    public static final Queue<LogEntry> ringDeque =  Queues.synchronizedQueue(EvictingQueue.create(ExternalSwitch.parseInt("pbh.logger.ringDeque.size", 100)));
    @Getter
    private static final AtomicLong seq = new AtomicLong(0);
    private PatternLayout layout;
    private static final ThrowableProxyConverter converter = new ThrowableProxyConverter();

    public JListAppender() {
        converter.start();
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
        long seqNumber = seq.incrementAndGet();
        if (allowWriteLogEntryDeque.get()) {
            var postAccessLog = new LogEntry(
                    eventObject.getTimeStamp(),
                    eventObject.getThreadName(),
                    slf4jLevel,
                    formattedMessage.trim(),
                    seqNumber);
            logEntryDeque.add(postAccessLog);
        }
        String messageBody = formattedMessage.trim();
        if (formattedMessage.startsWith("[")) {
            int splitIndex = messageBody.indexOf(": ");
            if (splitIndex != -1) {
                messageBody = messageBody.substring(splitIndex + 2);
            } else {
                messageBody = "";
            }
        }
        var rawLog = new LogEntry(
                eventObject.getTimeStamp(),
                eventObject.getThreadName(),
                slf4jLevel,
                messageBody,
                seqNumber);
        ringDeque.add(rawLog);
        Main.getEventBus().post(new NewLogEntryCreatedEvent(rawLog));
    }
}
