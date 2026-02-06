package com.ghostchu.peerbanhelper.platform.impl.win32.ecoqos;

import com.sun.jna.Structure;

// 定义 PROCESS_POWER_THROTTLING_STATE 结构体
public class PROCESS_POWER_THROTTLING_STATE extends Structure {
    public int Version;
    public int ControlMask;
    public int StateMask;

    public PROCESS_POWER_THROTTLING_STATE() {
        super();
    }

    @Override
    protected java.util.List<String> getFieldOrder() {
        return java.util.Arrays.asList("Version", "ControlMask", "StateMask");
    }

    // 常量定义，对应 C 代码中的宏
    public static final int PROCESS_POWER_THROTTLING_CURRENT_VERSION = 1;
    public static final int PROCESS_POWER_THROTTLING_EXECUTION_SPEED = 0x1; // 从 C 代码中推断
}