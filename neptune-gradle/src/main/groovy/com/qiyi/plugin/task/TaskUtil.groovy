package com.qiyi.plugin.task

import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.build.gradle.tasks.ManifestProcessorTask
import com.android.build.gradle.tasks.MergeResources
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.qiyi.plugin.QYPluginExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.util.VersionNumber

class TaskUtil {

    public static MergeResources getMergeResourcesTask(Project project,
                                                       ApplicationVariantImpl appVariant) {
        def scope = appVariant.getVariantData().getScope()
        QYPluginExtension extension = project.extensions.findByType(QYPluginExtension.class)

        MergeResources mergeResTask
        if (extension.agpVersion >= VersionNumber.parse("3.2")) {
            mergeResTask = appVariant.mergeResources
        } else if (extension.agpVersion >= VersionNumber.parse("3.0")) {
            mergeResTask = appVariant.getVariantData().mergeResourcesTask
        } else {
            String mergeTaskName = scope.getMergeResourcesTask().name
            mergeResTask = project.tasks.getByName(mergeTaskName) as MergeResources
        }

        return mergeResTask
    }

    public static ProcessAndroidResources getProcessAndroidResourcesTask(Project project,
                                                                         ApplicationVariantImpl appVariant) {
        def scope = appVariant.getVariantData().getScope()
        QYPluginExtension extension = project.extensions.findByType(QYPluginExtension.class)

        String processResTaskName = extension.agpVersion >= VersionNumber.parse("3.0") ?
                scope.getProcessResourcesTask().name : scope.getGenerateRClassTask().name
        ProcessAndroidResources processResTask = project.tasks.getByName(processResTaskName) as ProcessAndroidResources
        return processResTask
    }

    public static ManifestProcessorTask getManifestProcessorTask(Project project,
                                                                 ApplicationVariantImpl appVariant) {
        def scope = appVariant.getVariantData().getScope()
        QYPluginExtension extension = project.extensions.findByType(QYPluginExtension.class)

        ManifestProcessorTask manifestTask
        if (extension.agpVersion >= VersionNumber.parse("3.2")) {
            manifestTask = appVariant.getVariantData().getTaskContainer().processManifestTask
        } else if (extension.agpVersion >= VersionNumber.parse("3.1")) {
            // AGP 3.1 返回的是ManifestProcessTask
            Object task = appVariant.getVariantData().getScope().manifestProcessorTask
            if (task instanceof ManifestProcessorTask) {
                manifestTask = (ManifestProcessorTask)task
            } else {
                throw new GradleException("ManifestProcessorTask unknown task type ${task.getClass().name}")
            }
        } else if (extension.agpVersion >= VersionNumber.parse("3.0")) {
            // AGP 3.0.1 返回的是AndroidTask类型, AndroidTask类在3.1中被删除了，这里使用反射创建
            Object task = appVariant.getVariantData().getScope().manifestProcessorTask
            try {
                Class<?> clazz = Class.forName("com.android.build.gradle.internal.scope.AndroidTask")
                if (clazz.isInstance(task)) {
                    String manifestTaskName = task.name
                    manifestTask = project.tasks.getByName(manifestTaskName) as ManifestProcessorTask
                } else  {
                    throw new GradleException("ManifestProcessorTask unknown task type ${task.getClass().name}")
                }
            } catch (ClassNotFoundException e) {
                throw new GradleException("com.android.build.gradle.internal.scope.AndroidTask not found")
            }
        } else {
            def variantData = scope.getVariantData()
            def outputScope
            try {
                outputScope = variantData.getMainOutput().getScope()
            } catch (Throwable tr) {
                // 2.2.x
                outputScope = variantData.getOutputs().get(0).getScope()
            }
            String manifestTaskName = outputScope.getManifestProcessorTask().name
            manifestTask = project.tasks.getByName(manifestTaskName) as ManifestProcessorTask
        }
        return manifestTask
    }

    public static Task getDexTask(Project project, ApplicationVariantImpl appVariant) {
        String varName = appVariant.name.capitalize()
        Task dexTask = project.tasks.findByName("transformClassesWithDexFor${varName}")
        if (dexTask == null) {
            // 3.0.0 debug mode task name DexBuilder
            dexTask = project.tasks.findByName("transformClassesWithDexBuilderFor${varName}")
        }
        if (dexTask == null) {
            // multidex might disable
            dexTask = project.tasks.findByName("transformClassesWithPreDexFor${varName}")
        }
        if (dexTask == null) {
            // finally we try a lower version
            dexTask = project.tasks.findByName("dex${varName}")
        }

        if (dexTask != null) {
            println "found dexTask with name: ${dexTask.name}"
        } else {
            println "[warning] we don't found dex task!!!"
        }
        return dexTask
    }
}
