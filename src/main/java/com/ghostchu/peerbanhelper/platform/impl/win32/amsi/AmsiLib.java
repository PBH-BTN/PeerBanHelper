package com.ghostchu.peerbanhelper.platform.impl.win32.amsi;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * Windows AMSI (Antimalware Scan Interface) — Foreign Function & Memory API 实现
 */
public final class AmsiLib {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup AMSI = SymbolLookup.libraryLookup("amsi", Arena.global());

    // HRESULT AmsiInitialize(LPCWSTR appName, HAMSICONTEXT* amsiContext)
    private static final MethodHandle MH_AMSI_INITIALIZE = LINKER.downcallHandle(
            AMSI.find("AmsiInitialize").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,  // appName (LPCWSTR)
                    ValueLayout.ADDRESS)  // amsiContext (out pointer)
    );

    // void AmsiUninitialize(HAMSICONTEXT amsiContext)
    private static final MethodHandle MH_AMSI_UNINITIALIZE = LINKER.downcallHandle(
            AMSI.find("AmsiUninitialize").orElseThrow(),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
    );

    // HRESULT AmsiOpenSession(HAMSICONTEXT amsiContext, HAMSISESSION* amsiSession)
    private static final MethodHandle MH_AMSI_OPEN_SESSION = LINKER.downcallHandle(
            AMSI.find("AmsiOpenSession").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,  // amsiContext
                    ValueLayout.ADDRESS)  // amsiSession (out pointer)
    );

    // void AmsiCloseSession(HAMSICONTEXT amsiContext, HAMSISESSION amsiSession)
    private static final MethodHandle MH_AMSI_CLOSE_SESSION = LINKER.downcallHandle(
            AMSI.find("AmsiCloseSession").orElseThrow(),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    );

    // HRESULT AmsiScanString(HAMSICONTEXT, LPCWSTR string, LPCWSTR contentName, HAMSISESSION, AMSI_RESULT*)
    private static final MethodHandle MH_AMSI_SCAN_STRING = LINKER.downcallHandle(
            AMSI.find("AmsiScanString").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,  // amsiContext
                    ValueLayout.ADDRESS,  // string (LPCWSTR)
                    ValueLayout.ADDRESS,  // contentName (LPCWSTR)
                    ValueLayout.ADDRESS,  // amsiSession
                    ValueLayout.ADDRESS)  // result (AMSI_RESULT*, out)
    );

    // HRESULT AmsiScanBuffer(HAMSICONTEXT, PVOID buffer, ULONG length, LPCWSTR contentName, HAMSISESSION, AMSI_RESULT*)
    private static final MethodHandle MH_AMSI_SCAN_BUFFER = LINKER.downcallHandle(
            AMSI.find("AmsiScanBuffer").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,  // amsiContext
                    ValueLayout.ADDRESS,  // buffer
                    ValueLayout.JAVA_INT, // length (ULONG)
                    ValueLayout.ADDRESS,  // contentName (LPCWSTR)
                    ValueLayout.ADDRESS,  // amsiSession
                    ValueLayout.ADDRESS)  // result (AMSI_RESULT*, out)
    );

    private AmsiLib() {
    }

    /**
     * AmsiInitialize — 将句柄写入 {@code ctxOut}（单个 ADDRESS 大小的 segment）
     */
    public static int amsiInitialize(MemorySegment appNameWStr, MemorySegment ctxOut) {
        try {
            return (int) MH_AMSI_INITIALIZE.invokeExact(appNameWStr, ctxOut);
        } catch (Throwable t) {
            throw new RuntimeException("AmsiInitialize failed", t);
        }
    }

    public static void amsiUninitialize(MemorySegment amsiContext) {
        try {
            MH_AMSI_UNINITIALIZE.invokeExact(amsiContext);
        } catch (Throwable t) {
            throw new RuntimeException("AmsiUninitialize failed", t);
        }
    }

    public static int amsiOpenSession(MemorySegment amsiContext, MemorySegment sessOut) {
        try {
            return (int) MH_AMSI_OPEN_SESSION.invokeExact(amsiContext, sessOut);
        } catch (Throwable t) {
            throw new RuntimeException("AmsiOpenSession failed", t);
        }
    }

    public static void amsiCloseSession(MemorySegment amsiContext, MemorySegment amsiSession) {
        try {
            MH_AMSI_CLOSE_SESSION.invokeExact(amsiContext, amsiSession);
        } catch (Throwable t) {
            throw new RuntimeException("AmsiCloseSession failed", t);
        }
    }

    public static int amsiScanString(MemorySegment amsiContext, MemorySegment stringWStr,
                                     MemorySegment contentNameWStr, MemorySegment amsiSession,
                                     MemorySegment resultOut) {
        try {
            return (int) MH_AMSI_SCAN_STRING.invokeExact(amsiContext, stringWStr, contentNameWStr, amsiSession, resultOut);
        } catch (Throwable t) {
            throw new RuntimeException("AmsiScanString failed", t);
        }
    }

    public static int amsiScanBuffer(MemorySegment amsiContext, MemorySegment buffer, int length,
                                     MemorySegment contentNameWStr, MemorySegment amsiSession,
                                     MemorySegment resultOut) {
        try {
            return (int) MH_AMSI_SCAN_BUFFER.invokeExact(amsiContext, buffer, length, contentNameWStr, amsiSession, resultOut);
        } catch (Throwable t) {
            throw new RuntimeException("AmsiScanBuffer failed", t);
        }
    }
}