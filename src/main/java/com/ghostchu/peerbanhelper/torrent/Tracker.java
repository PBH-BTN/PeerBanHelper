package com.ghostchu.peerbanhelper.torrent;

import java.util.List;

public interface Tracker extends Comparable<Tracker> {

    List<String> getTrackersInGroup();

    String getLeadingTracker();
}
