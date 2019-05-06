package com.qiyi.plugin.task.factory

import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.tasks.factory.TaskFactoryUtils
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

/**
 * For Android Gradle Plugin 3.3.0+
 */
class TaskFactory2 extends TaskFactory {

    TaskFactory2(TaskContainer tasks) {
        super(tasks)
    }

    public <T extends Task> TaskProvider<T> register(TaskCreationAction<T> creationAction) {
        return TaskFactoryUtils.registerTask(tasks, creationAction, null, \
 null, null)
    }
}
