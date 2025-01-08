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

        /**
         * Compares this DisplayFlag with another DisplayFlag based on their priority.
         *
         * @param o the DisplayFlag to be compared
         * @return a negative integer, zero, or a positive integer as this DisplayFlag's
         *         priority is less than, equal to, or greater than the specified DisplayFlag's priority
         * @throws NullPointerException if the specified DisplayFlag is null
         */
        @Override
        public int compareTo(@NotNull ExchangeMap.DisplayFlag o) {
            return Integer.compare(priority, o.priority);
        }
    }
}
