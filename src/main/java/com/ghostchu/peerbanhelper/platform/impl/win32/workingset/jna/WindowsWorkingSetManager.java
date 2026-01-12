package com.ghostchu.peerbanhelper.platform.impl.win32.workingset.jna;

import com.ghostchu.peerbanhelper.platform.impl.win32.common.Kernel32;
import com.ghostchu.peerbanhelper.platform.impl.win32.common.PSAPI;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Windows 工作集管理器
 * 提供将进程不常用部分存入虚拟内存（分页文件）的功能
 */
public class WindowsWorkingSetManager {
    
    private static final Logger logger = LoggerFactory.getLogger(WindowsWorkingSetManager.class);
    
    // 特殊值，用于让系统自动管理工作集大小
    private static final long WORKING_SET_SIZE_AUTO = -1L;
    
    private final Kernel32 kernel32;
    private final PSAPI psapi;
    private final WinNT.HANDLE currentProcessHandle;
    
    /**
     * 构造函数
     */
    public WindowsWorkingSetManager() {
        this.kernel32 = Kernel32.INSTANCE;
        this.psapi = PSAPI.INSTANCE;
        this.currentProcessHandle = kernel32.GetCurrentProcess();
        
        if (currentProcessHandle == null) {
            throw new RuntimeException("Unable to retrieve current process handle");
        }
    }
    
    /**
     * 清空当前进程的工作集
     * 将不常用的页面从物理内存移动到分页文件
     * 
     * @return 操作是否成功
     */
    public boolean emptyWorkingSet() {
        try {
            boolean result = psapi.EmptyWorkingSet(currentProcessHandle);
            
            if (!result) {
                int errorCode = kernel32.GetLastError();
                logger.warn("Unable to empty working set {}", errorCode);
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error while empty working set", e);
            return false;
        }
    }
    
    /**
     * 设置进程工作集大小限制
     * 
     * @param minSizeBytes 最小工作集大小（字节），-1 表示自动管理
     * @param maxSizeBytes 最大工作集大小（字节），-1 表示自动管理
     * @return 操作是否成功
     */
    public boolean setWorkingSetSize(long minSizeBytes, long maxSizeBytes) {
        try {
            WinDef.DWORD minSize = new WinDef.DWORD(minSizeBytes);
            WinDef.DWORD maxSize = new WinDef.DWORD(maxSizeBytes);
            
            boolean result = kernel32.SetProcessWorkingSetSize(currentProcessHandle, minSize, maxSize);
            
            if (!result) {
                int errorCode = kernel32.GetLastError();
                logger.warn("Unable to set working set size: {}", errorCode);
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("Unable to set working set size", e);
            return false;
        }
    }
    
    /**
     * 设置工作集为自动管理模式
     * 
     * @return 操作是否成功
     */
    public boolean setAutoManageWorkingSet() {
        return setWorkingSetSize(WORKING_SET_SIZE_AUTO, WORKING_SET_SIZE_AUTO);
    }
    
    /**
     * 执行内存压缩操作
     * 先设置较小的工作集限制，然后清空工作集，最后恢复自动管理
     * 
     * @param targetSizeBytes 目标工作集大小（字节），设置为较小值以强制内存压缩
     * @return 操作是否成功
     */
    public boolean compressMemory(long targetSizeBytes) {
        try {
            // 1. 设置较小的工作集限制
            boolean setResult = setWorkingSetSize(targetSizeBytes, targetSizeBytes);
            if (!setResult) {
                logger.warn("Failed to set working set size to target size: {}", targetSizeBytes);
            }
            
            // 2. 清空工作集
            boolean emptyResult = emptyWorkingSet();
            
            // 3. 恢复自动管理
            boolean autoResult = setAutoManageWorkingSet();
            if (!autoResult) {
                logger.warn("Failed to restore working set to auto management");
            }
            
            boolean overallResult = emptyResult; // 主要看清空操作是否成功
            
            if (!overallResult) {
                logger.warn("Failed to compress memory, empty working set failed");
            }
            
            return overallResult;
            
        } catch (Exception e) {
            logger.error("Failed to working set", e);
            return false;
        }
    }
    
    /**
     * 执行轻量级内存整理
     * 仅清空工作集，不修改大小限制
     * 
     * @return 操作是否成功
     */
    public boolean trimMemory() {
        return emptyWorkingSet();
    }
}
