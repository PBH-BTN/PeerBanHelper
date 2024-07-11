package com.ghostchu.peerbanhelper.downloader;

import lombok.Builder;
import lombok.Data;

import java.util.StringJoiner;

@Data
public final class PeerFlag {
    private boolean interesting;
    private boolean choked;
    private boolean remoteInterested;
    private boolean remoteChoked;
    private boolean supportsExtensions;
    private boolean outgoingConnection;
    private boolean localConnection;
    private boolean handshake;
    private boolean connecting;
    private boolean onParole;
    private boolean seed;
    private boolean optimisticUnchoke;
    private boolean snubbed;
    private boolean uploadOnly;
    private boolean endGameMode;
    private boolean holePunched;
    private boolean i2pSocket;
    private boolean utpSocket;
    private boolean sslSocket;
    private boolean rc4Encrypted;
    private boolean plainTextEncrypted;

    private boolean fromTracker;
    private boolean fromDHT;
    private boolean fromPEX;
    private boolean fromLSD;

    private boolean fromResumeData;
    private boolean fromIncoming;

    private final String ltStdString;

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
        if (interesting) {
            if (remoteChoked) {
                joiner.add("d");
            } else {
                joiner.add("D");
            }
        }
        if (remoteInterested) {
            if (choked) {
                joiner.add("u");
            } else {
                joiner.add("U");
            }
        }
        if (!remoteChoked && !interesting)
            joiner.add("K");
        if (!choked && !remoteInterested)
            joiner.add("?");
        if (optimisticUnchoke)
            joiner.add("O");
        if (snubbed)
            joiner.add("S");
        if (!localConnection)
            joiner.add("I");
        if (fromDHT)
            joiner.add("H");
        if (fromPEX)
            joiner.add("X");
        if (fromLSD)
            joiner.add("L");
        if (rc4Encrypted)
            joiner.add("E");
        if (plainTextEncrypted)
            joiner.add("e");
        if (utpSocket)
            joiner.add("P");
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
