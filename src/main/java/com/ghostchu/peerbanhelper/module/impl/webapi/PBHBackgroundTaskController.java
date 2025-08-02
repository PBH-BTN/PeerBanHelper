package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.BackgroundTaskMetaDTO;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.backgroundtask.BackgroundTask;
import com.ghostchu.peerbanhelper.util.backgroundtask.BackgroundTaskManager;
import com.ghostchu.peerbanhelper.util.backgroundtask.BackgroundTaskStatus;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Component
public class PBHBackgroundTaskController extends AbstractFeatureModule {
    private final JavalinWebContainer javalinWebContainer;
    private final BackgroundTaskManager backgroundTaskManager;

    public PBHBackgroundTaskController(JavalinWebContainer javalinWebContainer, BackgroundTaskManager backgroundTaskManager) {
        super();
        this.javalinWebContainer = javalinWebContainer;
        this.backgroundTaskManager = backgroundTaskManager;
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - Background Task Controller";
    }

    @Override
    public @NotNull String getConfigName() {
        return "background-task-controller";
    }

    @Override
    public void onEnable() {
        javalinWebContainer.javalin()
                .get("/api/tasks/list", this::listBackgroundTasks, Role.USER_READ)
                .get("/api/tasks/{taskId}/meta", this::handleTaskMeta, Role.USER_READ)
                .get("/api/tasks/{taskId}/logs", this::handleTaskLogs, Role.USER_WRITE)
                .delete("/api/tasks/{taskId}", this::handleTaskCancel, Role.USER_WRITE);
    }

    private void handleTaskLogs(@NotNull Context context) {
        var taskId = context.pathParam("taskId");
        BackgroundTask task = backgroundTaskManager.getBackgroundTask(taskId);
        if (task == null) {
            context.json(new StdResp(false, tl(locale(context), Lang.BACKGROUND_TASK_NOT_FOUND), null));
            return;
        }
        context.json(new StdResp(true, null, task.getLogs()));
    }

    private void handleTaskCancel(@NotNull Context context) {
        var taskId = context.pathParam("taskId");
        BackgroundTask task = backgroundTaskManager.getBackgroundTask(taskId);
        if (task == null) {
            context.json(new StdResp(false, tl(locale(context), Lang.BACKGROUND_TASK_NOT_FOUND), null));
            return;
        }
        if(!task.isCancellable()){
            context.json(new StdResp(false, tl(locale(context), Lang.BACKGROUND_TASK_CANNOT_CANCEL), null));
            return;
        }
        task.cancel();
        context.json(new StdResp(true, tl(locale(context), Lang.BACKGROUND_TASK_CANCEL_REQUESTED), null));
    }

    private void handleTaskMeta(@NotNull Context context) {
        var taskId = context.pathParam("taskId");
        BackgroundTask task = backgroundTaskManager.getBackgroundTask(taskId);
        if (task == null) {
            context.json(new StdResp(false, tl(locale(context), Lang.BACKGROUND_TASK_NOT_FOUND), null));
            return;
        }
        context.json(new StdResp(true, null, toDto(context, taskId, task)));
    }

    private void listBackgroundTasks(@NotNull Context context) {
        List<BackgroundTaskMetaDTO> taskMetaList = new ArrayList<>();
        for (Map.Entry<String, BackgroundTask> entry : backgroundTaskManager.getBackgroundTasks().entrySet()) {
            String id = entry.getKey();
            BackgroundTask task = entry.getValue();
            taskMetaList.add(toDto(context, id, task));
        }
        // 将错误的任务放在最前面，取消、完成的任务放在最后面，其它任务在中间按照开始时间倒序排序
        taskMetaList.sort((o1, o2) -> {
            if (o1.status() == o2.status()) {
                return Long.compare(o2.startedAt(), o1.startedAt());
            }
            // 错误的任务排在最前面
            if (o1.status() == BackgroundTaskStatus.ERROR) return -1;
            if (o2.status() == BackgroundTaskStatus.ERROR) return 1;
            // 取消和完成的任务排在最后面
            if (o1.status() == BackgroundTaskStatus.CANCELLED || o1.status() == BackgroundTaskStatus.COMPLETED) return 1;
            if (o2.status() == BackgroundTaskStatus.CANCELLED || o2.status() == BackgroundTaskStatus.COMPLETED) return -1;
            return 0; // 其它任务按照开始时间倒序排序
        });
        context.json(new StdResp(true, null, taskMetaList));
    }

    private BackgroundTaskMetaDTO toDto(Context context, String id, BackgroundTask task) {
        return new BackgroundTaskMetaDTO(
                id,
                task.getTaskStatus(),
                task.isIndeterminate(),
                task.getProgress(),
                tl(locale(context), task.getTitle()),
                tl(locale(context), task.getMessage()),
                task.isCancellable(),
                task.getStartAt(),
                task.getEndedAt()
        );
    }

    @Override
    public void onDisable() {

    }
}
