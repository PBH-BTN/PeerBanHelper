package com.ghostchu.peerbanhelper.util.traversal.forwarder;

import com.ghostchu.peerbanhelper.util.MsgUtil;
import lombok.Data;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 连接信息，包含流量统计和速度信息
 */
@Data
public class ConnectionInfo {
    private final InetSocketAddress clientAddress;
    private final InetSocketAddress localAddress;
    private final InetSocketAddress remoteAddress;
    private final long connectionTime;
    
    // 流量统计 (字节)
    private final AtomicLong uploadBytes = new AtomicLong(0);
    private final AtomicLong downloadBytes = new AtomicLong(0);
    
    // 速度统计 (每秒字节数)
    private volatile long uploadSpeed = 0;
    private volatile long downloadSpeed = 0;
    
    // 上次速度计算时间
    private volatile long lastSpeedCalculationTime;
    private volatile long lastUploadBytes = 0;
    private volatile long lastDownloadBytes = 0;
    
    public ConnectionInfo(InetSocketAddress clientAddress,
                         InetSocketAddress localAddress, InetSocketAddress remoteAddress) {
        this.clientAddress = clientAddress;
        this.localAddress = localAddress;
        this.remoteAddress = remoteAddress;
        this.connectionTime = System.currentTimeMillis();
        this.lastSpeedCalculationTime = connectionTime;
    }
    
    /**
     * 添加上传字节数
     */
    public void addUploadBytes(long bytes) {
        uploadBytes.addAndGet(bytes);
    }
    
    /**
     * 添加下载字节数
     */
    public void addDownloadBytes(long bytes) {
        downloadBytes.addAndGet(bytes);
    }
    
    /**
     * 更新速度统计
     */
    public void updateSpeed() {
        long currentTime = System.currentTimeMillis();
        long timeDiff = currentTime - lastSpeedCalculationTime;
        
        if (timeDiff >= 1000) { // 至少1秒才更新一次速度
            long currentUpload = uploadBytes.get();
            long currentDownload = downloadBytes.get();
            
            long uploadDiff = currentUpload - lastUploadBytes;
            long downloadDiff = currentDownload - lastDownloadBytes;
            
            // 计算每秒速度
            uploadSpeed = (uploadDiff * 1000) / timeDiff;
            downloadSpeed = (downloadDiff * 1000) / timeDiff;
            
            lastSpeedCalculationTime = currentTime;
            lastUploadBytes = currentUpload;
            lastDownloadBytes = currentDownload;
        }
    }
    
    /**
     * 格式化流量大小
     */
    public static String formatBytes(long bytes) {
        return MsgUtil.humanReadableByteCountBin(bytes);
    }
    
    /**
     * 获取连接状态的字符串表示
     */
    public String getConnectionStatus() {
        updateSpeed();
        return String.format("%s:%d -> %s:%d -> %s:%d   [UP:%s, DOWN:%s] (%s/s, %s/s)",
                clientAddress.getAddress().getHostAddress(), clientAddress.getPort(),
                localAddress.getAddress().getHostAddress(), localAddress.getPort(),
                remoteAddress.getAddress().getHostAddress(), remoteAddress.getPort(),
                formatBytes(uploadBytes.get()), formatBytes(downloadBytes.get()),
                formatBytes(uploadSpeed), formatBytes(downloadSpeed));
    }
}
