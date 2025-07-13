package com.ghostchu.peerbanhelper.platform.win32.workingset.jna;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.StdCallLibrary;

/**
 * Windows Kernel32 API 接口定义
 * 用于进程工作集内存管理
 */
public interface Kernel32 extends StdCallLibrary {
    
    Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);
    
    /**
     * 获取当前进程的句柄
     * @return 当前进程的句柄
     */
    WinNT.HANDLE GetCurrentProcess();
    
    /**
     * 设置进程的工作集大小限制
     * @param hProcess 进程句柄
     * @param dwMinimumWorkingSetSize 最小工作集大小（以字节为单位）
     * @param dwMaximumWorkingSetSize 最大工作集大小（以字节为单位）
     * @return 如果函数成功，返回值为非零值；如果函数失败，返回值为零
     */
    boolean SetProcessWorkingSetSize(
            WinNT.HANDLE hProcess,
            WinDef.DWORD dwMinimumWorkingSetSize,
            WinDef.DWORD dwMaximumWorkingSetSize
    );
    
    /**
     * 获取最后的错误代码
     * @return 最后的错误代码
     */
    int GetLastError();
}
