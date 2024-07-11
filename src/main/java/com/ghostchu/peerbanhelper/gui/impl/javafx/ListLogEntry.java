package com.ghostchu.peerbanhelper.gui.impl.javafx;

import lombok.Data;
import org.slf4j.event.Level;

@Data
public class ListLogEntry {
    private final String log;
    private final Level level;
    private boolean selected = false;

    public ListLogEntry(String log, Level level) {
        this.log = log;
        this.level = level;
    }

}