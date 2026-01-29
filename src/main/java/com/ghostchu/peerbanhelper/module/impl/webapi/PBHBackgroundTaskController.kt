package com.ghostchu.peerbanhelper.module.impl.webapi

import com.ghostchu.peerbanhelper.module.AbstractWebSocketFeatureModule
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.BackgroundTaskDTO
import com.ghostchu.peerbanhelper.util.backgroundtask.BackgroundTask
import com.ghostchu.peerbanhelper.util.backgroundtask.BackgroundTaskManager
import com.ghostchu.peerbanhelper.util.backgroundtask.TaskStatusListener
import com.ghostchu.peerbanhelper.web.JavalinWebContainer
import com.ghostchu.peerbanhelper.web.Role
import com.ghostchu.peerbanhelper.web.wrapper.StdResp
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
        webContainer.javalin()
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
        val tasks = backgroundTaskManager.getTaskList()
        for (task in tasks) {
            try {
                ctx.send(StdResp(true, null, BackgroundTaskDTO.from(task)))
            } catch (e: Exception) {
                logger.error("Failed to send task status to WebSocket client", e)
            }
        }
    }

    private fun broadcastTaskUpdate(task: BackgroundTask) {
        val dto = BackgroundTaskDTO.from(task)
        val response = StdResp(true, null, dto)

        val sessionsSnapshot = ArrayList(wsSessions)
        for (session in sessionsSnapshot) {
            try {
                session.send(response)
            } catch (e: Exception) {
                logger.debug("Failed to send task update to WebSocket client", e)
            }
        }
    }
}
