package com.ghostchu.peerbanhelper.platform.win32.workingset;

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
    private final WinNT.HANDLE currentProcessHandle;
    
    /**
     * 构造函数
     */
    public WindowsWorkingSetManager() {
        this.kernel32 = Kernel32.INSTANCE;
        this.currentProcessHandle = kernel32.GetCurrentProcess();
        
        if (currentProcessHandle == null) {
            throw new RuntimeException("无法获取当前进程句柄");
        }
        
        logger.info("Windows 工作集管理器初始化完成");
    }
    
    /**
     * 清空当前进程的工作集
     * 将不常用的页面从物理内存移动到分页文件
     * 
     * @return 操作是否成功
     */
    public boolean emptyWorkingSet() {
        try {
            logger.debug("开始清空进程工作集...");
            
            boolean result = kernel32.EmptyWorkingSet(currentProcessHandle);
            
            if (result) {
                logger.info("成功清空进程工作集，不常用内存已移至分页文件");
            } else {
                int errorCode = kernel32.GetLastError();
                logger.warn("清空进程工作集失败，错误代码: {}", errorCode);
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("清空进程工作集时发生异常", e);
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
            logger.debug("设置进程工作集大小限制: min={}, max={}", minSizeBytes, maxSizeBytes);
            
            WinDef.DWORD minSize = new WinDef.DWORD(minSizeBytes == WORKING_SET_SIZE_AUTO ? WORKING_SET_SIZE_AUTO : minSizeBytes);
            WinDef.DWORD maxSize = new WinDef.DWORD(maxSizeBytes == WORKING_SET_SIZE_AUTO ? WORKING_SET_SIZE_AUTO : maxSizeBytes);
            
            boolean result = kernel32.SetProcessWorkingSetSize(currentProcessHandle, minSize, maxSize);
            
            if (result) {
                if (minSizeBytes == WORKING_SET_SIZE_AUTO && maxSizeBytes == WORKING_SET_SIZE_AUTO) {
                    logger.info("成功设置进程工作集为自动管理模式");
                } else {
                    logger.info("成功设置进程工作集大小限制: min={} bytes, max={} bytes", minSizeBytes, maxSizeBytes);
                }
            } else {
                int errorCode = kernel32.GetLastError();
                logger.warn("设置进程工作集大小失败，错误代码: {}", errorCode);
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("设置进程工作集大小时发生异常", e);
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
            logger.info("开始执行内存压缩操作，目标大小: {} bytes", targetSizeBytes);
            
            // 1. 设置较小的工作集限制
            boolean setResult = setWorkingSetSize(targetSizeBytes, targetSizeBytes);
            if (!setResult) {
                logger.warn("设置工作集限制失败，但继续执行清空操作");
            }
            
            // 2. 清空工作集
            boolean emptyResult = emptyWorkingSet();
            
            // 3. 恢复自动管理
            boolean autoResult = setAutoManageWorkingSet();
            if (!autoResult) {
                logger.warn("恢复自动管理模式失败");
            }
            
            boolean overallResult = emptyResult; // 主要看清空操作是否成功
            
            if (overallResult) {
                logger.info("内存压缩操作完成");
            } else {
                logger.warn("内存压缩操作部分失败");
            }
            
            return overallResult;
            
        } catch (Exception e) {
            logger.error("执行内存压缩操作时发生异常", e);
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
        logger.info("执行轻量级内存整理");
        return emptyWorkingSet();
    }
}
