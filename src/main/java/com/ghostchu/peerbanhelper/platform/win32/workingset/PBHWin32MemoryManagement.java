package com.ghostchu.peerbanhelper.platform.win32.workingset;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.platform.win32.workingset.jna.WorkingSetManagerFactory;
import com.sun.management.GarbageCollectionNotificationInfo;
import org.springframework.stereotype.Component;

import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class PBHWin32MemoryManagement {
    private final ScheduledExecutorService service = Executors.newScheduledThreadPool(1);

    public PBHWin32MemoryManagement() {
        if (!WorkingSetManagerFactory.isSupported() || !Main.getMainConfig().getBoolean("performance.windows-empty-working-set")) {
            return;
        }
        hookMemoryManagement();
        service.scheduleWithFixedDelay(this::runJob, 1L, 10L, TimeUnit.MINUTES);
    }

    private void hookMemoryManagement() {
        List<GarbageCollectorMXBean> gcBeans =
                ManagementFactory.getGarbageCollectorMXBeans();

        for (GarbageCollectorMXBean gcBean : gcBeans) {
            if (gcBean instanceof NotificationEmitter emitter) {
                emitter.addNotificationListener(
                        (notification, handback) -> {
                            if (notification.getType()
                                    .equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
                                var info = GarbageCollectionNotificationInfo
                                        .from((CompositeData) notification.getUserData());
                                boolean isFullGc =
                                        "end of major GC".equals(info.getGcAction())
                                                || info.getGcName().matches(".*(Full|Old|MarkSweep|Tenured|CMS).*");     // 各收集器命名差异兜底
                                if (isFullGc) {
                                    releaseMemory();
                                }
                            }
                        }, null, null);
            }
        }
    }

    private void runJob() {
        System.gc();
        service.schedule(this::releaseMemory, 10, TimeUnit.SECONDS);
    }

    private void releaseMemory() {
        WorkingSetManagerFactory.trimMemory();
    }


}
