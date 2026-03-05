package com.ghostchu.peerbanhelper.platform.impl.win32.ecoqos;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * EcoMode 所需 Kernel32 函数 — Foreign Function & Memory API 实现
 */
public final class EcoModeNative {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup KERNEL32 = SymbolLookup.libraryLookup("kernel32", Arena.global());

    // HANDLE GetCurrentProcess()
    private static final MethodHandle MH_GET_CURRENT_PROCESS = LINKER.downcallHandle(
            KERNEL32.find("GetCurrentProcess").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS)
    );

    // BOOL SetProcessInformation(HANDLE, PROCESS_INFORMATION_CLASS, LPVOID, DWORD)
    private static final MethodHandle MH_SET_PROCESS_INFORMATION = LINKER.downcallHandle(
            KERNEL32.find("SetProcessInformation").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,   // hProcess
                    ValueLayout.JAVA_INT,  // ProcessInformationClass
                    ValueLayout.ADDRESS,   // ProcessInformation (pointer to struct)
                    ValueLayout.JAVA_INT)  // ProcessInformationSize
    );

    // BOOL SetPriorityClass(HANDLE, DWORD)
    private static final MethodHandle MH_SET_PRIORITY_CLASS = LINKER.downcallHandle(
            KERNEL32.find("SetPriorityClass").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT)
    );

    // DWORD GetLastError()
    private static final MethodHandle MH_GET_LAST_ERROR = LINKER.downcallHandle(
            KERNEL32.find("GetLastError").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT)
    );

    private EcoModeNative() {
    }

    public static MemorySegment getCurrentProcess() {
        try {
            return (MemorySegment) MH_GET_CURRENT_PROCESS.invokeExact();
        } catch (Throwable t) {
            throw new RuntimeException("GetCurrentProcess failed", t);
        }
    }

    /**
     * SetProcessInformation
     *
     * @param hProcess                进程句柄
     * @param processInformationClass 信息类型（枚举值）
     * @param infoSegment             指向信息结构体的 MemorySegment
     * @param infoSize                结构体字节大小
     * @return 成功返回 true
     */
    public static boolean setProcessInformation(MemorySegment hProcess, int processInformationClass,
                                                MemorySegment infoSegment, int infoSize) {
        try {
            int result = (int) MH_SET_PROCESS_INFORMATION.invokeExact(hProcess, processInformationClass, infoSegment, infoSize);
            return result != 0;
        } catch (Throwable t) {
            throw new RuntimeException("SetProcessInformation failed", t);
        }
    }

    /**
     * SetPriorityClass
     *
     * @return 成功返回 true
     */
    public static boolean setPriorityClass(MemorySegment hProcess, int dwPriorityClass) {
        try {
            int result = (int) MH_SET_PRIORITY_CLASS.invokeExact(hProcess, dwPriorityClass);
            return result != 0;
        } catch (Throwable t) {
            throw new RuntimeException("SetPriorityClass failed", t);
        }
    }

    public static int getLastError() {
        try {
            return (int) MH_GET_LAST_ERROR.invokeExact();
        } catch (Throwable t) {
            throw new RuntimeException("GetLastError failed", t);
        }
    }
}