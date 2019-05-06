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
package org.qiyi.pluginlibrary.component.base;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;

import org.qiyi.pluginlibrary.component.stackmgr.PluginActivityControl;
import org.qiyi.pluginlibrary.context.PluginContextWrapper;
import org.qiyi.pluginlibrary.plugin.InterfaceToGetHost;
import org.qiyi.pluginlibrary.runtime.PluginLoadedApk;
import org.qiyi.pluginlibrary.runtime.PluginManager;
import org.qiyi.pluginlibrary.utils.IntentUtils;
import org.qiyi.pluginlibrary.utils.PluginDebugLog;
import org.qiyi.pluginlibrary.utils.ReflectionUtils;
import org.qiyi.pluginlibrary.utils.ResourcesToolForPlugin;

/**
 * 插件基线PluginActivity的代理实现
 * 每个PluginActivity持有一个PluginActivityDelegate对象，实现插件相关功能的注入
 */
class PluginActivityDelegate implements InterfaceToGetHost {
    private static final String TAG = "PluginActivityDelegate";

    private PluginLoadedApk mPlugin;

    /**
     * 为插件Activity构造一个插件对应的Base Context
     *
     * @param activity 插件真实的Activity
     * @param newBase  Activity原有的Base Context，一般为ContextImpl，是宿主的
     * @return 新的插件Context
     */
    Context createActivityContext(Activity activity, Context newBase) {
        // 通过插件Activity的ClassLoader查找插件实例，这个需要配合新的ClassLoader方案
        mPlugin = PluginManager.findPluginLoadedApkByClassLoader(activity.getClass().getClassLoader());
        if (mPlugin != null) {
            // 生成插件的Base Context
            newBase = new PluginContextWrapper(newBase, mPlugin);
        }

        if (PluginDebugLog.isDebug()) {
            PluginDebugLog.runtimeLog(TAG, "activity createActivityContext() is called(): " + activity.getClass().getName());
        }

        return newBase;
    }

    /**
     * 在插件Activity的onCreate调用前调用该方法
     */
    void handleActivityOnCreateBefore(Activity activity, Bundle savedInstanceState) {

        if (PluginDebugLog.isDebug()) {
            PluginDebugLog.runtimeLog(TAG, "activity handleActivityOnCreateBefore() is called(): " + activity.getClass().getName());
        }

        if (mPlugin == null) {
            // 通过插件的Intent再去查找一遍
            String pkgName = IntentUtils.parsePkgNameFromActivity(activity);
            if (!TextUtils.isEmpty(pkgName)) {
                mPlugin = PluginManager.getPluginLoadedApkByPkgName(pkgName);
            }
        }

        ClassLoader cl = mPlugin != null ? mPlugin.getPluginClassLoader() : activity.getClassLoader();
        // 修正Intent的ClassLoader，解决序列化的问题
        Intent intent = activity.getIntent();
        intent.setExtrasClassLoader(cl);
        IntentUtils.resetAction(intent); //恢复Intent的Action

        // 对FragmentActivity做特殊处理
        if (savedInstanceState != null) {
            //
            savedInstanceState.setClassLoader(cl);
            try {
                savedInstanceState.remove("android:support:fragments");
            } catch (Throwable tr) {
                tr.printStackTrace();
            }
        }
        // 替换Application，不然插件内无法监听LifeCycle
        if (mPlugin != null) {
            ReflectionUtils.on(activity).setNoException("mApplication", mPlugin.getPluginApplication());
        }
        // 再次确保Activity的Base Context已经被替换了
        Context mBase = activity.getBaseContext();
        if (mBase instanceof PluginContextWrapper) {
            PluginDebugLog.runtimeLog(TAG, "activity " + activity.getClass().getName() + " base context already be replaced");
        } else if (mPlugin != null) {
            mBase = new PluginContextWrapper(mBase, mPlugin);
            // 反射替换mBase成员变量
            ReflectionUtils.on(activity, ContextWrapper.class).set("mBase", mBase);
            ReflectionUtils.on(activity, ContextThemeWrapper.class).setNoException("mBase", mBase);
        }

        // 修改Activity的ActivityInfo和主题信息
        PluginActivityControl.changeActivityInfo(activity, activity.getClass().getName(), mPlugin);
    }

    /**
     * 在插件Activity#onCreate调用之后执行
     */
    void handleActivityOnCreateAfter(Activity activity, Bundle savedInstanceState) {

        if (PluginDebugLog.isDebug()) {
            PluginDebugLog.runtimeLog(TAG, "activity handleActivityOnCreateAfter() is called(): " + activity.getClass().getName());
        }
    }

    /**
     * 在插件Activity#onDetorty调用之后执行
     */
    void handleActivityOnDestroy(Activity activity) {

        if (PluginDebugLog.isDebug()) {
            PluginDebugLog.runtimeLog(TAG, "activity handleActivityOnDestroy() is called(): " + activity.getClass().getName());
        }
    }

    @Override
    public Context getOriginalContext() {

        if (mPlugin != null) {
            return mPlugin.getHostContext();
        }
        return null;
    }

    @Override
    public ResourcesToolForPlugin getHostResourceTool() {

        if (mPlugin != null) {
            return mPlugin.getHostResourceTool();
        }
        return null;
    }

    @Override
    public String getPluginPackageName() {
        return mPlugin != null ? mPlugin.getPluginPackageName() : "";
    }

    @Override
    public void exitApp() {
        if (mPlugin != null) {
            mPlugin.quitApp(true);
        }
    }
}
