package com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.wrapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
com.biglybt.pif.peers.Peer.java
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public final class PeerRecord {
    private boolean myPeer;
    private int state;
    private String peerIdBase64;
    private String ip;
    private int tcpListenPort;
    private int udpListenPort;
    private int udpNonDataListenPort;
    private int port;
    private boolean lanLocal;
    private boolean transferAvailable;
    private boolean downloadPossible;
    private boolean choked;
    private boolean choking;
    private boolean interested;
    private boolean interesting;
    private boolean seed;
    private boolean snubbed;
    private long snubbedTime;
    private PeerStatsRecord stats;
    private boolean incoming;
    private int percentDoneInThousandNotation;
    private String client;
    private boolean optimisticUnchoke;
    private boolean supportsMessaging;
    private boolean priorityConnection;
}
