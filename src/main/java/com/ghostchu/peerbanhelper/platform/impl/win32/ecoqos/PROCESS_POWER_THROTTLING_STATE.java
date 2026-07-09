package com.ghostchu.peerbanhelper.platform.impl.win32.ecoqos;

import java.lang.foreign.*;

/**
 * PROCESS_POWER_THROTTLING_STATE 结构体布局（Foreign Function & Memory API）
 * <pre>
 * typedef struct _PROCESS_POWER_THROTTLING_STATE {
 *   ULONG Version;
 *   ULONG ControlMask;
 *   ULONG StateMask;
 * } PROCESS_POWER_THROTTLING_STATE;
 * </pre>
 */
public final class PROCESS_POWER_THROTTLING_STATE {

    public static final int PROCESS_POWER_THROTTLING_CURRENT_VERSION = 1;
    public static final int PROCESS_POWER_THROTTLING_EXECUTION_SPEED = 0x1;

    /** 结构体内存布局：三个 ULONG（JAVA_INT，4 字节，自然对齐） */
    public static final StructLayout LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("Version"),
            ValueLayout.JAVA_INT.withName("ControlMask"),
            ValueLayout.JAVA_INT.withName("StateMask")
    );

    private static final long OFFSET_VERSION     = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("Version"));
    private static final long OFFSET_CONTROL     = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("ControlMask"));
    private static final long OFFSET_STATE       = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("StateMask"));

    private final MemorySegment segment;

    /**
     * 在给定 Arena 中分配并零初始化结构体
     */
    public PROCESS_POWER_THROTTLING_STATE(Arena arena) {
        this.segment = arena.allocate(LAYOUT);
    }

    public void setVersion(int v)      { segment.set(ValueLayout.JAVA_INT, OFFSET_VERSION, v); }
    public void setControlMask(int v)  { segment.set(ValueLayout.JAVA_INT, OFFSET_CONTROL, v); }
    public void setStateMask(int v)    { segment.set(ValueLayout.JAVA_INT, OFFSET_STATE,   v); }

    public int getVersion()     { return segment.get(ValueLayout.JAVA_INT, OFFSET_VERSION); }
    public int getControlMask() { return segment.get(ValueLayout.JAVA_INT, OFFSET_CONTROL); }
    public int getStateMask()   { return segment.get(ValueLayout.JAVA_INT, OFFSET_STATE);   }

    /** 返回底层 MemorySegment，用于传递给 native 函数 */
    public MemorySegment segment() { return segment; }

    /** 结构体字节大小 */
    public long byteSize() { return LAYOUT.byteSize(); }
}