package com.ghostchu.peerbanhelper.util.asynctask;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AsyncTaskManager {
    private final static List<AsyncTask> tasks = Collections.synchronizedList(new ArrayList<>());

    public static void registerTask(AsyncTask task) {
        tasks.add(task);
    }

    public static void unregisterTask(AsyncTask task) {
        tasks.remove(task);
    }

    public static List<AsyncTask> getTasks() {
        return tasks;
    }

    public static @Nullable AsyncTask getTaskById(String taskId) {
        return tasks.stream()
                .filter(task -> task.getTaskId().equals(taskId))
                .findFirst()
                .orElse(null);
    }
}
