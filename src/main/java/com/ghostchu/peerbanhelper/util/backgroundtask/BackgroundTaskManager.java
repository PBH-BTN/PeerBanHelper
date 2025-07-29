package com.ghostchu.peerbanhelper.util.backgroundtask;

import com.ghostchu.peerbanhelper.alert.AlertLevel;
import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
public class BackgroundTaskManager {

    private final Map<String, BackgroundTask> backgroundTasks = Collections.synchronizedMap(new LinkedHashMap<>());
    private final ExecutorService defaultExecutor = Executors.newWorkStealingPool(16);
    private final ScheduledExecutorService sched = Executors.newScheduledThreadPool(1);
    private final AlertManager alertManager;

    public BackgroundTaskManager(AlertManager alertManager) {
        this.alertManager = alertManager;
        sched.scheduleWithFixedDelay(this::printTasks, 10, 10, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void printTasks() {
        var set = backgroundTasks.entrySet();
        log.info(tlUI(Lang.BACKGROUND_TASK_PRINT_TITLE, set.size()));
        for (Map.Entry<String, BackgroundTask> entry : set) {
            var task = entry.getValue();
            var percent = task.isIndeterminate() ? "--%" : String.format("%.2f", entry.getValue().getProgress()) + "%";
            log.info(tlUI(Lang.BACKGROUND_TASK_PRINT_ITEM_RUNNING, tlUI(task.getTitle()), percent));
        }
    }

    @NotNull
    public Map.Entry<String, CompletableFuture<Void>> registerAndStart(@NotNull BackgroundTask backgroundTask) {
        var givenId = UUID.randomUUID().toString();
        backgroundTasks.put(backgroundTask.getId(), backgroundTask); // 先 add，避免race
        backgroundTask.setStartAt(System.currentTimeMillis());
        return Map.entry(givenId, CompletableFuture.runAsync(backgroundTask, defaultExecutor)
                .whenComplete((v, throwable) -> {
                    backgroundTask.setEndedAt(System.currentTimeMillis());
                    backgroundTasks.remove(givenId);
                    if (throwable != null) {
                        backgroundTask.setTaskStatus(BackgroundTaskStatus.ERROR);
                        log.error(tlUI(Lang.BACKGROUND_TASK_PRINT_ITEM_ERROR,tlUI(backgroundTask.getTitle())), throwable);
                        alertManager.publishAlert(true, AlertLevel.ERROR,
                                "background-task-failure-" + UUID.randomUUID(),
                                new TranslationComponent(Lang.BACKGROUND_TASK_EXCEPTION_TITLE),
                                new TranslationComponent(Lang.BACKGROUND_TASK_EXCEPTION_DESCRIPTION, backgroundTask.getTitle(), backgroundTask.getMessage(), backgroundTask.getProgress(), throwable.getClass().getName() + ": " + throwable.getMessage())
                        );
                    } else {
                        backgroundTask.setTaskStatus(BackgroundTaskStatus.COMPLETED);
                    }
                }));
    }

    @NotNull
    public Map<String, BackgroundTask> getBackgroundTasks() {
        return backgroundTasks;
    }

    @Nullable
    public BackgroundTask getBackgroundTask(String id) {
        return backgroundTasks.get(id);
    }
}
