package com.ghostchu.peerbanhelper.peer;

import lombok.Builder;
import lombok.Data;

import java.util.BitSet;
import java.util.StringJoiner;

@Data
public final class PeerFlag {
    private final String ltStdString;
    private final BitSet peerFlags = new BitSet(21);
    private final BitSet peerSourceFlags = new BitSet(6);

    public PeerFlag(String flags) {
        parseLibTorrent(flags);
        this.ltStdString = toString();
    }

    @Builder
    public PeerFlag(boolean interesting, boolean choked, boolean remoteInterested, boolean remoteChoked, boolean supportsExtensions, boolean outgoingConnection, boolean localConnection, boolean handshake, boolean connecting, boolean onParole, boolean seed, boolean optimisticUnchoke, boolean snubbed, boolean uploadOnly, boolean endGameMode, boolean holePunched, boolean i2pSocket, boolean utpSocket, boolean sslSocket, boolean rc4Encrypted, boolean plainTextEncrypted, boolean fromTracker, boolean fromDHT, boolean fromPEX, boolean fromLSD, boolean fromResumeData, boolean fromIncoming) {
        peerFlags.set(0, interesting);
        peerFlags.set(1, choked);
        peerFlags.set(2, remoteInterested);
        peerFlags.set(3, remoteChoked);
        peerFlags.set(4, supportsExtensions);
        peerFlags.set(5, outgoingConnection);
        peerFlags.set(6, localConnection);
        peerFlags.set(7, handshake);
        peerFlags.set(8, connecting);
        peerFlags.set(9, onParole);
        peerFlags.set(10, seed);
        peerFlags.set(11, optimisticUnchoke);
        peerFlags.set(12, snubbed);
        peerFlags.set(13, uploadOnly);
        peerFlags.set(14, endGameMode);
        peerFlags.set(15, holePunched);
        peerFlags.set(16, i2pSocket);
        peerFlags.set(17, utpSocket);
        peerFlags.set(18, sslSocket);
        peerFlags.set(19, rc4Encrypted);
        peerFlags.set(20, plainTextEncrypted);
        peerSourceFlags.set(0, fromTracker);
        peerSourceFlags.set(1, fromDHT);
        peerSourceFlags.set(2, fromPEX);
        peerSourceFlags.set(3, fromLSD);
        peerSourceFlags.set(4, fromResumeData);
        peerSourceFlags.set(5, fromIncoming);
        this.ltStdString = toString();
    }

    public boolean isInteresting() {
        return peerFlags.get(0);
    }

    public boolean isChoked() {
        return peerFlags.get(1);
    }

    public boolean isRemoteInterested() {
        return peerFlags.get(2);
    }

    public boolean isRemoteChoked() {
        return peerFlags.get(3);
    }

    public boolean isSupportsExtensions() {
        return peerFlags.get(4);
    }

    public boolean isOutgoingConnection() {
        return peerFlags.get(5);
    }

    public boolean isLocalConnection() {
        return peerFlags.get(6);
    }

    public boolean isHandshake() {
        return peerFlags.get(7);
    }

    public boolean isConnecting() {
        return peerFlags.get(8);
    }

    public boolean isOnParole() {
        return peerFlags.get(9);
    }

    public boolean isSeed() {
        return peerFlags.get(10);
    }

    public boolean isOptimisticUnchoke() {
        return peerFlags.get(11);
    }

    public boolean isSnubbed() {
        return peerFlags.get(12);
    }

    public boolean isUploadOnly() {
        return peerFlags.get(13);
    }

    public boolean isEndGameMode() {
        return peerFlags.get(14);
    }

    public boolean isHolePunched() {
        return peerFlags.get(15);
    }

    public boolean isI2pSocket() {
        return peerFlags.get(16);
    }

    public boolean isUtpSocket() {
        return peerFlags.get(17);
    }

    public boolean isSslSocket() {
        return peerFlags.get(18);
    }

    public boolean isRc4Encrypted() {
        return peerFlags.get(19);
    }

    public boolean isPlainTextEncrypted() {
        return peerFlags.get(20);
    }

    public boolean isFromTracker() {
        return peerSourceFlags.get(0);
    }

    public boolean isFromDHT() {
        return peerSourceFlags.get(1);
    }

    public boolean isFromPEX() {
        return peerSourceFlags.get(2);
    }

    public boolean isFromLSD() {
        return peerSourceFlags.get(3);
    }

    public boolean isFromResumeData() {
        return peerSourceFlags.get(4);
    }

    public boolean isFromIncoming() {
        return peerSourceFlags.get(5);
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(" ");
        if (isInteresting()) {
            if (isRemoteChoked()) {
                joiner.add("d");
            } else {
                joiner.add("D");
            }
        }
        if (isRemoteInterested()) {
            if (isChoked()) {
                joiner.add("u");
            } else {
                joiner.add("U");
            }
        }

        if (!isRemoteChoked() && !isInteresting())
            joiner.add("K");
        if (!isChoked() && !isRemoteInterested())
            joiner.add("?");
        if (isOptimisticUnchoke()) {
            joiner.add("O");
        }
        if (isSnubbed()) {
            joiner.add("S");
        }
        if (!isLocalConnection()) {
            joiner.add("I");
        }
        if (isFromDHT()) {
            joiner.add("H");
        }
        if (isFromPEX()) {
            joiner.add("X");
        }
        if (isFromLSD()) {
            joiner.add("L");
        }
        if (isRc4Encrypted()) {
            joiner.add("E");
        }
        if (isPlainTextEncrypted()) {
            joiner.add("e");
        }
        if (isUtpSocket()) {
            joiner.add("P");
        }
        return joiner.toString();
    }

    public void parseLibTorrent(String flags) {
        boolean interesting = false;
        boolean remoteChoked = false;
        boolean remoteInterested = false;
        boolean choked = false;
        boolean optimisticUnchoke = false;
        boolean snubbed = false;
        boolean localConnection = false;
        boolean fromDHT = false;
        boolean fromPEX = false;
        boolean fromLSD = false;
        boolean rc4Encrypted = false;
        boolean plainTextEncrypted = false;
        boolean utpSocket = false;
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
        peerFlags.set(0, interesting);
        peerFlags.set(1, choked);
        peerFlags.set(2, remoteInterested);
        peerFlags.set(3, remoteChoked);
        peerFlags.set(6, localConnection);
        peerFlags.set(11, optimisticUnchoke);
        peerFlags.set(12, snubbed);
        peerFlags.set(17, utpSocket);
        peerFlags.set(19, rc4Encrypted);
        peerFlags.set(20, plainTextEncrypted);
        peerSourceFlags.set(1, fromDHT);
        peerSourceFlags.set(2, fromPEX);
        peerSourceFlags.set(3, fromLSD);
    }

}
