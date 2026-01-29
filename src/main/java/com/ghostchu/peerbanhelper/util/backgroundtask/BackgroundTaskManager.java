package com.ghostchu.peerbanhelper.util.backgroundtask;

import com.ghostchu.peerbanhelper.util.CommonUtil;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class BackgroundTaskManager {
    @Getter
    private final List<BackgroundTask> taskList = Collections.synchronizedList(new ArrayList<>());

    public BackgroundTaskManager() {
        CommonUtil.getScheduler().scheduleWithFixedDelay(this::cleanTask, 0L, 10L, TimeUnit.SECONDS);
    }

    private void cleanTask() {
        var it = taskList.iterator();
        while (it.hasNext()) {
            //noinspection resource
            var task = it.next();
            if (!task.getStatus().isActive()) {
                if ((task.getStatus() == BackgroundTaskStatus.COMPLETED && OffsetDateTime.now().isAfter(task.getFinishedAt().plusMinutes(1)))
                        || OffsetDateTime.now().isAfter(task.getFinishedAt().plusMinutes(30))) {
                    it.remove();
                }
            }
        }
    }

    @NotNull
    public BackgroundTask create() {
        BackgroundTask task = new BackgroundTask(this);
        taskList.add(task);
        return task;
    }
}
