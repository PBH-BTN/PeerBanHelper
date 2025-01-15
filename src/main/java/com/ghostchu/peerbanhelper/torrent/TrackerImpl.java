package com.ghostchu.peerbanhelper.torrent;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TrackerImpl implements Tracker {

    private final List<String> trackers;

    public TrackerImpl(String string) {
        this.trackers = Stream.of(string.split("\n")).sorted().collect(Collectors.toList());
    }

    public TrackerImpl(List<String> string) {
        this.trackers = string;
    }

    @Override
    public List<String> getTrackersInGroup() {
        return trackers;
    }

    @Override
    public String getLeadingTracker() {
        return trackers.getFirst();
    }

    public static List<Tracker> parseFromTrackerList(String trackerList) {
        return Stream.of(trackerList.split("\n\n")).filter(str -> !str.isBlank()).map(TrackerImpl::new).collect(Collectors.toList());
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
