package com.ghostchu.peerbanhelper.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SharedObject {
    public static final Map<Object, Object> SCRIPT_THREAD_SAFE_MAP = new ConcurrentHashMap<>();
    public static final String SILENT_LOGIN_TOKEN_FOR_GUI = UUID.randomUUID().toString();
}
