package com.ghostchu.peerbanhelper.util.backgroundtask

import jakarta.annotation.PreDestroy
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import kotlin.time.Duration.Companion.seconds

typealias TaskStatusListener = (BackgroundTask) -> Unit

@Component
class BackgroundTaskManager {
    private val logger = LoggerFactory.getLogger(BackgroundTaskManager::class.java)
    private val taskList = CopyOnWriteArrayList<BackgroundTask>()
    private val statusListeners = CopyOnWriteArrayList<TaskStatusListener>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(8))

    init {
        scope.launch {
            while (isActive) {
                cleanTask()
                delay(5.seconds)
            }
        }
    }

    private fun cleanTask() {
        val now = OffsetDateTime.now()
        taskList.removeIf { task ->
            if (!task.status.isActive) {
                val finishedAt = task.finishedAt
                when {
                    task.status == BackgroundTaskStatus.COMPLETED && finishedAt != null && now.isAfter(
                        finishedAt.plusMinutes(
                            1
                        )
                    ) -> true

                    finishedAt != null && now.isAfter(finishedAt.plusMinutes(30)) -> true
                    task.isDisposalImmediatelyAfterComplete -> true
                    else -> false
                }
            } else {
                false
            }
        }
    }

    /**
     * Add a task and execute it in the coroutine pool (fire-and-forget).
     * The task's start(callback) method will be called internally.
     */
    fun addTask(task: BackgroundTask): BackgroundTask {
        addTaskAsync(task)
        return task
    }

    /**
     * Add a task and execute it in the coroutine pool.
     * Returns a CompletableFuture that completes when the task finishes.
     * Use this when you need to wait for the task result.
     */
    fun addTaskAsync(task: BackgroundTask): CompletableFuture<BackgroundTask> {
        val future = CompletableFuture<BackgroundTask>()
        task.status = BackgroundTaskStatus.QUEUED
        taskList.add(task)
        notifyListeners(task)

        scope.launch {
            try {
                task.status = BackgroundTaskStatus.RUNNING
                notifyListeners(task)

                val callback = Consumer<BackgroundTask> { updatedTask ->
                    notifyListeners(updatedTask)
                }

                task.start(callback)

                task.complete()
                notifyListeners(task)
                future.complete(task)
            } catch (e: CancellationException) {
                task.finishedAt = OffsetDateTime.now()
                task.status = BackgroundTaskStatus.CANCELLED
                notifyListeners(task)
                future.completeExceptionally(e)
                throw e
            } catch (e: Exception) {
                logger.error("Background task '${task.title}' failed", e)
                task.fail()
                notifyListeners(task)
                future.completeExceptionally(e)
            }
        }

        return future
    }

    fun getTaskList(): List<BackgroundTask> {
        val orderableList = ArrayList(taskList)
        orderableList.sortWith { a, b ->
            when {
                a.status.isActive && !b.status.isActive -> -1
                !a.status.isActive && b.status.isActive -> 1
                else -> {
                    val aIsCompleteOrFailed =
                        a.status == BackgroundTaskStatus.COMPLETED || a.status == BackgroundTaskStatus.FAILED
                    val bIsCompleteOrFailed =
                        b.status == BackgroundTaskStatus.COMPLETED || b.status == BackgroundTaskStatus.FAILED
                    when {
                        aIsCompleteOrFailed && !bIsCompleteOrFailed -> -1
                        !aIsCompleteOrFailed && bIsCompleteOrFailed -> 1
                        else -> b.startAt.compareTo(a.startAt)
                    }
                }
            }
        }
        return orderableList
    }

    fun addStatusListener(listener: TaskStatusListener) {
        statusListeners.add(listener)
    }

    fun removeStatusListener(listener: TaskStatusListener) {
        statusListeners.remove(listener)
    }

    private fun notifyListeners(task: BackgroundTask) {
        for (listener in statusListeners) {
            try {
                listener(task)
            } catch (e: Exception) {
                logger.error("Error notifying task status listener", e)
            }
        }
    }

    @PreDestroy
    fun shutdown() {
        scope.cancel("BackgroundTaskManager is shutting down")
    }
}
