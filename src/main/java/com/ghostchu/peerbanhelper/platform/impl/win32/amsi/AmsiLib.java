package com.ghostchu.peerbanhelper.platform.impl.win32.amsi;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.PointerByReference;

public interface AmsiLib extends Library {
    AmsiLib INSTANCE = Native.load("amsi", AmsiLib.class);

    // HRESULT AmsiInitialize(LPCWSTR appName, HAMSICONTEXT* amsiContext)
    int AmsiInitialize(String appName, PointerByReference amsiContext);

    // void AmsiUninitialize(HAMSICONTEXT amsiContext)
    void AmsiUninitialize(Pointer amsiContext);

    // HRESULT AmsiOpenSession(HAMSICONTEXT amsiContext, HAMSISESSION* amsiSession)
    int AmsiOpenSession(Pointer amsiContext, PointerByReference amsiSession);

    // void AmsiCloseSession(HAMSICONTEXT amsiContext, HAMSISESSION amsiSession)
    void AmsiCloseSession(Pointer amsiContext, Pointer amsiSession);

    // HRESULT AmsiScanString(HAMSICONTEXT amsiContext, LPCWSTR string, LPCWSTR contentName, HAMSISESSION amsiSession, AMSI_RESULT* result)
    int AmsiScanString(Pointer amsiContext, WString string, WString contentName, Pointer amsiSession, int[] result);

    // HRESULT AmsiScanBuffer(HAMSICONTEXT amsiContext, Pointer buffer, ULONG length, LPCWSTR contentName, HAMSISESSION amsiSession, AMSI_RESULT* result)
    int AmsiScanBuffer(Pointer amsiContext, Pointer buffer, int length, WString contentName, Pointer amsiSession, int[] result);
}