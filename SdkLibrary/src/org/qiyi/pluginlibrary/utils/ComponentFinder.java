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
package org.qiyi.pluginlibrary.utils;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.TypedValue;

import org.qiyi.pluginlibrary.component.processmgr.ProcessManager;
import org.qiyi.pluginlibrary.constant.IntentConstant;
import org.qiyi.pluginlibrary.pm.PluginLiteInfo;
import org.qiyi.pluginlibrary.pm.PluginPackageInfo;
import org.qiyi.pluginlibrary.pm.PluginPackageManagerNative;
import org.qiyi.pluginlibrary.runtime.PluginLoadedApk;
import org.qiyi.pluginlibrary.runtime.PluginManager;

import java.util.List;

/**
 * 在{@link PluginLoadedApk}代表的插件中查找能够处理{@link Intent}的组件
 * 并设置组件代理,支持显式和隐式查找
 */
public class ComponentFinder {
    public static final String DEFAULT_ACTIVITY_PROXY_PREFIX =
            "org.qiyi.pluginlibrary.component.InstrActivityProxy";
    public static final String DEFAULT_TRANSLUCENT_ACTIVITY_PROXY_PREFIX =
            "org.qiyi.pluginlibrary.component.InstrActivityProxyTranslucent";
    public static final String DEFAULT_LANDSCAPE_ACTIVITY_PROXY_PREFIX =
            "org.qiyi.pluginlibrary.component.InstrActivityProxyLandscape";
    public static final String DEFAULT_CONFIGCHANGE_ACTIVITY_PROXY_PREFIX =
            "org.qiyi.pluginlibrary.component.InstrActivityProxyHandleConfigChange";
    public static final String DEFAULT_TASK_AFFINITY_ACTIVITY_PROXY_PREFIX =
            "org.qiyi.pluginlibrary.component.InstrActivityProxySingleTask";
    public static final String DEFAULT_SERVICE_PROXY_PREFIX =
            "org.qiyi.pluginlibrary.component.ServiceProxy";
    public static final int TRANSLUCENTCOLOR = Color.parseColor("#00000000");
    private static final String TAG = "ComponentFinder";

    /**
     * 在插件中查找可以处理mIntent的Service组件,找到之后为其分配合适的Proxy
     *
     * @param mLoadedApk 插件的{@link PluginLoadedApk}对象
     * @param mIntent    需要查找的Intent
     */
    public static void switchToServiceProxy(PluginLoadedApk mLoadedApk, Intent mIntent) {
        if (mIntent == null || mLoadedApk == null) {
            return;
        }

        if (mIntent.getComponent() == null) {
            //隐式启动
            ServiceInfo mServiceInfo = mLoadedApk.getPluginPackageInfo().resolveService(mIntent);
            if (mServiceInfo != null) {
                switchToServiceProxy(mLoadedApk, mIntent, mServiceInfo.name);
            }
        } else {
            //显示启动
            String targetService = mIntent.getComponent().getClassName();
            switchToServiceProxy(mLoadedApk, mIntent, targetService);
        }
    }


    /**
     * 在插件中查找可以处理mIntent的Service组件,找到之后为其分配合适的Proxy
     *
     * @param mLoadedApk    插件的{@link PluginLoadedApk}对象
     * @param mIntent       需要查找的Intent
     * @param targetService 处理当前Intent的组件的类名
     */
    public static void switchToServiceProxy(PluginLoadedApk mLoadedApk,
                                            Intent mIntent,
                                            String targetService) {
        if (null == mLoadedApk
                || null == mIntent
                || TextUtils.isEmpty(targetService)
                || mLoadedApk.getPluginPackageInfo() == null
                || mLoadedApk.getPluginPackageInfo().getServiceInfo(targetService) == null) {
            return;
        }
        mIntent.setExtrasClassLoader(mLoadedApk.getPluginClassLoader());
        mIntent.addCategory(IntentConstant.EXTRA_TARGET_CATEGORY + System.currentTimeMillis())
                .putExtra(IntentConstant.EXTRA_TARGET_IS_PLUGIN_KEY, true)
                .putExtra(IntentConstant.EXTRA_TARGET_CLASS_KEY, targetService)
                .putExtra(IntentConstant.EXTRA_TARGET_PACKAGE_KEY, mLoadedApk.getPluginPackageName());
        try {
            mIntent.setClass(mLoadedApk.getHostContext(),
                    Class.forName(matchServiceProxyByFeature(mLoadedApk.getProcessName())));
            String intentInfo = mIntent.toString();
            if (null != mIntent.getExtras()) {
                intentInfo = intentInfo + mIntent.getExtras().toString();
            }
            PluginDebugLog.runtimeLog(TAG, "switchToServiceProxy intent info: " + intentInfo);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }


    /**
     * 在插件中查找可以处理mIntent的Activity组件,找到之后为其分配合适的Proxy
     *
     * @param mPluginPackageName  插件包名
     * @param mIntent  跳转Activity的Intent
     * @param requestCode 请求码
     * @param context 宿主的Context
     * @return 处理后的Intent
     */
    public static Intent switchToActivityProxy(String mPluginPackageName,
                                               Intent mIntent,
                                               int requestCode,
                                               Context context) {

        if (mIntent == null) {
            PluginDebugLog.runtimeLog(TAG, "handleStartActivityIntent intent is null!");
            return mIntent;
        }

        if (TextUtils.isEmpty(mPluginPackageName)) {
            // Note: 宿主的Instrumentation会执行到这里, 不能做拦截, 导致本来要启动独立App的逻辑变成启动插件
            PluginDebugLog.runtimeLog(TAG, "handleStartActivityIntent mPluginPackageName is null");
            return mIntent;
        }

        PluginDebugLog.runtimeLog(TAG, "handleStartActivityIntent: pluginId: "
                + mPluginPackageName + ", intent: " + mIntent
                + ", requestCode: " + requestCode);
        if (hasProxyActivity(mIntent)) {
            PluginDebugLog.runtimeLog(TAG,
                    "handleStartActivityIntent has change the intent just return");
            return mIntent;
        }

        ActivityInfo targetActivity = null;
        PluginLoadedApk mLoadedApk = PluginManager.getPluginLoadedApkByPkgName(mPluginPackageName);
        if (mIntent.getComponent() != null
                && !TextUtils.isEmpty(mIntent.getComponent().getClassName())) {
            // action 为空，但是指定了包名和 activity类名
            ComponentName compName = mIntent.getComponent();
            String pkg = compName.getPackageName();
            String toActName = compName.getClassName();
            if (mLoadedApk != null) {
                if (TextUtils.equals(pkg, mPluginPackageName)
                        || TextUtils.equals(pkg, mLoadedApk.getHostPackageName())) {
                    PluginPackageInfo mPlugin = mLoadedApk.getPluginPackageInfo();
                    targetActivity = mPlugin.getActivityInfo(toActName);
                    if (targetActivity != null) {
                        PluginDebugLog.runtimeLog(TAG,
                                "switchToActivityProxy find targetActivity in current currentPlugin!");
                    }
                }
            } else {
                if (!TextUtils.isEmpty(pkg)) {
                    PluginPackageInfo otherPluginInfo = PluginPackageManagerNative.getInstance(context)
                            .getPluginPackageInfo(pkg);
                    if (otherPluginInfo != null) {
                        targetActivity = otherPluginInfo.getActivityInfo(toActName);
                        if (targetActivity != null) {
                            PluginDebugLog.runtimeFormatLog(TAG,
                                    "switchToActivityProxy find targetActivity in plugin %s!", pkg);
                        }
                    }
                }
            }
        } else {
            if (mLoadedApk != null) {
                PluginPackageInfo info = mLoadedApk.getPluginPackageInfo();
                if (info != null) {
                    targetActivity = info.resolveActivity(mIntent);
                }
            } else {
                if (null != context) {
                    List<PluginLiteInfo> packageList =
                            PluginPackageManagerNative.getInstance(context).getInstalledApps();
                    if (packageList != null) {
                        for (PluginLiteInfo pkgInfo : packageList) {
                            if (pkgInfo != null) {
                                PluginPackageInfo target = PluginPackageManagerNative.getInstance(context)
                                        .getPluginPackageInfo(context, pkgInfo);
                                if (null != target) {
                                    targetActivity = target.resolveActivity(mIntent);
                                    if (targetActivity != null) {
                                        PluginDebugLog.runtimeFormatLog(TAG,
                                                "switchToActivityProxy find targetActivity in plugin %s!", pkgInfo.packageName);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        PluginDebugLog.runtimeLog(TAG, "handleStartActivityIntent pluginId: "
                + mPluginPackageName + " intent: " + mIntent.toString()
                + " targetActivity: " + targetActivity);
        if (targetActivity != null) {
            setActivityProxy(mIntent, targetActivity);
        }
        if (mLoadedApk != null) {
            mLoadedApk.getActivityStackSupervisor().dealLaunchMode(mIntent);
        }
        String intentInfo = mIntent.toString();
        if (null != mIntent.getExtras()) {
            intentInfo = intentInfo + mIntent.getExtras().toString();
        }
        return mIntent;

    }

    /**
     * 判断Intent代表的Activity组件是否已经设置代理，如果已经设置，直接返回
     *
     * @param mIntent Activity对应的Intent
     * @return true:已经设置代理，false：没有设置
     */
    private static boolean hasProxyActivity(Intent mIntent) {
        if (mIntent != null && mIntent.getComponent() != null
                && !TextUtils.isEmpty(IntentUtils.getTargetClass(mIntent))
                && !TextUtils.isEmpty(IntentUtils.getTargetPackage(mIntent))) {
            if (mIntent.getComponent().getClassName().startsWith(ComponentFinder.DEFAULT_ACTIVITY_PROXY_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 为插件中的Activity 设置代理
     *
     * @param mIntent        需要设置代理的Activity的Intent
     * @param targetActivity 目标的ActivityInfo，包含插件包名和跳转Activity的名称
     */
    private static void setActivityProxy(Intent mIntent, ActivityInfo targetActivity) {
        String mPackageName = targetActivity.packageName;
        String activityName = targetActivity.name;
        PluginLoadedApk mLoadedApk = PluginManager.getPluginLoadedApkByPkgName(targetActivity.packageName);
        if (null == mLoadedApk) {
            PluginDebugLog.runtimeFormatLog(TAG,
                    "setActivityProxy failed, %s, PluginLoadedApk is null",
                    targetActivity.packageName);
            return;
        }
        ComponentName compName = new ComponentName(mLoadedApk.getHostPackageName(),
                findActivityProxy(mLoadedApk, targetActivity));
        mIntent.setExtrasClassLoader(mLoadedApk.getPluginClassLoader());
        mIntent.setComponent(compName)
                .addCategory(activityName)
                .putExtra(IntentConstant.EXTRA_TARGET_IS_PLUGIN_KEY, true)
                .putExtra(IntentConstant.EXTRA_TARGET_PACKAGE_KEY, mPackageName)
                .putExtra(IntentConstant.EXTRA_TARGET_CLASS_KEY, activityName);
        IntentUtils.setProxyInfo(mIntent, mPackageName);
    }

    /**
     * 为插件中的Activity分配代理
     *
     * @param mLoadedApk 插件的实例
     * @param actInfo    插件Activity对应的ActivityInfo
     * @return  返回代理Activity的类名
     */
    public static String findActivityProxy(PluginLoadedApk mLoadedApk, ActivityInfo actInfo) {
        boolean isTranslucent = false;
        boolean isHandleConfigChange = false;
        boolean isLandscape = false;
        boolean hasTaskAffinity = false;

        //通过主题判断是否是透明的
        Resources.Theme mTheme = mLoadedApk.getPluginTheme();
        mTheme.applyStyle(actInfo.getThemeResource(), true);
        TypedArray array = mTheme.obtainStyledAttributes(new int[]{
                android.R.attr.windowIsTranslucent,
        });
        boolean attr_0 = array.getBoolean(0, false);
        array.recycle();
        try {
            TypedValue tv = new TypedValue();
            mTheme.resolveAttribute(android.R.attr.windowBackground, tv, true);
            if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                PluginDebugLog.runtimeFormatLog(TAG, "windowBackground is color and is translucent:%s",
                        (tv.data == TRANSLUCENTCOLOR));
                isTranslucent = attr_0 && (tv.data == TRANSLUCENTCOLOR);
            } else {
                PluginDebugLog.runtimeFormatLog(TAG, "windowBackground is drawable!");
                isTranslucent = false;
            }
        } catch (Exception e) {
            PluginDebugLog.runtimeFormatLog(TAG, "windowBackground read exception!");
            isTranslucent = attr_0;
        }
        if (!isTranslucent) {
            //兼容遗留逻辑
            if (actInfo.metaData != null) {
                String special_cfg = actInfo.metaData.getString(IntentConstant.META_KEY_ACTIVITY_SPECIAL);
                if (!TextUtils.isEmpty(special_cfg)) {
                    if (special_cfg.contains(IntentConstant.PLUGIN_ACTIVITY_TRANSLUCENT)) {
                        PluginDebugLog.runtimeLog(TAG,
                                "findActivityProxy meta data contains translucent flag");
                        isTranslucent = true;
                    }

                    if (special_cfg.contains(IntentConstant.PLUGIN_ACTIVTIY_HANDLE_CONFIG_CHAGNE)) {
                        PluginDebugLog.runtimeLog(TAG,
                                "findActivityProxy meta data contains handleConfigChange flag");
                        isHandleConfigChange = true;
                    }
                }
            }
        }

        if (TextUtils.equals(actInfo.taskAffinity,
                mLoadedApk.getPluginPackageName() + IntentConstant.TASK_AFFINITY_CONTAINER)
                && actInfo.launchMode == ActivityInfo.LAUNCH_SINGLE_TASK) {
            PluginDebugLog.runtimeLog(TAG, "findActivityProxy activity taskAffinity: "
                    + actInfo.taskAffinity + " hasTaskAffinity = true");
            hasTaskAffinity = true;
        }

        if (actInfo.screenOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            PluginDebugLog.runtimeLog(TAG, "findActivityProxy activity screenOrientation: "
                    + actInfo.screenOrientation + " isHandleConfigChange = false");
            isHandleConfigChange = false;
        }

        if (actInfo.screenOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            PluginDebugLog.runtimeLog(TAG, "findActivityProxy isLandscape = true");
            isLandscape = true;
        }

        return matchActivityProxyByFeature(hasTaskAffinity, isTranslucent, isLandscape,
                isHandleConfigChange, mLoadedApk.getProcessName());
    }

    /**
     * 根据被代理的Activity的Feature和进程名称选择代理
     *
     * @param hasTaskAffinity 是否独立任务栈
     * @param isTranslucent   是否透明
     * @param isLandscape     是否横屏
     * @param isHandleConfig  配置变化是否仅仅执行onConfiguration方法
     * @param mProcessName    当前插件运行的进程名称
     * @return 代理Activity的名称
     */
    private static String matchActivityProxyByFeature(
            boolean hasTaskAffinity,
            boolean isTranslucent,
            boolean isLandscape,
            boolean isHandleConfig,
            String mProcessName) {
        int index = ProcessManager.getProcessIndex(mProcessName);
        if (index < 0 || index > 2) {
            //越界检查
            PluginDebugLog.log(TAG, "matchActivityProxyByFeature index is out of bounds!");
            index = 1;
        }

        String proxyActivityName;
        if (hasTaskAffinity) {
            proxyActivityName = ComponentFinder.DEFAULT_TASK_AFFINITY_ACTIVITY_PROXY_PREFIX + index;
        } else if (isTranslucent) {
            proxyActivityName = ComponentFinder.DEFAULT_TRANSLUCENT_ACTIVITY_PROXY_PREFIX + index;
        } else if (isLandscape) {
            proxyActivityName = ComponentFinder.DEFAULT_LANDSCAPE_ACTIVITY_PROXY_PREFIX + index;
        } else if (isHandleConfig) {
            proxyActivityName = ComponentFinder.DEFAULT_CONFIGCHANGE_ACTIVITY_PROXY_PREFIX + index;
        } else {
            proxyActivityName = ComponentFinder.DEFAULT_ACTIVITY_PROXY_PREFIX + index;
        }

        PluginDebugLog.runtimeFormatLog(TAG, "matchActivityProxyByFeature:%s",
                ComponentFinder.DEFAULT_TASK_AFFINITY_ACTIVITY_PROXY_PREFIX + index);
        return proxyActivityName;
    }

    /**
     * 通过进程名称匹配ServiceProxy
     *
     * @param processName 进程名称
     * @return Service代理名称
     */
    public static String matchServiceProxyByFeature(String processName) {
        String proxyServiceName =
                DEFAULT_SERVICE_PROXY_PREFIX + ProcessManager.getProcessIndex(processName);
        PluginDebugLog.runtimeFormatLog(TAG, "matchServiceProxyByFeature:%s", proxyServiceName);
        return proxyServiceName;
    }
}
