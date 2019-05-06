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
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.os.RemoteException;
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
 * 因为android4.1 以下系统dexopt会导致线程hang住无法返回，所以我们放到了一个独立进程，减小概率。
 * dexopt系统bug：http://code.google.com/p/android/issues/detail?id=14962
 */
public class PluginInstallerService extends Service {

    public static final String TAG = "PluginInstallerService";
    public static final String ACTION_INSTALL = "com.qiyi.neptune.action.INSTALL";

    private static final int MSG_ACTION_INSTALL = 0;
    private static final int MSG_ACTION_QUIT = 1;
    private static final int DELAY_QUIT_TIME = 1000 * 30;  // 30s

    private volatile Looper mServiceLooper;
    private volatile ServiceHandler mServiceHandler;

    private final class ServiceHandler extends Handler {
        ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            PluginDebugLog.installLog(TAG, "handleMessage: what " + msg.what);
            if (msg.what == MSG_ACTION_INSTALL) {
                mServiceHandler.removeMessages(MSG_ACTION_QUIT); //插件准备安装，移除退出消息
                if (msg.obj instanceof Intent) {
                    onHandleIntent((Intent) msg.obj);
                }

                if (!mServiceHandler.hasMessages(MSG_ACTION_INSTALL)) {
                    // 没有其他的安装消息了, 30s之后退出Service
                    Message quit = mServiceHandler.obtainMessage(MSG_ACTION_QUIT);
                    mServiceHandler.sendMessageDelayed(quit, DELAY_QUIT_TIME);
                }
            } else if (msg.what == MSG_ACTION_QUIT) {
                stopSelf();
            }
        }
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
            mServiceHandler.removeMessages(MSG_ACTION_QUIT);
        }
        PluginDebugLog.installLog(TAG, "pluginInstallerService onStartCommand MSG_ACTION_INSTALL");
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
            PluginInstaller.handleInstall(this, srcFile, pluginInfo, new IInstallCallBack.Stub() {
                @Override
                public void onPackageInstalled(PluginLiteInfo info) throws RemoteException {
                    setInstallSuccess(info);
                }

                @Override
                public void onPackageInstallFail(PluginLiteInfo info, int failReason) throws RemoteException {
                    setInstallFail(info, failReason);
                }
            });
        }
    }

    private void setInstallFail(PluginLiteInfo info, int failReason) {
        Intent intent = new Intent(PluginPackageManager.ACTION_PACKAGE_INSTALLFAIL);
        intent.setPackage(getPackageName());
        intent.putExtra(IntentConstant.EXTRA_PKG_NAME, info.packageName);
        intent.putExtra(ErrorType.ERROR_REASON, failReason);               // 同时返回安装失败的原因
        intent.putExtra(IntentConstant.EXTRA_PLUGIN_INFO, (Parcelable) info);// 同时返回APK的插件信息
        try {
            sendBroadcast(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setInstallSuccess(PluginLiteInfo info) {
        Intent intent = new Intent(PluginPackageManager.ACTION_PACKAGE_INSTALLED);
        intent.setPackage(getPackageName());
        intent.putExtra(IntentConstant.EXTRA_PKG_NAME, info.packageName);
        intent.putExtra(IntentConstant.EXTRA_DEST_FILE, info.srcApkPath);    // 同时返回安装后的安装文件目录。
        intent.putExtra(IntentConstant.EXTRA_PLUGIN_INFO, (Parcelable) info);// 同时返回APK的插件信息
        try {
            sendBroadcast(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
