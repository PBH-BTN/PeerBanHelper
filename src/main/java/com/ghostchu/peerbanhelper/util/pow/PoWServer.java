package com.ghostchu.peerbanhelper.util.pow;

import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * PoW 无交互验证码（服务端）
 */
public class PoWServer {
    private final byte[] challenge;
    private final int difficultyBits; // e.g., 20 bits = leading 2.5 bytes of zero
    private final String algorithm;

    public PoWServer(int difficultyBits, String algorithm) {
        this.difficultyBits = difficultyBits;
        this.challenge = new byte[32];
        this.algorithm = algorithm;
        new SecureRandom().nextBytes(this.challenge);
    }

    public byte[] getChallenge() {
        return challenge;
    }

    public int getDifficultyBits() {
        return difficultyBits;
    }

    public boolean verify(byte[] nonce) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        digest.update(challenge);
        digest.update(nonce);
        byte[] hash = digest.digest();
        return hasLeadingZeroBits(hash, difficultyBits);
    }

    private boolean hasLeadingZeroBits(byte[] hash, int bits) {
        int fullBytes = bits / 8;
        int remainingBits = bits % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (hash[i] != 0) return false;
        }
        if (remainingBits > 0) {
            int mask = 0xFF << (8 - remainingBits);
            return (hash[fullBytes] & mask) == 0;
        }
        return true;
    }
}