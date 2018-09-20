/*
 *
 * Copyright 2018 iQIYI.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.qiyi.pluginlibrary.install;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Parcelable;
import android.text.TextUtils;

import org.qiyi.pluginlibrary.constant.IntentConstant;
import org.qiyi.pluginlibrary.pm.PluginLiteInfo;
import org.qiyi.pluginlibrary.pm.PluginPackageManager;
import org.qiyi.pluginlibrary.utils.FileUtils;
import org.qiyi.pluginlibrary.utils.PluginDebugLog;
import org.qiyi.pluginlibrary.utils.VersionUtils;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * 负责插件插件安装，卸载，数据删除
 */
public class PluginInstaller {
    public static final String TAG = "PluginInstaller";

    public static final String PLUGIN_ROOT_PATH = "pluginapp";
    public static final String APK_SUFFIX = ".apk";
    public static final String NATIVE_LIB_PATH = "lib";
    public static final String SO_SUFFIX = ".so";
    public static final String DEX_SUFFIX = ".dex";
    public static final String ANDROID_ASSETS = "/android_asset/";
    // scheme前缀
    public static final String SCHEME_ASSETS = "assets://";
    public static final String SCHEME_FILE = "file://";
    public static final String SCHEME_SO = "so://";
    public static final String SCHEME_DEX = "dex://";
    /* 标识插件安装广播是否注册，只注册一次 */
    private static boolean sInstallerReceiverRegistered = false;
    /* 存放正在安装的插件列表 */
    private static List<String> sInstallingList = Collections.synchronizedList(new LinkedList<String>());
    /* 内置插件列表 */
    private static List<String> sBuiltinAppList = Collections.synchronizedList(new ArrayList<String>());
    /* 插件安装监听广播 */
    private static BroadcastReceiver sApkInstallerReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String pkgName = intent.getStringExtra(IntentConstant.EXTRA_PKG_NAME);
            if (!TextUtils.isEmpty(pkgName)) {
                PluginDebugLog.installFormatLog(TAG, "install success and remove pkg:%s", pkgName);
                sInstallingList.remove(pkgName);
            }
        }
    };

    /**
     * 获取插件安装的根目录
     */
    public static File getPluginappRootPath(Context context) {
        File repoDir = context.getDir(PLUGIN_ROOT_PATH, 0);
        if (!repoDir.exists()) {
            repoDir.mkdirs();
        }
        PluginDebugLog.installFormatLog(TAG, "getPluginappRootPath:%s", repoDir);
        return repoDir;
    }

    /**
     * 插件classloader注入到parent classloader时，指定的optimizedDirectory路径,保存解析后的dex
     * API >= 26时，该参数已废弃 @see <a href="https://android.googlesource.com/platform/libcore/+/master/dalvik/src/main/java/dalvik/system/BaseDexClassLoader.java"</a>
     */
    public static File getPluginInjectRootPath(Context context) {
        File repoDir = getPluginappRootPath(context);
        File dexDir = new File(repoDir, "dex");
        if (!dexDir.exists()) {
            dexDir.mkdirs();
        }
        PluginDebugLog.installFormatLog(TAG, "getPluginInjectRootPath:%s", dexDir);
        return dexDir;
    }

    /**
     * 安装一个插件
     * 如果info.mPath为空，则安装内置在assets/pluginapp目录下的apk
     * 如果info.mPath不为空，则安装mPath路径下的apk，可能是asset目录，也可能是文件绝对路径
     *
     * @param context 宿主的Context
     * @param info    插件info信息
     */
    public static void install(Context context, PluginLiteInfo info) {

        if (TextUtils.isEmpty(info.mPath)) {
            String buildInPath = SCHEME_ASSETS + PLUGIN_ROOT_PATH + "/" + info.packageName + APK_SUFFIX;
            PluginDebugLog.installFormatLog(TAG, "install buildIn apk: %s, info: %s", buildInPath, info);
            startInstall(context, buildInPath, info);
            return;
        }

        String filePath = info.mPath;
        if (filePath.startsWith(SCHEME_FILE)) {
            Uri uri = Uri.parse(filePath);
            filePath = uri.getPath();
            if (TextUtils.isEmpty(filePath)) {
                throw new IllegalArgumentException("illegal install file path: " + info.mPath);
            }

            if (filePath.startsWith(ANDROID_ASSETS)) {
                String buildInPath = SCHEME_ASSETS + filePath.substring(ANDROID_ASSETS.length());
                PluginDebugLog.installFormatLog(TAG, "install buildIn apk: %s, info: %s", buildInPath, info);
                startInstall(context, buildInPath, info);
                return;
            }
        }

        PluginDebugLog.installFormatLog(TAG, "install external apk: %s, info: %s", filePath, info);
        if (filePath.endsWith(SO_SUFFIX)) {
            startInstall(context, SCHEME_SO + filePath, info);
        } else if (filePath.endsWith(DEX_SUFFIX)) {
            startInstall(context, SCHEME_DEX + filePath, info);
        } else {
            startInstall(context, SCHEME_FILE + filePath, info);
        }
    }

    /**
     * 安装内置在 assets/pluginapp 目录下的 apk
     *
     * @param context  宿主的Context
     * @param info    插件Info信息
     */
    @Deprecated
    public static void installBuiltinApps(final Context context,
                                          final PluginLiteInfo info) {
        String pluginApk = info.packageName + APK_SUFFIX;
        String buildInPath = SCHEME_ASSETS + PLUGIN_ROOT_PATH + "/" + pluginApk;
        startInstall(context, buildInPath, info);
    }

    /**
     * 安装一个 apk file 文件. 用于安装比如下载后的文件，或者从sdcard安装。安装过程采用独立进程异步安装。 安装完会有
     * {@link PluginPackageManager ＃ACTION_PACKAGE_INSTALLED} 广播。
     *
     * @param context 宿主的Context
     * @param pluginInfo 插件信息
     */
    @Deprecated
    public static void installApkFile(Context context, PluginLiteInfo pluginInfo) {

        String filePath = pluginInfo.mPath;
        if (filePath.endsWith(SO_SUFFIX)) {
            startInstall(context, SCHEME_SO + filePath, pluginInfo);
        } else if (filePath.endsWith(DEX_SUFFIX)) {
            startInstall(context, SCHEME_DEX + filePath, pluginInfo);
        } else {
            startInstall(context, SCHEME_FILE + filePath, pluginInfo);
        }
    }

    /**
     * 调用 {@link PluginInstallerService} 进行实际的安装过程。采用独立进程异步操作。
     *
     * @param context
     * @param filePath 支持两种scheme {@link PluginInstaller#SCHEME_ASSETS} 和
     *                 {@link PluginInstaller#SCHEME_FILE}
     * @param info     插件信息
     */
    private static void startInstall(Context context, String filePath, PluginLiteInfo info) {

        registerInstallderReceiver(context);
        /*
         * 获取packageName
         * 1、内置app，要求必须以 packageName.apk 命名，处于效率考虑。
         * 2、外部文件的安装，直接从file中获取packageName, 消耗100ms级别，可以容忍。
         */
        boolean isBuildin = false;
        PluginDebugLog.installFormatLog(TAG, "startInstall with file path:%s and plugin pkgName:%s"
                , filePath, info.packageName);

        if (filePath.startsWith(SCHEME_ASSETS)) {
            isBuildin = true;
        }

        if (!TextUtils.isEmpty(info.packageName)) {
            add2InstallList(info.packageName); // 添加到安装中列表
            if (isBuildin) {
                PluginDebugLog.installFormatLog(TAG, "add %s in buildInAppList", info.packageName);
                sBuiltinAppList.add(info.packageName); // 添加到内置app安装列表中
            }
        } else {
            PluginDebugLog.installLog(TAG, "startInstall PluginLiteInfo.packageName is null, just return!");
            throw new IllegalArgumentException("startInstall plugin lite info packageName is empty");
        }

        Intent intent = new Intent(PluginInstallerService.ACTION_INSTALL);
        intent.setClass(context, PluginInstallerService.class);
        intent.putExtra(IntentConstant.EXTRA_SRC_FILE, filePath);
        intent.putExtra(IntentConstant.EXTRA_PLUGIN_INFO, (Parcelable) info);

        context.startService(intent);
    }

    /**
     * 注册插件安装的监听广播
     */
    private static void registerInstallderReceiver(Context context) {
        if (sInstallerReceiverRegistered) {
            // 已经注册过就不再注册
            return;
        }

        Context appContext = context.getApplicationContext();
        IntentFilter filter = new IntentFilter();
        filter.addAction(PluginPackageManager.ACTION_PACKAGE_INSTALLED);
        filter.addAction(PluginPackageManager.ACTION_PACKAGE_INSTALLFAIL);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        appContext.registerReceiver(sApkInstallerReceiver, filter);

        sInstallerReceiverRegistered = true;
    }

    /**
     * 添加到安装中列表
     */
    private synchronized static void add2InstallList(String packageName) {
        PluginDebugLog.installFormatLog(TAG, "add2InstallList with %s", packageName);
        if (sInstallingList.contains(packageName)) {
            return;
        }
        sInstallingList.add(packageName);
    }


    /**
     * 获取安装的插件的dex目录
     */
    private static List<File> getInstalledDexFile(Context context, final String packageName) {

        List<File> dexFiles = new ArrayList<>();
        File dataDir = new File(PluginInstaller.getPluginappRootPath(context), packageName);
        FileFilter fileFilter = new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                String name = pathname.getName();
                return name.contains(packageName) && name.endsWith(DEX_SUFFIX);
            }
        };

        File[] files = dataDir.listFiles(fileFilter);
        if (files != null) {
            for (File file : files) {
                dexFiles.add(file);
            }
        }
        File dexDir = PluginInstaller.getPluginInjectRootPath(context);
        files = dexDir.listFiles(fileFilter);
        if (files != null) {
            for (File file : files) {
                dexFiles.add(file);
            }
        }

        return dexFiles;
    }


    /**
     * 删除已经安装插件的apk,dex,so库等文件
     */
    public static void deleteInstallerPackage(
            Context context, PluginLiteInfo info, String packageName) {
        PluginDebugLog.installFormatLog(TAG, "deleteInstallerPackage:%s", packageName);
        if (context == null || TextUtils.isEmpty(packageName)) {
            return;
        }

        File rootDir = PluginInstaller.getPluginappRootPath(context);
        File dataDir = new File(rootDir, packageName);
        List<File> dexFiles = getInstalledDexFile(context, packageName);
        for (File dexPath : dexFiles) {
            // 删除dex文件
            if (dexPath.delete()) {
                PluginDebugLog.installFormatLog(TAG, "deleteInstallerPackage %s,  dex %s success!", packageName, dexPath.getAbsolutePath());
            } else {
                PluginDebugLog.installFormatLog(TAG, "deleteInstallerPackage %s, dex %s fail!", packageName, dexPath.getAbsolutePath());
            }
        }

        File lib = new File(dataDir, "lib");
        // 删除lib目录下的so库
        boolean deleted = FileUtils.deleteDirectory(lib);
        PluginDebugLog.installFormatLog(TAG, "deleteInstallerPackage lib %s success: %s", packageName, deleted);

        File apk = null;
        String apkPath = info.srcApkPath;
        if (!TextUtils.isEmpty(apkPath)) {
            apk = new File(apkPath);  //删除旧的apk
            if (apk.exists() && apk.delete()) {
                PluginDebugLog.installFormatLog(TAG, "deleteInstallerPackage apk  %s success!", packageName);
            } else {
                PluginDebugLog.installFormatLog(TAG, "deleteInstallerPackage apk  %s fail!", packageName);
            }
        } else {
            PluginDebugLog.installFormatLog(TAG, "deleteInstallerPackage info srcApkPath is empty %s", packageName);
            apk = new File(rootDir, packageName + "." + info.pluginVersion + PluginInstaller.APK_SUFFIX);
            if (!apk.exists()) {
                apk = new File(rootDir, packageName + PluginInstaller.APK_SUFFIX);
            }
        }

        if (VersionUtils.hasOreo()) {
            //删除prof文件
            File mProf = new File(apk.getAbsolutePath() + ".prof");
            PluginDebugLog.installFormatLog(TAG, "prof path:%s", mProf.getAbsolutePath());
            if (mProf.exists() && mProf.delete()) {
                PluginDebugLog.installFormatLog(TAG, "deleteInstallerPackage prof  %s success!", packageName);
            } else {
                PluginDebugLog.installFormatLog(TAG, "deleteInstallerPackage prof  %s fail!", packageName);
            }
            //删除odex和vdex文件
            String currentInstructionSet = "";
            try {
                currentInstructionSet = FileUtils.getCurrentInstructionSet();
            } catch (Exception e) {
                currentInstructionSet = "arm";
            }

            String pathPrefix = apk.getParent() + "/oat/"
                    + currentInstructionSet + "/" + packageName;

            String oPath = pathPrefix + ".odex";
            String vPath = pathPrefix + ".vdex";

            File oPathFile = new File(oPath);
            File vPathFile = new File(vPath);
            PluginDebugLog.installFormatLog(TAG, "odex path:%s", oPathFile.getAbsolutePath());
            PluginDebugLog.installFormatLog(TAG, "vdex path:%s", vPathFile.getAbsolutePath());
            if (oPathFile.exists() && oPathFile.delete()) {
                PluginDebugLog.installFormatLog(TAG, "deleteInstallerPackage odex  %s success!", packageName);
            } else {
                PluginDebugLog.installFormatLog(TAG, "deleteInstallerPackage odex  %s fail!", packageName);
            }

            if (vPathFile.exists() && vPathFile.delete()) {
                PluginDebugLog.installFormatLog(TAG, "deleteInstallerPackage vdex  %s success!", packageName);
            } else {
                PluginDebugLog.installFormatLog(TAG, "deleteInstallerPackage vdex  %s fail!", packageName);
            }
        }
    }

    /**
     * 删除已经安装插件的数据目录(包括db,sp,cache和files目录)
     */
    public static void deletePluginData(Context context, String packageName) {
        File dataDir = new File(PluginInstaller.getPluginappRootPath(context), packageName);
        File db = new File(dataDir, "databases");
        File sharedPreference = new File(dataDir, "shared_prefs");
        File file = new File(dataDir, "files");
        File cache = new File(dataDir, "cache");

        boolean deleted = FileUtils.deleteDirectory(db);
        PluginDebugLog.installFormatLog(TAG, "deletePluginData db %s success: %s", packageName, deleted);

        deleted = FileUtils.deleteDirectory(sharedPreference);
        PluginDebugLog.installFormatLog(TAG, "deletePluginData sp %s success: %s", packageName, deleted);

        deleted = FileUtils.deleteDirectory(file);
        PluginDebugLog.installFormatLog(TAG, "deletePluginData file %s success: %s", packageName, deleted);

        deleted = FileUtils.deleteDirectory(cache);
        PluginDebugLog.installFormatLog(TAG, "deletePluginData cache %s success: %s", packageName, deleted);
    }

    /**
     * 查看某个app是否正在安装
     */
    public static synchronized boolean isInstalling(String packageName) {
        return sInstallingList.contains(packageName);
    }

    /**
     * 从插件文件名中提取插件的包名，在没有正确获取包名的情况下使用
     * 插件命名规范： {pkgName}.apk
     */
    public static String extractPkgNameFromPath(String filePath) {

        int start = filePath.lastIndexOf("/");
        int end = filePath.length();
        if (filePath.endsWith(PluginInstaller.SO_SUFFIX)) {
            end = filePath.lastIndexOf(PluginInstaller.SO_SUFFIX);
        } else if (filePath.endsWith(PluginInstaller.DEX_SUFFIX)) {
            end = filePath.lastIndexOf(PluginInstaller.DEX_SUFFIX);
        } else if (filePath.contains(PluginInstaller.APK_SUFFIX)) {
            end = filePath.lastIndexOf(PluginInstaller.APK_SUFFIX);
        }
        String mapPackagename = filePath.substring(start + 1, end);
        PluginDebugLog.runtimeFormatLog(TAG, "filePath: %s, pkgName: ", filePath, mapPackagename);
        return mapPackagename;
    }
}
