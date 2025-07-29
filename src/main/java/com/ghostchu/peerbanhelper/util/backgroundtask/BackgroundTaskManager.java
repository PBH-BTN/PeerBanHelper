package com.ghostchu.peerbanhelper.util.backgroundtask;

import com.ghostchu.peerbanhelper.alert.AlertLevel;
import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
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

@Component
public class BackgroundTaskManager {
    private final Map<String, BackgroundTask> backgroundTasks = Collections.synchronizedMap(new LinkedHashMap<>());
    private final ExecutorService defaultExecutor = Executors.newWorkStealingPool(16);
    private final AlertManager alertManager;

    public BackgroundTaskManager(AlertManager alertManager) {
        this.alertManager = alertManager;
    }

    @NotNull
    public Map.Entry<String, CompletableFuture<Void>> registerAndStart(@NotNull BackgroundTask backgroundTask) {
        var givenId = UUID.randomUUID().toString();
        backgroundTasks.put(givenId, backgroundTask); // 先 add，避免race
        backgroundTask.setStartAt(System.currentTimeMillis());
        return Map.entry(givenId, CompletableFuture.runAsync(backgroundTask, defaultExecutor)
                .whenComplete((v, throwable) -> {
                    backgroundTask.setEndedAt(System.currentTimeMillis());
                    backgroundTasks.remove(givenId);
                    if (throwable != null) {
                        backgroundTask.setTaskStatus(BackgroundTaskStatus.ERROR);
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
