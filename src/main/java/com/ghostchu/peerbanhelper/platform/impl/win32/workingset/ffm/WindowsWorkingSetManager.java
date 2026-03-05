package com.ghostchu.peerbanhelper.platform.impl.win32.workingset.ffm;

import com.ghostchu.peerbanhelper.platform.impl.win32.common.Kernel32;
import com.ghostchu.peerbanhelper.platform.impl.win32.common.PSAPI;
import io.sentry.Sentry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;

/**
 * Windows 工作集管理器
 * 提供将进程不常用部分存入虚拟内存（分页文件）的功能
 */
public class WindowsWorkingSetManager {

    private static final Logger logger = LoggerFactory.getLogger(WindowsWorkingSetManager.class);

    // 特殊值：让系统自动管理工作集大小（传递 -1L 即 SIZE_T 最大值）
    private static final long WORKING_SET_SIZE_AUTO = -1L;

    private final MemorySegment currentProcessHandle;

    /**
     * 构造函数
     */
    public WindowsWorkingSetManager() {
        this.currentProcessHandle = Kernel32.getCurrentProcess();
        if (currentProcessHandle == null || currentProcessHandle.address() == 0) {
            throw new RuntimeException("Unable to retrieve current process handle");
        }
    }

    /**
     * 清空当前进程的工作集
     */
    public boolean emptyWorkingSet() {
        try {
            boolean result = PSAPI.emptyWorkingSet(currentProcessHandle);
            if (!result) {
                int errorCode = Kernel32.getLastError();
                logger.warn("Unable to empty working set {}", errorCode);
            }
            return result;
        } catch (Exception e) {
            logger.error("Error while empty working set", e);
            Sentry.captureException(e);
            return false;
        }
    }

    /**
     * 设置进程工作集大小限制
     *
     * @param minSizeBytes 最小工作集大小（字节），-1 表示自动管理
     * @param maxSizeBytes 最大工作集大小（字节），-1 表示自动管理
     */
    public boolean setWorkingSetSize(long minSizeBytes, long maxSizeBytes) {
        try {
            boolean result = Kernel32.setProcessWorkingSetSize(currentProcessHandle, minSizeBytes, maxSizeBytes);
            if (!result) {
                int errorCode = Kernel32.getLastError();
                logger.warn("Unable to set working set size: {}", errorCode);
            }
            return result;
        } catch (Exception e) {
            logger.error("Unable to set working set size", e);
            Sentry.captureException(e);
            return false;
        }
    }

    /**
     * 设置工作集为自动管理模式
     */
    public boolean setAutoManageWorkingSet() {
        return setWorkingSetSize(WORKING_SET_SIZE_AUTO, WORKING_SET_SIZE_AUTO);
    }

    /**
     * 执行内存压缩操作
     */
    public boolean compressMemory(long targetSizeBytes) {
        try {
            boolean setResult = setWorkingSetSize(targetSizeBytes, targetSizeBytes);
            if (!setResult) {
                logger.warn("Failed to set working set size to target size: {}", targetSizeBytes);
            }

            boolean emptyResult = emptyWorkingSet();

            boolean autoResult = setAutoManageWorkingSet();
            if (!autoResult) {
                logger.warn("Failed to restore working set to auto management");
            }

            if (!emptyResult) {
                logger.warn("Failed to compress memory, empty working set failed");
            }

            return emptyResult;
        } catch (Exception e) {
            logger.error("Failed to working set", e);
            Sentry.captureException(e);
            return false;
        }
    }

    /**
     * 执行轻量级内存整理
     */
    public boolean trimMemory() {
        return emptyWorkingSet();
    }
}
