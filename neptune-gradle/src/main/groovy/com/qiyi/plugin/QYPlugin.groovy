package com.qiyi.plugin

import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.builder.model.Version
import com.qiyi.plugin.dex.RClassTransform
import com.qiyi.plugin.hooker.TaskHookerManager
import com.qiyi.plugin.task.InstallPlugin
import com.qiyi.plugin.task.factory.TaskFactory
import com.qiyi.plugin.task.TaskUtil
import com.qiyi.plugin.task.factory.TaskFactory2
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.util.VersionNumber

class QYPlugin implements Plugin<Project> {
    Project project
    /** plugin extension */
    QYPluginExtension pluginExt

    @Override
    void apply(Project project) {

        if (!project.plugins.hasPlugin("com.android.application")) {
            throw new GradleException("com.android.application not found, QYPlugin can be only apply to android application module")
        }

        pluginExt = project.extensions.create("neptune", QYPluginExtension)
        this.project = project

        def android = project.extensions.getByType(AppExtension)
        def version = Version.ANDROID_GRADLE_PLUGIN_VERSION
        println "current AGP version ${version}"

        project.afterEvaluate {
            // init plugin extension
            android.applicationVariants.each { ApplicationVariantImpl variant ->

                pluginExt.with {
                    agpVersion = VersionNumber.parse(version)
                    packageName = variant.applicationId
                    versionName = variant.versionName
                    packagePath = packageName.replace('.'.charAt(0), File.separatorChar)
                }
                // 创建安装插件的Task
                createInstallPluginTask(variant)
            }

            checkConfig()
        }

        if (pluginExt.agpVersion >= VersionNumber.parse("3.0")) {
            // 注册修改Class的Transform
            android.registerTransform(new RClassTransform(project))
        }

        // 注册hook task相关任务
        TaskHookerManager taskHooker = new TaskHookerManager(project)
        taskHooker.registerTaskHooker()
    }

    /**
     * 创建安装插件apk到宿主特定目录的任务
     */
    private void createInstallPluginTask(ApplicationVariantImpl variant) {
        if (pluginExt.hostPackageName != null && pluginExt.hostPackageName.length() > 0) {
            TaskFactory taskFactory
            InstallPlugin installTask
            if (pluginExt.agpVersion >= VersionNumber.parse("3.3")) {
                taskFactory = new TaskFactory2(project.getTasks())
                installTask = taskFactory.register(new InstallPlugin.CreationAction(variant)).get()
            } else {
                taskFactory = new TaskFactory(project.getTasks())
                installTask = taskFactory.create(new InstallPlugin.ConfigAction(variant))
            }
            String assembleTaskName = TaskUtil.getAssembleTask(project, variant).name
            installTask.dependsOn(assembleTaskName)
        }
    }

    private void checkConfig() {
        if (!pluginExt.pluginMode) {
            // Not in plugin compile mode, close all the feature
            pluginExt.stripResource = false
            pluginExt.dexModify = false
        }

        if (pluginExt.packageId <= 0x01 || pluginExt.packageId > 0x7F) {
            throw new GradleException("invalid package Id 0x${Integer.toHexString(pluginExt.packageId)}")
        }

        if (pluginExt.packageId != 0x7F && pluginExt.pluginMode) {
            pluginExt.stripResource = true
        }

        String parameters = "plugin config parameters: pluginMode=${pluginExt.pluginMode}, packageId=0x${Integer.toHexString(pluginExt.packageId)}, stripResource=${pluginExt.stripResource}, dexModify=${pluginExt.dexModify}"
        println parameters
    }
}