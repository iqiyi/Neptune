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
package org.qiyi.pluginlibrary.pm;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;

import org.qiyi.pluginlibrary.install.IActionFinishCallback;
import org.qiyi.pluginlibrary.install.IInstallCallBack;
import org.qiyi.pluginlibrary.utils.PluginDebugLog;

import java.util.List;

/**
 * 插件安装Service管理，正常情况下此Service会一直存在，
 * 该Service运行在主进程，所有的操作都代理给
 * {@link PluginPackageManager}实现
 */
public class PluginPackageManagerService extends Service {
    private static final String TAG = "PluginPackageManagerService";
    // 插件信息管理
    private PluginPackageManager mManager;

    @Override
    public void onCreate() {
        mManager = PluginPackageManager.getInstance(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return initBinder();
    }

    private IPluginPackageManager.Stub initBinder() {
        return new IPluginPackageManager.Stub() {

            @Override
            public List<PluginLiteInfo> getInstalledApps() throws RemoteException {
                if (mManager == null) {
                    return null;
                }
                return mManager.getInstalledApps();
            }

            @Override
            public PluginLiteInfo getPackageInfo(String pkg) throws RemoteException {
                if (mManager == null || TextUtils.isEmpty(pkg)) {
                    return null;
                }
                return mManager.getPackageInfo(pkg);
            }

            @Override
            public boolean isPackageInstalled(String pkg) throws RemoteException {
                if (mManager == null || TextUtils.isEmpty(pkg)) {
                    return false;
                }
                return mManager.isPackageInstalled(pkg);
            }

            @Override
            public boolean canInstallPackage(PluginLiteInfo info) throws RemoteException {
                if (mManager == null || info == null || TextUtils.isEmpty(info.packageName)) {
                    return false;
                }
                return mManager.canInstallPackage(info);
            }

            @Override
            public boolean canUninstallPackage(PluginLiteInfo info) throws RemoteException {
                if (mManager == null || info == null || TextUtils.isEmpty(info.packageName)) {
                    return false;
                }
                return mManager.canUninstallPackage(info);
            }

            @Override
            public void install(PluginLiteInfo info, IInstallCallBack listener) throws RemoteException {
                if (mManager == null || info == null || TextUtils.isEmpty(info.packageName)) {
                    return;
                }
                mManager.install(info, listener);
            }

            @Override
            public boolean uninstall(PluginLiteInfo info) throws RemoteException {
                return mManager != null && mManager.uninstall(info);
            }

            @Override
            public void packageAction(
                    PluginLiteInfo info, IInstallCallBack callBack) throws RemoteException {
                if (mManager == null ||
                        info == null || TextUtils.isEmpty(info.packageName)) {
                    PluginDebugLog.runtimeLog(TAG, "packageAction param error, packageInfo is null or packageName is empty");
                    return;
                }
                mManager.packageAction(info, callBack);
            }

            @Override
            public void setActionFinishCallback(
                    IActionFinishCallback actionFinishCallback) throws RemoteException {
                if (mManager != null) {
                    mManager.setActionFinishCallback(actionFinishCallback);
                }
            }

            @Override
            public PluginPackageInfo getPluginPackageInfo(String pkgName) throws RemoteException {
                if (mManager != null) {
                    return mManager.getPluginPackageInfo(pkgName);
                }
                return null;
            }

            @Override
            public List<String> getPluginRefs(String pkgName) throws RemoteException {
                if (mManager != null) {
                    return mManager.getPluginRefs(pkgName);
                }
                return null;
            }
        };
    }
}
