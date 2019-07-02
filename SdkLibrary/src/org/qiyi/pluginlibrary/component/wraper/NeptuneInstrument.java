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
package org.qiyi.pluginlibrary.component.wraper;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;

import org.qiyi.pluginlibrary.Neptune;
import org.qiyi.pluginlibrary.NeptuneConfig;
import org.qiyi.pluginlibrary.component.InstrActivityProxy1;
import org.qiyi.pluginlibrary.component.TransRecoveryActivity1;
import org.qiyi.pluginlibrary.component.base.IPluginBase;
import org.qiyi.pluginlibrary.component.stackmgr.PluginActivityControl;
import org.qiyi.pluginlibrary.context.PluginContextWrapper;
import org.qiyi.pluginlibrary.pm.PluginLiteInfo;
import org.qiyi.pluginlibrary.pm.PluginPackageManagerNative;
import org.qiyi.pluginlibrary.runtime.NotifyCenter;
import org.qiyi.pluginlibrary.runtime.PluginLoadedApk;
import org.qiyi.pluginlibrary.runtime.PluginManager;
import org.qiyi.pluginlibrary.utils.ComponentFinder;
import org.qiyi.pluginlibrary.utils.ErrorUtil;
import org.qiyi.pluginlibrary.utils.IntentUtils;
import org.qiyi.pluginlibrary.utils.LayoutInflaterCompat;
import org.qiyi.pluginlibrary.utils.PluginDebugLog;
import org.qiyi.pluginlibrary.utils.ReflectionUtils;

/**
 * 自定义的全局的Instrumentation
 * 负责转移插件的跳转目标和创建插件的Activity实例
 * 用于Hook ActivityThread中的全局Instrumentation
 */
public class NeptuneInstrument extends PluginInstrument {

    private static final String TAG = "NeptuneInstrument";
    private static final String HAS_CURENT_PERMISSIONS_REQUEST_KEY =
            "android:hasCurrentPermissionsRequest";
    private static final String FRAGMENTS_TAG = "android:fragments";
    private static final String SUPPORT_FRAGMENTS_TAG = "android:support:fragments";

    private ActivityRecoveryHelper mRecoveryHelper = new ActivityRecoveryHelper();

    public NeptuneInstrument(Instrumentation hostInstr) {
        super(hostInstr);
    }

    /**
     * 某些case下创建Activity时PluginLoadedApk创建成功了，
     * 但是Application还没有初始化完成，这里补充一次初始化
     */
    private void tryInitPluginApplication(@Nullable PluginLoadedApk loadedApk) {
        if (loadedApk != null) {
            loadedApk.invokeApplicationIfNeed();
        }
    }

    /**
     * 当前插件是否启用了进程恢复模式
     */
    private boolean enableRecoveryMode(String pkgName) {
        PluginLiteInfo packageInfo = PluginPackageManagerNative.getInstance(Neptune.getHostContext()).getPackageInfo(pkgName);
        return packageInfo != null && packageInfo.enableRecovery;
    }

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        if (className.startsWith(ComponentFinder.DEFAULT_ACTIVITY_PROXY_PREFIX)) {
            // 插件代理Activity，替换回插件真实的Activity
            String[] result = IntentUtils.parsePkgAndClsFromIntent(intent);
            String packageName = result[0];
            String targetClass = result[1];

            PluginDebugLog.runtimeLog(TAG, "newActivity: " + className + ", targetClass: " + targetClass);
            if (!TextUtils.isEmpty(packageName)) {
                PluginLoadedApk loadedApk = PluginManager.getPluginLoadedApkByPkgName(packageName);
                tryInitPluginApplication(loadedApk);
                if (loadedApk != null && !TextUtils.isEmpty(targetClass)) {
                    Activity activity = mHostInstr.newActivity(loadedApk.getPluginClassLoader(), targetClass, intent);
                    activity.setIntent(intent);

                    if (!dispatchToBaseActivity(activity)) {
                        // 这里需要替换Resources，是因为ContextThemeWrapper会缓存一个Resource对象，而在Activity#attach()和
                        // Activity#onCreate()之间，系统会调用Activity#setTheme()初始化主题，Android 4.1+
                        ReflectionUtils.on(activity).setNoException("mResources", loadedApk.getPluginResource());
                    }

                    return activity;
                } else if (loadedApk == null && enableRecoveryMode(packageName)) {
                    // loadedApk为空，可能是正在恢复进程，当前插件配置支持进程恢复，则跳转到 RecoveryActivity
                    // 否则还是跳转InstrActivityProxy1
                    return mHostInstr.newActivity(cl, mRecoveryHelper.selectRecoveryActivity(className), intent);
                }
            }
        }
        return mHostInstr.newActivity(cl, className, intent);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        boolean isRecovery = activity instanceof TransRecoveryActivity1;
        if (isRecovery) {
            mRecoveryHelper.saveIcicle(activity, icicle);
            mHostInstr.callActivityOnCreate(activity, null);
            return;
        }
        final Intent intent = activity.getIntent();
        String[] result = IntentUtils.parsePkgAndClsFromIntent(intent);
        String packageName = result[0];
        String targetClass = result[1];
        boolean isLaunchPlugin = false;
        if (activity instanceof InstrActivityProxy1) {
            // 如果恢复的Activity是插件的代理Activity，兼容处理下icicle序列化的问题
            PluginDebugLog.runtimeFormatLog(TAG, "callActivityOnCreate: %s cls: %s", packageName, activity.getClass().getName());
            PluginLoadedApk loadedApk = PluginManager.getPluginLoadedApkByPkgName(packageName);
            tryInitPluginApplication(loadedApk);
            if (loadedApk != null) {
                icicle = mRecoveryHelper.recoveryIcicle(activity, icicle, loadedApk.getPluginClassLoader());
            } else {
                // loadedApk为空，icicle没有设置ClassLoader可能有问题
                icicle = getSafeSavedInstanceState(icicle);
            }
        } else if (IntentUtils.isIntentForPlugin(intent)
            && !TextUtils.isEmpty(packageName)) {
            // 不是代理Activity, 插件真实的Activity
            PluginDebugLog.runtimeFormatLog(TAG, "callActivityOnCreate: %s", packageName);
            PluginLoadedApk loadedApk = PluginManager.getPluginLoadedApkByPkgName(packageName);
            tryInitPluginApplication(loadedApk);
            if (loadedApk != null) {
                icicle = mRecoveryHelper.recoveryIcicle(activity, icicle, loadedApk.getPluginClassLoader());
                if (!dispatchToBaseActivity(activity)) {
                    // 如果分发给插件Activity的基类了，就不需要在这里反射hook替换相关成员变量了
                    ReflectionUtils activityRef = ReflectionUtils.on(activity);
                    activityRef.setNoException("mResources", loadedApk.getPluginResource());
                    activityRef.setNoException("mApplication", loadedApk.getPluginApplication());
                    Context pluginContext = new PluginContextWrapper(activity.getBaseContext(), loadedApk);
                    ReflectionUtils.on(activity, ContextWrapper.class).set("mBase", pluginContext);
                    // 5.0以下ContextThemeWrapper内会保存一个mBase，也需要反射替换掉
                    ReflectionUtils.on(activity, ContextThemeWrapper.class).setNoException("mBase", pluginContext);
                    ReflectionUtils.on(activity).setNoException("mInstrumentation", loadedApk.getPluginInstrument());
                    // Activity override了getLayoutInflater()返回了window的LayoutInflater，这里需要修改mPrivateFactory
                    LayoutInflaterCompat.setPrivateFactory(activity.getLayoutInflater());
                    // 修改插件Activity的ActivityInfo, theme, window等信息
                    PluginActivityControl.changeActivityInfo(activity, targetClass, loadedApk);
                }
                if (activity.getParent() == null) {
                    loadedApk.getActivityStackSupervisor().pushActivityToStack(activity);
                }
                isLaunchPlugin = true;
            } else {
                // loadedApk为空，icicle没有设置ClassLoader可能有问题
                icicle = getSafeSavedInstanceState(icicle);
            }
            //恢复重置Action
            IntentUtils.resetAction(intent);
        }

        try {
            mHostInstr.callActivityOnCreate(activity, icicle);
            if (isLaunchPlugin) {
                NotifyCenter.notifyPluginStarted(activity, intent);
                NotifyCenter.notifyPluginActivityLoaded(activity);
            }
            mRecoveryHelper.mockActivityOnRestoreInstanceStateIfNeed(this, activity);
        } catch (Exception ex) {
            ErrorUtil.throwErrorIfNeed(ex);
            if (isLaunchPlugin) {
                NotifyCenter.notifyStartPluginError(activity);
            } else {
                // 非插件启动，把异常抛给系统，方便定位解决问题
                throw ex;
            }
            activity.finish();
        }
    }

    @Override
    public void callActivityOnDestroy(Activity activity) {
        mHostInstr.callActivityOnDestroy(activity);
        if (activity.getParent() != null) {
            return;
        }

        final Intent intent = activity.getIntent();
        String pkgName = IntentUtils.parsePkgNameFromActivity(activity);
        if (IntentUtils.isIntentForPlugin(intent)
                || intent == null) {
            // intent为null时，如果能够从Activity中解析出pkgName，也应该是插件的页面
            if (!TextUtils.isEmpty(pkgName)) {
                PluginDebugLog.runtimeLog(TAG, "callActivityOnDestroy: " + pkgName);
                PluginLoadedApk loadedApk = PluginManager.getPluginLoadedApkByPkgName(pkgName);
                if (loadedApk != null) {
                    loadedApk.getActivityStackSupervisor().popActivityFromStack(activity);
                }
            }
        }
    }

    @Override
    public void callActivityOnRestoreInstanceState(Activity activity, Bundle savedInstanceState) {
        if (activity instanceof TransRecoveryActivity1) {
            mRecoveryHelper.saveSavedInstanceState(activity, savedInstanceState);
            return;
        }
        if (IntentUtils.isIntentForPlugin(activity.getIntent())) {
            String pkgName = IntentUtils.parsePkgAndClsFromIntent(activity.getIntent())[0];
            PluginLoadedApk loadedApk = PluginManager.getPluginLoadedApkByPkgName(pkgName);
            if (loadedApk != null && savedInstanceState != null) {
                savedInstanceState.setClassLoader(loadedApk.getPluginClassLoader());
            }
        }
        mHostInstr.callActivityOnRestoreInstanceState(activity, savedInstanceState);
    }

    /**
     * 插件Activity恢复时, savedInstanceState可能还有序列化对象，
     * 如果解析失败则返回null，防止崩溃
     */
    private Bundle getSafeSavedInstanceState(Bundle icicle) {
        if (icicle != null) {
            try {
                // 读取bundle数据，有问题会抛出 java.lang.RuntimeException: Parcelable encountered ClassNotFoundException, 置空数据
                boolean hasPermissionRequest = icicle.getBoolean(HAS_CURENT_PERMISSIONS_REQUEST_KEY, false);
                // 不恢复Fragment的状态，里面可能存在自定义Parcelable数据
                icicle.remove(SUPPORT_FRAGMENTS_TAG);
                icicle.remove(FRAGMENTS_TAG);
            } catch (RuntimeException e) {
                icicle = null;
            }
        }
        return icicle;
    }

    /**
     * 将Activity反射相关操作分发给插件Activity的基类
     */
    private static boolean dispatchToBaseActivity(Activity activity) {

        return Neptune.getConfig().getSdkMode() == NeptuneConfig.INSTRUMENTATION_BASEACT_MODE
                && activity instanceof IPluginBase;
    }
}
