package com.ghostchu.peerbanhelper.util.backgroundtask

import com.ghostchu.peerbanhelper.text.TranslationComponent
import java.util.function.Consumer

/**
 * A functional background task that executes a provided action.
 * The action receives the task itself as a parameter for updating progress.
 */
class FunctionalBackgroundTask(
    title: TranslationComponent,
    private val action: TaskAction
) : BackgroundTask(title) {

    fun interface TaskAction {
        @Throws(Exception::class)
        fun execute(task: BackgroundTask, callback: Consumer<BackgroundTask>)
    }

    override fun start(callback: Consumer<BackgroundTask>) {
        try {
            action.execute(this, callback)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}
