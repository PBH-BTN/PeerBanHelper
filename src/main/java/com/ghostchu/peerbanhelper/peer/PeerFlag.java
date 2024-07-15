package com.ghostchu.peerbanhelper.peer;

import lombok.Builder;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

import java.util.StringJoiner;

@Data
public final class PeerFlag {
    private final String ltStdString;
    @Nullable
    private Boolean interesting;
    @Nullable
    private Boolean choked;
    @Nullable
    private Boolean remoteInterested;
    @Nullable
    private Boolean remoteChoked;
    @Nullable
    private Boolean supportsExtensions;
    @Nullable
    private Boolean outgoingConnection;
    @Nullable
    private Boolean localConnection;
    @Nullable
    private Boolean handshake;
    @Nullable
    private Boolean connecting;
    @Nullable
    private Boolean onParole;
    @Nullable
    private Boolean seed;
    @Nullable
    private Boolean optimisticUnchoke;
    @Nullable
    private Boolean snubbed;
    @Nullable
    private Boolean uploadOnly;
    @Nullable
    private Boolean endGameMode;
    @Nullable
    private Boolean holePunched;
    @Nullable
    private Boolean i2pSocket;
    @Nullable
    private Boolean utpSocket;
    @Nullable
    private Boolean sslSocket;
    @Nullable
    private Boolean rc4Encrypted;
    @Nullable
    private Boolean plainTextEncrypted;
    @Nullable
    private Boolean fromTracker;
    @Nullable
    private Boolean fromDHT;
    @Nullable
    private Boolean fromPEX;
    @Nullable
    private Boolean fromLSD;
    @Nullable
    private Boolean fromResumeData;
    @Nullable
    private Boolean fromIncoming;

    public PeerFlag(String flags) {
        parseLibTorrent(flags);
        this.ltStdString = toString();
    }

    @Builder
    public PeerFlag(boolean interesting, boolean choked, boolean remoteInterested, boolean remoteChoked, boolean supportsExtensions, boolean outgoingConnection, boolean localConnection, boolean handshake, boolean connecting, boolean onParole, boolean seed, boolean optimisticUnchoke, boolean snubbed, boolean uploadOnly, boolean endGameMode, boolean holePunched, boolean i2pSocket, boolean utpSocket, boolean sslSocket, boolean rc4Encrypted, boolean plainTextEncrypted, boolean fromTracker, boolean fromDHT, boolean fromPEX, boolean fromLSD, boolean fromResumeData, boolean fromIncoming) {
        this.interesting = interesting;
        this.choked = choked;
        this.remoteInterested = remoteInterested;
        this.remoteChoked = remoteChoked;
        this.supportsExtensions = supportsExtensions;
        this.outgoingConnection = outgoingConnection;
        this.localConnection = localConnection;
        this.handshake = handshake;
        this.connecting = connecting;
        this.onParole = onParole;
        this.seed = seed;
        this.optimisticUnchoke = optimisticUnchoke;
        this.snubbed = snubbed;
        this.uploadOnly = uploadOnly;
        this.endGameMode = endGameMode;
        this.holePunched = holePunched;
        this.i2pSocket = i2pSocket;
        this.utpSocket = utpSocket;
        this.sslSocket = sslSocket;
        this.rc4Encrypted = rc4Encrypted;
        this.plainTextEncrypted = plainTextEncrypted;
        this.fromTracker = fromTracker;
        this.fromDHT = fromDHT;
        this.fromPEX = fromPEX;
        this.fromLSD = fromLSD;
        this.fromResumeData = fromResumeData;
        this.fromIncoming = fromIncoming;
        this.ltStdString = toString();
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(" ");
        if (interesting != null && interesting) {
            if (remoteChoked != null && remoteChoked) {
                joiner.add("d");
            } else {
                joiner.add("D");
            }
        }
        if (remoteInterested != null && remoteInterested) {
            if (choked != null && choked) {
                joiner.add("u");
            } else {
                joiner.add("U");
            }
        }
        if (remoteChoked != null && interesting != null) {
            if (!remoteChoked && !interesting)
                joiner.add("K");
        }
        if (choked != null && remoteInterested != null) {
            if (!choked && !remoteInterested)
                joiner.add("?");
        }
        if (optimisticUnchoke != null && optimisticUnchoke) {
            joiner.add("O");
        }
        if (snubbed != null && snubbed) {
            joiner.add("S");
        }
        if (localConnection != null && !localConnection) {
            joiner.add("I");
        }
        if (fromDHT != null && fromDHT) {
            joiner.add("H");
        }
        if (fromPEX != null && fromPEX) {
            joiner.add("X");
        }
        if (fromLSD != null && fromLSD) {
            joiner.add("L");
        }
        if (rc4Encrypted != null && rc4Encrypted) {
            joiner.add("E");
        }
        if (plainTextEncrypted != null && plainTextEncrypted) {
            joiner.add("e");
        }
        if (utpSocket != null && utpSocket) {
            joiner.add("P");
        }
        return joiner.toString();
    }

    public void parseLibTorrent(String flags) {
        for (char c : flags.toCharArray()) {
            switch (c) {
                case 'd' -> {
                    interesting = true;
                    remoteChoked = true;
                }
                case 'D' -> {
                    interesting = true;
                    remoteChoked = false;
                }
                case 'u' -> {
                    remoteInterested = true;
                    choked = true;
                }
                case 'U' -> {
                    remoteInterested = true;
                    choked = false;
                }
                case 'K' -> {
                    remoteChoked = false;
                    interesting = false;
                }
                case '?' -> {
                    choked = false;
                    remoteInterested = false;
                }
                case 'O' -> optimisticUnchoke = true;
                case 'S' -> snubbed = false;
                case 'I' -> localConnection = false;
                case 'H' -> fromDHT = true;
                case 'X' -> fromPEX = true;
                case 'L' -> fromLSD = true;
                case 'E' -> rc4Encrypted = true;
                case 'e' -> plainTextEncrypted = true;
                case 'P' -> utpSocket = true;
            }
        }
    }

}
