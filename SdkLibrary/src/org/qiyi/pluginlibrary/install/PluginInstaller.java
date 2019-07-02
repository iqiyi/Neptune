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
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Parcelable;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.qiyi.pluginlibrary.Neptune;
import org.qiyi.pluginlibrary.constant.IntentConstant;
import org.qiyi.pluginlibrary.error.ErrorType;
import org.qiyi.pluginlibrary.pm.PluginLiteInfo;
import org.qiyi.pluginlibrary.pm.PluginPackageManager;
import org.qiyi.pluginlibrary.utils.ErrorUtil;
import org.qiyi.pluginlibrary.utils.FileUtils;
import org.qiyi.pluginlibrary.utils.PluginDebugLog;
import org.qiyi.pluginlibrary.utils.ReflectionUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 负责插件的安装操作
 */
public class PluginInstaller {
    public static final String TAG = "PluginInstaller";

    public static final String PLUGIN_ROOT_PATH = "pluginapp";
    public static final String APK_SUFFIX = ".apk";
    public static final String ZIP_SUFFIX = ".zip";
    public static final String NATIVE_LIB_PATH = "lib";
    public static final String NATIVE_DEX_PATH = "dex";
    public static final String SO_SUFFIX = ".so";
    public static final String DEX_SUFFIX = ".dex";
    public static final String ANDROID_ASSETS = "/android_asset/";
    // scheme前缀
    public static final String SCHEME_ASSETS = "assets://";
    public static final String SCHEME_FILE = "file://";
    public static final String SCHEME_SO = "so://";
    public static final String SCHEME_DEX = "dex://";

    private static class InstallerThreadFactory implements ThreadFactory {
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix = "PluginInstaller-thread-";

        InstallerThreadFactory() {
            SecurityManager s = System.getSecurityManager();
            group = s != null ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement());
            if (t.isDaemon()) {
                t.setDaemon(false);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }

    // 插件安装线程池
    private static Executor sInstallerExecutor = Executors.newFixedThreadPool(1, new InstallerThreadFactory());

    /**
     * 获取插件安装的根目录
     */
    public static File getPluginappRootPath(Context context) {
        File repoDir = context.getDir(PLUGIN_ROOT_PATH, 0);
        if (!repoDir.exists()) {
            repoDir.mkdirs();
        }
        return repoDir;
    }

    /**
     * 插件classloader注入到parent classloader时，指定的optimizedDirectory路径,保存解析后的dex
     * API >= 26时，该参数已废弃 @see <a href="https://android.googlesource.com/platform/libcore/+/master/dalvik/src/main/java/dalvik/system/BaseDexClassLoader.java"</a>
     */
    public static File getPluginInjectRootPath(Context context) {
        File rootDir = getPluginappRootPath(context);
        File dexDir = new File(rootDir, "dex");
        if (!dexDir.exists()) {
            dexDir.mkdirs();
        }
        return dexDir;
    }

    /**
     * 根据插件PluginLiteInfo传入的mPath转换为相应的schema
     *
     * @param info 插件info信息
     * @return 对应的schema路径
     */
    private static String mappingSchemaPath(@NonNull PluginLiteInfo info) {
        if (TextUtils.isEmpty(info.mPath)) {
            // 路径为空，认为是内置插件
            String buildInPath = SCHEME_ASSETS + PLUGIN_ROOT_PATH + "/" + info.packageName + APK_SUFFIX;
            PluginDebugLog.installFormatLog(TAG, "install buildIn apk: %s, info: %s", buildInPath, info);
            return buildInPath;
        }
        String filePath = info.mPath;
        Uri uri = Uri.parse(filePath);
        if (filePath.startsWith(SCHEME_FILE)) {
            filePath = uri.getPath();  //文件绝对路径
            if (TextUtils.isEmpty(filePath)) {
                throw new IllegalArgumentException("illegal install file path: " + info.mPath);
            }
            if (filePath.startsWith(ANDROID_ASSETS)) {
                String buildInPath = SCHEME_ASSETS + filePath.substring(ANDROID_ASSETS.length());
                PluginDebugLog.installFormatLog(TAG, "install buildIn apk: %s, info: %s", buildInPath, info);
                return buildInPath;
            }
        }

        PluginDebugLog.installFormatLog(TAG, "install external apk: %s, info: %s", filePath, info);
        String targetPath = info.mPath;
        // 根据文件后缀做兼容
        if (filePath.endsWith(SO_SUFFIX)) {
            targetPath = SCHEME_SO + filePath;
        } else if (filePath.endsWith(DEX_SUFFIX)) {
            targetPath = SCHEME_DEX + filePath;
        } else if (TextUtils.isEmpty(uri.getScheme())){
            targetPath = SCHEME_FILE + filePath;
        }
        return targetPath;
    }

    /**
     * 准备安装一个插件
     * 如果info.mPath为空，则安装内置在assets/pluginapp目录下的apk
     * 如果info.mPath不为空，则安装mPath路径下的apk，可能是asset目录，也可能是文件绝对路径
     *
     * @param context  宿主的Context
     * @param info     插件info信息
     * @param callBack 安装监听器
     */
    public static void startInstall(final Context context, @NonNull final PluginLiteInfo info, final IInstallCallBack callBack) {
        // mapping获取schema形式的path
        final String targetPath = mappingSchemaPath(info);
        // 根据版本判断是启用独立Service进程安装，还是直接安装
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN
                || Neptune.getConfig().withInstallerProcess()
                || info.useInstallerProcess) {
            // 4.1以下系统启用独立进程Service安装插件
            Intent intent = new Intent(PluginInstallerService.ACTION_INSTALL);
            intent.setPackage(context.getPackageName());
            intent.setClass(context, PluginInstallerService.class);
            intent.putExtra(IntentConstant.EXTRA_SRC_FILE, targetPath);
            intent.putExtra(IntentConstant.EXTRA_PLUGIN_INFO, (Parcelable) info);
            try {
                context.startService(intent);
            } catch (Exception e) {
                // java.lang.IllegalStateException: Not allowed to start service Intent, app is in background uid UidRecord
            }
        } else {
            // 4.2以上直接在当前进程安装
            sInstallerExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    handleInstall(context, targetPath, info, callBack);
                }
            });
        }
    }

    /**
     * 处理插件安装过程
     *
     * @param context  宿主的Context
     * @param srcFile  源路径
     * @param info     插件info信息
     * @param callBack 回调
     */
    static void handleInstall(Context context, String srcFile, PluginLiteInfo info, IInstallCallBack callBack) {
        PluginDebugLog.installFormatLog(TAG, "handleInstall srcFile:%s, info: %s", srcFile, info);
        if (srcFile.startsWith(PluginInstaller.SCHEME_ASSETS)) {
            installBuiltinApk(context, srcFile, info, callBack);
        } else if (srcFile.startsWith(PluginInstaller.SCHEME_SO)) {
            installSoPlugin(context, srcFile, info, callBack);
        } else if (srcFile.startsWith(PluginInstaller.SCHEME_DEX)) {
            installDexPlugin(context, srcFile, info, callBack);
        } else if (srcFile.startsWith(PluginInstaller.SCHEME_FILE)) {
            installApkFile(context, srcFile, info, callBack);
        } else {
            srcFile = PluginInstaller.SCHEME_FILE + srcFile;
            installApkFile(context, srcFile, info, callBack);
        }
    }

    /**
     * 安装so插件，解压释放压缩包中的so库到相应的目录
     * 压缩包按照apk格式：对应架构的so库放到lib/{arch}/目录下
     */
    private static void installSoPlugin(Context context, String srcFile,
                                        PluginLiteInfo info, IInstallCallBack callback) {
        String soZipPath = srcFile.substring(PluginInstaller.SCHEME_SO.length());
        File soZipFile = new File(soZipPath);
        if (!soZipFile.exists()) {
            setInstallFail(context, srcFile, ErrorType.INSTALL_ERROR_SO_NOT_EXIST, info, callback);
            return;
        }

        // 获取插件安装地址
        String packageName = info.packageName;
        String apkName = info.packageName + "." + info.pluginVersion + PluginInstaller.APK_SUFFIX;
        File destFile = getDefaultInstallLocation(context, apkName);
        int copyResult = tryCopyPluginFile(soZipFile, destFile);
        if (copyResult != ErrorType.SUCCESS) {
            setInstallFail(context, srcFile, copyResult, info, callback);
            return;
        }

        PluginDebugLog.installFormatLog(TAG,
                "installSoPlugin, begin install native lib, pkgName:%s", packageName);
        File pkgDir = new File(PluginInstaller.getPluginappRootPath(context), packageName);
        if (!pkgDir.exists() && !pkgDir.mkdirs()) {
            setInstallFail(context, srcFile, ErrorType.INSTALL_ERROR_MKDIR_FAILED, info, callback);
            return;
        }

        File libDir = new File(pkgDir, PluginInstaller.NATIVE_LIB_PATH);
        FileUtils.deleteDirectory(libDir);
        if (!libDir.exists() && !libDir.mkdirs()) {
            setInstallFail(context, srcFile, ErrorType.INSTALL_ERROR_MKDIR_FAILED, info, callback);
            return;
        }

        boolean extractResult = tryCopyNativeLib(context, destFile.getAbsolutePath(), libDir.getAbsolutePath());
        PluginDebugLog.installFormatLog(TAG, "installSoPlugin %s success: %s", packageName, extractResult);
        if (extractResult) {
            setInstallSuccess(context, srcFile, destFile.getAbsolutePath(), info, callback);
        } else {
            setInstallFail(context, srcFile, ErrorType.INSTALL_ERROR_SO_UNZIP_FAILED, info, callback);
        }
    }

    /**
     * 安装dex插件，只做拷贝
     * 压缩包结构按照apk格式：classes.dex, classes2.dex
     */
    private static void installDexPlugin(Context context, String srcFile,
                                         PluginLiteInfo info, IInstallCallBack callback) {
        String dexZipPath = srcFile.substring(PluginInstaller.SCHEME_DEX.length());
        File dexZipFile = new File(dexZipPath);
        if (!dexZipFile.exists()) {
            setInstallFail(context, srcFile, ErrorType.INSTALL_ERROR_DEX_NOT_EXIST, info, callback);
            return;
        }

        // 获取插件安装地址
        String packageName = info.packageName;
        String apkName = info.packageName + "." + info.pluginVersion + PluginInstaller.APK_SUFFIX;
        File destFile = getDefaultInstallLocation(context, apkName);
        int copyResult = tryCopyPluginFile(dexZipFile, destFile);
        if (copyResult != ErrorType.SUCCESS) {
            setInstallFail(context, srcFile, copyResult, info, callback);
            return;
        }

        PluginDebugLog.installFormatLog(TAG,
                "installDexPlugin, begin install native lib, pkgName:%s", packageName);
        String rootDir = PluginInstaller.getPluginappRootPath(context).getAbsolutePath();
        File pkgDir = new File(rootDir, packageName);
        if (!pkgDir.exists() && !pkgDir.mkdirs()) {
            setInstallFail(context, srcFile, ErrorType.INSTALL_ERROR_MKDIR_FAILED, info, callback);
            return;
        }

        File dexDir = new File(pkgDir, PluginInstaller.NATIVE_DEX_PATH);
        FileUtils.deleteDirectory(dexDir);
        if (!dexDir.exists() && !dexDir.mkdirs()) {
            setInstallFail(context, srcFile, ErrorType.INSTALL_ERROR_MKDIR_FAILED, info, callback);
            return;
        }
        // dexopt, 提前优化插件的dex
        PluginDebugLog.installFormatLog(TAG,
                "doInstall: began install dex,pkgName:%s", packageName);
        tryInstallNativeDex(destFile, packageName, rootDir);
        PluginDebugLog.installFormatLog(TAG, "installDexPlugin %s success", packageName);
        setInstallSuccess(context, srcFile, destFile.getAbsolutePath(), info, callback);
    }


    /**
     * 安装asset/pluginapp内置插件
     */
    private static void installBuiltinApk(Context context, String assetsPathWithScheme,
                                          PluginLiteInfo info, IInstallCallBack callback) {
        String assetsPath = assetsPathWithScheme.substring(PluginInstaller.SCHEME_ASSETS.length());
        PluginDebugLog.installFormatLog(TAG,
                "PluginInstallerService installBuildInApk assetsPath" + assetsPath);
        // 先把 asset 拷贝到临时文件
        InputStream is = null;
        try {
            is = context.getAssets().open(assetsPath);
            doInstall(context, is, assetsPathWithScheme, info, callback);

        } catch (IOException e) {
            setInstallFail(context, assetsPathWithScheme, e instanceof FileNotFoundException ?
                    ErrorType.INSTALL_ERROR_ASSET_APK_NOT_FOUND : ErrorType.INSTALL_ERROR_FILE_IOEXCEPTION, info, callback);
        } finally {
            FileUtils.closeQuietly(is);
        }
    }


    /**
     * 安装sd卡上的插件
     */
    private static void installApkFile(Context context, String apkFilePathWithScheme,
                                       PluginLiteInfo info, IInstallCallBack callback) {
        String apkFilePath = apkFilePathWithScheme.substring(PluginInstaller.SCHEME_FILE.length());
        PluginDebugLog.installFormatLog(TAG, "PluginInstallerService::installApkFile: %s", apkFilePath);

        File source = new File(apkFilePath);
        if (!source.exists()) {
            setInstallFail(context, apkFilePathWithScheme, ErrorType.INSTALL_ERROR_APK_NOT_EXIST, info, callback);
            return;
        }

        InputStream is = null;
        try {
            is = new FileInputStream(source);
            doInstall(context, is, apkFilePathWithScheme, info, callback);
        } catch (FileNotFoundException e) {
            setInstallFail(context, apkFilePathWithScheme, ErrorType.INSTALL_ERROR_APK_NOT_EXIST, info, callback);
        } finally {
            FileUtils.closeQuietly(is);
        }
    }

    /**
     * 安装某个插件
     *
     * @param context           上下文
     * @param is                输入流，文件流或asset流
     * @param srcPathWithScheme 文件路径
     * @param info              插件信息
     * @param callback          安装结果回调
     */
    private static void doInstall(Context context, InputStream is, String srcPathWithScheme,
                                  PluginLiteInfo info, IInstallCallBack callback) {

        if (is == null || TextUtils.isEmpty(srcPathWithScheme)) {
            PluginDebugLog.installLog(TAG, "doInstall: srcPathWithScheme or InputStream is null and just return!");
            setInstallFail(context, srcPathWithScheme, ErrorType.INSTALL_ERROR_STREAM_NULL,
                    info, callback);
            return;
        }

        PluginDebugLog.installFormatLog(TAG,
                "doInstall: %s, pkgName: %s", srcPathWithScheme, info.packageName);

        PackageManager pm = context.getPackageManager();
        String apkFilePath = null;
        if (srcPathWithScheme.startsWith(PluginInstaller.SCHEME_FILE)) {
            apkFilePath = srcPathWithScheme.substring(PluginInstaller.SCHEME_FILE.length());
        } else if (srcPathWithScheme.startsWith(PluginInstaller.SCHEME_ASSETS)) {
            // 解压拷贝Asset目录下的插件
            File tempFile = new File(PluginInstaller.getPluginappRootPath(context), System.currentTimeMillis() + ".tmp");
            boolean result = FileUtils.copyToFile(is, tempFile);
            PluginDebugLog.installLog(TAG, "doInstall copy result" + result);
            if (!result) {
                tempFile.delete();
                setInstallFail(context, srcPathWithScheme, ErrorType.INSTALL_ERROR_ASSET_APK_COPY_FAILED, info, callback);
                return;
            }
            apkFilePath = tempFile.getAbsolutePath();
        }
        if (null == apkFilePath) {
            setInstallFail(context, srcPathWithScheme, ErrorType.INSTALL_ERROR_FILE_PATH_ILLEGAL, info, callback);
            return;
        }
        // 再次校验源文件是否存在
        File srcApkFile = new File(apkFilePath);
        if (!srcApkFile.exists()) {
            setInstallFail(context, srcPathWithScheme, ErrorType.INSTALL_ERROR_APK_NOT_EXIST, info, callback);
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
            setInstallFail(context, srcPathWithScheme, ErrorType.INSTALL_ERROR_APK_PARSE_FAILED, info, callback);
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

        if (!TextUtils.equals(info.packageName, pkgInfo.packageName)) {
            PluginDebugLog.installLog(TAG, "doInstall with apk packageName not match with plugin name, " + packageName);
            setInstallFail(context, srcPathWithScheme, ErrorType.INSTALL_ERROR_PKG_NAME_NOT_MATCH, info, callback);
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
                setInstallFail(context, srcPathWithScheme, ErrorType.INSTALL_ERROR_PKG_NAME_NOT_MATCH, info, callback);
                return;
            }
        }
        // 获取插件安装地址
        String apkName = packageName + "." + info.pluginVersion + PluginInstaller.APK_SUFFIX;
        File destFile = getPreferredInstallLocation(context, pkgInfo, apkName);
        int copyResult = tryCopyPluginFile(srcApkFile, destFile);
        if (copyResult != ErrorType.SUCCESS) {
            setInstallFail(context, srcPathWithScheme, copyResult, info, callback);
            return;
        }

        PluginDebugLog.installFormatLog(TAG,
                "doInstall: begin install native lib, pkgName:%s", packageName);
        String rootDir = PluginInstaller.getPluginappRootPath(context).getAbsolutePath();
        File pkgDir = new File(rootDir, packageName);
        if (!pkgDir.exists() && !pkgDir.mkdirs()) {
            setInstallFail(context, srcPathWithScheme, ErrorType.INSTALL_ERROR_MKDIR_FAILED, info, callback);
            return;
        }

        File libDir = new File(pkgDir, PluginInstaller.NATIVE_LIB_PATH);
        if (!libDir.exists() && !libDir.mkdirs()) {
            setInstallFail(context, srcPathWithScheme, ErrorType.INSTALL_ERROR_MKDIR_FAILED, info, callback);
            return;
        }

        tryCopyNativeLib(context, destFile.getAbsolutePath(), libDir.getAbsolutePath());
        PluginDebugLog.installFormatLog(TAG,
                "doInstall: finish install lib,pkgName:%s", packageName);
        // dexopt, 提前优化插件的dex
        PluginDebugLog.installFormatLog(TAG,
                "doInstall: began install dex,pkgName:%s", packageName);
        tryInstallNativeDex(destFile, packageName, rootDir);
        PluginDebugLog.installFormatLog(TAG,
                "doInstall: finish install dex,pkgName:%s", packageName);
        // dexoat结束之后，再通知插件安装完成
        setInstallSuccess(context, srcPathWithScheme, destFile.getAbsolutePath(), info, callback);
    }

    /**
     * 拷贝插件文件，如果在相同文件夹下面，直接rename，否则copy
     * @param srcFile  插件源文件
     * @param destFile  插件目标文件
     * @return 0: copy成功，其他：对应的错误码
     */
    private static int tryCopyPluginFile(File srcFile, File destFile) {
        int copyResult = ErrorType.SUCCESS;
        if (destFile.exists()) {
            destFile.delete();
        }

        if (TextUtils.equals(srcFile.getParent(), destFile.getParent())) {
            // 目标文件和临时文件在同一目录下
            PluginDebugLog.installLog(TAG,
                    "tryCopyPluginFile: tmpFile and destFile in same directory!");
            if (!srcFile.renameTo(destFile)) {
                // rename失败，尝试拷贝
                boolean tempResult = FileUtils.copyToFile(srcFile, destFile);
                if (!tempResult) {
                    PluginDebugLog.installLog(TAG, "tryCopyPluginFile: copy apk failed!");
                    copyResult = ErrorType.INSTALL_ERROR_RENAME_FAILED;
                } else {
                    srcFile.delete();
                }
            }
        } else {
            // 拷贝到其他目录，比如安装到 sdcard
            PluginDebugLog.installLog(TAG,
                    "tryCopyPluginFile: tmpFile and destFile in different directory!");
            boolean tempResult = FileUtils.copyToFile(srcFile, destFile);
            if (!tempResult) {
                PluginDebugLog.installLog(TAG, "tryCopyPluginFile: copy apk failed!");
                copyResult = ErrorType.INSTALL_ERROR_APK_COPY_FAILED;
            }
        }
        return copyResult;
    }

    /**
     * 拷贝释放so库到对应的lib目录
     *
     * @param context 上下文
     * @param apkPath 插件apk文件
     * @param libDir  插件lib目录
     */
    private static boolean tryCopyNativeLib(Context context, String apkPath, String libDir) {
        // 拷贝Native SO库
        return FileUtils.installNativeLibrary(context, apkPath, libDir);
    }

    /**
     * 执行dexopt优化dex，提高插件运行速度
     *
     * @param apkFile  插件apk文件
     * @param pkgName  插件包名
     * @param pkgDirPath  dexopt目录
     */
    private static void tryInstallNativeDex(File apkFile, String pkgName, String pkgDirPath) {
        // 进行dexopt优化dex
        FileUtils.installDex(apkFile, pkgName, pkgDirPath);
    }


    /**
     * 回调安装失败结果
     *
     * @param context           上下文
     * @param srcPathWithScheme 安装文件路径
     * @param failReason        失败原因
     * @param info              安装插件信息
     * @param callback          安装结果回调
     */
    private static void setInstallFail(Context context, String srcPathWithScheme, int failReason,
                                       @NonNull PluginLiteInfo info, IInstallCallBack callback) {
        info.srcApkPath = "";
        info.installStatus = PluginLiteInfo.PLUGIN_UNINSTALLED;

        PluginDebugLog.installLog(TAG, "Send setInstallFail with reason: " + failReason + " PluginPackageInfo: " + info);
        if (callback != null) {
            try {
                callback.onPackageInstallFail(info, failReason);
                return;
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        // 发生异常了，发送广播
        Intent intent = new Intent(PluginPackageManager.ACTION_PACKAGE_INSTALLFAIL);
        intent.setPackage(context.getPackageName());
        intent.putExtra(IntentConstant.EXTRA_PKG_NAME, info.packageName);
        intent.putExtra(IntentConstant.EXTRA_SRC_FILE, srcPathWithScheme);// 同时返回安装前的安装文件目录。
        intent.putExtra(ErrorType.ERROR_REASON, failReason);               // 同时返回安装失败的原因
        intent.putExtra(IntentConstant.EXTRA_PLUGIN_INFO, (Parcelable) info);// 同时返回APK的插件信息
        context.sendBroadcast(intent);
    }

    /**
     * 发送安装成功的广播
     *
     * @param context           上下文
     * @param srcPathWithScheme 安装文件路径
     * @param destPath          安装后apk文件路径
     * @param info              安装插件信息
     * @param callback          安装结果回调
     */
    private static void setInstallSuccess(Context context, String srcPathWithScheme,
                                          String destPath, @NonNull PluginLiteInfo info, IInstallCallBack callback) {
        info.srcApkPath = destPath;
        info.installStatus = PluginLiteInfo.PLUGIN_INSTALLED;

        PluginDebugLog.installLog(TAG, "Send setInstallSuccess PluginPackageInfo: " + info);
        if (callback != null) {
            try {
                callback.onPackageInstalled(info);
                return;
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        // 发生异常，发送广播
        Intent intent = new Intent(PluginPackageManager.ACTION_PACKAGE_INSTALLED);
        intent.setPackage(context.getPackageName());
        intent.putExtra(IntentConstant.EXTRA_PKG_NAME, info.packageName);
        intent.putExtra(IntentConstant.EXTRA_SRC_FILE, srcPathWithScheme);// 同时返回安装前的安装文件目录。
        intent.putExtra(IntentConstant.EXTRA_DEST_FILE, destPath);        // 同时返回安装后的安装文件目录。
        intent.putExtra(IntentConstant.EXTRA_PLUGIN_INFO, (Parcelable) info);// 同时返回APK的插件信息
        context.sendBroadcast(intent);
    }

    /**
     * 获取插件安装目录，内置目录或者SD卡目录
     * 目前VR插件是安装在SD的/Android/data目录的
     */
    private static File getPreferredInstallLocation(Context context, PackageInfo pkgInfo, String apkName) {

        int installLocation = ReflectionUtils.on(pkgInfo).<Integer>get("installLocation");
        PluginDebugLog.installFormatLog(TAG, "plugin apk %s, installLocation: %s,", apkName, installLocation);

        // see PackageInfo.INSTALL_LOCATION_PREFER_EXTERNAL
        final int INSTALL_LOCATION_PREFER_EXTERNAL = 2;
        boolean preferExternal = installLocation == INSTALL_LOCATION_PREFER_EXTERNAL;

        // 查看外部存储器是否可用
        if (preferExternal) {
            String state = Environment.getExternalStorageState();
            if (!Environment.MEDIA_MOUNTED.equals(state)) {   // 不可用
                preferExternal = false;
            }
        }

        if (preferExternal) {
            // 尝试安装到外部存储器
            File externalDir = context.getExternalFilesDir(PluginInstaller.PLUGIN_ROOT_PATH);
            if (externalDir != null && externalDir.exists()) {
                File destFile = new File(externalDir, apkName);
                PluginDebugLog.installFormatLog(TAG, "install to Location %s", destFile.getPath());
                return destFile;
            }
        }
        // 返回默认安装位置
        return getDefaultInstallLocation(context, apkName);
    }

    /**
     * 获取默认的安装目录
     */
    private static File getDefaultInstallLocation(Context context, String apkName) {
        // 默认安装到内置internal data dir
        File destFile = new File(PluginInstaller.getPluginappRootPath(context), apkName);
        PluginDebugLog.installFormatLog(TAG, "install to Location %s:", destFile.getPath());
        return destFile;
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
        String mapPkgName = filePath.substring(start + 1, end);
        PluginDebugLog.runtimeFormatLog(TAG, "filePath: %s, pkgName: ", filePath, mapPkgName);
        return mapPkgName;
    }
}
