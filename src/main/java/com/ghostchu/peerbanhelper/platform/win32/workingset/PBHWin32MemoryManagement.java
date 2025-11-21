package com.ghostchu.peerbanhelper.platform.win32.workingset;

import com.ghostchu.peerbanhelper.platform.win32.workingset.jna.WorkingSetManagerFactory;
import com.ghostchu.peerbanhelper.util.lab.Experiments;
import com.ghostchu.peerbanhelper.util.lab.Laboratory;
import com.sun.management.GarbageCollectionNotificationInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Component
public class PBHWin32MemoryManagement {
    private final ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
    private final Pattern gcKeyword = Pattern.compile(".*(Full|Old|MarkSweep|Tenured|CMS|G1|ZGC|Shenandoah).*");
    private long lastRun = 0;

    public PBHWin32MemoryManagement(Laboratory laboratory) {
        try {
            if (!WorkingSetManagerFactory.isSupported() || !laboratory.isExperimentActivated(Experiments.WIN32_EMPTY_WORKING_SET.getExperiment())) {
                return;
            }
            log.debug("PBHWin32MemoryManagement initialized, hooking memory management.");
            hookMemoryManagement();
            service.scheduleWithFixedDelay(this::runJob, 1L, 10L, TimeUnit.MINUTES);
            log.debug("PBHWin32MemoryManagement scheduled to run every 10 minutes.");
        } catch (Throwable e) {
            log.warn("Failed to initialize PBHWin32MemoryManagement, memory management may not work properly.", e);
        }
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
                                                || gcKeyword.matcher(info.getGcName()).matches()
                                                || "end of cycle".equals(info.getGcAction()); // 适配更多GC类型
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
        if (System.currentTimeMillis() - lastRun > 60 * 1000) return;
        log.debug("Releasing memory by emptying working set.");
        WorkingSetManagerFactory.trimMemory();
        lastRun = System.currentTimeMillis();
    }


}
