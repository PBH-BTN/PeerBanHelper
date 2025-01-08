package com.ghostchu.peerbanhelper.exchange;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public class ExchangeMap {
    public static final Set<DisplayFlag> GUI_DISPLAY_FLAGS = Collections.synchronizedSet(new TreeSet<>());

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
         * @param o the other DisplayFlag to compare with this instance
         * @return a negative integer, zero, or a positive integer if this DisplayFlag's
         *         priority is less than, equal to, or greater than the other DisplayFlag's priority
         * @throws NullPointerException if the provided DisplayFlag is null
         */
        @Override
        public int compareTo(@NotNull ExchangeMap.DisplayFlag o) {
            return Integer.compare(priority, o.priority);
        }
    }
}
