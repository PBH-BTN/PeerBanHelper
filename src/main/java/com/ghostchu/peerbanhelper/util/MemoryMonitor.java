package com.ghostchu.peerbanhelper.util;

import com.ghostchu.peerbanhelper.alert.AlertLevel;
import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import org.springframework.stereotype.Component;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.List;

@Component
public final class MemoryMonitor {
    private static final long MB = 1024 * 1024; // 1 MB in bytes
    private static long maxHeapMB;

    public MemoryMonitor(AlertManager alertManager) {
        try {
            List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
            MemoryPoolMXBean poolToMonitor = null;
            long ptmSize = 0;
            long overallMax = 0;

            for (MemoryPoolMXBean pool : pools) {
                final long poolMax = pool.getUsage().getMax();
                if (poolMax > 0 && pool.getType() == MemoryType.HEAP) {
                    overallMax += poolMax;
                }
                if (pool.getType() == MemoryType.HEAP && pool.isCollectionUsageThresholdSupported()) {
                    final long max = pool.getUsage().getMax();
                    if (max > ptmSize) {
                        poolToMonitor = pool;
                        ptmSize = max;
                    }
                }
            }

            maxHeapMB = (overallMax + MB - 1) / MB; // Calculate max heap size in MB

            if (poolToMonitor != null) {
                long threshold = poolToMonitor.getUsage().getMax() * 3 / 4;
                threshold = Math.min(threshold, 5 * MB);

                MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
                NotificationEmitter emitter = (NotificationEmitter) memoryMXBean;
                emitter.addNotificationListener(
                        new NotificationListener() {
                            private long lastMBLog = Long.MAX_VALUE;

                            @Override
                            public void handleNotification(Notification notification, Object handback) {
                                final MemoryPoolMXBean pool = (MemoryPoolMXBean) handback;
                                final long used = pool.getCollectionUsage().getUsed();
                                final long max = pool.getUsage().getMax();
                                final long available = Math.max(0, max - used);
                                final long mbAvailable = (available + MB - 1) / MB;

                                if (mbAvailable <= 4) {
                                    synchronized (this) {
                                        if (mbAvailable >= lastMBLog) {
                                            return;
                                        }
                                        lastMBLog = mbAvailable;
                                    }
                                    alertManager.publishAlert(true,
                                            AlertLevel.FATAL,
                                            "outofmemory",
                                            new TranslationComponent(Lang.PROGRAM_OUT_OF_MEMORY_TITLE),
                                            new TranslationComponent(Lang.PROGRAM_OUT_OF_MEMORY_DESCRIPTION, mbAvailable == 0 ? "< 0" : mbAvailable, maxHeapMB));
                                }
                            }
                        },
                        null, poolToMonitor
                );

                poolToMonitor.setCollectionUsageThreshold(threshold);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        if (maxHeapMB == 0) {
            maxHeapMB = (Runtime.getRuntime().maxMemory() + MB - 1) / MB;
        }
    }

    public long getMaxHeapMB() {
        return maxHeapMB;
    }

}
