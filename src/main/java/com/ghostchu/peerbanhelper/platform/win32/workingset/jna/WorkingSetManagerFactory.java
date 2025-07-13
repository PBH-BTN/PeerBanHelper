package com.ghostchu.peerbanhelper.platform.win32.workingset.jna;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 工作集管理器工厂类
 * 提供平台无关的内存管理接口
 */
public class WorkingSetManagerFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(WorkingSetManagerFactory.class);
    
    private static volatile WindowsWorkingSetManager instance;
    private static volatile boolean isWindows;
    
    static {
        // 检测操作系统
        String osName = System.getProperty("os.name", "").toLowerCase();
        isWindows = osName.contains("windows");
    }
    
    /**
     * 获取工作集管理器实例
     * 
     * @return 工作集管理器实例，如果不是Windows平台则返回null
     */
    public static WindowsWorkingSetManager getInstance() {
        if (!isWindows) {
            return null;
        }
        
        if (instance == null) {
            synchronized (WorkingSetManagerFactory.class) {
                if (instance == null) {
                    try {
                        instance = new WindowsWorkingSetManager();
                    } catch (Exception e) {
                        return null;
                    }
                }
            }
        }
        
        return instance;
    }
    
    /**
     * 检查当前平台是否支持工作集管理
     * 
     * @return 如果支持返回true，否则返回false
     */
    public static boolean isSupported() {
        return isWindows;
    }
    
    /**
     * 执行内存整理操作（便捷方法）
     * 
     * @return 操作是否成功
     */
    public static boolean trimMemory() {
        WindowsWorkingSetManager manager = getInstance();
        if (manager != null) {
            return manager.trimMemory();
        }
        return false;
    }
    
    /**
     * 执行内存压缩操作（便捷方法）
     * 
     * @param targetSizeBytes 目标工作集大小（字节）
     * @return 操作是否成功
     */
    public static boolean compressMemory(long targetSizeBytes) {
        WindowsWorkingSetManager manager = getInstance();
        if (manager != null) {
            return manager.compressMemory(targetSizeBytes);
        }
        return false;
    }
    
    /**
     * 使用默认参数执行内存压缩操作（便捷方法）
     * 目标大小设置为8MB
     * 
     * @return 操作是否成功
     */
    public static boolean compressMemory() {
        return compressMemory(8 * 1024 * 1024); // 8MB
    }
}
