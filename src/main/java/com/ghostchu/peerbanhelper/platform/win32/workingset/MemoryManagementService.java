package com.ghostchu.peerbanhelper.platform.win32.workingset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 内存管理服务集成示例
 * 展示如何在应用程序中集成和使用工作集管理功能
 */
public class MemoryManagementService {
    
    private static final Logger logger = LoggerFactory.getLogger(MemoryManagementService.class);
    
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "MemoryManagement-Thread");
        t.setDaemon(true);
        return t;
    });
    
    private final MemoryMonitor monitor = new MemoryMonitor();
    private final boolean isSupported;
    
    // 配置参数
    private final long checkIntervalMinutes;
    private final double memoryThreshold;
    private final boolean enableAutoTrim;
    private final boolean enablePeriodicCompress;
    private final long compressIntervalHours;
    
    /**
     * 构造函数
     * 
     * @param checkIntervalMinutes 检查间隔（分钟）
     * @param memoryThreshold 内存阈值（0.0-1.0）
     * @param enableAutoTrim 启用自动内存整理
     * @param enablePeriodicCompress 启用定期内存压缩
     * @param compressIntervalHours 压缩间隔（小时）
     */
    public MemoryManagementService(long checkIntervalMinutes, double memoryThreshold, 
                                  boolean enableAutoTrim, boolean enablePeriodicCompress,
                                  long compressIntervalHours) {
        this.checkIntervalMinutes = checkIntervalMinutes;
        this.memoryThreshold = memoryThreshold;
        this.enableAutoTrim = enableAutoTrim;
        this.enablePeriodicCompress = enablePeriodicCompress;
        this.compressIntervalHours = compressIntervalHours;
        this.isSupported = WorkingSetManagerFactory.isSupported();
        
        logger.info("内存管理服务初始化: 平台支持={}, 检查间隔={}分钟, 阈值={}", 
                   isSupported, checkIntervalMinutes, memoryThreshold);
    }
    
    /**
     * 使用默认配置的构造函数
     */
    public MemoryManagementService() {
        this(5, 0.8, true, true, 2); // 5分钟检查，80%阈值，2小时压缩
    }
    
    /**
     * 启动内存管理服务
     */
    public void start() {
        if (!isSupported) {
            logger.info("当前平台不支持工作集管理，内存管理服务将不会启动");
            return;
        }
        
        logger.info("启动内存管理服务...");
        
        // 启动定期检查任务
        if (enableAutoTrim) {
            scheduler.scheduleWithFixedDelay(
                this::performMemoryCheck,
                checkIntervalMinutes,
                checkIntervalMinutes,
                TimeUnit.MINUTES
            );
            logger.info("定期内存检查任务已启动，间隔: {} 分钟", checkIntervalMinutes);
        }
        
        // 启动定期压缩任务
        if (enablePeriodicCompress) {
            scheduler.scheduleWithFixedDelay(
                this::performMemoryCompress,
                compressIntervalHours,
                compressIntervalHours,
                TimeUnit.HOURS
            );
            logger.info("定期内存压缩任务已启动，间隔: {} 小时", compressIntervalHours);
        }
        
        // 打印初始内存状态
        logMemoryStatus();
    }
    
    /**
     * 停止内存管理服务
     */
    public void stop() {
        logger.info("停止内存管理服务...");
        scheduler.shutdown();
        
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("内存管理服务已停止");
    }
    
    /**
     * 执行内存检查
     */
    private void performMemoryCheck() {
        try {
            logger.debug("执行定期内存检查...");
            
            if (monitor.isMemoryUsageHigh(memoryThreshold)) {
                logger.info("检测到内存使用率超过阈值 ({})，执行内存整理", memoryThreshold);
                
                boolean result = WorkingSetManagerFactory.trimMemory();
                if (result) {
                    logger.info("自动内存整理完成");
                    logMemoryStatus();
                } else {
                    logger.warn("自动内存整理失败");
                }
            } else {
                logger.debug("内存使用率正常，无需整理");
            }
            
        } catch (Exception e) {
            logger.error("执行内存检查时发生异常", e);
        }
    }
    
    /**
     * 执行内存压缩
     */
    private void performMemoryCompress() {
        try {
            logger.info("执行定期内存压缩...");
            
            long estimatedWorkingSet = monitor.getEstimatedWorkingSetSize();
            long targetSize = Math.max(estimatedWorkingSet / 4, 16 * 1024 * 1024); // 压缩到1/4或最少16MB
            
            boolean result = WorkingSetManagerFactory.compressMemory(targetSize);
            if (result) {
                logger.info("定期内存压缩完成，目标大小: {}", MemoryMonitor.formatBytes(targetSize));
                logMemoryStatus();
            } else {
                logger.warn("定期内存压缩失败");
            }
            
        } catch (Exception e) {
            logger.error("执行内存压缩时发生异常", e);
        }
    }
    
    /**
     * 手动触发内存整理
     * 
     * @return 操作是否成功
     */
    public boolean triggerMemoryTrim() {
        if (!isSupported) {
            logger.debug("平台不支持，无法执行内存整理");
            return false;
        }
        
        logger.info("手动触发内存整理...");
        boolean result = WorkingSetManagerFactory.trimMemory();
        
        if (result) {
            logger.info("手动内存整理完成");
            logMemoryStatus();
        } else {
            logger.warn("手动内存整理失败");
        }
        
        return result;
    }
    
    /**
     * 手动触发内存压缩
     * 
     * @param targetSizeBytes 目标大小（字节）
     * @return 操作是否成功
     */
    public boolean triggerMemoryCompress(long targetSizeBytes) {
        if (!isSupported) {
            logger.debug("平台不支持，无法执行内存压缩");
            return false;
        }
        
        logger.info("手动触发内存压缩，目标大小: {}", MemoryMonitor.formatBytes(targetSizeBytes));
        boolean result = WorkingSetManagerFactory.compressMemory(targetSizeBytes);
        
        if (result) {
            logger.info("手动内存压缩完成");
            logMemoryStatus();
        } else {
            logger.warn("手动内存压缩失败");
        }
        
        return result;
    }
    
    /**
     * 获取内存状态信息
     * 
     * @return 内存状态字符串
     */
    public String getMemoryStatus() {
        return monitor.getMemorySummary();
    }
    
    /**
     * 记录当前内存状态
     */
    private void logMemoryStatus() {
        logger.info("当前内存状态:\n{}", monitor.getMemorySummary());
    }
    
    /**
     * 检查服务是否支持
     * 
     * @return 如果支持返回true
     */
    public boolean isSupported() {
        return isSupported;
    }
    
    /**
     * 应用程序关闭时的清理方法
     * 建议在应用程序的shutdown hook中调用
     */
    public void performShutdownCleanup() {
        if (!isSupported) {
            return;
        }
        
        logger.info("执行应用程序关闭时的内存清理...");
        
        try {
            // 在关闭前执行一次内存整理
            boolean result = WorkingSetManagerFactory.trimMemory();
            if (result) {
                logger.info("关闭时内存清理完成");
            } else {
                logger.debug("关闭时内存清理失败，但不影响程序正常关闭");
            }
        } catch (Exception e) {
            logger.debug("关闭时内存清理发生异常，但不影响程序正常关闭", e);
        }
    }
    
    /**
     * 使用示例
     */
    public static void main(String[] args) {
        // 创建内存管理服务
        MemoryManagementService service = new MemoryManagementService();
        
        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            service.performShutdownCleanup();
            service.stop();
        }));
        
        // 启动服务
        service.start();
        
        // 模拟应用程序运行
        try {
            logger.info("应用程序运行中...");
            
            // 手动触发一次内存整理测试
            Thread.sleep(5000);
            service.triggerMemoryTrim();
            
            // 让程序运行一段时间以观察自动管理
            Thread.sleep(30000);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        logger.info("示例程序结束");
    }
}
