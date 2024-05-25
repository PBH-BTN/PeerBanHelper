package com.ghostchu.peerbanhelper.log4j2;

import lombok.Getter;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.apache.logging.log4j.core.layout.PatternLayout.createDefaultLayout;

@Plugin(name = "MemoryLoggerAppender", category = "Core", elementType = "appender", printObject = true)
public class MemoryLoggerAppender extends AbstractAppender {
    @Getter
    private static final List<String> logs = Collections.synchronizedList(new LinkedList<>());

    private final int maxLines;

    private MemoryLoggerAppender(String name, Layout<?> layout, Filter filter, int maxLines, boolean ignoreExceptions) {
        super(name, filter, layout, ignoreExceptions, new Property[0]);
        this.maxLines = maxLines;
        //  LOGGER.info("MemoryLoggerAppender Installed!");
    }

    @SuppressWarnings("unused")
    @PluginFactory
    public static MemoryLoggerAppender createAppender(@PluginAttribute("name") String name,
                                                      @PluginAttribute("maxLines") int maxLines,
                                                      @PluginAttribute("ignoreExceptions") boolean ignoreExceptions,
                                                      @PluginElement("Layout") Layout<?> layout,
                                                      @PluginElement("Filters") Filter filter) {
        if (name == null) {
            LOGGER.error("No name provided for MemoryLoggerAppender");
            return null;
        }
        if (layout == null) {
            layout = createDefaultLayout();
        }
        return new MemoryLoggerAppender(name, layout, filter, maxLines, ignoreExceptions);
    }


    @Override
    public void append(LogEvent event) {
        String message = new String(this.getLayout().toByteArray(event));
        logs.addAll(Arrays.asList(message.split("\n")));
        int lineCount = logs.size();
        int linesToCut = (lineCount - maxLines) + (maxLines / 2);
        linesToCut = Math.min(linesToCut, lineCount);
        for (int i = 0; i < linesToCut; i++) {
            logs.removeFirst();
        }
    }
}