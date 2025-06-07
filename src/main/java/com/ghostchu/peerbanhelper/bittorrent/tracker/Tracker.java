package com.ghostchu.peerbanhelper.bittorrent.tracker;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface Tracker extends Comparable<Tracker> {
    @Nullable
    List<String> getTrackersInGroup();
    @Nullable
    String getLeadingTracker();
}
