package com.qiyi.plugin.task

import com.android.ddmlib.AdbCommandRejectedException
import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.IDevice
import com.android.ddmlib.InstallException
import com.android.ddmlib.MultiLineReceiver
import com.android.ddmlib.NullOutputReceiver
import com.android.ddmlib.ShellCommandUnresponsiveException
import com.android.ddmlib.SyncException
import com.android.ddmlib.TimeoutException
import com.android.sdklib.AndroidVersion
import com.qiyi.plugin.QYPluginExtension
import org.gradle.api.Project

import java.util.concurrent.TimeUnit

class DeviceWrapper {

    private static final long INSTALL_TIMEOUT_MINUTES

    static {
        String installTimeout = System.getenv("ADB_INSTALL_TIMEOUT")
        long time = 4
        if (installTimeout != null) {
            try {
                time = Long.parseLong(installTimeout)
            } catch (NumberFormatException e) {
                // use default value
            }
        }
        INSTALL_TIMEOUT_MINUTES = time
    }

    private IDevice device
    private Project project

    DeviceWrapper(IDevice device, Project project) {
        this.device = device
        this.project = project
    }

    private String getHostPackage() {
        QYPluginExtension pluginExt = project.extensions.findByType(QYPluginExtension.class)
        return pluginExt.hostPackageName
    }

    private String getPluginPackage() {
        QYPluginExtension pluginExt = project.extensions.findByType(QYPluginExtension.class)
        return pluginExt.packageName
    }

    private String getPluginVersion() {
        QYPluginExtension pluginExt = project.extensions.findByType(QYPluginExtension.class)
        return pluginExt.versionName
    }

    public void installPackage(File apkFile, boolean reinstall)
            throws InstallException {

        println "ready to install plugin apk ${apkFile} to host APP ${hostPackage}"
        String apkPath = apkFile.getAbsolutePath()
        try {
            // 检测宿主App是否安装到设备上
            checkHostAppInstalled(hostPackage)
            // 推送插件APK到设备上
            String remoteFilePath = device.syncPackageToDevice(apkPath)
            // 安装插件到宿主APP目录
            installRemotePackage(remoteFilePath, reinstall)
            // 删除远程临时文件
            device.removeRemotePackage(remoteFilePath)
            // 杀进程，重启客户端
            restartHostApp()

        } catch (IOException e) {
            throw new InstallException(e)
        } catch (AdbCommandRejectedException e) {
            throw new InstallException(e)
        } catch (TimeoutException e) {
            throw new InstallException(e)
        } catch (SyncException e) {
            throw new InstallException(e)
        }
    }

    /**
     * 校验宿主APP是否已经安装到设备上
     * @param device
     * @throws InstallException
     */
    void checkHostAppInstalled(String pkgName) throws InstallException {
        try {
            ListPackageReceiver receiver = new ListPackageReceiver()
            String cmd = "pm list packages"
            device.executeShellCommand(cmd, receiver, INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)

            if (!receiver.isPackageInstalled(pkgName)) {
                throw new InstallException("host package " + pkgName + " not installed in device")
            }
        } catch (TimeoutException e) {
            throw new InstallException(e)
        } catch (AdbCommandRejectedException e) {
            throw new InstallException(e)
        } catch (ShellCommandUnresponsiveException e) {
            throw new InstallException(e)
        } catch (IOException e) {
            throw new InstallException(e)
        }
    }

    String getPluginApkName() throws InstallException {
        try {
            CollectingOutputReceiver receiver = new CollectingOutputReceiver()

            String cmd = "run-as ${hostPackage} ls app_pluginapp"
            println "ls plugin apk name cmd: " + cmd
            device.executeShellCommand(cmd, receiver, INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)

            String result = receiver.getOutput().trim()
            if (result.contains("Package \'${hostPackage}\' is not debuggable")) {
                throw new InstallException("Host App is not debuggable, please install debuggable apk")
            } else if (result.contains("Permission denied")) {
                throw new InstallException("No permission into Host App /data/data/${hostPackage} directory")
            }

            String[] splits = result.split("\\s+")
            for (String ss : splits) {
                if (ss.startsWith(pluginPackage) && ss.endsWith(".apk")) {
                    return ss
                }
            }
            return "${pluginPackage}.${pluginVersion}.apk"
        } catch (TimeoutException e) {
            throw new InstallException(e)
        } catch (AdbCommandRejectedException e) {
            throw new InstallException(e)
        } catch (ShellCommandUnresponsiveException e) {
            throw new InstallException(e)
        } catch (IOException e) {
            throw new InstallException(e)
        }
    }

    void installRemotePackage(String remoteFilePath, boolean reInstall) throws InstallException {
        try {
            println "remote apk file path: ${remoteFilePath}"
            String apkName = getPluginApkName()
            NullOutputReceiver receiver = new NullOutputReceiver()
            // 覆盖原有的apk
            String targetFile = "/data/data/${hostPackage}/app_pluginapp/${apkName}"
            String cmd = "run-as ${hostPackage} cp ${remoteFilePath} ${targetFile}"
            println "copy file into host app directory cmd: " + cmd
            device.executeShellCommand(cmd, receiver, INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            // 删除dex相关文件
            deleteDexFiles(apkName)
            // 执行dex2oat优化
            execDex2Oat(apkName)
        } catch (TimeoutException e) {
            throw new InstallException(e)
        } catch (AdbCommandRejectedException e) {
            throw new InstallException(e)
        } catch (ShellCommandUnresponsiveException e) {
            throw new InstallException(e)
        } catch (IOException e) {
            throw new InstallException(e)
        }
    }

    void deleteDexFiles(String apkName) {
        List<String> toDeleted = new ArrayList<>()
        // 删除原有的dex文件
        String rootDir = "/data/data${hostPackage}/app_pluginapp"
        String dexName = apkName.replace('.apk', '.dex')

        String targetDex = "${rootDir}/${pluginPackage}/${dexName}"
        toDeleted.add(targetDex)

        // 删除prof和odex，vdex文件
        String prof = "${rootDir}/${apkName}.prof"
        toDeleted.add(prof)

        String odexName = apkName.replace('.apk', '.odex')
        String vdexName = apkName.replace('.apk', '.vdex')
        String odex = "${rootDir}/oat/arm/${odexName}"
        String vdex = "${rootDir}/oat/arm/${vdexName}"
        toDeleted.add(odex)
        toDeleted.add(vdex)

        for (String path : toDeleted) {
            String cmd = "run-as ${hostPackage} rm ${path}"
            println "delete dex related file cmd: " + cmd
            device.executeShellCommand(cmd, new NullOutputReceiver(), INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)
        }
    }

    void execDex2Oat(String apkName) {
        AndroidVersion version = getAndroidVersion()
        if (version.apiLevel < 21) {
            println "Device below Android Lollipop not exec dexopt optimize, api version ${version}"
            return
        }

        String dexName = apkName.replace('.apk', '.dex')
        String odexName = apkName.replace(".apk", ".odex")
        String apkFile = "/data/data/${hostPackage}/app_pluginapp/${apkName}"
        String oatFile = "/data/data/${hostPackage}/app_pluginapp/${pluginPackage}/${dexName}"
        if (version.apiLevel >= 26) {
            oatFile = "/data/data/${hostPackage}/app_pluginapp/oat/arm/${odexName}"
        }

        println "exec dex2oat to optimize the apk ${apkFile}"

        StringBuilder sb = new StringBuilder("dex2oat")
        if (version.apiLevel >= 24) {
            sb.append(" --runtime-arg")
              .append(" -classpath")
              .append(" --runtime-arg")
              .append(" &")
        }
        sb.append(" --dex-file=")
          .append(apkFile)
          .append(" --oat-file=")
          .append(oatFile)
          .append(" --instruction-set=arm")
        if (version.apiLevel >= 26) {
            sb.append(" --compiler-filter=quicken")
        } else {
            sb.append(" --compiler-filter=interpret-only")
        }

        String cmd = sb.toString()
        println "dex2oat cmd: " + cmd
        device.executeShellCommand(cmd, new NullOutputReceiver(), INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)
    }

    void restartHostApp() {
        try {
            println "restartHostApp exec ......"
            NullOutputReceiver receiver = new NullOutputReceiver()
            String cmd = "am force-stop ${hostPackage}"
            device.executeShellCommand(cmd, receiver, INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)

            if ("com.qiyi.video" == hostPackage || "tv.pps.mobile" == hostPackage) {
                cmd = "am start -n ${hostPackage}/${hostPackage}.WelcomeActivity"
            } else {
                cmd = "am start -n -a android.intent.action.MAIN -c android.intent.category.LAUNCHER ${hostPackage}"
            }
            device.executeShellCommand(cmd, receiver, INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)
        } catch (TimeoutException e) {
            throw new InstallException(e)
        } catch (AdbCommandRejectedException e) {
            throw new InstallException(e)
        } catch (ShellCommandUnresponsiveException e) {
            throw new InstallException(e)
        } catch (IOException e) {
            throw new InstallException(e)
        }
    }


    AndroidVersion getAndroidVersion() {
        device.getProperties()
        AndroidVersion version = device.getVersion()
        if (version.apiLevel < 14) {
            // 通过ADB shell读取build.prop文件
            try {
                String cmd = "cat /system/build.prop"
                BuildPropReceiver receiver = new BuildPropReceiver()
                device.executeShellCommand(cmd, receiver, INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)

                String buildApi = receiver.getProperty(IDevice.PROP_BUILD_API_LEVEL)
                String codeName = receiver.getProperty(IDevice.PROP_BUILD_CODENAME)
                if (buildApi != null && buildApi.length() > 0) {
                    version = new AndroidVersion(Integer.parseInt(buildApi), codeName)
                }
            } catch (Exception e) {
                e.printStackTrace()
            }
        }

        return version
    }


    static final class BuildPropReceiver extends MultiLineReceiver {

        Map<String, String> properties

        BuildPropReceiver() {
            properties = new HashMap<>()
        }

        @Override
        void processNewLines(String[] lines) {
            for (String line : lines) {
                if (line == null || line.length() <= 0
                    || line.startsWith("#")
                    || line.startsWith("import")
                    || !line.contains("=")) {
                    continue
                }
                StringTokenizer tokenizer = new StringTokenizer(line.trim(), "=")
                String key = tokenizer.nextToken()
                String value = tokenizer.hasMoreTokens() ? tokenizer.nextToken() : ""
                if (key.length() > 0 && value.length() >= 0) {
                    properties.put(key, value)
                }
            }
        }

        String getProperty(String key) {
            return properties.get(key)
        }

        @Override
        boolean isCancelled() {
            return false
        }
    }

    static final class ListPackageReceiver extends MultiLineReceiver {

        private Set<String> packageLists

        ListPackageReceiver() {
            packageLists = new HashSet<>()
        }

        @Override
        void processNewLines(String[] lines) {
            for (String line : lines) {
                if (line == null || line.length() <= 0) {
                    continue
                }
                //package:com.qrd.omadownload
                line = line.trim()
                if (line.startsWith("package:")) {
                    StringTokenizer tokenizer = new StringTokenizer(line, ":")
                    String pkg = tokenizer.nextToken()
                    String name = tokenizer.nextToken()
                    if (pkg.length() > 0 && name.length() > 0) {
                        packageLists.add(name)
                    }
                }
            }
        }

        @Override
        boolean isCancelled() {
            return false
        }

        boolean isPackageInstalled(String pkgName) {
            return packageLists.contains(pkgName)
        }
    }
}
