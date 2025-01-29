package com.ghostchu.peerbanhelper.util;

public final class NullUtil {
    @SafeVarargs
    public static <T> T anyNotNull(T... obj) {
        for (T t : obj) {
            if (t != null) {
                return t;
            }
        }
        return null;
    }

}
