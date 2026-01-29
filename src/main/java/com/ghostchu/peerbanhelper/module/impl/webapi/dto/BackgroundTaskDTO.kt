package com.ghostchu.peerbanhelper.module.impl.webapi.dto

import com.ghostchu.peerbanhelper.text.TranslationComponent
import com.ghostchu.peerbanhelper.util.backgroundtask.BackgroundTask
import com.ghostchu.peerbanhelper.util.backgroundtask.BackgroundTaskProgressBarType
import com.ghostchu.peerbanhelper.util.backgroundtask.BackgroundTaskStatus

enum class BackgroundTaskEventType {
    UPDATED,
}

data class BackgroundTaskEvent(
    val type: BackgroundTaskEventType,
    val task: BackgroundTaskDTO
)

data class BackgroundTaskDTO(
    val id: String,
    val title: TranslationComponent,
    val statusText: TranslationComponent?,
    val status: BackgroundTaskStatus,
    val barType: BackgroundTaskProgressBarType,
    val progress: Double,
    val current: Long,
    val max: Long
) {
    companion object {
        fun from(task: BackgroundTask): BackgroundTaskDTO {
            return BackgroundTaskDTO(
                id = task.id,
                title = task.title,
                statusText = task.statusText,
                status = task.status,
                barType = task.barType,
                progress = task.progress,
                current = task.current,
                max = task.max
            )
        }
    }
}
