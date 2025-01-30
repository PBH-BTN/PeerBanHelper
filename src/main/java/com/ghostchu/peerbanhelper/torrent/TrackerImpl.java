package com.ghostchu.peerbanhelper.torrent;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class TrackerImpl implements Tracker {

    private final List<String> trackers;

    public TrackerImpl(String string) {
        Objects.requireNonNull(string, "tracker string cannot be null");
        this.trackers = Stream.of(string.split("\n"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .sorted()
                .toList();
    }

    public TrackerImpl(List<String> string) {
        Objects.requireNonNull(string, "tracker list cannot be null");
        this.trackers = string;
    }

    @Override
    public List<String> getTrackersInGroup() {
        return trackers;
    }

    @Override
    public String getLeadingTracker() {
        if (trackers.isEmpty()) {
            throw new IllegalStateException("No trackers available");
        }
        return trackers.getFirst();
    }

    public static List<Tracker> parseFromTrackerList(String trackerList) {
        if (trackerList == null) {
            return Collections.emptyList();
        }
        return Stream.of(trackerList.split("\n\n"))
            .filter(str -> !str.isBlank())
            .map(TrackerImpl::new)
            .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public String toString() {
        return String.join("\n", trackers);
    }

    @Override
    public int compareTo(@NotNull Tracker o) {
        return toString().compareTo(o.toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrackerImpl tracker = (TrackerImpl) o;
        return Objects.equals(trackers, tracker.trackers);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(trackers);
    }
}
