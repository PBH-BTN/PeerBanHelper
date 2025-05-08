package com.ghostchu.peerbanhelper.bittorrent.tracker;

import java.util.List;

public interface Tracker extends Comparable<Tracker> {

    List<String> getTrackersInGroup();

    String getLeadingTracker();
}
