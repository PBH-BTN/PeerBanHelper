package com.ghostchu.peerbanhelper.platform.win32.workingset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Windows 工作集管理使用示例
 * 展示如何使用工作集管理功能
 */
public class WorkingSetUsageExample {
    
    private static final Logger logger = LoggerFactory.getLogger(WorkingSetUsageExample.class);
    
    /**
     * 运行示例
     */
    public static void runExample() {
        logger.info("=== Windows 工作集管理示例 ===");
        
        // 检查平台支持
        if (!WorkingSetManagerFactory.isSupported()) {
            logger.warn("当前平台不支持工作集管理功能");
            return;
        }
        
        // 创建内存监控器
        MemoryMonitor monitor = new MemoryMonitor();
        
        // 显示初始内存状态
        logger.info("初始内存状态:");
        logger.info(monitor.getMemorySummary());
        logger.info("估算工作集大小: {}", MemoryMonitor.formatBytes(monitor.getEstimatedWorkingSetSize()));
        
        // 获取工作集管理器
        WindowsWorkingSetManager manager = WorkingSetManagerFactory.getInstance();
        if (manager == null) {
            logger.error("无法获取工作集管理器实例");
            return;
        }
        
        // 示例1：简单的内存整理
        logger.info("\n=== 执行简单内存整理 ===");
        boolean trimResult = manager.trimMemory();
        logger.info("内存整理结果: {}", trimResult ? "成功" : "失败");
        
        // 等待一下让系统处理
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 显示整理后的内存状态
        logger.info("整理后内存状态:");
        logger.info(monitor.getMemorySummary());
        
        // 示例2：内存压缩
        logger.info("\n=== 执行内存压缩 ===");
        long targetSize = 16 * 1024 * 1024; // 16MB
        boolean compressResult = manager.compressMemory(targetSize);
        logger.info("内存压缩结果: {}", compressResult ? "成功" : "失败");
        
        // 等待一下让系统处理
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 显示压缩后的内存状态
        logger.info("压缩后内存状态:");
        logger.info(monitor.getMemorySummary());
        
        // 示例3：使用便捷方法
        logger.info("\n=== 使用便捷方法 ===");
        boolean easyTrimResult = WorkingSetManagerFactory.trimMemory();
        logger.info("便捷内存整理结果: {}", easyTrimResult ? "成功" : "失败");
        
        boolean easyCompressResult = WorkingSetManagerFactory.compressMemory();
        logger.info("便捷内存压缩结果: {}", easyCompressResult ? "成功" : "失败");
        
        // 示例4：基于内存使用情况的智能整理
        logger.info("\n=== 智能内存整理 ===");
        if (monitor.shouldTrimMemory()) {
            logger.info("检测到内存使用率较高，执行内存整理");
            boolean smartTrimResult = manager.trimMemory();
            logger.info("智能内存整理结果: {}", smartTrimResult ? "成功" : "失败");
        } else {
            logger.info("内存使用率正常，无需执行内存整理");
        }
        
        logger.info("\n=== 示例结束 ===");
    }
    
    /**
     * 创建一些内存使用以演示效果
     */
    @SuppressWarnings("unused")
    public static void createMemoryLoad() {
        logger.info("创建内存负载以演示效果...");
        
        // 创建一些对象来增加内存使用
        byte[][] memoryLoad = new byte[100][];
        for (int i = 0; i < memoryLoad.length; i++) {
            memoryLoad[i] = new byte[1024 * 1024]; // 1MB each
        }
        
        logger.info("内存负载创建完成");
    }
    
    /**
     * 定期内存整理任务示例
     */
    public static class PeriodicMemoryTrimTask implements Runnable {
        
        private final Logger logger = LoggerFactory.getLogger(PeriodicMemoryTrimTask.class);
        private final MemoryMonitor monitor = new MemoryMonitor();
        private final long intervalMs;
        
        public PeriodicMemoryTrimTask(long intervalMs) {
            this.intervalMs = intervalMs;
        }
        
        @Override
        public void run() {
            logger.info("启动定期内存整理任务，间隔: {}ms", intervalMs);
            
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(intervalMs);
                    
                    if (monitor.shouldTrimMemory()) {
                        logger.info("执行定期内存整理");
                        boolean result = WorkingSetManagerFactory.trimMemory();
                        logger.info("定期内存整理结果: {}", result ? "成功" : "失败");
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            logger.info("定期内存整理任务结束");
        }
    }
}
