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
package org.qiyi.pluginlibrary.component;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.view.KeyEvent;

import org.qiyi.pluginlibrary.Neptune;
import org.qiyi.pluginlibrary.constant.IntentConstant;
import org.qiyi.pluginlibrary.pm.PluginLiteInfo;
import org.qiyi.pluginlibrary.pm.PluginPackageManagerNative;
import org.qiyi.pluginlibrary.runtime.NotifyCenter;
import org.qiyi.pluginlibrary.runtime.PluginManager;
import org.qiyi.pluginlibrary.utils.FileUtils;
import org.qiyi.pluginlibrary.utils.IRecoveryCallback;
import org.qiyi.pluginlibrary.utils.IntentUtils;
import org.qiyi.pluginlibrary.utils.PluginDebugLog;

/**
 * 主进程恢复 中转Activity
 * 进程因资源不足被回收以后，恢复时插件信息会丢失，这个页面作为临时页面处理插件恢复问题。
 */
public class TransRecoveryActivity1 extends Activity {
    private static String TAG = "TransRecoveryActivity0";
    /**
     * 启动插件 Receiver 的优先级
     * <p>
     * 在恢复 Activity 堆栈时，如果栈顶 Activity 是透明主题，会连续恢复多个 Activity 直到非透明主题 Activity，
     * 这个优先级递增，保证后恢复的 Activity 可以先打开被压到栈底。
     * <p>
     * 只有进程恢复时才需要，进程恢复时会自动重置。
     */
    private static int sLaunchPluginReceiverPriority;
    private BroadcastReceiver mFinishSelfReceiver;
    private BroadcastReceiver mLaunchPluginReceiver;
    private String mPluginPackageName;
    private String mPluginClassName;
    private IRecoveryCallback mRecoveryCallback;
    private Handler mHandler = new Handler();
    private Runnable mMockServiceReady = new Runnable() {
        @Override
        public void run() {
            PluginDebugLog.runtimeLog(TAG, "mock ServiceConnected event.");
            NotifyCenter.notifyServiceConnected(TransRecoveryActivity1.this, "");
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initRecoveryCallback();

        String[] packageAndClass = IntentUtils.parsePkgAndClsFromIntent(getIntent());
        mPluginPackageName = packageAndClass[0];
        mPluginClassName = packageAndClass[1];
        PluginDebugLog.runtimeFormatLog(TAG, "TransRecoveryActivity0 onCreate....%s %s", mPluginPackageName, mPluginClassName);

        if (mPluginPackageName == null) {
            finish();
            return;
        }

        mRecoveryCallback.beforeRecovery(this, mPluginPackageName, mPluginClassName);

        // PPMS 可能未连接，getPackageInfo 会直接读取 SharedPreference
        PluginLiteInfo packageInfo = PluginPackageManagerNative.getInstance(this).getPackageInfo(mPluginPackageName);
        boolean enableRecovery = packageInfo != null && packageInfo.enableRecovery;

        if (!enableRecovery) {
            finish();
            return;
        }

        mRecoveryCallback.onSetContentView(this, mPluginPackageName, mPluginClassName);

        mLaunchPluginReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, Intent intent) {
                PluginDebugLog.runtimeFormatLog(TAG, "LaunchPluginReceiver#onReceive %s %s", mPluginClassName, intent.getStringExtra(IntentConstant.EXTRA_SERVICE_CLASS));
                boolean ppmsReady = PluginPackageManagerNative.getInstance(context).isConnected();
                boolean hostReady = mRecoveryCallback.beforeLaunch(context, mPluginPackageName, mPluginClassName);
                if (ppmsReady && hostReady) {
                    PluginDebugLog.runtimeFormatLog(TAG, "LaunchPluginReceiver#launch %s", mPluginClassName);
                    PluginManager.launchPlugin(context, createLaunchPluginIntent(), FileUtils.getCurrentProcessName(context));
                    unregisterReceiver(mLaunchPluginReceiver);
                    mLaunchPluginReceiver = null;
                }
            }
        };
        IntentFilter serviceConnectedFilter = new IntentFilter(IntentConstant.ACTION_SERVICE_CONNECTED);
        serviceConnectedFilter.setPriority(sLaunchPluginReceiverPriority++);
        registerReceiver(mLaunchPluginReceiver, serviceConnectedFilter);

        // 启动插件成功或者失败后，都应该 finish
        mFinishSelfReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                TransRecoveryActivity1.this.finish();
                mRecoveryCallback.afterRecovery(context, mPluginPackageName, mPluginClassName);
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(IntentConstant.ACTION_START_PLUGIN_ERROR);
        filter.addAction(IntentConstant.ACTION_PLUGIN_LOADED);
        registerReceiver(mFinishSelfReceiver, filter);
    }

    private Intent createLaunchPluginIntent() {
        Intent intent = new Intent(getIntent());
        intent.setComponent(new ComponentName(mPluginPackageName, mPluginClassName));
        return intent;
    }

    private void initRecoveryCallback() {
        mRecoveryCallback = Neptune.getConfig().getRecoveryCallback();
        if (mRecoveryCallback == null) {
            mRecoveryCallback = new IRecoveryCallback.DefaultRecoveryCallback();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 模拟触发 service 连接成功，防止部分手机由于 plugin1 进程自启动 service 提前准备好后，无法再次触发 service 连接事件
        mHandler.postDelayed(mMockServiceReady, 500);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mLaunchPluginReceiver != null) {
            unregisterReceiver(mLaunchPluginReceiver);
        }
        if (mFinishSelfReceiver != null) {
            unregisterReceiver(mFinishSelfReceiver);
        }
        mHandler.removeCallbacks(mMockServiceReady);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 限制 back 按键，不允许退出
        return keyCode == KeyEvent.KEYCODE_BACK || super.onKeyDown(keyCode, event);
    }
}
