package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.BackgroundTaskMetaDTO;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.backgroundtask.BackgroundTask;
import com.ghostchu.peerbanhelper.util.backgroundtask.BackgroundTaskManager;
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
                .post("/api/tasks/{taskId}/cancel", this::handleTaskCancel, Role.USER_WRITE);
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
        context.json(taskMetaList);
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
