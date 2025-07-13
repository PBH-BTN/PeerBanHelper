package com.ghostchu.peerbanhelper.platform.win32.ecoqos;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.WinNT;

public interface EcoModeNative extends Library {

    EcoModeNative INSTANCE = Native.load("kernel32", EcoModeNative.class);

    // BOOL SetProcessInformation(HANDLE hProcess, PROCESS_INFORMATION_CLASS ProcessInformationClass, LPVOID ProcessInformation, DWORD ProcessInformationSize);
    boolean SetProcessInformation(WinNT.HANDLE hProcess, int ProcessInformationClass, Structure ProcessInformation, int ProcessInformationSize);

    // BOOL SetPriorityClass(HANDLE hProcess, DWORD dwPriorityClass);
    boolean SetPriorityClass(WinNT.HANDLE hProcess, int dwPriorityClass);

    WinNT.HANDLE GetCurrentProcess();

    int GetLastError();
}