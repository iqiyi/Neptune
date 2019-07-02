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

import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;
import android.text.TextUtils;

import org.qiyi.pluginlibrary.component.stackmgr.PServiceSupervisor;
import org.qiyi.pluginlibrary.component.stackmgr.PluginServiceWrapper;
import org.qiyi.pluginlibrary.constant.IntentConstant;
import org.qiyi.pluginlibrary.context.PluginContextWrapper;
import org.qiyi.pluginlibrary.error.ErrorType;
import org.qiyi.pluginlibrary.runtime.NotifyCenter;
import org.qiyi.pluginlibrary.runtime.PluginLoadedApk;
import org.qiyi.pluginlibrary.runtime.PluginManager;
import org.qiyi.pluginlibrary.utils.ErrorUtil;
import org.qiyi.pluginlibrary.utils.IntentUtils;
import org.qiyi.pluginlibrary.utils.PluginDebugLog;
import org.qiyi.pluginlibrary.utils.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * :plugin1 进程的Service代理
 */
public class ServiceProxy1 extends Service {
    private static final String TAG = ServiceProxy1.class.getSimpleName();

    private static ConcurrentMap<String, Vector<Method>> sMethods = new ConcurrentHashMap<String, Vector<Method>>(2);

    private boolean mKillProcessOnDestroy = false;


    @Override
    public void onCreate() {
        PluginDebugLog.log(TAG, "ServiceProxy1>>>>>onCreate()");
        super.onCreate();
        handleSelfLaunchPluginService();
    }

    /**
     * Must invoke on the main thread
     */
    private void handleSelfLaunchPluginService() {
        List<PluginServiceWrapper> selfLaunchServices = new ArrayList<PluginServiceWrapper>(1);
        for (PluginServiceWrapper plugin : PServiceSupervisor.getAliveServices().values()) {
            PServiceSupervisor.removeServiceByIdentity(PluginServiceWrapper.getIdentify(plugin.getPkgName(), plugin.getServiceClassName()));
            if (plugin.needSelfLaunch()) {
                selfLaunchServices.add(plugin);
            }
        }
        for (PluginServiceWrapper item : selfLaunchServices) {
            loadTargetService(item.getPkgName(), item.getServiceClassName());
        }
    }

    private PluginServiceWrapper findPluginService(String pkgName, String clsName) {
        return PServiceSupervisor.getServiceByIdentifer(PluginServiceWrapper.getIdentify(pkgName, clsName));
    }

    public PluginServiceWrapper loadTargetService(String targetPackageName, String targetClassName) {
        PluginServiceWrapper currentPlugin = findPluginService(targetPackageName, targetClassName);
        PluginDebugLog.log(TAG, "ServiceProxy1>>>>>loadTargetService() target:"
                + (currentPlugin == null ? "null" : currentPlugin.getClass().getName()));
        if (currentPlugin == null) {
            PluginDebugLog.log(TAG, "ServiceProxy1>>>>loadTargetService plugin has loaded:"
                    + PluginManager.isPluginLoaded(targetPackageName) + "; targetPackageName:" + targetPackageName);

            Service targetService;
            try {
                PluginLoadedApk mLoadedApk = PluginManager.getPluginLoadedApkByPkgName(targetPackageName);
                if (null == mLoadedApk) {
                    PluginDebugLog.log(TAG, "ServiceProxy1>>>>loadTargetService pluginLoadedApk not found for @"
                            + targetPackageName);
                    return null;
                }
                targetService = ((Service) mLoadedApk.getPluginClassLoader()
                        .loadClass(targetClassName).newInstance());
                PluginContextWrapper actWrapper = new PluginContextWrapper(ServiceProxy1.this.getBaseContext(),
                        mLoadedApk, true);
                ReflectionUtils.on(targetService).call("attach", sMethods, null, actWrapper,
                        ReflectionUtils.getFieldValue(this, "mThread"), targetClassName,
                        ReflectionUtils.getFieldValue(this, "mToken"), mLoadedApk.getPluginApplication(),
                        ReflectionUtils.getFieldValue(this, "mActivityManager"));

                PluginDebugLog.log(TAG, "load targetService success, pkgName: " + targetPackageName
                        + ", clsName: " + targetClassName);
            } catch (Exception e) {
                ErrorUtil.throwErrorIfNeed(e);
                String errMsg = "load Service class " + targetClassName + " failed: " + e.getMessage();
                PluginManager.deliver(this, false, targetPackageName,
                        ErrorType.ERROR_PLUGIN_LOAD_TARGET_SERVICE, errMsg);
                PluginDebugLog.log(TAG, "load targetService failed, pkgName: " + targetPackageName
                        + ", clsName: " + targetClassName);
                return null;
            }

            try {
                currentPlugin = new PluginServiceWrapper(targetClassName, targetPackageName, this, targetService);
                targetService.onCreate();
                currentPlugin.updateServiceState(PluginServiceWrapper.PLUGIN_SERVICE_CREATED);

                PServiceSupervisor.addServiceByIdentity(targetPackageName + "." + targetClassName, currentPlugin);

                PluginDebugLog.log(TAG, "ServiceProxy1>>>start service, pkgName: " + targetPackageName
                        + ", clsName: " + targetClassName);
            } catch (Exception e) {
                ErrorUtil.throwErrorIfNeed(e);
                String errMsg = "call Service " + targetClassName + "#onCreate() failed: " + e.getMessage();
                PluginManager.deliver(this, false, targetPackageName,
                        ErrorType.ERROR_PLUGIN_CREATE_TARGET_SERVICE, errMsg);
                PluginDebugLog.log(TAG, "call targetService#onCreate failed, pkgName: " + targetPackageName
                        + ", clsName: " + targetClassName);
                return null;
            }
        }
        return currentPlugin;
    }

    @Override
    public IBinder onBind(Intent paramIntent) {

        PluginDebugLog.log(TAG, "ServiceProxy1>>>>>onBind():" + (paramIntent == null ? "null" : paramIntent));
        mKillProcessOnDestroy = false;
        if (paramIntent == null) {
            return null;
        }
        final String[] pkgAndCls = IntentUtils.parsePkgAndClsFromIntent(paramIntent);
        String targetPackageName = "";
        String targetClassName;
        if (pkgAndCls != null && pkgAndCls.length == 2) {
            targetPackageName = pkgAndCls[0];
            targetClassName = pkgAndCls[1];
            PluginServiceWrapper currentPlugin = loadTargetService(targetPackageName, targetClassName);

            if (currentPlugin != null && currentPlugin.getCurrentService() != null) {
                currentPlugin.updateBindCounter(1);
                return currentPlugin.getCurrentService().onBind(paramIntent);
            }
        }
        // 返回fake binder，否则后续的bindService都收不到ServiceConnection回调
        PluginDebugLog.log(TAG, "ServiceProxy1>>>>>onBind(): return fake binder due to currentPlugin is null, pkg: " + targetPackageName);
        return null;
    }

    @Override
    public void onConfigurationChanged(Configuration paramConfiguration) {
        ConcurrentMap<String, PluginServiceWrapper> aliveServices =
                PServiceSupervisor.getAliveServices();
        if (aliveServices != null) {
            // Notify all alive plugin service
            for (PluginServiceWrapper plugin : aliveServices.values()) {
                if (plugin != null && plugin.getCurrentService() != null) {
                    plugin.getCurrentService().onConfigurationChanged(paramConfiguration);
                }
            }
        } else {
            super.onConfigurationChanged(paramConfiguration);
        }
    }

    @Override
    public void onDestroy() {
        PluginDebugLog.log(TAG, "onDestroy " + getClass().getName());
        ConcurrentMap<String, PluginServiceWrapper> aliveServices =
                PServiceSupervisor.getAliveServices();
        if (aliveServices != null) {
            // Notify all alive plugin service to do destroy
            for (PluginServiceWrapper plugin : aliveServices.values()) {
                if (plugin != null && plugin.getCurrentService() != null) {
                    plugin.getCurrentService().onDestroy();
                }
            }
            PServiceSupervisor.clearServices();
        }
        super.onDestroy();
        if (mKillProcessOnDestroy) {
            Process.killProcess(Process.myPid());
        }
    }

    @Override
    public void onLowMemory() {
        if (PServiceSupervisor.getAliveServices().size() > 0) {
            // Notify all alive plugin service to do destroy
            for (PluginServiceWrapper plugin : PServiceSupervisor.getAliveServices().values()) {
                if (plugin != null && plugin.getCurrentService() != null) {
                    plugin.getCurrentService().onLowMemory();
                }
            }
        } else {
            super.onLowMemory();
        }
    }

    @Override
    public int onStartCommand(Intent paramIntent, int paramInt1, int paramInt2) {
        PluginDebugLog.log(TAG, "ServiceProxy1>>>>>onStartCommand():" + (paramIntent == null ? "null" : paramIntent));
        if (paramIntent == null) {
            mKillProcessOnDestroy = false;
            super.onStartCommand(null, paramInt1, paramInt2);
            return START_NOT_STICKY;
        }
        // 退出Service
        if (TextUtils.equals(IntentConstant.ACTION_QUIT_SERVICE, paramIntent.getAction())) {
            PluginDebugLog.runtimeLog(TAG, "service " + getClass().getName() + " received quit intent action");
            mKillProcessOnDestroy = true;
            stopSelf();
            return START_NOT_STICKY;
        }
        // 启动插件
        if (TextUtils.equals(IntentConstant.ACTION_START_PLUGIN, paramIntent.getAction())) {
            PluginDebugLog.runtimeLog(TAG, "service " + getClass().getName() + " received start plugin intent action");
            String processName = paramIntent.getStringExtra(IntentConstant.EXTRA_TARGET_PROCESS);
            Intent launchIntent = paramIntent.getParcelableExtra(IntentConstant.EXTRA_START_INTENT_KEY);
            if (!TextUtils.isEmpty(processName) && launchIntent != null) {
                launchIntent.setExtrasClassLoader(this.getClass().getClassLoader());
                PluginManager.launchPlugin(this, launchIntent, processName);
            }
            return START_NOT_STICKY;
        }

        final String[] pkgAndCls = IntentUtils.parsePkgAndClsFromIntent(paramIntent);
        String targetPackageName = "";
        String targetClassName = "";
        if (pkgAndCls != null && pkgAndCls.length == 2) {
            targetPackageName = pkgAndCls[0];
            targetClassName = pkgAndCls[1];
        }
        
        PluginServiceWrapper currentPlugin = loadTargetService(targetPackageName, targetClassName);
        PluginDebugLog.log(TAG, "ServiceProxy1>>>>>onStartCommand() currentPlugin: " + currentPlugin);
        if (currentPlugin != null && currentPlugin.getCurrentService() != null) {
            currentPlugin.updateServiceState(PluginServiceWrapper.PLUGIN_SERVICE_STARTED);
            int result = currentPlugin.getCurrentService().onStartCommand(paramIntent, paramInt1, paramInt2);
            PluginDebugLog.log(TAG, "ServiceProxy1>>>>>onStartCommand() result: " + result);
            if (result == START_REDELIVER_INTENT || result == START_STICKY) {
                currentPlugin.setSelfLaunch(true);
            }
            mKillProcessOnDestroy = false;
            return START_NOT_STICKY;
        } else {
            PluginDebugLog.log(TAG, "ServiceProxy1>>>>>onStartCommand() currentPlugin is null!");
            mKillProcessOnDestroy = false;
            super.onStartCommand(paramIntent, paramInt1, paramInt2);
            return START_NOT_STICKY;
        }
    }

    @Override
    public boolean onUnbind(Intent paramIntent) {
        PluginDebugLog.log(TAG, "ServiceProxy1>>>>>onUnbind():" + (paramIntent == null ? "null" : paramIntent));
        boolean result = false;
        if (null != paramIntent) {
            String targetClassName = IntentUtils.getTargetClass(paramIntent);
            String targetPackageName = IntentUtils.getTargetPackage(paramIntent);
            PluginServiceWrapper plugin = findPluginService(targetPackageName, targetClassName);
            if (plugin != null && plugin.getCurrentService() != null) {
                plugin.updateBindCounter(-1);
                result = plugin.getCurrentService().onUnbind(paramIntent);
                plugin.tryToDestroyService();
            }
        }
        super.onUnbind(paramIntent);
        return result;
    }

    @Override
    public void onStart(Intent intent, int startId) {
        PluginDebugLog.log(TAG, "ServiceProxy1>>>>>onStart():" + (intent == null ? "null" : intent));
        if (intent == null) {
            super.onStart(null, startId);
            return;
        }
        String targetClassName = IntentUtils.getTargetClass(intent);
        String targetPackageName = IntentUtils.getTargetPackage(intent);
        PluginServiceWrapper currentPlugin = loadTargetService(targetPackageName, targetClassName);

        if (currentPlugin != null && currentPlugin.getCurrentService() != null) {
            currentPlugin.updateServiceState(PluginServiceWrapper.PLUGIN_SERVICE_STARTED);
            currentPlugin.getCurrentService().onStart(intent, startId);
        }
        super.onStart(intent, startId);
    }

    @Override
    public void onTrimMemory(int level) {
        if (PServiceSupervisor.getAliveServices().size() > 0) {
            // Notify all alive plugin service to do onTrimMemory
            for (PluginServiceWrapper plugin : PServiceSupervisor.getAliveServices().values()) {
                if (plugin != null && plugin.getCurrentService() != null) {
                    plugin.getCurrentService().onTrimMemory(level);
                }
            }
        } else {
            super.onTrimMemory(level);
        }
    }

    @Override
    public void onRebind(Intent intent) {
        PluginDebugLog.log(TAG, "ServiceProxy1>>>>>onRebind():" + (intent == null ? "null" : intent));
        if (intent == null) {
            super.onRebind(null);
            return;
        }
        String targetClassName = IntentUtils.getTargetClass(intent);
        String targetPackageName = IntentUtils.getTargetPackage(intent);
        PluginServiceWrapper currentPlugin = findPluginService(targetPackageName, targetClassName);

        if (currentPlugin != null && currentPlugin.getCurrentService() != null) {
            currentPlugin.updateBindCounter(1);
            currentPlugin.getCurrentService().onRebind(intent);
        }
        super.onRebind(intent);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
    }
}
