package com.ghostchu.peerbanhelper.util.backgroundtask;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class BackgroundTaskManager {
    private final List<BackgroundTask> taskList = Collections.synchronizedList(new ArrayList<>());

    public BackgroundTaskManager() {
        CommonUtil.getScheduler().scheduleWithFixedDelay(this::cleanTask, 0L, 5L, TimeUnit.SECONDS);
    }

    private void cleanTask() {
        var it = taskList.iterator();
        while (it.hasNext()) {
            //noinspection resource
            var task = it.next();
            if (!task.getStatus().isActive()) {
                if ((task.getStatus() == BackgroundTaskStatus.COMPLETED && OffsetDateTime.now().isAfter(task.getFinishedAt().plusMinutes(1)))
                        || OffsetDateTime.now().isAfter(task.getFinishedAt().plusMinutes(30))
                        || task.isDisposalImmediatelyAfterComplete()) {
                    it.remove();
                }
            }
        }
    }

    @NotNull
    public BackgroundTask create(@NotNull TranslationComponent title) {
        BackgroundTask task = new BackgroundTask(this, title);
        taskList.add(task);
        return task;
    }

    @NotNull
    public List<BackgroundTask> getTaskList() {
        var orderableList = new ArrayList<>(taskList);
        // 优先运行中和暂停中、然后是失败和完成的、最后是排队中的，时间倒序
        orderableList.sort((a, b) -> {
            if (a.getStatus().isActive() && !b.getStatus().isActive()) {
                return -1;
            } else if (!a.getStatus().isActive() && b.getStatus().isActive()) {
                return 1;
            } else {
                if ((a.getStatus() == BackgroundTaskStatus.COMPLETED || a.getStatus() == BackgroundTaskStatus.FAILED)
                        && !(b.getStatus() == BackgroundTaskStatus.COMPLETED || b.getStatus() == BackgroundTaskStatus.FAILED)) {
                    return -1;
                } else if (!(a.getStatus() == BackgroundTaskStatus.COMPLETED || a.getStatus() == BackgroundTaskStatus.FAILED)
                        && (b.getStatus() == BackgroundTaskStatus.COMPLETED || b.getStatus() == BackgroundTaskStatus.FAILED)) {
                    return 1;
                } else {
                    return b.getStartAt().compareTo(a.getStartAt());
                }
            }
        });
        return orderableList;
    }
}
