package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.module.AbstractWebSocketFeatureModule;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.asynctask.AsyncTask;
import com.ghostchu.peerbanhelper.util.asynctask.AsyncTaskLoggerListener;
import com.ghostchu.peerbanhelper.util.asynctask.AsyncTaskManager;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.peerbanhelper.web.wrapper.StdWsAction;
import com.ghostchu.peerbanhelper.web.wrapper.StdWsAlertDTO;
import com.ghostchu.peerbanhelper.web.wrapper.StdWsMessage;
import io.javalin.http.Context;
import io.javalin.websocket.WsCloseStatus;
import io.javalin.websocket.WsConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.slf4j.event.Level;
import org.springframework.stereotype.Component;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;
import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
public class PBHAsyncTaskController extends AbstractWebSocketFeatureModule {
    private final JavalinWebContainer javalinWebContainer;

    public PBHAsyncTaskController(JavalinWebContainer javalinWebContainer) {
        super();
        this.javalinWebContainer = javalinWebContainer;
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - BgTask Controller";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-bgtask";
    }

    @Override
    public void onEnable() {
        javalinWebContainer.javalin().unsafe.routes
                .get("/api/asyncTask/list", this::handleTaskLists, Role.USER_READ)
                .ws("/api/asyncTask/{taskId}/stream", this::handleStream, Role.USER_READ);
        Main.getEventBus().register(this);
    }

    private void handleStream(WsConfig wsConfig) {
        acceptWebSocket(wsConfig, (ctx) -> {
            var taskId = ctx.pathParam("taskId");
            var task = AsyncTaskManager.getTaskById(taskId);
            if (task == null) {
                ctx.send(new StdWsMessage(false, StdWsAction.ALERT, new StdWsAlertDTO(Level.ERROR, tlUI(Lang.BGTASK_WEBSOCKET_TASK_NOT_FOUND))));
                return;
            }
            task.getLogs().forEach(log -> ctx.send(new StdWsMessage(true, StdWsAction.BUSINESS, log)));
            task.addLoggerListener(new AsyncTaskLoggerListener() {
                @Override
                public void onLog(AsyncTask task, String log) {
                    ctx.send(new StdWsMessage(true, StdWsAction.BUSINESS, log));
                }

                @Override
                public void onTaskClose(AsyncTask task) {
                    ctx.closeSession(WsCloseStatus.NORMAL_CLOSURE);
                }
            });
            if (task.isClosed()) { // 避免任务关闭后才挂上监听器
                ctx.closeSession(WsCloseStatus.NORMAL_CLOSURE);
            }
        });
    }

    private void handleTaskLists(@NotNull Context context) {
        context.json(new StdResp(true, null, AsyncTaskManager.getTasks()
                .stream()
                .map(bgTask -> new BgTaskDTO(
                        bgTask.getTaskId(),
                        tl(locale(context), bgTask.getTitle()),
                        tl(locale(context), bgTask.getDescription()),
                        bgTask.getProgress()
                )).toList()));
    }

    @Override
    public void onDisable() {

    }

    @AllArgsConstructor
    @Data
    @NotNull
    public static class BgTaskDTO {
        private String taskId;
        private String taskTitle;
        private String taskDescription;
        private double taskProgress;
    }
}
