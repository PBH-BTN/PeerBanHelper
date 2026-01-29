package com.ghostchu.peerbanhelper.util.backgroundtask

import com.ghostchu.peerbanhelper.text.TranslationComponent
import java.time.OffsetDateTime
import java.util.*
import java.util.function.Consumer

abstract class BackgroundTask(var title: TranslationComponent) {
    val id: String = UUID.randomUUID().toString()
    var statusText: TranslationComponent? = null
    val startAt: OffsetDateTime = OffsetDateTime.now()
    var finishedAt: OffsetDateTime? = null
    var status: BackgroundTaskStatus = BackgroundTaskStatus.QUEUED
    var barType: BackgroundTaskProgressBarType = BackgroundTaskProgressBarType.INDETERMINATE
    var isDisposalImmediatelyAfterComplete: Boolean = false
    var max: Long = 0
    var current: Long = 0

    /**
     * Execute the task logic. Call the callback whenever status changes to notify listeners.
     * @param callback callback to invoke when task status/progress changes
     */
    abstract fun start(callback: Consumer<BackgroundTask>)

    /**
     * Mark the task as completed
     */
    fun complete() {
        finishedAt = OffsetDateTime.now()
        if (status == BackgroundTaskStatus.QUEUED || status == BackgroundTaskStatus.RUNNING) {
            status = BackgroundTaskStatus.COMPLETED
        }
    }

    /**
     * Mark the task as failed
     */
    fun fail() {
        finishedAt = OffsetDateTime.now()
        status = BackgroundTaskStatus.FAILED
    }

    val progress: Double
        get() = if (max == 0L) 0.0 else current.toDouble() / max.toDouble()
}
