package com.qiyi.plugin.task

import com.android.annotations.NonNull
import com.android.build.gradle.internal.scope.TaskConfigAction
import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer

class TaskFactory {

    private final TaskContainer tasks

    TaskFactory(TaskContainer tasks) {
        this.tasks = tasks
    }


    public <T extends Task> T create(
            TaskConfigAction<T> configAction) {
        return create(tasks, configAction.getName(), configAction.getType(), configAction)
    }


    public synchronized < T extends Task> T create(
            @NonNull TaskContainer taskFactory,
            @NonNull String taskName,
            @NonNull Class<T> taskClass,
            Action<? super T> configAction) {

        return taskFactory.create(taskName, taskClass, configAction)
    }
}
