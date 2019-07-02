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

import android.content.Context;
import android.text.TextUtils;

import org.qiyi.pluginlibrary.pm.PluginLiteInfo;
import org.qiyi.pluginlibrary.pm.PluginPackageManager;
import org.qiyi.pluginlibrary.utils.CpuAbiUtils;
import org.qiyi.pluginlibrary.utils.FileUtils;
import org.qiyi.pluginlibrary.utils.PluginDebugLog;
import org.qiyi.pluginlibrary.utils.VersionUtils;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;

/**
 * 负责插件的卸载相关逻辑
 */
public class PluginUninstaller extends PluginInstaller {
    private static final String TAG = "PluginUninstaller";

    /**
     * 删除已经安装插件的apk,dex,so库等文件
     */
    public static void deleteInstallerPackage(
            Context context, PluginLiteInfo info) {
        deleteInstallerPackage(context, info, true);
    }

    /**
     * 删除已经安装插件的apk,dex,so库等文件
     */
    public static void deleteInstallerPackage(
            Context context, PluginLiteInfo info, boolean deleteAllVersion) {
        String packageName = info.packageName;
        PluginDebugLog.installFormatLog(TAG, "deleteInstallerPackage:%s", packageName);

        File rootDir = PluginInstaller.getPluginappRootPath(context);
        deleteDexFiles(rootDir, packageName);

        File dataDir = new File(rootDir, packageName);
        File lib = new File(dataDir, NATIVE_LIB_PATH);
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
        // 删除历史版本遗留的apk
        if (deleteAllVersion) {
            deleteOldApks(rootDir, packageName);
        }
        // 删除odex和vdex文件
        deleteOatFiles(apk, packageName, info.pluginVersion, deleteAllVersion);
    }


    /**
     * 删除已安装插件相关dex文件
     */
    private static void deleteDexFiles(File rootDir, final String packageName) {

        List<File> dexFiles = new ArrayList<>();
        File dataDir = new File(rootDir, packageName);
        FileFilter fileFilter = new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                String name = pathname.getName();
                return name.startsWith(packageName) && name.endsWith(DEX_SUFFIX);
            }
        };

        File[] files = dataDir.listFiles(fileFilter);
        if (files != null) {
            for (File file : files) {
                dexFiles.add(file);
            }
        }
        File dexDir = new File(rootDir, "dex");
        files = dexDir.listFiles(fileFilter);
        if (files != null) {
            for (File file : files) {
                dexFiles.add(file);
            }
        }
        // 删除相关dex文件
        for (File dexPath : dexFiles) {
            if (dexPath.delete()) {
                PluginDebugLog.installFormatLog(TAG, "deleteDexFiles %s,  dex %s success!", packageName, dexPath.getAbsolutePath());
            } else {
                PluginDebugLog.installFormatLog(TAG, "deleteDexFiles %s, dex %s fail!", packageName, dexPath.getAbsolutePath());
            }
        }
    }

    /**
     * 删除遗留的低版本的apk
     */
    private static void deleteOldApks(File rootDir, final String packageName) {
        List<File> apkFiles = new ArrayList<>();
        FileFilter fileFilter = new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                String name = pathname.getName();
                return name.startsWith(packageName) && name.endsWith(APK_SUFFIX);
            }
        };
        File[] files = rootDir.listFiles(fileFilter);
        if (files != null) {
            for (File file : files) {
                apkFiles.add(file);
            }
        }
        // 删除相关apk文件
        for (File apkFile : apkFiles) {
            if (apkFile.delete()) {
                PluginDebugLog.installFormatLog(TAG, "deleteOldApks %s,  dex %s success!", packageName, apkFile.getAbsolutePath());
            } else {
                PluginDebugLog.installFormatLog(TAG, "deleteOldApks %s, dex %s fail!", packageName, apkFile.getAbsolutePath());
            }
        }
    }

    /**
     * Android O以上删除dexoat优化生成的odex和vdex文件
     */
    private static void deleteOatFiles(File apkFile, final String packageName, final String version, final boolean deleteAllVersion) {
        if (VersionUtils.hasOreo()) {
            //删除prof文件
            File mProf = new File(apkFile.getAbsolutePath() + ".prof");
            PluginDebugLog.installFormatLog(TAG, "prof path:%s", mProf.getAbsolutePath());
            if (mProf.exists() && mProf.delete()) {
                PluginDebugLog.installFormatLog(TAG, "deleteInstallerPackage prof  %s success!", packageName);
            } else {
                PluginDebugLog.installFormatLog(TAG, "deleteInstallerPackage prof  %s fail!", packageName);
            }
            //删除odex和vdex文件
            String currentInstructionSet = CpuAbiUtils.getCurrentInstructionSet();
            File oatDir = new File(apkFile.getParent() + "/oat/"
                    + currentInstructionSet);
            if (!oatDir.exists()) {
                return;
            }

            List<File> toDeleted = new ArrayList<>();
            FileFilter fileFilter = new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    String name = pathname.getName();
                    return name.startsWith(deleteAllVersion ? packageName : packageName + "." + version)
                            && (name.endsWith(".odex") || name.endsWith(".vdex"));
                }
            };

            File[] files = oatDir.listFiles(fileFilter);
            if (files != null) {
                for (File file : files) {
                    toDeleted.add(file);
                }
            }

            for (File dexPath : toDeleted) {
                if (dexPath.delete()) {
                    PluginDebugLog.installFormatLog(TAG, "deleteInstallerPackage odex/vdex: %s  %s success!",
                            dexPath.getAbsolutePath(), packageName);
                } else {
                    PluginDebugLog.installFormatLog(TAG, "deleteInstallerPackage odex/vdex: %s  %s fail!",
                            dexPath.getAbsolutePath(), packageName);
                }
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

        File extCache = new File(PluginPackageManager.getExternalCacheRootDir(), packageName);
        File extFiles = new File(PluginPackageManager.getExternalFilesRootDir(), packageName);

        // 需要清理数据的目录列表
        File[] toDeleted = new File[]{db, sharedPreference, file, cache, dataDir, extCache, extFiles};
        for (File dstDir : toDeleted) {
            if (dstDir != null && dstDir.exists()) {
                boolean deleted = FileUtils.cleanDirectoryContent(dstDir);
                PluginDebugLog.installFormatLog(TAG, "deletePluginData directory %s for plugin %s, deleted: ",
                        dstDir.getAbsolutePath(), packageName, deleted);
            }
        }
    }

}
