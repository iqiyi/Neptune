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
package org.qiyi.pluginlibrary;

import android.annotation.SuppressLint;
import android.app.ActivityThread;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;

import org.qiyi.pluginlibrary.component.wraper.NeptuneInstrument;
import org.qiyi.pluginlibrary.component.wraper.PluginInstrument;
import org.qiyi.pluginlibrary.install.IInstallCallBack;
import org.qiyi.pluginlibrary.pm.IPluginUninstallCallBack;
import org.qiyi.pluginlibrary.pm.PluginLiteInfo;
import org.qiyi.pluginlibrary.pm.PluginPackageManagerNative;
import org.qiyi.pluginlibrary.runtime.PluginManager;
import org.qiyi.pluginlibrary.utils.PluginDebugLog;
import org.qiyi.pluginlibrary.utils.ReflectionUtils;
import org.qiyi.pluginlibrary.utils.VersionUtils;

import java.io.File;

/**
 * Neptune对外暴露的统一调用类
 */
public class Neptune {
    private static final String TAG = "Neptune";

    public static final boolean SEPARATED_CLASSLOADER = true;
    public static final boolean NEW_COMPONENT_PARSER = true;

    @SuppressLint("StaticFieldLeak")
    private static Context sHostContext;

    private static NeptuneConfig sGlobalConfig;

    private static Instrumentation mHostInstr;

    private Neptune() {
    }

    /**
     * 初始化Neptune插件环境
     *
     * @param application  宿主的Appliction
     * @param config  配置信息
     */
    public static void init(Application application, NeptuneConfig config) {

        sHostContext = application;
        sGlobalConfig = config != null ? config
                : new NeptuneConfig.NeptuneConfigBuilder().build();

        PluginDebugLog.setIsDebug(sGlobalConfig.isDebug());

        boolean hookInstr = VersionUtils.hasPie() || sGlobalConfig.getSdkMode() != NeptuneConfig.LEGACY_MODE;
        if (hookInstr) {
            hookInstrumentation();
        }

        // 调用getInstance()方法会初始化bindService
        PluginPackageManagerNative.getInstance(sHostContext).setPackageInfoManager(sGlobalConfig.getPluginInfoProvider());
        // 注册卸载监听广播
        PluginManager.registerUninstallReceiver(sHostContext);
    }

    public static Context getHostContext() {
        return sHostContext;
    }

    public static NeptuneConfig getConfig() {
        return sGlobalConfig;
    }


    /**
     * 反射替换ActivityThread的mInstrumentation
     */
    private static void hookInstrumentation() {

        PluginDebugLog.runtimeLog(TAG, "need to hook Instrumentation for plugin framework");
        ActivityThread activityThread = ActivityThread.currentActivityThread();
        Instrumentation hostInstr = getHostInstrumentation();

        if (hostInstr != null) {
            String hostInstrName = hostInstr.getClass().getName();
            PluginDebugLog.runtimeLog(TAG, "host Instrument name: " + hostInstrName);

            if (hostInstrName.startsWith("com.chaozhuo.superme")
                    || hostInstrName.startsWith("com.lody.virtual")) {
                // warning: 特殊case，VirtualApp环境，暂不Hook
                PluginDebugLog.runtimeLog(TAG, "reject hook instrument, run in VirtualApp Environment");
            } else if (hostInstr instanceof NeptuneInstrument) {
                // already hooked
                PluginDebugLog.runtimeLog(TAG, "ActivityThread Instrumentation already hooked");
            } else {
                PluginInstrument pluginInstrument = new NeptuneInstrument(hostInstr);
                ReflectionUtils.on(activityThread).set("mInstrumentation", pluginInstrument);
                PluginDebugLog.runtimeLog(TAG, "init hook ActivityThread Instrumentation success");
            }
        } else {
            PluginDebugLog.runtimeLog(TAG, "init hook ActivityThread Instrumentation failed, hostInstr==null");
        }
    }

    /**
     * 获取ActivityThread的Instrumentation对象
     */
    public static Instrumentation getHostInstrumentation() {

        if (mHostInstr == null) {
            ActivityThread activityThread = ActivityThread.currentActivityThread();
            Instrumentation hostInstr = activityThread.getInstrumentation();
            mHostInstr = PluginInstrument.unwrap(hostInstr);
        }

        return mHostInstr;
    }

    /**
     * 安装sd卡上的插件
     *
     * @param context 宿主的Context
     * @param apkPath 插件apk路径
     */
    public static void install(Context context, String apkPath) {
        install(context, apkPath, null);
    }

    /**
     * 安装sd上的插件
     *
     * @param context  宿主的Context
     * @param apkPath  插件apk路径
     * @param callBack 安装回调
     */
    public static void install(Context context, String apkPath, IInstallCallBack callBack) {

        File apkFile = new File(apkPath);
        if (!apkFile.exists()) {
            return;
        }

        PluginLiteInfo liteInfo = new PluginLiteInfo();
        Context mContext = ensureContext(context);
        PackageInfo packageInfo = mContext.getPackageManager()
                .getPackageArchiveInfo(apkPath, 0);
        if (packageInfo != null) {
            liteInfo.mPath = apkPath;
            liteInfo.packageName = packageInfo.packageName;
            liteInfo.pluginVersion = packageInfo.versionName;
            install(mContext, liteInfo, callBack);
        }
    }

    /**
     * 安装一个插件
     *
     * @param context  宿主的Context
     * @param info     插件的信息，包括包名，路径等
     * @param callBack 安装回调
     */
    public static void install(Context context, PluginLiteInfo info, IInstallCallBack callBack) {
        // install
        Context mContext = ensureContext(context);
        PluginPackageManagerNative.getInstance(mContext).install(info, callBack);
    }


    /**
     * 根据包名卸载一个插件
     *
     * @param context  宿主的Context
     * @param pkgName  待卸载插件的包名
     */
    public static void uninstall(Context context, String pkgName) {
        uninstall(context, pkgName, null);
    }


    /**
     * 根据包名卸载一个插件
     *
     * @param context  宿主的Context
     * @param pkgName  待卸载插件的包名
     * @param callBack  卸载回调
     */
    public static void uninstall(Context context, String pkgName, IPluginUninstallCallBack callBack) {
        Context mContext = ensureContext(context);
        PluginLiteInfo info = PluginPackageManagerNative.getInstance(mContext).getPackageInfo(pkgName);
        if (info != null) {
            uninstall(mContext, info, callBack);
        }
    }

    /**
     * 卸载一个插件
     *
     * @param context 宿主的Context
     * @param info    待卸载插件的信息，包括包名，路径等
     * @param callBack 卸载回调
     */
    public static void uninstall(Context context, PluginLiteInfo info, IPluginUninstallCallBack callBack) {
        // uninstall
        Context mContext = ensureContext(context);
        PluginPackageManagerNative.getInstance(mContext).uninstall(info, callBack);
    }

    /**
     * 启动一个插件的入口类
     *
     * @param mHostContext  宿主的Context
     * @param pkgName  待启动插件的包名
     */
    public static void launchPlugin(Context mHostContext, String pkgName) {
        // start plugin
        PluginManager.launchPlugin(mHostContext, pkgName);
    }

    /**
     * 根据Intent启动一个插件
     *
     * @param mHostContext  宿主的Context
     * @param intent  需要启动插件的Intent
     */
    public static void launchPlugin(Context mHostContext, Intent intent) {
        // start plugin, 默认选择进程
        PluginManager.launchPlugin(mHostContext, intent, null);
    }

    /**
     * 根据Intent启动一个插件，指定运行进程的名称
     *
     * @param mHostContext  宿主的Context
     * @param intent  需要启动插件的Intent
     * @param processName  指定启动插件运行的进程
     */
    public static void launchPlugin(Context mHostContext, Intent intent, String processName) {
        // start plugin, 指定进程
        PluginManager.launchPlugin(mHostContext, intent, processName);
    }

    /**
     * 判断插件是否安装
     *
     * @param context  宿主的Context
     * @param pkgName  插件的包名
     * @return  插件已安装， 返回true; 插件未安装，返回false
     */
    public static boolean isPackageInstalled(Context context, String pkgName) {

        Context mContext = ensureContext(context);
        return PluginPackageManagerNative.getInstance(mContext).isPackageInstalled(pkgName);
    }

    /**
     * 判断插件是否可用
     *
     * @param context  宿主的Context
     * @param pkgName  插件的包名
     * @return  插件是可用的，返回true; 插件不可用，返回false
     */
    public static boolean isPackageAvailable(Context context, String pkgName) {

        Context mContext = ensureContext(context);
        return PluginPackageManagerNative.getInstance(mContext).isPackageAvailable(pkgName);
    }

    /**
     * 获取插件PluginLiteInfo
     *
     * @param context  宿主的Context
     * @param pkgName  插件的包名
     * @return  插件的信息
     */
    public static PluginLiteInfo getPluginInfo(Context context, String pkgName) {

        Context mContext = ensureContext(context);
        return PluginPackageManagerNative.getInstance(mContext).getPackageInfo(pkgName);
    }

    private static Context ensureContext(Context originContext) {
        if (originContext != null) {
            return originContext;
        }
        return sHostContext;
    }
}
