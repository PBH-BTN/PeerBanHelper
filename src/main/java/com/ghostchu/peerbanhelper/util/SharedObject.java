package com.ghostchu.peerbanhelper.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SharedObject {
    public static final Map<Object, Object> SCRIPT_THREAD_SAFE_MAP = new ConcurrentHashMap<>();
}
