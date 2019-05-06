package com.qiyi.plugin.task

import com.android.annotations.NonNull
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.InstallVariantTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.testing.ConnectedDevice
import com.android.builder.testing.ConnectedDeviceProvider
import com.android.builder.testing.api.DeviceConnector
import com.android.builder.testing.api.DeviceException
import com.android.builder.testing.api.DeviceProvider
import com.android.ddmlib.IDevice
import com.android.ide.common.process.ProcessException
import com.android.utils.ILogger
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.jetbrains.annotations.NotNull

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method


class InstallPlugin extends InstallVariantTask {
    ApplicationVariantImpl variant

    Collection<BaseVariantOutput> variantOutputs

    public Collection<BaseVariantOutput> getVariantOutputs() {
        return variantOutputs
    }

    public void setVariant(ApplicationVariantImpl variant) {
        this.variant = variant
        this.variantOutputs = variant.getOutputs()
    }

    public void setVariantOutputs(Collection<BaseVariantOutput> variantOutputs) {
        this.variantOutputs = variantOutputs
    }

    @TaskAction
    @Override
    void install() throws DeviceException, ProcessException, InterruptedException {
        final ILogger iLogger = getILogger()
        DeviceProvider deviceProvider = new ConnectedDeviceProvider(adbExe, getTimeOutInMs(), iLogger)
        deviceProvider.init()

        List<File> apkFiles = new ArrayList<>()
        variantOutputs.each { output ->
            apkFiles.add(output.outputFile)
        }

        if (apkFiles.size() <= 0) {
            throw new GradleException("no apk found in directory " + getApkDirectory())
        } else if (apkFiles.size() > 1) {
            throw new GradleException("more than one apk found in directory " + getApkDirectory() + ", size = " + apkFiles.size())
        }

        installPlugin(deviceProvider, apkFiles.get(0))
    }

    void installPlugin(
            @NonNull DeviceProvider deviceProvider,
            @NonNull File apkFile)
            throws DeviceException, ProcessException {
        int successfulInstallCount = 0
        List<? extends DeviceConnector> devices = deviceProvider.getDevices()
        for (final DeviceConnector device : devices) {
            // start install apk
            IDevice iDevice = getIDevice(device)
            DeviceWrapper wrapper = new DeviceWrapper(iDevice, project, variant)
            wrapper.installPackage(apkFile, true)
            successfulInstallCount++
        }
    }


    private IDevice getIDevice(DeviceConnector connector) {
        if (connector instanceof ConnectedDevice) {
            ConnectedDevice cd = (ConnectedDevice)connector
            try {
                return cd.getIDevice()
            } catch (Throwable tr) {
                // 2.3.3 没有public方法
                try {
                    Field field = ConnectedDevice.class.getDeclaredField("iDevice")
                    field.setAccessible(true)
                    return (IDevice)field.get(cd)
                } catch (Throwable e) {
                    e.printStackTrace()
                }
            }
        }
        throw new GradleException("unknown device connector type " + connector.getClass().getName())
    }

    /**
     * AGP 3.3.0以上版本
     */
    public static class CreationAction extends VariantTaskCreationAction<InstallPlugin> {
        private final ApplicationVariantImpl variant

        InstallVariantTask.CreationAction action

        CreationAction(ApplicationVariantImpl variant) {
            super(variant.getVariantData().scope)
            this.variant = variant
            this.action = new InstallVariantTask.CreationAction(variant.getVariantData().scope)
        }

        @Override
        String getName() {
            return getVariantScope().getTaskName("install", "Plugin")
        }

        @Override
        Class<InstallPlugin> getType() {
            return InstallPlugin.class
        }

        @Override
        void configure(@NotNull InstallPlugin task) {
            super.configure(task)
            println "class name: " + variant.getOutputs().getClass().getName()

            task.setVariant(variant)
            task.setVariantOutputs(variant.getOutputs())
            action.configure(task)
        }
    }

    /**
     * AGP 3.3.0以下版本
     */
    public static class ConfigAction implements TaskConfigAction<InstallPlugin> {
        private final ApplicationVariantImpl variant

        private final VariantScope scope

        //private InstallVariantTask.ConfigAction action
        private Object action

        ConfigAction(ApplicationVariantImpl variant) {
            this.variant = variant
            this.scope = variant.getVariantData().getScope()
            //action = new InstallVariantTask.ConfigAction(scope)
            Class<?> installAction = Class.forName("com.android.build.gradle.internal.tasks.InstallVariantTask\$ConfigAction")
            Constructor<?> constructor = installAction.getDeclaredConstructor(VariantScope.class)
            constructor.setAccessible(true)
            action = constructor.newInstance(scope)
        }

        @Override
        String getName() {
            return scope.getTaskName("install", "Plugin")
        }

        @Override
        Class<InstallPlugin> getType() {
            return InstallPlugin.class
        }

        @Override
        void execute(InstallPlugin task) {
            println "class name: " + variant.getOutputs().getClass().getName()

            task.setVariant(variant)
            task.setVariantOutputs(variant.getOutputs())
            //action.execute(task)
            Method method = action.getClass().getDeclaredMethod("execute", InstallVariantTask.class)
            method.setAccessible(true)
            method.invoke(action, task)
        }
    }
}
