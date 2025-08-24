package com.cdnbye.core.nat;

import com.ghostchu.peerbanhelper.util.ByteUtil;

import java.util.Arrays;

/**
 * A wrapper for byte arrays to allow them to be used as keys in a Map.
 * Implements content-based equals() and hashCode().
 */
public final class ByteArrayWrapper {
    private final byte[] data;
    private final int hashCode;

    public ByteArrayWrapper(byte[] data) {
        if (data == null) {
            throw new NullPointerException();
        }
        this.data = data;
        this.hashCode = Arrays.hashCode(data);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        ByteArrayWrapper that = (ByteArrayWrapper) other;
        return Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        // Provide a more readable representation for logging
        return ByteUtil.bytesToHex(data);
    }
}