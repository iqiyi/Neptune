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
import android.text.TextUtils;
import android.view.ContextThemeWrapper;

import org.qiyi.pluginlibrary.Neptune;
import org.qiyi.pluginlibrary.NeptuneConfig;
import org.qiyi.pluginlibrary.component.TransRecoveryActivity1;
import org.qiyi.pluginlibrary.component.base.IPluginBase;
import org.qiyi.pluginlibrary.component.stackmgr.PluginActivityControl;
import org.qiyi.pluginlibrary.context.PluginContextWrapper;
import org.qiyi.pluginlibrary.runtime.NotifyCenter;
import org.qiyi.pluginlibrary.runtime.PluginLoadedApk;
import org.qiyi.pluginlibrary.runtime.PluginManager;
import org.qiyi.pluginlibrary.utils.ComponentFinder;
import org.qiyi.pluginlibrary.utils.ErrorUtil;
import org.qiyi.pluginlibrary.utils.IntentUtils;
import org.qiyi.pluginlibrary.utils.PluginDebugLog;
import org.qiyi.pluginlibrary.utils.ReflectionUtils;

/**
 * 自定义的全局的Instrumentation
 * 负责转移插件的跳转目标和创建插件的Activity实例
 * 用于Hook ActivityThread中的全局Instrumentation
 */
public class NeptuneInstrument extends PluginInstrument {

    private static final String TAG = "NeptuneInstrument";
    private ActivityRecoveryHelper mRecoveryHelper = new ActivityRecoveryHelper();

    public NeptuneInstrument(Instrumentation hostInstr) {
        super(hostInstr);
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
                if (loadedApk != null && targetClass != null) {
                    Activity activity = mHostInstr.newActivity(loadedApk.getPluginClassLoader(), targetClass, intent);
                    activity.setIntent(intent);

                    if (!dispatchToBaseActivity(activity)) {
                        // 这里需要替换Resources，是因为ContextThemeWrapper会缓存一个Resource对象，而在Activity#attach()和
                        // Activity#onCreate()之间，系统会调用Activity#setTheme()初始化主题，Android 4.1+
                        ReflectionUtils.on(activity).setNoException("mResources", loadedApk.getPluginResource());
                    }

                    return activity;
                } else if (loadedApk == null) {
                    // loadedApk 为空，可能是正在恢复进程，跳转到 RecoveryActivity
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
        boolean isLaunchPlugin = false;
        if (IntentUtils.isIntentForPlugin(intent)) {
            String packageName = result[0];
            String targetClass = result[1];
            if (!TextUtils.isEmpty(packageName)) {
                PluginDebugLog.runtimeLog(TAG, "callActivityOnCreate: " + packageName);
                PluginLoadedApk loadedApk = PluginManager.getPluginLoadedApkByPkgName(packageName);
                if (loadedApk != null) {
                    icicle = mRecoveryHelper.recoveryIcicle(activity, icicle);
                    // 设置 extra 的 ClassLoader，不然可能会出现 BadParcelException, ClassNotFound
                    if (icicle != null) {
                        icicle.setClassLoader(loadedApk.getPluginClassLoader());
                    }
                    if (!dispatchToBaseActivity(activity)) {
                        // 如果分发给插件Activity的基类了，就不需要在这里反射hook替换相关成员变量了
                        try {
                            ReflectionUtils activityRef = ReflectionUtils.on(activity);
                            activityRef.setNoException("mResources", loadedApk.getPluginResource());
                            activityRef.setNoException("mApplication", loadedApk.getPluginApplication());
                            Context pluginContext = new PluginContextWrapper(activity.getBaseContext(), loadedApk);
                            ReflectionUtils.on(activity, ContextWrapper.class).set("mBase", pluginContext);
                            // 5.0以下ContextThemeWrapper内会保存一个mBase，也需要反射替换掉
                            ReflectionUtils.on(activity, ContextThemeWrapper.class).setNoException("mBase", pluginContext);
                            ReflectionUtils.on(activity).setNoException("mInstrumentation", loadedApk.getPluginInstrument());

                            // 修改插件Activity的ActivityInfo, theme, window等信息
                            PluginActivityControl.changeActivityInfo(activity, targetClass, loadedApk);
                        } catch (Exception e) {
                            PluginDebugLog.runtimeLog(TAG, "callActivityOnCreate with exception: " + e.getMessage());
                        }

                    }

                    if (activity.getParent() == null) {
                        loadedApk.getActivityStackSupervisor().pushActivityToStack(activity);
                    }
                    isLaunchPlugin = true;
                }
            }
            IntentUtils.resetAction(intent);  //恢复Action
        }

        try {
            mHostInstr.callActivityOnCreate(activity, icicle);

            if (isLaunchPlugin) {
                NotifyCenter.notifyPluginStarted(activity, intent);
                NotifyCenter.notifyPluginActivityLoaded(activity);
            }
            mRecoveryHelper.mockActivityOnRestoreInstanceStateIfNeed(this, activity);
        } catch (Exception e) {
            ErrorUtil.throwErrorIfNeed(e);
            if (isLaunchPlugin) {
                NotifyCenter.notifyStartPluginError(activity);
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
     * 将Activity反射相关操作分发给插件Activity的基类
     */
    private boolean dispatchToBaseActivity(Activity activity) {

        return Neptune.getConfig().getSdkMode() == NeptuneConfig.INSTRUMENTATION_BASEACT_MODE
                && activity instanceof IPluginBase;
    }
}
