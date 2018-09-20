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

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.text.TextUtils;

import org.qiyi.pluginlibrary.constant.IntentConstant;
import org.qiyi.pluginlibrary.error.ErrorType;
import org.qiyi.pluginlibrary.pm.PluginLiteInfo;
import org.qiyi.pluginlibrary.pm.PluginPackageManager;
import org.qiyi.pluginlibrary.utils.ErrorUtil;
import org.qiyi.pluginlibrary.utils.FileUtils;
import org.qiyi.pluginlibrary.utils.PluginDebugLog;
import org.qiyi.pluginlibrary.utils.ReflectionUtils;
import org.qiyi.pluginlibrary.utils.VersionUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import dalvik.system.DexClassLoader;

/**
 * 在独立进程对插件进程进行安装
 */
public class PluginInstallerService extends Service {

    public static final String TAG = "PluginInstallerService";
    public static final String ACTION_INSTALL = "com.qiyi.neptune.action.INSTALL";


    private static int MSG_ACTION_INSTALL = 0;
    private static int MSG_ACTION_QUIT = 1;
    private static int DELAY_QUIT_TIME = 1000 * 10;  // 10s
    private volatile Looper mServiceLooper;
    private volatile ServiceHandler mServiceHandler;

    /**
     * 初始化 dex，因为第一次loaddex，如果放hostapp 进程，
     * 有可能会导致hang住(参考类的说明)。
     * 所以在安装阶段独立进程中执行。
     *
     * @param apkFile  插件apk文件
     * @param packageName 插件包名
     */
    private static void installDex(final File apkFile,
                                   String packageName,
                                   String pkgDirPath) {

        final File pkgDir = new File(pkgDirPath, packageName);
        DexOptimizer.optimize(apkFile, pkgDir, VersionUtils.hasNougat(), new DexOptimizer.ResultCallback() {
            @Override
            public void onStart(File dexFile, File optimizedDir) {
                if (dexFile != null) {
                    PluginDebugLog.installFormatLog(TAG, "DexOptimizer onStart: dexFile:%s", dexFile.getAbsolutePath());
                }

            }

            @Override
            public void onSuccess(File dexFile, File optimizedDir, File optimizedFile) {
                if (dexFile != null) {
                    PluginDebugLog.installFormatLog(TAG, "DexOptimizer onSuccess: dexFile:%s", dexFile.getAbsolutePath());
                }

            }

            @Override
            public void onFailed(File dexFile, File optimizedDir, Throwable thr) {
                try {
                    new DexClassLoader(apkFile.getAbsolutePath(), pkgDir.getAbsolutePath(), null, getClass().getClassLoader());
                    PluginDebugLog.installFormatLog(TAG, "DexOptimizer onFail:%s", thr.getMessage());
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        });
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        HandlerThread thread = new HandlerThread("PluginInstallerService");
        thread.start();

        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        if (mServiceHandler.hasMessages(MSG_ACTION_QUIT)) {
            PluginDebugLog.installLog(TAG, "pluginInstallerService removeMessages MSG_ACTION_QUIT");
            mServiceHandler.removeMessages(MSG_ACTION_QUIT);
        }
        PluginDebugLog.installLog(TAG, "pluginInstallerService onStartCommond MSG_ACTION_INSTALL");
        Message msg = mServiceHandler.obtainMessage(MSG_ACTION_INSTALL);
        msg.arg1 = startId;
        msg.obj = intent;
        mServiceHandler.sendMessage(msg);
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        mServiceLooper.quit();
        super.onDestroy();
        // 退出时结束进程
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    /**
     * 处理插件安装任务
     */
    private void onHandleIntent(Intent intent) {

        String action = intent.getAction();
        if (ACTION_INSTALL.equals(action)) { // 插件安装
            String srcFile = intent.getStringExtra(IntentConstant.EXTRA_SRC_FILE);
            PluginLiteInfo pluginInfo = intent.getParcelableExtra(IntentConstant.EXTRA_PLUGIN_INFO);
            handleInstall(srcFile, pluginInfo);
        }
    }

    /**
     * 安装插件
     *
     * @param srcFile 插件路径
     * @param info  插件info信息
     */
    private void handleInstall(String srcFile, PluginLiteInfo info) {
        if (null == info) {
            PluginDebugLog.installFormatLog(TAG, "Install srcFile:%s fail beacute info is null!", srcFile);
            return;
        }

        PluginDebugLog.installFormatLog(TAG, "handleInstall srcFile:%s, info: %s", srcFile, info);
        if (srcFile.startsWith(PluginInstaller.SCHEME_ASSETS)) {
            installBuiltinApk(srcFile, info);
        } else if (srcFile.startsWith(PluginInstaller.SCHEME_SO)) {
            installSoPlugin(srcFile, info);
        } else if (srcFile.startsWith(PluginInstaller.SCHEME_DEX)) {
            installDexPlugin(srcFile, info);
        } else if (srcFile.startsWith(PluginInstaller.SCHEME_FILE)) {
            installAPKFile(srcFile, info);
        } else {
            srcFile = PluginInstaller.SCHEME_FILE + srcFile;
            installAPKFile(srcFile, info);
        }
    }

    /**
     * 安装so插件，只做拷贝
     */
    private void installSoPlugin(String srcFile, PluginLiteInfo info) {
        String soFilePath = srcFile.substring(PluginInstaller.SCHEME_SO.length());
        File destFileTemp = new File(PluginInstaller.getPluginappRootPath(this), System.currentTimeMillis() + ".tmp");
        File soFile = new File(soFilePath);
        if (!soFile.exists()) {
            setInstallFail(srcFile, ErrorType.INSTALL_ERROR_SO_NOT_EXIST, info);
            return;
        }

        boolean copyResult = FileUtils.copyToFile(soFile, destFileTemp);
        if (copyResult && null != info && !TextUtils.isEmpty(info.packageName)) {
            File destFile = new File(PluginInstaller.getPluginappRootPath(this), info.packageName + PluginInstaller.SO_SUFFIX);
            if (destFileTemp.exists() && destFileTemp.renameTo(destFile)) {

                String libDir = PluginInstaller.getPluginappRootPath(this).getAbsolutePath() + File.separator + info.packageName;
                FileUtils.deleteDirectory(new File(libDir));
                boolean flag = FileUtils.installNativeLibrary(destFile.getAbsolutePath(), libDir);
                if (flag) {
                    setInstallSuccess(info.packageName, srcFile, destFile.getAbsolutePath(), info);
                    return;
                } else {
                    PluginDebugLog.installLog(TAG, "handleInstall SO, install so lib failed!");
                    setInstallFail(srcFile, ErrorType.INSTALL_ERROR_SO_UNZIP_FAILED, info);
                    return;
                }
            } else {
                PluginDebugLog.installLog(TAG, "handleInstall SO, rename failed!");
            }
        }
        setInstallFail(srcFile, ErrorType.INSTALL_ERROR_SO_COPY_FAILED, info);
    }

    /**
     * 安装Dex插件，只做拷贝
     */
    private void installDexPlugin(String srcFile, PluginLiteInfo info) {
        String dexFilePath = srcFile.substring(PluginInstaller.SCHEME_DEX.length());
        File destFileTemp = new File(PluginInstaller.getPluginappRootPath(this), System.currentTimeMillis() + ".tmp");
        File dexFile = new File(dexFilePath);
        if (!dexFile.exists()) {
            setInstallFail(srcFile, ErrorType.INSTALL_ERROR_DEX_NOT_EXIST, info);
            return;
        }

        boolean copyResult = FileUtils.copyToFile(new File(dexFilePath), destFileTemp);
        if (copyResult && null != info && !TextUtils.isEmpty(info.packageName)) {
            File destFile = new File(PluginInstaller.getPluginappRootPath(this), info.packageName + PluginInstaller.DEX_SUFFIX);
            if (destFileTemp.exists() && destFileTemp.renameTo(destFile)) {
                setInstallSuccess(info.packageName, srcFile, destFile.getAbsolutePath(), info);
                return;
            } else {
                PluginDebugLog.installLog(TAG, "handleInstall dex, rename failed!");
            }
        }
        setInstallFail(srcFile, ErrorType.INSTALL_ERROR_DEX_COPY_FAILED, info);
    }


    /**
     * 安装asset/pluginapp内置插件
     */
    private void installBuiltinApk(String assetsPathWithScheme, PluginLiteInfo info) {
        String assetsPath = assetsPathWithScheme.substring(PluginInstaller.SCHEME_ASSETS.length());
        PluginDebugLog.installFormatLog(TAG,
                "PluginInstallerService installBuildInApk assetsPath" + assetsPath);
        // 先把 asset 拷贝到临时文件。
        InputStream is = null;
        try {

            is = this.getAssets().open(assetsPath);
            doInstall(is, assetsPathWithScheme, info);

        } catch (IOException e) {
            setInstallFail(assetsPathWithScheme, e instanceof FileNotFoundException ?
                    ErrorType.INSTALL_ERROR_ASSET_APK_NOT_FOUND : ErrorType.INSTALL_ERROR_FILE_IOEXCEPTION, info);
        } finally {
            FileUtils.closeQuietly(is);
        }
    }


    /**
     * 安装sd卡上的插件
     */
    private void installAPKFile(String apkFilePathWithScheme, PluginLiteInfo info) {
        String apkFilePath = apkFilePathWithScheme.substring(PluginInstaller.SCHEME_FILE.length());
        PluginDebugLog.installFormatLog(TAG, "PluginInstallerService::installAPKFile: %s", apkFilePath);

        File source = new File(apkFilePath);
        if (!source.exists()) {
            if (info != null && !TextUtils.isEmpty(info.packageName)) {
                PluginPackageManager.notifyClientPluginException(
                        this, info.packageName, "download Apk file not exist!");
            }
            setInstallFail(apkFilePathWithScheme, ErrorType.INSTALL_ERROR_APK_NOT_EXIST, info);
            return;
        }

        InputStream is = null;
        try {
            is = new FileInputStream(source);
            doInstall(is, apkFilePathWithScheme, info);
        } catch (FileNotFoundException e) {
            setInstallFail(apkFilePathWithScheme, ErrorType.INSTALL_ERROR_APK_NOT_EXIST, info);
        } finally {
            FileUtils.closeQuietly(is);
        }
    }

    /**
     * 安装某个插件
     */
    private void doInstall(InputStream is, String srcPathWithScheme, PluginLiteInfo info) {

        if (is == null || srcPathWithScheme == null) {
            PluginDebugLog.installLog(TAG, "doInstall : srcPathWithScheme or InputStream is null and just return!");
            return;
        }

        PluginDebugLog.installFormatLog(TAG,
                "doInstall : %s,pkgName: %s", srcPathWithScheme, info.packageName);

        PackageManager pm = this.getPackageManager();
        String apkFilePath = null;
        File tempFile = null;
        if (srcPathWithScheme.startsWith(PluginInstaller.SCHEME_FILE)) {
            apkFilePath = srcPathWithScheme.substring(PluginInstaller.SCHEME_FILE.length());
        } else if (srcPathWithScheme.startsWith(PluginInstaller.SCHEME_ASSETS)) {
            // 解压拷贝Asset目录下的插件
            tempFile = new File(PluginInstaller.getPluginappRootPath(this), System.currentTimeMillis() + ".tmp");
            boolean result = FileUtils.copyToFile(is, tempFile);
            PluginDebugLog.installLog(TAG, "doInstall copy result" + result);
            if (!result) {
                tempFile.delete();
                setInstallFail(srcPathWithScheme, ErrorType.INSTALL_ERROR_ASSET_APK_COPY_FAILED, info);
                return;
            }
            apkFilePath = tempFile.getAbsolutePath();
        }
        if (null == apkFilePath) {
            setInstallFail(srcPathWithScheme, ErrorType.INSTALL_ERROR_FILE_PATH_ILLEGAL, info);
            return;
        }
        // 校验源文件是否存在
        File srcApkFile = new File(apkFilePath);
        if (!srcApkFile.exists()) {
            setInstallFail(srcPathWithScheme, ErrorType.INSTALL_ERROR_APK_NOT_EXIST, info);
            return;
        }
        // 解析apk数据
        PackageInfo pkgInfo = null;
        try {
            pkgInfo = pm.getPackageArchiveInfo(apkFilePath, PackageManager.GET_ACTIVITIES);
        } catch (Throwable e) {
            ErrorUtil.throwErrorIfNeed(e);
        }

        if (pkgInfo == null) {
            setInstallFail(srcPathWithScheme, ErrorType.INSTALL_ERROR_APK_PARSE_FAILED, info);
            return;
        }

        info.srcApkPkgName = pkgInfo.packageName;
        info.srcApkVersion = pkgInfo.versionName;

        String packageName = !TextUtils.isEmpty(info.packageName) ?
                info.packageName : pkgInfo.packageName;

        if (PluginDebugLog.isDebug()) {
            int nameStart = srcPathWithScheme.lastIndexOf("/");
            int nameEnd = srcPathWithScheme.lastIndexOf(PluginInstaller.APK_SUFFIX);
            String fileName = srcPathWithScheme.substring(nameStart + 1, nameEnd);
            PluginDebugLog.installLog(TAG, "doInstall with: " + packageName + " and file: " + fileName);
            // 待安装的插件和apk里的包名是否一致
            if (!fileName.equals(packageName) || !TextUtils.equals(info.packageName, pkgInfo.packageName)) {
                PluginDebugLog.installFormatLog(TAG, "doInstall with wrong apk, packageName not match, toInstall packageName=%s, "
                        + "toInstall apk fileName=%s, packageName in apk=%s", info.packageName, fileName, pkgInfo.packageName);
            }
            // 待安装的插件和apk里的版本号是否一致
            if (!TextUtils.equals(info.pluginVersion, pkgInfo.versionName)) {
                PluginDebugLog.installFormatLog(TAG, "doInstall with wrong apk, versionName not match, packageName=%s, "
                        + "toInstall version=%s, versionName in apk=%s", packageName, info.pluginVersion, pkgInfo.versionName);
            }
        }

        if (!TextUtils.isEmpty(info.packageName) && !TextUtils.equals(info.packageName, pkgInfo.packageName)) {
            PluginDebugLog.installLog(TAG, "doInstall with apk packageName not match with plugin name, " + packageName);
            setInstallFail(srcPathWithScheme, ErrorType.INSTALL_ERROR_PKG_NAME_NOT_MATCH, info);
            return;
        }

        // 如果是内置app，检查文件名是否以包名命名，处于效率原因，要求内置app必须以包名命名.
        if (srcPathWithScheme.startsWith(PluginInstaller.SCHEME_ASSETS)) {
            int start = srcPathWithScheme.lastIndexOf("/");
            int end = srcPathWithScheme.lastIndexOf(PluginInstaller.APK_SUFFIX);
            String fileName = srcPathWithScheme.substring(start + 1, end);
            if (!packageName.equals(fileName) || !TextUtils.equals(fileName, pkgInfo.packageName)) {
                // throw new RuntimeException(srcPathWithScheme + " must be
                // named with it's package name : "
                // + packageName + PluginInstaller.APK_SUFFIX);
                PluginDebugLog.installLog(TAG, "doInstall build plugin, package name is not same as in apk file, return!");
                setInstallFail(srcPathWithScheme, ErrorType.INSTALL_ERROR_PKG_NAME_NOT_MATCH, info);
                return;
            }
        }
        // 获取插件安装目录
        String apkName = packageName + "." + info.pluginVersion + PluginInstaller.APK_SUFFIX;
        File destFile = getPreferredInstallLocation(pkgInfo, apkName);
        if (destFile.exists()) {
            destFile.delete();
        }

        if (srcApkFile.getParent().equals(destFile.getParent())) {
            // 目标文件和临时文件在同一目录下
            PluginDebugLog.installFormatLog(TAG,
                    "doInstall:%s tmpFile and destFile in same directory!", packageName);
            if (!srcApkFile.renameTo(destFile)) {
                boolean copyResult = FileUtils.copyToFile(srcApkFile, destFile);
                if (!copyResult) {
                    setInstallFail(srcPathWithScheme, ErrorType.INSTALL_ERROR_RENAME_FAILED, info);
                    return;
                }
            }
        } else {
            // 拷贝到其他目录，比如安装到 sdcard
            PluginDebugLog.installFormatLog(TAG,
                    "doInstall:%s tmpFile and destFile in different directory!", packageName);
            boolean tempResult = FileUtils.copyToFile(srcApkFile, destFile);
            if (!tempResult) {
                PluginDebugLog.installFormatLog(TAG, "doInstall:%s copy apk failed!", packageName);
                setInstallFail(srcPathWithScheme, ErrorType.INSTALL_ERROR_APK_COPY_FAILED, info);
                return;
            }
        }
        PluginDebugLog.installFormatLog(TAG,
                "pluginInstallerService begin install lib, pkgName:%s", packageName);
        if (null != tempFile && tempFile.exists()) {
            tempFile.delete();  //删除临时文件
        }

        File pkgDir = new File(PluginInstaller.getPluginappRootPath(this), packageName);
        if (!pkgDir.exists() && !pkgDir.mkdir()) {
            setInstallFail(srcPathWithScheme, ErrorType.INSTALL_ERROR_MKDIR_FAILED, info);
            return;
        }

        File libDir = new File(pkgDir, PluginInstaller.NATIVE_LIB_PATH);
        if (!libDir.exists() && !libDir.mkdirs()) {
            setInstallFail(srcPathWithScheme, ErrorType.INSTALL_ERROR_MKDIR_FAILED, info);
            return;
        }

        FileUtils.installNativeLibrary(destFile.getAbsolutePath(), libDir.getAbsolutePath());  //拷贝so库
        PluginDebugLog.installFormatLog(TAG,
                "pluginInstallerService finish install lib,pkgName:%s", packageName);
        // dexopt, 提前优化插件的dex
        PluginDebugLog.installFormatLog(TAG,
                "pluginInstallerService began install dex,pkgName:%s", packageName);
        installDex(destFile, packageName, PluginInstaller.getPluginappRootPath(this).getAbsolutePath());
        PluginDebugLog.installFormatLog(TAG,
                "pluginInstallerService finish install dex,pkgName:%s", packageName);
        // dexoat结束之后，再通知插件安装完成
        setInstallSuccess(packageName, srcPathWithScheme, destFile.getAbsolutePath(), info);
    }

    /**
     * 发送安装失败的广播
     *
     * @param srcPathWithScheme 安装文件路径
     * @param failReason        失败原因
     * @param info              安装插件信息
     */
    private void setInstallFail(String srcPathWithScheme, int failReason, PluginLiteInfo info) {
        if (info != null) {
            info.installStatus = PluginLiteInfo.PLUGIN_UNINSTALLED;
        }
        Intent intent = new Intent(PluginPackageManager.ACTION_PACKAGE_INSTALLFAIL);
        intent.setPackage(getPackageName());
        intent.putExtra(IntentConstant.EXTRA_PKG_NAME, info != null ? info.packageName : "");
        intent.putExtra(IntentConstant.EXTRA_SRC_FILE, srcPathWithScheme);// 同时返回安装前的安装文件目录。
        intent.putExtra(ErrorType.ERROR_REASON, failReason);               // 同时返回安装失败的原因
        intent.putExtra(IntentConstant.EXTRA_PLUGIN_INFO, (Parcelable) info);// 同时返回APK的插件信息
        sendBroadcast(intent);
        if (info != null) {
            PluginDebugLog.installLog(TAG, "Send setInstallFail with reason: " + failReason + " PluginPackageInfoExt: " + info);
        }
    }

    /**
     * 发送安装成功的广播
     */
    private void setInstallSuccess(String pkgName, String srcPathWithScheme, String destPath, PluginLiteInfo info) {
        if (info != null) {
            info.srcApkPath = destPath;
            info.installStatus = PluginLiteInfo.PLUGIN_INSTALLED;
        }
        Intent intent = new Intent(PluginPackageManager.ACTION_PACKAGE_INSTALLED);
        intent.setPackage(getPackageName());
        intent.putExtra(IntentConstant.EXTRA_PKG_NAME, pkgName);
        intent.putExtra(IntentConstant.EXTRA_SRC_FILE, srcPathWithScheme);// 同时返回安装前的安装文件目录。
        intent.putExtra(IntentConstant.EXTRA_DEST_FILE, destPath);        // 同时返回安装后的安装文件目录。
        intent.putExtra(IntentConstant.EXTRA_PLUGIN_INFO, (Parcelable) info);// 同时返回APK的插件信息
        sendBroadcast(intent);
        if (info != null) {
            PluginDebugLog.installLog(TAG, "Send setInstallSuccess " + " PluginPackageInfoExt: " + info);
        }
    }

    /**
     * 获取插件安装目录，内置目录或者SD卡目录
     * 目前VR插件是安装在SD的/Android/data目录的
     */
    private File getPreferredInstallLocation(PackageInfo pkgInfo, String apkName) {

        boolean preferExternal = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
            int installLocation = ReflectionUtils.on(pkgInfo).<Integer>get("installLocation");

            PluginDebugLog.installLog(TAG, "installLocation:" + installLocation);

            // see PackageInfo.INSTALL_LOCATION_PREFER_EXTERNAL
            final int INSTALL_LOCATION_PREFER_EXTERNAL = 2;

            if (installLocation == INSTALL_LOCATION_PREFER_EXTERNAL) {
                preferExternal = true;
            }
        } else {
            // android 2.1 以及以下不支持安装到sdcard
            preferExternal = false;
        }

        // 查看外部存储器是否可用
        if (preferExternal) {
            String state = Environment.getExternalStorageState();
            if (!Environment.MEDIA_MOUNTED.equals(state)) {   // 不可用
                preferExternal = false;
            }
        }

        File destFile = null;
        if (!preferExternal) {
            // 默认安装到 internal data dir
            destFile = new File(PluginInstaller.getPluginappRootPath(this), apkName);
            PluginDebugLog.installFormatLog(TAG, "install to Location %s:", destFile.getPath());
        } else {
            // 安装到外部存储器
            destFile = new File(getExternalFilesDir(PluginInstaller.PLUGIN_ROOT_PATH), apkName);
            PluginDebugLog.installFormatLog(TAG, "install to Location %s", destFile.getPath());
        }
        return destFile;
    }

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            PluginDebugLog.installLog(TAG, "handleMessage: what " + msg.what);
            if (msg.what == MSG_ACTION_INSTALL) {
                if (msg.obj instanceof Intent) {
                    onHandleIntent((Intent) msg.obj);
                }
                if (!mServiceHandler.hasMessages(MSG_ACTION_INSTALL) && !mServiceHandler.hasMessages(MSG_ACTION_QUIT)) {
                    PluginDebugLog.installLog(TAG, "sendMessage MSG_ACTION_QUIT");
                    Message quit = mServiceHandler.obtainMessage(MSG_ACTION_QUIT);
                    mServiceHandler.sendMessageDelayed(quit, DELAY_QUIT_TIME);
                }
            } else if (msg.what == MSG_ACTION_QUIT) {
                stopSelf();
            }
        }
    }
}
