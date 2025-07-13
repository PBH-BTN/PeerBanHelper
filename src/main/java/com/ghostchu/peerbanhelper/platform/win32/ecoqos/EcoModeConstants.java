package com.ghostchu.peerbanhelper.platform.win32.ecoqos;

public class EcoModeConstants {
    // Process Information Class
    public static final int ProcessPowerThrottling = 4; // 对应 C 代码中的 ProcessPowerThrottling 枚举值

    // Priority Class Constants
    public static final int IDLE_PRIORITY_CLASS = 0x00000040;
    public static final int NORMAL_PRIORITY_CLASS = 0x00000020;
    public static final int HIGH_PRIORITY_CLASS = 0x00000080;
    public static final int REALTIME_PRIORITY_CLASS = 0x00000100;
}