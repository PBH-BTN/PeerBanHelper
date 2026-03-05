package com.ghostchu.peerbanhelper.platform.impl.win32.common;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * Windows PSAPI — Foreign Function & Memory API 实现
 */
public final class PSAPI {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup PSAPI_LIB = SymbolLookup.libraryLookup("psapi", Arena.global());

    // BOOL EmptyWorkingSet(HANDLE hProcess)
    private static final MethodHandle MH_EMPTY_WORKING_SET = LINKER.downcallHandle(
            PSAPI_LIB.find("EmptyWorkingSet").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
    );

    private PSAPI() {
    }

    /**
     * EmptyWorkingSet — 清空进程工作集
     *
     * @param hProcess 进程句柄
     * @return 成功返回 true
     */
    public static boolean emptyWorkingSet(MemorySegment hProcess) {
        try {
            int result = (int) MH_EMPTY_WORKING_SET.invokeExact(hProcess);
            return result != 0;
        } catch (Throwable t) {
            throw new RuntimeException("EmptyWorkingSet failed", t);
        }
    }
}
