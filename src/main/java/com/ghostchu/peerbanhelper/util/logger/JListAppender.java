package com.ghostchu.peerbanhelper.util.logger;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.event.Level;

import javax.swing.*;
import java.util.concurrent.LinkedBlockingDeque;

public class JListAppender extends AppenderBase<ILoggingEvent> {

    public static final LinkedBlockingDeque<LogEntry> logEntryDeque = new LinkedBlockingDeque<>();
    private PatternLayout layout;

    public JListAppender() {
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
            logEntryDeque.add(new LogEntry(slf4jLevel, formattedMessage));
        });
    }


}
