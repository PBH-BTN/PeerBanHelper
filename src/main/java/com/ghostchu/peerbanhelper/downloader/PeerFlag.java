package com.ghostchu.peerbanhelper.downloader;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class PeerFlag {
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

    public PeerFlag(String flags) {
        parseLibTorrent(flags);
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
