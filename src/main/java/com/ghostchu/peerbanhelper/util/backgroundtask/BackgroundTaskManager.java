package com.ghostchu.peerbanhelper.util.backgroundtask;

import com.ghostchu.peerbanhelper.alert.AlertLevel;
import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import org.jetbrains.annotations.NotNull;
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
    private final ExecutorService defaultExecutor = Executors.newThreadPerTaskExecutor(r -> {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        if (r instanceof BackgroundTaskRunnable backgroundTaskRunnable) {
            thread.setName(backgroundTaskRunnable.getName());
            thread.setPriority(Thread.MIN_PRIORITY);
        }
        return thread;
    });
    private final AlertManager alertManager;

    public BackgroundTaskManager(AlertManager alertManager) {
        this.alertManager = alertManager;
    }

    @NotNull
    public CompletableFuture<Void> registerAndStart(@NotNull BackgroundTask backgroundTask) {
        var givenId = UUID.randomUUID().toString();
        backgroundTasks.put(givenId, backgroundTask); // 先 add，避免race
        return CompletableFuture.runAsync(backgroundTask, defaultExecutor)
                .whenComplete((v, throwable) -> {
                    backgroundTasks.remove(givenId);
                    if (throwable != null) {
                        alertManager.publishAlert(true, AlertLevel.ERROR,
                                "background-task-failure-" + UUID.randomUUID(),
                                new TranslationComponent(Lang.BACKGROUND_TASK_EXCEPTION_TITLE),
                                new TranslationComponent(Lang.BACKGROUND_TASK_EXCEPTION_DESCRIPTION, backgroundTask.getTitle(), backgroundTask.getMessage(), backgroundTask.getProgress(), throwable.getClass().getName() + ": " + throwable.getMessage())
                        );
                    }
                });
    }

    @NotNull
    public Map<String, BackgroundTask> getBackgroundTasks() {
        return backgroundTasks;
    }
}
