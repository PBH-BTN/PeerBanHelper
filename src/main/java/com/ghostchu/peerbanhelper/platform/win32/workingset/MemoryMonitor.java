package com.ghostchu.peerbanhelper.platform.win32.workingset;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

/**
 * 内存监控工具类
 * 提供内存使用情况的查询和格式化功能
 */
public class MemoryMonitor {
    
    private final MemoryMXBean memoryBean;
    
    /**
     * 构造函数
     */
    public MemoryMonitor() {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
    }
    
    /**
     * 获取堆内存使用情况
     * 
     * @return 堆内存使用情况
     */
    public MemoryUsage getHeapMemoryUsage() {
        return memoryBean.getHeapMemoryUsage();
    }
    
    /**
     * 获取非堆内存使用情况
     * 
     * @return 非堆内存使用情况
     */
    public MemoryUsage getNonHeapMemoryUsage() {
        return memoryBean.getNonHeapMemoryUsage();
    }
    
    /**
     * 获取内存使用情况摘要
     * 
     * @return 内存使用情况摘要字符串
     */
    public String getMemorySummary() {
        MemoryUsage heap = getHeapMemoryUsage();
        MemoryUsage nonHeap = getNonHeapMemoryUsage();
        
        StringBuilder sb = new StringBuilder();
        sb.append("内存使用情况:\n");
        sb.append("  堆内存: ").append(formatMemoryUsage(heap)).append("\n");
        sb.append("  非堆内存: ").append(formatMemoryUsage(nonHeap));
        
        return sb.toString();
    }
    
    /**
     * 格式化内存使用情况
     * 
     * @param usage 内存使用情况
     * @return 格式化后的字符串
     */
    private String formatMemoryUsage(MemoryUsage usage) {
        return String.format("已用: %s, 已提交: %s, 最大: %s",
                formatBytes(usage.getUsed()),
                formatBytes(usage.getCommitted()),
                usage.getMax() == -1 ? "无限制" : formatBytes(usage.getMax())
        );
    }
    
    /**
     * 格式化字节数为人类可读的格式
     * 
     * @param bytes 字节数
     * @return 格式化后的字符串
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        
        String[] units = {"KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes;
        
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        
        return String.format("%.2f %s", size, units[unitIndex]);
    }
    
    /**
     * 获取进程工作集大小（近似值）
     * 通过JVM内存使用情况估算
     * 
     * @return 工作集大小（字节）
     */
    public long getEstimatedWorkingSetSize() {
        MemoryUsage heap = getHeapMemoryUsage();
        MemoryUsage nonHeap = getNonHeapMemoryUsage();
        
        // 估算值：已使用的堆内存 + 非堆内存 + 一些额外开销
        return heap.getUsed() + nonHeap.getUsed() + (32 * 1024 * 1024); // 额外32MB开销估算
    }
    
    /**
     * 检查内存使用是否过高
     * 
     * @param thresholdRatio 阈值比例（0.0-1.0）
     * @return 如果内存使用超过阈值返回true
     */
    public boolean isMemoryUsageHigh(double thresholdRatio) {
        MemoryUsage heap = getHeapMemoryUsage();
        
        if (heap.getMax() == -1) {
            // 无最大限制，使用已提交内存作为参考
            return (double) heap.getUsed() / heap.getCommitted() > thresholdRatio;
        } else {
            return (double) heap.getUsed() / heap.getMax() > thresholdRatio;
        }
    }
    
    /**
     * 建议是否执行内存整理
     * 
     * @return 如果建议执行内存整理返回true
     */
    public boolean shouldTrimMemory() {
        // 如果堆内存使用率超过70%，建议执行内存整理
        return isMemoryUsageHigh(0.7);
    }
}
