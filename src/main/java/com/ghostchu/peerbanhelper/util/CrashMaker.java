package com.ghostchu.peerbanhelper.util;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

@SuppressWarnings("removal")
public class CrashMaker {
    public void crash() {
        getUnsafeInstance().putAddress(0, 0);
    }

    public Unsafe getUnsafeInstance() {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (Unsafe) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get Unsafe instance", e);
        }
    }
}
