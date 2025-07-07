package com.ghostchu.peerbanhelper;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class CrashTest {

    public static void crash() {
        getUnsafeInstance().putAddress(0, 0);
    }

    public static void main(String[] args) {
        crash();
    }
    public static Unsafe getUnsafeInstance() {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (Unsafe) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get Unsafe instance", e);
        }
    }
}
