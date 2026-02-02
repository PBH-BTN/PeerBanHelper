package com.ghostchu.peerbanhelper.module.impl.webapi

import com.ghostchu.peerbanhelper.module.AbstractWebSocketFeatureModule
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.BackgroundTaskDTO
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.BackgroundTaskEvent
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.BackgroundTaskEventType
import com.ghostchu.peerbanhelper.text.TextManager.tl
import com.ghostchu.peerbanhelper.util.backgroundtask.BackgroundTask
import com.ghostchu.peerbanhelper.util.backgroundtask.BackgroundTaskManager
import com.ghostchu.peerbanhelper.util.backgroundtask.TaskStatusListener
import com.ghostchu.peerbanhelper.web.JavalinWebContainer
import com.ghostchu.peerbanhelper.web.Role
import io.javalin.websocket.WsConfig
import io.javalin.websocket.WsContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class PBHBackgroundTaskController : AbstractWebSocketFeatureModule() {
    private val logger = LoggerFactory.getLogger(PBHBackgroundTaskController::class.java)

    @Autowired
    private lateinit var webContainer: JavalinWebContainer

    @Autowired
    private lateinit var backgroundTaskManager: BackgroundTaskManager

    private val statusListener: TaskStatusListener = { task ->
        broadcastTaskUpdate(task)
    }

    override fun isConfigurable(): Boolean = false

    override fun getName(): String = "WebAPI - Background Tasks"

    override fun getConfigName(): String = "webapi-background-tasks"

    override fun onEnable() {
        webContainer.javalin().unsafe.routes
            .ws("/api/tasks/stream", this::handleTaskStream, Role.USER_READ)

        backgroundTaskManager.addStatusListener(statusListener)
    }

    override fun onDisable() {
        backgroundTaskManager.removeStatusListener(statusListener)
    }

    private fun handleTaskStream(wsConfig: WsConfig) {
        acceptWebSocket(wsConfig) { ctx ->
            sendCurrentTasks(ctx)
        }
    }

    private fun sendCurrentTasks(ctx: WsContext) {
        val lang = locale(getContextFromWsContext(ctx))
        for (task in backgroundTaskManager.getTaskList()) {
            try {
                ctx.send(
                    BackgroundTaskEvent(
                        BackgroundTaskEventType.UPDATED, BackgroundTaskDTO(
                            id = task.id,
                            title = tl(lang, task.title),
                            statusText = tl(lang, task.statusText),
                            status = task.status,
                            barType = task.barType,
                            progress = task.progress,
                            current = task.current,
                            max = task.max
                        )
                    )
                )
            } catch (e: Exception) {
                logger.error("Failed to send task status to WebSocket client", e)
            }
        }
    }

    private fun broadcastTaskUpdate(task: BackgroundTask) {
        for (session in wsSessions) {
            try {
                val lang = locale(getContextFromWsContext(session))
                val response = BackgroundTaskEvent(
                    BackgroundTaskEventType.UPDATED, BackgroundTaskDTO(
                        id = task.id,
                        title = tl(lang, task.title),
                        statusText = if (task.statusText != null) tl(lang, task.statusText!!) else null,
                        status = task.status,
                        barType = task.barType,
                        progress = task.progress,
                        current = task.current,
                        max = task.max
                    )
                )
                session.send(response)
            } catch (e: Exception) {
                logger.debug("Failed to send task update to WebSocket client", e)
            }
        }
    }
}
