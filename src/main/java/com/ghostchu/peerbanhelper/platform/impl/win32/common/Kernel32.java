package com.ghostchu.peerbanhelper.platform.impl.win32.common;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * Windows Kernel32 API — Foreign Function & Memory API 实现
 */
public final class Kernel32 {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup KERNEL32 = SymbolLookup.libraryLookup("kernel32", Arena.global());

    // HANDLE GetCurrentProcess()
    private static final MethodHandle MH_GET_CURRENT_PROCESS = LINKER.downcallHandle(
            KERNEL32.find("GetCurrentProcess").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS)
    );

    // BOOL SetProcessWorkingSetSize(HANDLE hProcess, SIZE_T min, SIZE_T max)
    // On 64-bit Windows SIZE_T = 8 bytes => JAVA_LONG
    private static final MethodHandle MH_SET_WORKING_SET_SIZE = LINKER.downcallHandle(
            KERNEL32.find("SetProcessWorkingSetSize").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG)
    );

    // DWORD GetLastError()
    private static final MethodHandle MH_GET_LAST_ERROR = LINKER.downcallHandle(
            KERNEL32.find("GetLastError").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT)
    );

    private Kernel32() {
    }

    /**
     * GetCurrentProcess — 返回伪句柄，无需关闭
     */
    public static MemorySegment getCurrentProcess() {
        try {
            return (MemorySegment) MH_GET_CURRENT_PROCESS.invokeExact();
        } catch (Throwable t) {
            throw new RuntimeException("GetCurrentProcess failed", t);
        }
    }

    /**
     * SetProcessWorkingSetSize
     *
     * @return 非零为成功
     */
    public static boolean setProcessWorkingSetSize(MemorySegment hProcess, long minBytes, long maxBytes) {
        try {
            int result = (int) MH_SET_WORKING_SET_SIZE.invokeExact(hProcess, minBytes, maxBytes);
            return result != 0;
        } catch (Throwable t) {
            throw new RuntimeException("SetProcessWorkingSetSize failed", t);
        }
    }

    /**
     * GetLastError
     */
    public static int getLastError() {
        try {
            return (int) MH_GET_LAST_ERROR.invokeExact();
        } catch (Throwable t) {
            throw new RuntimeException("GetLastError failed", t);
        }
    }
}
