package com.ghostchu.peerbanhelper.log4j2;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static org.apache.logging.log4j.core.layout.PatternLayout.createDefaultLayout;

@Plugin(name = "SwingLoggerAppender", category = "Core", elementType = "appender", printObject = true)
public class SwingLoggerAppender extends AbstractAppender {
    private static final Set<Consumer<LoggerEvent>> listeners = new HashSet<>();

    public static int maxLinesSetting = 300;

    private SwingLoggerAppender(String name, Layout<?> layout, Filter filter, int maxLines, boolean ignoreExceptions) {
        super(name, filter, layout, ignoreExceptions, new Property[0]);
        this.maxLinesSetting = maxLines;
    }

    @SuppressWarnings("unused")
    @PluginFactory
    public static SwingLoggerAppender createAppender(@PluginAttribute("name") String name,
                                                     @PluginAttribute("maxLines") int maxLines,
                                                     @PluginAttribute("ignoreExceptions") boolean ignoreExceptions,
                                                     @PluginElement("Layout") Layout<?> layout,
                                                     @PluginElement("Filters") Filter filter) {
        maxLinesSetting = maxLines;
        if (name == null) {
            LOGGER.error("No name provided for JTextAreaAppender");
            return null;
        }

        if (layout == null) {
            layout = createDefaultLayout();
        }
        return new SwingLoggerAppender(name, layout, filter, maxLines, ignoreExceptions);
    }

    public static void registerListener(Consumer<LoggerEvent> listener) {
        listeners.add(listener);
    }

    @Override
    public void append(LogEvent event) {
        String message = new String(this.getLayout().toByteArray(event));
        listeners.forEach(c -> c.accept(new LoggerEvent(message, event, maxLinesSetting)));
    }

    public record LoggerEvent(String message, LogEvent event, int maxLines) {
    }
}