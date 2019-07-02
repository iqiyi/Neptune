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
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
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
import java.util.Map;

/**
 * 在{@link PluginLoadedApk}代表的插件中查找能够处理{@link Intent}的组件
 * 并设置组件代理,支持显式和隐式查找
 */
public class ComponentFinder {
    private static final String TAG = "ComponentFinder";
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
    public static final String DEFAULT_PICTURE_IN_PICTURE_ACTIVITY_PROXY_PREFIX =
            "org.qiyi.pluginlibrary.component.InstrActivityProxyPip";
    public static final String DEFAULT_SERVICE_PROXY_PREFIX =
            "org.qiyi.pluginlibrary.component.ServiceProxy";


    /**
     * 在插件中查找可以处理mIntent的Service组件,找到之后为其分配合适的Proxy
     *
     * @param mPluginPackageName 插件包名
     * @param mIntent            跳转Activity的Intent
     * @param context            宿主的Context
     * @return 处理后的Intent
     */
    public static Intent switchToServiceProxy(String mPluginPackageName, Intent mIntent, Context context) {
        if (mIntent == null) {
            PluginDebugLog.runtimeLog(TAG, "switchToServiceProxy intent is null");
            return null;
        }

        ServiceInfo targetService = null;
        String targetPkg = "";
        String hostPkg = context.getPackageName();
        if (mIntent.getComponent() != null
                && !TextUtils.isEmpty(mIntent.getComponent().getClassName())) {
            // action 为空，但是指定了包名和activity类名
            ComponentName compName = mIntent.getComponent();
            String pkg = compName.getPackageName();
            String toSerName = compName.getClassName();
            if (TextUtils.equals(pkg, mPluginPackageName)
                    || TextUtils.equals(pkg, hostPkg)) {
                // 跳转当前插件
                targetPkg = mPluginPackageName;
            } else if (IntentUtils.isIntentToPlugin(mIntent)) {
                // 跳转其他插件, 插件A--->插件B, 没有flag认为是跳转外部独立APP
                targetPkg = pkg;
            }

            PluginDebugLog.runtimeFormatLog(TAG, "switchToServiceProxy explicit search target service %s in plugin pkg %s",
                    toSerName, targetPkg);
            if (!TextUtils.isEmpty(targetPkg)) {
                PluginLoadedApk mLoadedApk = PluginManager.getPluginLoadedApkByPkgName(targetPkg);
                PluginPackageInfo mPlugin = mLoadedApk != null ? mLoadedApk.getPluginPackageInfo()
                        : PluginPackageManagerNative.getInstance(context).getPluginPackageInfo(targetPkg);
                if (mPlugin != null) {
                    targetService = mPlugin.getServiceInfo(toSerName);
                    if (targetService != null) {
                        PluginDebugLog.runtimeFormatLog(TAG,
                                "switchToServiceProxy find targetService %s in plugin %s", toSerName, targetPkg);
                    }
                }
            }
        } else {
            // 隐式启动，优先在当前插件中查找
            String searchPkg = "";
            boolean fallback = false;
            String pkg = mIntent.getPackage();
            if (TextUtils.isEmpty(pkg)
                    || TextUtils.equals(pkg, mPluginPackageName)
                    || TextUtils.equals(pkg, hostPkg)) {
                // 没有设置pkg，设置的是当前插件的包名或者宿主的包名，优先搜索当前插件
                searchPkg = mPluginPackageName;
                fallback = IntentUtils.isIntentToPlugin(mIntent);
            } else if (IntentUtils.isIntentToPlugin(mIntent)) {
                // 隐式跳转其他插件， 没有flag则认为是跳转外部独立APP
                searchPkg = mIntent.getPackage();
                fallback = false;
            }

            PluginDebugLog.runtimeFormatLog(TAG, "switchToServiceProxy implicit search target service in plugin pkg %s",
                    searchPkg);
            if (!TextUtils.isEmpty(searchPkg)) {
                PluginLoadedApk mLoadedApk = PluginManager.getPluginLoadedApkByPkgName(searchPkg);
                PluginPackageInfo mPlugin = mLoadedApk != null ? mLoadedApk.getPluginPackageInfo()
                        : PluginPackageManagerNative.getInstance(context).getPluginPackageInfo(searchPkg);
                if (mPlugin != null) {
                    targetService = mPlugin.resolveService(mIntent);
                    if (targetService != null) {
                        targetPkg = searchPkg;
                        PluginDebugLog.runtimeFormatLog(TAG,
                                "switchToServiceProxy find targetService %s in plugin %s", targetService.name, searchPkg);
                    }
                }
            }

            if (targetService == null && fallback) {
                PluginDebugLog.runtimeFormatLog(TAG, "switchToServiceProxy not find targetService in plugin %s, " +
                        "fallback to search in all installed plugins", searchPkg);
                // 去其他插件里查找
                List<PluginLiteInfo> packageList =
                        PluginPackageManagerNative.getInstance(context).getInstalledApps();
                if (packageList != null) {
                    for (PluginLiteInfo pkgInfo : packageList) {
                        if (pkgInfo == null || TextUtils.equals(pkgInfo.packageName, searchPkg)) {
                            continue;
                        }

                        PluginPackageInfo target = PluginPackageManagerNative.getInstance(context)
                                .getPluginPackageInfo(context, pkgInfo);
                        if (target != null) {
                            targetService = target.resolveService(mIntent);
                            if (targetService != null) {
                                targetPkg = pkgInfo.packageName;
                                PluginDebugLog.runtimeFormatLog(TAG,
                                        "switchToServiceProxy find targetService in other plugin %s!", pkgInfo.packageName);
                                break;
                            }
                        }
                    }
                }
            }
        }

        String intentInfo = mIntent.toString();
        if (null != mIntent.getExtras()) {
            intentInfo = intentInfo + mIntent.getExtras().toString();
        }

        if (targetService != null) {
            PluginDebugLog.runtimeFormatLog(TAG, "switchToServiceProxy from plugin %s to plugin %s, "
                    + "targetService: %s, intent: %s", mPluginPackageName, targetPkg, targetService, intentInfo);
            setServiceProxy(mIntent, targetService);
        } else {
            PluginDebugLog.runtimeFormatLog(TAG, "switchToServiceProxy not find targetService from plugin %s, intent: %s",
                    mPluginPackageName, intentInfo);
        }

        return mIntent;
    }

    /**
     * 为插件中的Service设置代理
     *
     * @param mIntent        需要设置代理的Service的Intent
     * @param targetService 目标的ServiceInfo，包含插件包名和跳转Service的名称
     */
    private static void setServiceProxy(Intent mIntent, ServiceInfo targetService) {
        String mPackageName = targetService.packageName;
        String serviceName = targetService.name;
        PluginLoadedApk mLoadedApk = PluginManager.getPluginLoadedApkByPkgName(mPackageName);
        if (null == mLoadedApk) {
            PluginDebugLog.runtimeFormatLog(TAG,
                    "setServiceProxy failed, %s, PluginLoadedApk is null", mPackageName);
            return;
        }

        PluginDebugLog.runtimeFormatLog(TAG, "setServiceProxy  serviceInfo: " + targetService.toString());
        mIntent.setExtrasClassLoader(mLoadedApk.getPluginClassLoader());
        mIntent.addCategory(IntentConstant.EXTRA_TARGET_CATEGORY + System.currentTimeMillis())
                .putExtra(IntentConstant.EXTRA_TARGET_IS_PLUGIN_KEY, true)
                .putExtra(IntentConstant.EXTRA_TARGET_CLASS_KEY, serviceName)
                .putExtra(IntentConstant.EXTRA_TARGET_PACKAGE_KEY, mLoadedApk.getPluginPackageName());
        try {
            mIntent.setClass(mLoadedApk.getHostContext(),
                    Class.forName(matchServiceProxyByFeature(mLoadedApk.getProcessName())));
            IntentUtils.setProxyInfo(mIntent, mPackageName);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }


    /**
     * 在插件中查找可以处理mIntent的Activity组件,找到之后为其分配合适的Proxy
     *
     * @param mPluginPackageName 插件包名
     * @param mIntent            跳转Activity的Intent
     * @param requestCode        请求码
     * @param context            宿主的Context
     * @return 处理后的Intent
     */
    public static Intent switchToActivityProxy(String mPluginPackageName,
                                               Intent mIntent,
                                               int requestCode,
                                               Context context) {
        if (mIntent == null) {
            PluginDebugLog.runtimeLog(TAG, "switchToActivityProxy intent is null!");
            return null;
        }

        if (TextUtils.isEmpty(mPluginPackageName)) {
            // Note: 宿主的Instrumentation会执行到这里, 不能做拦截, 导致本来要启动独立App的逻辑变成启动插件
            PluginDebugLog.runtimeLog(TAG, "switchToActivityProxy mPluginPackageName is null");
            return mIntent;
        }

        PluginDebugLog.runtimeLog(TAG, "switchToActivityProxy: plugin: "
                + mPluginPackageName + ", intent: " + mIntent
                + ", requestCode: " + requestCode);
        if (hasProxyActivity(mIntent)) {
            PluginDebugLog.runtimeLog(TAG,
                    "switchToActivityProxy has already set the intent to proxy activity");
            return mIntent;
        }

        ActivityInfo targetActivity = null;
        String targetPkg = "";
        String hostPkg = context.getPackageName();
        if (mIntent.getComponent() != null
                && !TextUtils.isEmpty(mIntent.getComponent().getClassName())) {
            // action 为空，但是指定了包名和activity类名
            ComponentName compName = mIntent.getComponent();
            String pkg = compName.getPackageName();
            String toActName = compName.getClassName();
            if (TextUtils.equals(pkg, mPluginPackageName)
                    || TextUtils.equals(pkg, hostPkg)) {
                // 跳转当前插件
                targetPkg = mPluginPackageName;
            } else if (IntentUtils.isIntentToPlugin(mIntent)) {
                // 跳转其他插件, 插件A--->插件B, 没有flag认为是跳转外部独立APP
                targetPkg = pkg;
            }

            PluginDebugLog.runtimeFormatLog(TAG, "switchToActivityProxy explicit search target activity %s in plugin %s",
                    toActName, targetPkg);
            if (!TextUtils.isEmpty(targetPkg)) {
                PluginLoadedApk mLoadedApk = PluginManager.getPluginLoadedApkByPkgName(targetPkg);
                PluginPackageInfo mPlugin = mLoadedApk != null ? mLoadedApk.getPluginPackageInfo()
                        : PluginPackageManagerNative.getInstance(context).getPluginPackageInfo(targetPkg);
                if (mPlugin != null) {
                    targetActivity = mPlugin.getActivityInfo(toActName);
                    if (targetActivity != null) {
                        PluginDebugLog.runtimeFormatLog(TAG,
                                "switchToActivityProxy find targetActivity %s in plugin %s", toActName, targetPkg);
                    }
                }
            }
        } else {
            // 隐式启动，优先在当前插件中查找
            String searchPkg = "";
            boolean fallback = false;
            String pkg = mIntent.getPackage();
            if (TextUtils.isEmpty(pkg)
                    || TextUtils.equals(pkg, mPluginPackageName)
                    || TextUtils.equals(mIntent.getPackage(), hostPkg)) {
                // 没有设置pkg，设置的是当前插件的包名或者宿主的包名，优先搜索当前插件
                searchPkg = mPluginPackageName;
                fallback = IntentUtils.isIntentToPlugin(mIntent);
            } else if (IntentUtils.isIntentToPlugin(mIntent)) {
                // 隐式跳转其他插件， 没有flag则认为是跳转外部独立APP
                searchPkg = mIntent.getPackage();
                fallback = false;
            }

            PluginDebugLog.runtimeFormatLog(TAG, "switchToActivityProxy implicit search target activity in plugin %s",
                    searchPkg);
            if (!TextUtils.isEmpty(searchPkg)) {
                PluginLoadedApk mLoadedApk = PluginManager.getPluginLoadedApkByPkgName(searchPkg);
                PluginPackageInfo mPlugin = mLoadedApk != null ? mLoadedApk.getPluginPackageInfo()
                        : PluginPackageManagerNative.getInstance(context).getPluginPackageInfo(searchPkg);
                if (mPlugin != null) {
                    targetActivity = mPlugin.resolveActivity(mIntent);
                    if (targetActivity != null) {
                        targetPkg = searchPkg;
                        PluginDebugLog.runtimeFormatLog(TAG,
                                "switchToActivityProxy find targetActivity %s in plugin %s", targetActivity.name, searchPkg);
                    }
                }
            }

            if (targetActivity == null && fallback) {
                PluginDebugLog.runtimeFormatLog(TAG, "switchToActivityProxy not find targetActivity in plugin %s, " +
                        "fallback to search in all installed plugins", searchPkg);
                // 去其他插件里查找
                List<PluginLiteInfo> packageList =
                        PluginPackageManagerNative.getInstance(context).getInstalledApps();
                if (packageList != null) {
                    for (PluginLiteInfo pkgInfo : packageList) {
                        if (pkgInfo == null || TextUtils.equals(pkgInfo.packageName, searchPkg)) {
                            continue;
                        }

                        PluginPackageInfo target = PluginPackageManagerNative.getInstance(context)
                                .getPluginPackageInfo(context, pkgInfo);
                        if (target != null) {
                            targetActivity = target.resolveActivity(mIntent);
                            if (targetActivity != null) {
                                targetPkg = pkgInfo.packageName;
                                PluginDebugLog.runtimeFormatLog(TAG,
                                        "switchToActivityProxy find targetActivity in other plugin %s!", pkgInfo.packageName);
                                break;
                            }
                        }
                    }
                }
            }
        }

        String intentInfo = mIntent.toString();
        if (null != mIntent.getExtras()) {
            intentInfo = intentInfo + mIntent.getExtras().toString();
        }

        if (targetActivity != null) {
            PluginDebugLog.runtimeFormatLog(TAG, "switchToActivityProxy from plugin %s to plugin %s, "
                + "targetActivity: %s, intent: %s", mPluginPackageName, targetPkg, targetActivity, intentInfo);
            setActivityProxy(mIntent, targetActivity);
            PluginLoadedApk mLoadedApk = PluginManager.getPluginLoadedApkByPkgName(targetPkg);
            if (mLoadedApk != null) {
                mLoadedApk.getActivityStackSupervisor().dealLaunchMode(mIntent);
            }
        } else {
            PluginDebugLog.runtimeFormatLog(TAG, "switchToActivityProxy not find targetActivity from plugin %s, intent: %s",
                    mPluginPackageName, intentInfo);
        }

        return mIntent;
    }

    /**
     * 查找能够响应这个Uri的插件
     *
     * @param context 宿主的上下文
     * @param uri     provider的Uri地址
     * @return 能够响应的插件包名
     */
    public static String resolvePkgName(Context context, Uri uri) {
        if (uri == null || uri.getAuthority() == null) {
            return null;
        }

        ProviderInfo provider = resolveProviderInfo(context, uri.getAuthority());
        if (provider != null) {
            return provider.packageName;
        }
        return "";
    }

    /**
     * 查找能够响应这个authority的插件ProviderInfo
     *
     * @param context   宿主的上下文
     * @param authority
     * @return 返回能够响应的ProviderInfo
     */
    public static ProviderInfo resolveProviderInfo(Context context, String authority) {
        // 首先去内存中查找
        for (Map.Entry<String, PluginLoadedApk> entry : PluginManager.getAllPluginLoadedApk().entrySet()) {
            PluginLoadedApk loadedApk = entry.getValue();
            ProviderInfo provider = loadedApk.getProviderInfoByAuthority(authority);
            if (provider != null) {
                return provider;
            }
        }
        // 没有找到，则重新查找一遍所有已安装的插件PackageInfo
        List<PluginLiteInfo> packageList =
                PluginPackageManagerNative.getInstance(context).getInstalledApps();
        if (packageList != null) {
            for (PluginLiteInfo pkgInfo : packageList) {
                if (pkgInfo != null) {
                    PluginPackageInfo target = PluginPackageManagerNative.getInstance(context)
                            .getPluginPackageInfo(context, pkgInfo);
                    if (target != null) {
                        ProviderInfo provider = target.resolveProvider(authority);
                        if (provider != null) {
                            PluginDebugLog.runtimeFormatLog(TAG, "resolvePkgName find plugin %s can handle authority %s",
                                    pkgInfo.packageName, authority);
                            return provider;
                        }
                    }
                }
            }
        }
        return null;
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
            String proxyActName = mIntent.getComponent().getClassName();
            return proxyActName.startsWith(ComponentFinder.DEFAULT_ACTIVITY_PROXY_PREFIX);
        }
        return false;
    }

    /**
     * 为插件中的Activity设置代理
     *
     * @param mIntent        需要设置代理的Activity的Intent
     * @param targetActivity 目标的ActivityInfo，包含插件包名和跳转Activity的名称
     */
    private static void setActivityProxy(Intent mIntent, ActivityInfo targetActivity) {
        String mPackageName = targetActivity.packageName;
        String activityName = targetActivity.name;
        PluginLoadedApk mLoadedApk = PluginManager.getPluginLoadedApkByPkgName(mPackageName);
        if (null == mLoadedApk) {
            PluginDebugLog.runtimeFormatLog(TAG,
                    "setActivityProxy failed, %s, PluginLoadedApk is null", mPackageName);
            return;
        }

        PluginDebugLog.runtimeFormatLog(TAG, "setActivityProxy  activityInfo: " + targetActivity.toString());
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
     * @return 返回代理Activity的类名
     */
    public static String findActivityProxy(PluginLoadedApk mLoadedApk, ActivityInfo actInfo) {
        boolean isTranslucent = false;
        boolean isHandleConfigChange = false;
        boolean isLandscape = false;
        boolean hasTaskAffinity = false;
        boolean supportPip = false;

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
                        (tv.data == Color.TRANSPARENT));
                isTranslucent = attr_0 && (tv.data == Color.TRANSPARENT);
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

        if (supportPictureInPicture(actInfo)) {
            PluginDebugLog.runtimeLog(TAG, "findActivityProxy activity taskAffinity: "
                    + actInfo.taskAffinity + " hasTaskAffinity = true" + ", supportPictureInPicture = true");
            supportPip = true;
        }

        if (actInfo.launchMode == ActivityInfo.LAUNCH_SINGLE_TASK) {
            String pkgName = mLoadedApk.getPluginPackageName();
            if (TextUtils.equals(actInfo.taskAffinity, pkgName + IntentConstant.TASK_AFFINITY_CONTAINER1)) {
                PluginDebugLog.runtimeLog(TAG, "findActivityProxy activity taskAffinity: "
                        + actInfo.taskAffinity + " hasTaskAffinity = true");
                hasTaskAffinity = true;
            }
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

        return matchActivityProxyByFeature(supportPip, hasTaskAffinity, isTranslucent, isLandscape,
                isHandleConfigChange, mLoadedApk.getProcessName());
    }

    /**
     * 根据被代理的Activity的Feature和进程名称选择代理
     *
     * @param supportPip      是否支持Android N画中画功能
     * @param hasTaskAffinity 是否独立任务栈
     * @param isTranslucent   是否透明
     * @param isLandscape     是否横屏
     * @param isHandleConfig  配置变化是否仅仅执行onConfiguration方法
     * @param mProcessName    当前插件运行的进程名称
     * @return 代理Activity的名称
     */
    private static String matchActivityProxyByFeature(
            boolean supportPip,
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
        if (supportPip) {
            proxyActivityName = ComponentFinder.DEFAULT_PICTURE_IN_PICTURE_ACTIVITY_PROXY_PREFIX + index;
        } else if (hasTaskAffinity) {
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

        PluginDebugLog.runtimeFormatLog(TAG, "matchActivityProxyByFeature: %s", proxyActivityName);
        return proxyActivityName;
    }

    private static boolean supportPictureInPicture(ActivityInfo actInfo) {
        boolean supportPip = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // supportPip = actInfo.supportsPictureInPicture();
                // 不能直接调用，否则混淆会有warning，除非添加ignore warning配置
                supportPip = ReflectionUtils.on(actInfo).call("supportsPictureInPicture").get();
            } catch (Exception e) {
                ErrorUtil.throwErrorIfNeed(e);
            }
        }
        return supportPip;
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

    /**
     * 根据代理Service名称修复插件运行进程名称
     *
     * @param serviceName 代理service名称
     * @return 运行的进程名
     */
    public static String fixProcessNameByService(Context context, String serviceName) {
        if (serviceName.startsWith(DEFAULT_SERVICE_PROXY_PREFIX)) {
            char index = serviceName.charAt(serviceName.length() - 1);
            String processName = context.getPackageName();
            switch (index) {
                case '0':
                    break;
                case '1':
                    processName = processName + ProcessManager.PROXY_PROCESS1;
                    break;
                case '2':
                    processName = processName + ProcessManager.PROXY_PROCESS2;
                    break;
                case '3':
                    processName = processName + ProcessManager.PROXY_DOWNLOADER;
                    break;
            }
            return processName;
        }
        throw new IllegalArgumentException("unknown serviceName: " + serviceName);
    }
}
