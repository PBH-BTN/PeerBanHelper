package com.ghostchu.peerbanhelper.exchange;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public class ExchangeMap {
    public static volatile Set<DisplayFlag> GUI_DISPLAY_FLAGS = Collections.synchronizedSet(new TreeSet<>());

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DisplayFlag implements Comparable<DisplayFlag> {
        private String id;
        private int priority;
        private String content;

        @Override
        public int compareTo(@NotNull ExchangeMap.DisplayFlag o) {
            int priorityCompare = Integer.compare(priority, o.priority);
            return priorityCompare != 0 ? priorityCompare : id.compareTo(o.id);
        }
    }
}
