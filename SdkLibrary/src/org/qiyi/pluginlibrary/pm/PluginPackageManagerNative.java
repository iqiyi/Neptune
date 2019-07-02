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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.qiyi.pluginlibrary.error.ErrorType;
import org.qiyi.pluginlibrary.install.IActionFinishCallback;
import org.qiyi.pluginlibrary.install.IInstallCallBack;
import org.qiyi.pluginlibrary.install.IUninstallCallBack;
import org.qiyi.pluginlibrary.runtime.NotifyCenter;
import org.qiyi.pluginlibrary.utils.ContextUtils;
import org.qiyi.pluginlibrary.utils.ErrorUtil;
import org.qiyi.pluginlibrary.utils.PluginDebugLog;
import org.qiyi.pluginlibrary.utils.ProcessUtils;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.qiyi.pluginlibrary.pm.PluginPackageManagerProvider.PLUGIN_INFO_KEY;
import static org.qiyi.pluginlibrary.pm.PluginPackageManagerProvider.RESULT_KEY;

/**
 * 此类的功能和{@link PluginPackageManager}基本一致<br/>
 * 只不过同一个功能这个类可以在任何进程使用<br>
 * {@link PluginPackageManager}只能在主进程使用
 * <p>
 * 该类通过IPC与{@link PluginPackageManagerService}进行交互，
 * 实现插件的安装和卸载功能
 */
public class PluginPackageManagerNative {
    private static final String TAG = "PluginPackageManagerNative";
    private static final Object sLock = new Object();  //同步锁
    // 插件安装/卸载Action的mapping
    private static ConcurrentHashMap<String, CopyOnWriteArrayList<Action>> sActionMap =
            new ConcurrentHashMap<String, CopyOnWriteArrayList<Action>>();
    /**
     * 安装包任务队列，目前仅在启动插件时处理插件依赖时使用
     */
    private static ConcurrentLinkedQueue<PackageAction> mPackageActions =
            new ConcurrentLinkedQueue<PackageAction>();
    private boolean mIsInitialized = false;
    private Context mContext;
    private PluginPackageManager mPackageManager;
    private IPluginPackageManager mService = null;
    private ServiceConnection mServiceConnection = null;
    private Uri mProviderUri;

    private PluginPackageManagerNative() {
        // no-op
    }

    public static PluginPackageManagerNative getInstance(Context context) {

        PluginPackageManagerNative ppmn = InnerHolder.sInstance;
        ppmn.init(context);

        return ppmn;
    }

    /**
     * 执行等待中的Action
     */
    private static void executePendingAction() {
        PluginDebugLog.runtimeLog(TAG, "executePendingAction start....");
        for (Map.Entry<String, CopyOnWriteArrayList<Action>> entry : sActionMap.entrySet()) {
            if (entry != null) {
                final CopyOnWriteArrayList<Action> actions = entry.getValue();
                if (actions == null) {
                    continue;
                }

                synchronized (actions) {  // Action列表加锁同步
                    PluginDebugLog.installFormatLog(TAG, "execute %d pending actions!", actions.size());
                    Iterator<Action> iterator = actions.iterator();
                    while (iterator.hasNext()) {
                        Action action = iterator.next();
                        if (action.meetCondition()) {
                            PluginDebugLog.installFormatLog(TAG, "start doAction for pending action %s", action.toString());
                            action.doAction();
                            break;
                        } else {
                            PluginDebugLog.installFormatLog(TAG, "remove deprecate pending action from action list for %s", action.toString());
                            actions.remove(action);  // CopyOnWriteArrayList在遍历过程中不能使用iterator删除元素
                        }
                    }
                }
            }
        }
    }

    /**
     * 执行之前未执行的PackageAction操作
     */
    private static void executePackageAction(Context context) {
        PluginDebugLog.runtimeLog(TAG, "executePackageAction start....");
        Iterator<PackageAction> iterator = mPackageActions.iterator();
        while (iterator.hasNext()) {
            PackageAction action = iterator.next();
            PluginDebugLog.runtimeLog(TAG, "executePackageAction iterator: " + action.toString());
            PluginPackageManagerNative.getInstance(context).
                    packageAction(action.packageInfo, action.callBack);
            iterator.remove();
        }
    }

    private static boolean actionIsReady(Action action) {
        if (action != null) {
            String packageName = action.getPackageName();
            if (!TextUtils.isEmpty(packageName)) {
                if (sActionMap.containsKey(packageName)) {
                    List<Action> actionList = sActionMap.get(packageName);
                    if (actionList != null && actionList.indexOf(action) == 0) {
                        PluginDebugLog.log(TAG, "action is ready for " + action.toString());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean addAction(Action action) {
        if (action == null || TextUtils.isEmpty(action.getPackageName())) {
            return false;
        }

        String packageName = action.getPackageName();
        CopyOnWriteArrayList<Action> actionList = sActionMap.get(packageName);
        if (actionList == null) {
            actionList = new CopyOnWriteArrayList<Action>();
            sActionMap.put(packageName, actionList);
        }
        PluginDebugLog.log(TAG, "add action in action list for " + action.toString());
        actionList.add(action);
        return true;
    }

    private void init(@NonNull Context context) {
        if (mIsInitialized) {
            return;
        }

        mContext = context.getApplicationContext();
        mPackageManager = PluginPackageManager.getInstance(mContext);
        mProviderUri = PluginPackageManagerProvider.getUri(mContext);
        mIsInitialized = true;

        onBindService(mContext);
    }

    public void setPackageInfoManager(IPluginInfoProvider packageInfoManager) {
        PluginPackageManager.setPluginInfoProvider(packageInfoManager);
    }

    public synchronized boolean isConnected() {
        return mService != null;
    }

    private void onBindService(Context context) {
        if (context != null) {
            try {
                Intent intent = new Intent(context, PluginPackageManagerService.class);
                context.startService(intent);
                context.bindService(intent, getConnection(context), Context.BIND_AUTO_CREATE);
            } catch (Exception e) {
                // java.lang.IllegalStateException: Not allowed to start service Intent, app is in background uid UidRecord
            }
        }
    }

    /**
     * 提交一个PluginInstallAction安装插件任务
     */
    public void install(@NonNull PluginLiteInfo info, IInstallCallBack callBack) {
        PluginInstallAction action = new PluginInstallAction();
        action.observer = callBack;
        action.info = info;
        action.callbackHost = this;
        if (action.meetCondition() && addAction(action) && actionIsReady(action)) {
            action.doAction();
        }
    }

    /**
     * 能否安装这个插件
     */
    private boolean canInstallPackage(PluginLiteInfo info) {
        if (isConnected()) {
            try {
                return mService.canInstallPackage(info);
            } catch (RemoteException e) {
                // ignore
            }
        }
        PluginDebugLog.runtimeLog(TAG, "canInstallPackage, service is disconnected, need rebind");
        onBindService(mContext);
        // 通过ContentProvider查询
        Bundle extras = new Bundle();
        extras.putParcelable(PLUGIN_INFO_KEY, info);
        Bundle result = callRemoteProvider(PluginPackageManagerProvider.CAN_INSTALL_PACKAGE, "", extras);
        if (result != null) {
            result.setClassLoader(PluginLiteInfo.class.getClassLoader());
            return result.getBoolean(RESULT_KEY, true);
        }
        // default true
        return true;
    }

    /**
     * 通过aidl调用{@link PluginPackageManagerService}进行安装
     */
    private void installInternal(PluginLiteInfo info, IInstallCallBack listener) {
        if (isConnected()) {
            try {
                mService.install(info, listener);
                return;
            } catch (RemoteException e) {
                // ignore
            }
        }
        PluginDebugLog.runtimeLog(TAG, "installInternal, service is disconnected, need rebind");
        onBindService(mContext);
    }

    /**
     * 提交一个PluginUninstallAction删除插件apk数据的Action
     * 只会删除插件apk，dex和so库
     */
    public void deletePackage(@NonNull PluginLiteInfo info, IUninstallCallBack observer) {
        PluginUninstallAction action = new PluginUninstallAction();
        action.info = info;
        action.callbackHost = this;
        action.deleteData = false;
        action.observer = observer;
        if (action.meetCondition() && addAction(action) && actionIsReady(action)) {
            action.doAction();
        }
    }

    /**
     * 提交一个PluginUninstallAction卸载插件的Action
     * 卸载插件会清除插件所有相关数据，包括缓存的数据
     */
    public void uninstall(@NonNull PluginLiteInfo info, IUninstallCallBack observer) {
        PluginUninstallAction action = new PluginUninstallAction();
        action.info = info;
        action.callbackHost = this;
        action.deleteData = true;
        action.observer = observer;
        if (action.meetCondition() && addAction(action) && actionIsReady(action)) {
            action.doAction();
        }
    }

    /**
     * 能否卸载这个插件
     */
    private boolean canUninstallPackage(PluginLiteInfo info) {
        if (isConnected()) {
            try {
                return mService.canUninstallPackage(info);
            } catch (RemoteException e) {
                // ignore
            }
        }
        PluginDebugLog.runtimeLog(TAG, "canUninstallPackage, service is disconnected, need rebind");
        onBindService(mContext);
        // 通过ContentProvider查询
        Bundle extras = new Bundle();
        extras.putParcelable(PLUGIN_INFO_KEY, info);
        Bundle result = callRemoteProvider(PluginPackageManagerProvider.CAN_UNINSTALL_PACAKGE, "", extras);
        if (result != null) {
            result.setClassLoader(PluginLiteInfo.class.getClassLoader());
            return result.getBoolean(RESULT_KEY, true);
        }
        // default true
        return true;
    }

    /**
     * 通过aidl调用{@link PluginPackageManagerService}进行删除插件apk
     */
    private void deletePackageInternal(PluginLiteInfo info, IUninstallCallBack callback) {
        if (isConnected()) {
            try {
                mService.deletePackage(info, callback);
                return;
            } catch (RemoteException e) {
                // ignore
            }
        }
        PluginDebugLog.runtimeLog(TAG, "deletePackageInternal, service is disconnected, need rebind");
        onBindService(mContext);
    }

    /**
     * 通过aidl调用{@link PluginPackageManagerService}进行卸载
     */
    private void uninstallInternal(PluginLiteInfo info, IUninstallCallBack callback) {
        if (isConnected()) {
            try {
                mService.uninstall(info, callback);
                return;
            } catch (RemoteException e) {
                // ignore
            }
        }
        PluginDebugLog.runtimeLog(TAG, "uninstallInternal, service is disconnected, need rebind");
        onBindService(mContext);
    }

    /**
     * 执行action操作，异步执行，如果service不存在，待连接之后执行。
     */
    public void packageAction(PluginLiteInfo packageInfo, IInstallCallBack callback) {
        if (isConnected()) {
            try {
                PluginDebugLog.runtimeLog(TAG, "packageAction service is connected and not null, call remote service");
                mService.packageAction(packageInfo, callback);
                return;
            } catch (RemoteException e) {
                // ignore
            }
        }
        PluginDebugLog.runtimeLog(TAG, "packageAction service is disconnected, need to rebind");
        addPackageAction(packageInfo, callback);
        onBindService(mContext);
    }

    private void addPackageAction(PluginLiteInfo info, IInstallCallBack callback) {
        PackageAction action = new PackageAction();
        action.time = System.currentTimeMillis();
        action.packageInfo = info;
        action.callBack = callback;
        mPackageActions.add(action);
        clearExpiredPkgAction();
    }

    private void clearExpiredPkgAction() {
        long currentTime = System.currentTimeMillis();
        synchronized (this) {
            Iterator<PackageAction> iterator = mPackageActions.iterator();
            while (iterator.hasNext()) {
                PackageAction action = iterator.next();
                if (currentTime - action.time >= 60 * 1000) {// 1分钟
                    PluginDebugLog.runtimeLog(TAG, "packageAction is expired, remove it");
                    if (action.callBack != null) {
                        try {
                            action.callBack.onPackageInstallFail(action.packageInfo,
                                    ErrorType.INSTALL_ERROR_CLIENT_TIME_OUT);
                        } catch (RemoteException e) {
                            // ignore
                        }
                    }
                    iterator.remove();
                }
            }
        }
    }

    private ServiceConnection getConnection(Context context) {
        if (mServiceConnection == null) {
            mServiceConnection = new PluginPackageManagerServiceConnection(context);
        }
        return mServiceConnection;
    }

    public void release() {
        Context applicationContext = mContext.getApplicationContext();
        if (applicationContext != null) {
            if (mServiceConnection != null) {
                try {
                    applicationContext.unbindService(mServiceConnection);
                } catch (Exception e) {
                    // ignore
                }
                mServiceConnection = null;
            }
            Intent intent = new Intent(applicationContext, PluginPackageManagerService.class);
            applicationContext.stopService(intent);
        }
    }

    /**
     * 获取已经安装的插件列表，通过aidl到{@link PluginPackageManager}中获取值，
     * 如果service不存在，直接在sharedPreference中读取值，并且启动service
     * @return 返回所有安装插件信息
     */
    public List<PluginLiteInfo> getInstalledApps() {
        if (isConnected()) {
            try {
                return mService.getInstalledApps();
            } catch (RemoteException e) {
                // ignore
            }
        }
        PluginDebugLog.runtimeLog(TAG, "getInstalledApps, service is disconnected, need rebind");
        onBindService(mContext);
        // 通过ContentProvider获取数据
        Bundle result = callRemoteProvider(PluginPackageManagerProvider.GET_INSTALLED_APPS, "");
        List<PluginLiteInfo> installedList = null;
        if (result != null) {
            result.setClassLoader(PluginLiteInfo.class.getClassLoader());
            installedList = result.getParcelableArrayList(RESULT_KEY);
        }
        // fallback to current process method
        if (installedList == null || installedList.isEmpty()) {
            // 调用当前进程的方法处理
            installedList = mPackageManager.getInstalledPackagesDirectly();
        }
        return installedList;
    }

    /**
     * 获取插件依赖关系
     */
    public List<String> getPluginRefs(String pkgName) {
        if (isConnected()) {
            try {
                return mService.getPluginRefs(pkgName);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        onBindService(mContext);
        return mPackageManager.getPluginRefsDirectly(pkgName);
    }

    /**
     * 判断某个插件是否已经安装，通过aidl到{@link PluginPackageManagerService}中获取值，如果service不存在，
     * 直接在sharedPreference中读取值，并且启动service
     *
     * @param pkgName 插件包名
     * @return 返回是否安装
     */
    public boolean isPackageInstalled(String pkgName) {
        if (isConnected()) {
            try {
                return mService.isPackageInstalled(pkgName);
            } catch (RemoteException e) {
                // ignore
            }
        }
        PluginDebugLog.runtimeLog(TAG, "isPackageInstalled, service is disconnected, need rebind");
        onBindService(mContext);
        boolean isInstalled = false;
        if (ProcessUtils.isMainProcess(mContext)) {
            isInstalled = mPackageManager.isPackageInstalled(pkgName);
        } else {
            // 其他进程通过ContentProvider处理
            Bundle result = callRemoteProvider(PluginPackageManagerProvider.IS_PACKAGE_INSTALLED, pkgName);
            if (result != null) {
                result.setClassLoader(PluginLiteInfo.class.getClassLoader());
                isInstalled = result.getBoolean(RESULT_KEY, false);
            }
        }
        return isInstalled;
    }

    /**
     * 判断某个插件是否可用，如果插件正在执行安装/卸载操作，则认为不可用
     */
    public boolean isPackageAvailable(String pkgName) {

        if (sActionMap.containsKey(pkgName) && !TextUtils.isEmpty(pkgName)) {
            List<Action> actions = sActionMap.get(pkgName);
            if (actions != null && actions.size() > 0) {
                PluginDebugLog.log(TAG, actions.size() + " actions in action list for " + pkgName + " isPackageAvailable : true");
                if (PluginDebugLog.isDebug()) {
                    for (int index = 0; index < actions.size(); index++) {
                        Action action = actions.get(index);
                        if (action != null) {
                            PluginDebugLog.log(TAG, index + " action in action list: " + action.toString());
                        }
                    }
                }
                return false;
            }
        }

        boolean available = isPackageInstalled(pkgName);
        PluginDebugLog.log(TAG, pkgName + " isPackageAvailable : " + available);
        return available;
    }

    /**
     * 根据应用包名，获取插件信息，通过aidl到PackageManagerService中获取值，如果service不存在，
     * 直接在sharedPreference中读取值，并且启动service
     *
     * @param pkg 插件包名
     * @return 返回插件信息
     */
    public PluginLiteInfo getPackageInfo(String pkg) {
        if (isConnected()) {
            try {
                PluginDebugLog.runtimeLog(TAG, "getPackageInfo service is connected and not null, call remote service");
                return mService.getPackageInfo(pkg);
            } catch (RemoteException e) {
                // ignore
            }
        }
        PluginDebugLog.runtimeLog(TAG, "getPackageInfo, service is disconnected, need rebind");
        onBindService(mContext);
        // 通过ContentProvider获取PluginLiteInfo
        Bundle result = callRemoteProvider(PluginPackageManagerProvider.GET_PACKAGE_INFO, pkg);
        PluginLiteInfo info = null;
        if (result != null) {
            result.setClassLoader(PluginLiteInfo.class.getClassLoader());
            info = result.getParcelable(RESULT_KEY);
        }
        // still null, fallback to current process method
        if (info == null) {
            info = mPackageManager.getPackageInfoDirectly(pkg);
        }
        return info;
    }

    /**
     * 获取插件的{@link android.content.pm.PackageInfo}
     */
    public PluginPackageInfo getPluginPackageInfo(String packageName) {
        PluginLiteInfo pluginInfo = getPackageInfo(packageName);
        PluginPackageInfo target = null;
        if (pluginInfo != null) {
            target = getPluginPackageInfo(mContext, pluginInfo);
        }

        return target;
    }

    /**
     * 获取插件的{@link android.content.pm.PackageInfo}
     */
    public PluginPackageInfo getPluginPackageInfo(Context context, PluginLiteInfo mPackageInfo) {
        if (mPackageInfo == null || TextUtils.isEmpty(mPackageInfo.packageName)) {
            return null;
        }

        String pkgName = mPackageInfo.packageName;
        if (isConnected()) {
            try {
                return mService.getPluginPackageInfo(pkgName);
            } catch (RemoteException e) {
                // ignore
            }
        }
        PluginDebugLog.runtimeLog(TAG, "getPluginPackageInfo, service is disconnected, need rebind");
        onBindService(mContext);
        // 通过ContentProvider获取
        Bundle result = callRemoteProvider(PluginPackageManagerProvider.GET_PLUGIN_PACKAGE_INFO, pkgName);
        PluginPackageInfo target = null;
        if (result != null) {
            result.setClassLoader(PluginPackageInfo.class.getClassLoader());
            target = result.getParcelable(RESULT_KEY);
        }
        // still null fallback to current process method
        if (target == null) {
            PluginPackageManager.updateSrcApkPath(context, mPackageInfo);
            if (!TextUtils.isEmpty(mPackageInfo.srcApkPath)) {
                File file = new File(mPackageInfo.srcApkPath);
                if (file.exists()) {
                    target = new PluginPackageInfo(ContextUtils.getOriginalContext(mContext), file);
                }
            }
        }
        return target;
    }

    /**
     * 调用远程ContentProvider进行ipc获取信息
     */
    private Bundle callRemoteProvider(String method, String args) {
        return callRemoteProvider(method, args, new Bundle());
    }

    /**
     * 调用远程ContentProvider进行ipc获取信息
     */
    private Bundle callRemoteProvider(String method, String args, Bundle extras) {
        Bundle result = null;
        try {
            result = mContext.getContentResolver().call(mProviderUri, method, args, extras);
        } catch (Exception e) {
            ErrorUtil.throwErrorIfNeed(e);
        }
        return result;
    }

    private interface Action {
        String getPackageName();

        boolean meetCondition();

        void doAction();
    }

    /**
     * Action执行完毕回调
     */
    private static class ActionFinishCallback extends IActionFinishCallback.Stub {

        private String mProcessName;

        private Executor mActionExecutor;

        public ActionFinishCallback(String processName) {
            mProcessName = processName;
            mActionExecutor = Executors.newFixedThreadPool(1);
        }

        @Override
        public void onActionComplete(PluginLiteInfo info, int resultCode) throws RemoteException {
            String pkgName = info.packageName;
            PluginDebugLog.installFormatLog(TAG, "onActionComplete with %s, resultCode: %d", pkgName, resultCode);
            if (sActionMap.containsKey(pkgName)) {
                final CopyOnWriteArrayList<Action> actions = sActionMap.get(pkgName);
                if (actions == null) {
                    return;
                }

                synchronized (actions) {  // Action列表加锁同步
                    PluginDebugLog.installFormatLog(TAG, "%s has %d action in list!", pkgName, actions.size());
                    if (actions.size() > 0) {
                        Action finishedAction = actions.remove(0);
                        if (finishedAction != null) {
                            PluginDebugLog.installFormatLog(TAG,
                                    "get and remove first action:%s ", finishedAction.toString());
                        }

                        if (actions.isEmpty()) {
                            PluginDebugLog.installFormatLog(TAG,
                                    "onActionComplete remove empty action list of %s", pkgName);
                            sActionMap.remove(pkgName);
                        } else {
                            // 执行下一个Action操作，不能同步，否则容易出现栈溢出
                            executeNextAction(actions, pkgName);
                        }
                    }
                }
            }
        }

        /**
         * 异步执行下一个Action
         */
        private void executeNextAction(final CopyOnWriteArrayList<Action> actions, final String packageName) {
            mActionExecutor.execute(new Runnable() {
                @Override
                public void run() {

                    synchronized (actions) {  // Action列表加锁同步
                        if (actions.size() > 0) {
                            PluginDebugLog.installFormatLog(TAG, "start find can execute action ...");
                            Iterator<Action> iterator = actions.iterator();
                            while (iterator.hasNext()) {
                                Action action = iterator.next();
                                if (action.meetCondition()) {
                                    PluginDebugLog.installFormatLog(TAG,
                                            "doAction for %s and action is %s", packageName,
                                            action.toString());
                                    action.doAction();
                                    break;  //跳出循环
                                } else {
                                    PluginDebugLog.installFormatLog(TAG,
                                            "remove deprecate action of %s,and action:%s "
                                            , packageName, action.toString());
                                    actions.remove(action);
                                }
                            }

                            if (actions.isEmpty()) {
                                PluginDebugLog.installFormatLog(TAG,
                                        "executeNextAction remove empty action list of %s", packageName);
                                sActionMap.remove(packageName);
                            }
                        }
                    }
                }
            });
        }

        @Override
        public String getProcessName() throws RemoteException {
            return mProcessName;
        }
    }

    /**
     * 插件安装的Action
     */
    private static class PluginInstallAction implements Action {

        public IInstallCallBack observer;
        public PluginLiteInfo info;
        public PluginPackageManagerNative callbackHost;

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("PluginInstallAction: ")
                    .append(" has IInstallCallBack: ").append(observer != null)
                    .append(" packageName: ").append(info.packageName)
                    .append(" plugin_ver: ").append(info.pluginVersion)
                    .append(" plugin_gray_version: ").append(info.pluginGrayVersion);
            return builder.toString();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }

            PluginInstallAction action = (PluginInstallAction) obj;
            return TextUtils.equals(this.info.packageName, action.info.packageName)
                    && TextUtils.equals(this.info.pluginVersion, action.info.pluginVersion);
        }

        @Override
        public String getPackageName() {
            return info.packageName;
        }

        @Override
        public boolean meetCondition() {
            boolean canMeetCondition = false;
            boolean serviceConnected = callbackHost.isConnected();
            if (serviceConnected) {
                canMeetCondition = callbackHost.canInstallPackage(info);
            } else {
                // set canMeetCondition to true in case of
                // PluginPackageManagerService
                // is not connected, so that the action can be added in action list.
                canMeetCondition = true;
            }
            PluginDebugLog.installFormatLog(TAG, "%s 's PluginInstallAction meetCondition:%s",
                    info.packageName, String.valueOf(canMeetCondition));
            return canMeetCondition;
        }

        @Override
        public void doAction() {
            PluginDebugLog.installFormatLog(TAG, "PluginInstallAction for plugin %s is ready to execute", info.packageName);
            if (callbackHost != null) {
                callbackHost.installInternal(info, observer);
            }
        }
    }

    /**
     * 插件卸载的Action
     */
    private static class PluginUninstallAction implements Action {

        public PluginLiteInfo info;
        public PluginPackageManagerNative callbackHost;
        public boolean deleteData;
        IUninstallCallBack observer;

        @Override
        public String getPackageName() {
            return info.packageName;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("PluginUninstallAction: ")
                    .append(" has IPackageDeleteObserver: ").append(observer != null)
                    .append(" deleteData").append(deleteData)
                    .append(" packageName: ").append(info.packageName)
                    .append(" plugin_ver: ").append(info.pluginVersion)
                    .append(" plugin_gray_ver: ").append(info.pluginGrayVersion);

            return builder.toString();
        }

        @Override
        public boolean meetCondition() {
            boolean canMeetCondition = false;
            boolean serviceConnected = callbackHost.isConnected();
            if (serviceConnected) {
                canMeetCondition = callbackHost.canUninstallPackage(info);
            } else {
                // set canMeetCondition to true in case of
                // PluginPackageManagerService
                // is not connected, so that the action can be added in action list.
                canMeetCondition = true;
            }
            PluginDebugLog.installFormatLog(TAG,
                    "%s 's PluginDeleteAction canMeetCondition %s", info.packageName, canMeetCondition);
            return canMeetCondition;
        }

        @Override
        public void doAction() {
            if (callbackHost != null) {
                if (deleteData) {
                    callbackHost.uninstallInternal(info, observer);
                } else {
                    callbackHost.deletePackageInternal(info, observer);
                }
            }
        }
    }

    private static class InnerHolder {
        @SuppressWarnings("StaticFieldLeak")
        private static PluginPackageManagerNative sInstance = new PluginPackageManagerNative();
    }

    /**
     * 与{@link PluginPackageManagerService}交互的ServiceConnection
     */
    private class PluginPackageManagerServiceConnection implements ServiceConnection {

        private Context mContext;
        private ExecutorService mActionExecutor;
        private IBinder.DeathRecipient mDeathRecipient = new IBinder.DeathRecipient() {
            @Override
            public void binderDied() {
                synchronized (sLock) {
                    if (mService != null) {
                        mService.asBinder().unlinkToDeath(this, 0); //注销监听
                    }
                    mService = null;
                    PluginDebugLog.runtimeLog(TAG, "binderDied called, remote binder is died");
                }
            }
        };

        PluginPackageManagerServiceConnection(Context context) {
            mContext = context;
            mActionExecutor = Executors.newFixedThreadPool(1);
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (sLock) {
                mService = IPluginPackageManager.Stub.asInterface(service);
                try {
                    // 监听远程Binder死亡通知
                    service.linkToDeath(mDeathRecipient, 0);
                } catch (RemoteException e) {
                    // ignore
                }

                PluginDebugLog.runtimeLog(TAG, "onServiceConnected called");
                if (mService != null) {
                    try {
                        String processName = ProcessUtils.getCurrentProcessName(mContext);
                        mService.setActionFinishCallback(new ActionFinishCallback(processName));
                        NotifyCenter.notifyServiceConnected(mContext, PluginPackageManagerService.class.getName());
                    } catch (Exception e) {
                        // ignore
                    }
                    // 异步执行在等待中的任务
                    mActionExecutor.submit(new Runnable() {
                        @Override
                        public void run() {
                            executePackageAction(mContext);
                            executePendingAction();
                        }
                    });
                } else {
                    PluginDebugLog.runtimeLog(TAG, "onServiceConnected, mService is null");
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            synchronized (sLock) {
                mService = null;
                PluginDebugLog.runtimeLog(TAG, "onServiceDisconnected called");
            }
        }
    }

    /**
     * 包依赖任务队列对象。
     */
    private class PackageAction {
        long time;                // 时间戳
        IInstallCallBack callBack;// 安装回调
        PluginLiteInfo packageInfo;//插件信息

        @Override
        public String toString() {
            return "{time: " + time + ", info: " + packageInfo.packageName;
        }
    }
}
