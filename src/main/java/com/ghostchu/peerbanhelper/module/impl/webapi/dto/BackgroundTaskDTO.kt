package com.ghostchu.peerbanhelper.module.impl.webapi.dto

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
    val title: String,
    val statusText: String?,
    val status: BackgroundTaskStatus,
    val barType: BackgroundTaskProgressBarType,
    val progress: Double,
    val current: Long,
    val max: Long
)