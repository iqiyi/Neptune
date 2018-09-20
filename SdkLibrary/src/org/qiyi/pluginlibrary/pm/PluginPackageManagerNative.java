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
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.qiyi.pluginlibrary.error.ErrorType;
import org.qiyi.pluginlibrary.install.IActionFinishCallback;
import org.qiyi.pluginlibrary.install.IInstallCallBack;
import org.qiyi.pluginlibrary.runtime.NotifyCenter;
import org.qiyi.pluginlibrary.utils.ContextUtils;
import org.qiyi.pluginlibrary.utils.FileUtils;
import org.qiyi.pluginlibrary.utils.PluginDebugLog;

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
     * 安装包任务队列，目前仅处理插件依赖时使用
     */
    private static ConcurrentLinkedQueue<ExecutionPackageAction> mPackageActions =
            new ConcurrentLinkedQueue<ExecutionPackageAction>();
    private boolean mIsInitialized = false;
    private Context mContext;
    private PluginPackageManager mPackageManager;
    private IPluginPackageManager mService = null;
    private ServiceConnection mServiceConnection = null;
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
                CopyOnWriteArrayList<Action> actions = entry.getValue();
                PluginDebugLog.installFormatLog(TAG, "execute %d pending actions!", actions.size());
                Iterator<Action> iterator = actions.iterator();
                while (iterator.hasNext()) {
                    Action action = iterator.next();
                    if (action == null) {
                        continue;
                    }
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

    /**
     * 执行之前未执行的PackageAction操作
     */
    private static void executePackageAction(Context context) {
        if (context != null) {
            PluginDebugLog.runtimeLog(TAG, "executePackageAction start....");
            Iterator<ExecutionPackageAction> iterator = mPackageActions.iterator();
            while (iterator.hasNext()) {
                ExecutionPackageAction action = iterator.next();
                ActionType type = action.type;
                PluginDebugLog.runtimeLog(TAG, "executePackageAction iterator, actionType: " + type);
                switch (type) {
                    case PACKAGE_ACTION:
                        PluginPackageManagerNative.getInstance(context).
                                packageAction(action.packageInfo, action.callBack);
                        break;
                    default:
                        break;
                }
                iterator.remove();
            }
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
                // ignore
            }
        }
    }

    /**
     * 提交一个PluginInstallAction安装插件任务
     */
    public void install(PluginLiteInfo info, IInstallCallBack listener) {
        PluginInstallAction action = new PluginInstallAction();
        action.listener = listener;
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
        onBindService(mContext);
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
        onBindService(mContext);
    }

    /**
     * 提交一个PluginUninstallAction卸载插件的Action
     */
    public void uninstall(PluginLiteInfo info, IPluginUninstallCallBack observer) {
        PluginUninstallAction action = new PluginUninstallAction();
        action.info = info;
        action.callbackHost = this;
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
        onBindService(mContext);
        return true;
    }

    /**
     * 通过aidl调用{@link PluginPackageManagerService}进行卸载
     */
    private void uninstallInternal(PluginLiteInfo info) {
        if (isConnected()) {
            try {
                mService.uninstall(info);
                return;
            } catch (RemoteException e) {
                // ignore
            }
        }

        onBindService(mContext);
    }

    /**
     * 执行action操作，异步执行，如果service不存在，待连接之后执行。
     */
    public void packageAction(PluginLiteInfo packageInfo, IInstallCallBack callBack) {
        if (isConnected()) {
            try {
                PluginDebugLog.runtimeLog(TAG, "packageAction service is connected and not null, call remote service");
                mService.packageAction(packageInfo, callBack);
                return;
            } catch (RemoteException e) {
                // ignore
            }
        }
        PluginDebugLog.runtimeLog(TAG, "packageAction service is disconnected, need to rebind");
        ExecutionPackageAction action = new ExecutionPackageAction();
        action.type = ActionType.PACKAGE_ACTION;
        action.time = System.currentTimeMillis();
        action.packageInfo = packageInfo;
        action.callBack = callBack;
        packageActionModified(action);
        onBindService(mContext);
    }

    private void packageActionModified(ExecutionPackageAction action) {
        mPackageActions.add(action);
        clearExpiredPkgAction();
    }

    private void clearExpiredPkgAction() {
        long currentTime = System.currentTimeMillis();
        synchronized (this) {
            Iterator<ExecutionPackageAction> iterator = mPackageActions.iterator();
            while (iterator.hasNext()) {
                ExecutionPackageAction action = iterator.next();
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
            }
            Intent intent = new Intent(applicationContext, PluginPackageManagerService.class);
            applicationContext.stopService(intent);
        }
    }

    /**
     * 获取已经安装的插件列表，通过aidl到{@link PluginPackageManager}中获取值，
     * 如果service不存在，直接在sharedPreference中读取值，并且启动service
     *
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
        List<PluginLiteInfo> installedList = mPackageManager.getInstalledPackagesDirectly();
        onBindService(mContext);
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

        boolean isInstalled = mPackageManager.isPackageInstalledDirectly(pkgName);
        onBindService(mContext);
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
        PluginLiteInfo info = mPackageManager.getPackageInfoDirectly(pkg);
        onBindService(mContext);
        return info;

    }

    /**
     * 获取插件的{@link android.content.pm.PackageInfo}
     */
    public PluginPackageInfo getPluginPackageInfo(String packageName) {
        PluginLiteInfo pluginLiteInfo = getPackageInfo(packageName);
        PluginPackageInfo target = null;
        if (pluginLiteInfo != null) {
            target = getPluginPackageInfo(mContext, pluginLiteInfo);
        }

        return target;
    }

    /**
     * 获取插件的{@link android.content.pm.PackageInfo}
     */
    public PluginPackageInfo getPluginPackageInfo(Context context, PluginLiteInfo mPackageInfo) {
        PluginPackageInfo target = null;
        if (mPackageInfo != null && !TextUtils.isEmpty(mPackageInfo.packageName)) {
            if (isConnected()) {
                try {
                    target = mService.getPluginPackageInfo(mPackageInfo.packageName);
                } catch (RemoteException e) {
                    // ignore
                }
            } else {
                PluginPackageManager.updateSrcApkPath(context, mPackageInfo);
                if (!TextUtils.isEmpty(mPackageInfo.srcApkPath)) {
                    File file = new File(mPackageInfo.srcApkPath);
                    if (file.exists()) {
                        target = new PluginPackageInfo(ContextUtils.getOriginalContext(mContext), file);
                    }
                }
                onBindService(mContext);
            }
        }
        return target;
    }

    enum ActionType {
        INSTALL_APK_FILE, // installApkFile
        INSTALL_BUILD_IN_APPS, // installBuiltinApps
        DELETE_PACKAGE, // deletePackage
        PACKAGE_ACTION, // packageAction
        UNINSTALL_ACTION,// uninstall
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
        public void onActionComplete(String packageName, int errorCode) throws RemoteException {
            PluginDebugLog.installFormatLog(TAG, "onActionComplete with %s, errorCode:%d", packageName, errorCode);
            if (sActionMap.containsKey(packageName)) {
                final CopyOnWriteArrayList<Action> actions = sActionMap.get(packageName);
                if (actions != null && actions.size() > 0) {
                    PluginDebugLog.installFormatLog(TAG, "%s has %d action in list!", packageName, actions.size());

                    synchronized (this) {
                        Action finishedAction = actions.remove(0);
                        if (finishedAction != null) {
                            PluginDebugLog.installFormatLog(TAG,
                                    "get and remove first action:%s ", finishedAction.toString());
                        }

                        if (finishedAction instanceof PluginUninstallAction) {
                            PluginDebugLog.installFormatLog(TAG,
                                    "this is PluginUninstallAction  for :%s", packageName);
                            PluginUninstallAction uninstallAction = (PluginUninstallAction) finishedAction;
                            if (uninstallAction.observer != null && uninstallAction.info != null
                                    && !TextUtils.isEmpty(uninstallAction.info.packageName)) {
                                PluginDebugLog.installFormatLog(TAG, "PluginUninstallAction packageDeleted for %s", packageName);
                                uninstallAction.observer.onPluginUninstall(uninstallAction.info.packageName, errorCode);
                            }
                        }
                        // 执行下一个卸载操作，不能同步，防止栈溢出
                        executeNextAction(actions, packageName);

                        if (actions.isEmpty()) {
                            PluginDebugLog.installFormatLog(TAG,
                                    "remove empty action list of %s", packageName);
                            sActionMap.remove(packageName);
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

                    PluginDebugLog.installFormatLog(TAG, "start find can execute action ...");
                    Iterator<Action> iterator = actions.iterator();
                    while (iterator.hasNext()) {
                        Action action = iterator.next();
                        if (action == null) {
                            continue;
                        }

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
                                "remove empty action list of %s", packageName);
                        sActionMap.remove(packageName);
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

        public IInstallCallBack listener;
        public PluginLiteInfo info;
        public PluginPackageManagerNative callbackHost;

        @Override
        public String toString() {
            StringBuilder infoBuilder = new StringBuilder();
            infoBuilder.append("PluginInstallAction: ");
            infoBuilder.append(" has IInstallCallBack: ").append(listener != null);
            if (info != null) {
                infoBuilder.append(" packageName: ").append(info.packageName);
                infoBuilder.append(" plugin_ver: ").append(info.pluginVersion);
                infoBuilder.append(" plugin_gray_version: ").append(info.pluginGrayVersion);
            }
            return infoBuilder.toString();
        }

        @Override
        public String getPackageName() {
            return info != null ? info.packageName : null;
        }

        @Override
        public boolean meetCondition() {
            boolean canMeetCondition = false;
            boolean serviceConnected = callbackHost.isConnected();
            if (serviceConnected && info != null) {
                canMeetCondition = callbackHost.canInstallPackage(info);
            } else if (!serviceConnected) {
                // set canMeetCondition to true in case of
                // PluginPackageManagerService
                // is not connected, so that the action can be added in action list.
                canMeetCondition = true;
            }
            if (info != null) {
                PluginDebugLog.installFormatLog(TAG, "%s 's PluginInstallAction meetCondition:%s",
                        info.packageName, String.valueOf(canMeetCondition));
            }
            return canMeetCondition;
        }

        @Override
        public void doAction() {
            if (callbackHost != null) {
                callbackHost.installInternal(info, listener);
            }
        }
    }

    /**
     * 插件卸载的Action
     */
    private static class PluginUninstallAction implements Action {

        public PluginLiteInfo info;
        public PluginPackageManagerNative callbackHost;
        IPluginUninstallCallBack observer;

        @Override
        public String getPackageName() {
            return info != null ? info.packageName : null;
        }

        @Override
        public String toString() {
            StringBuilder infoBuilder = new StringBuilder();
            infoBuilder.append("PluginDeleteAction: ");
            infoBuilder.append(
                    " has IPackageDeleteObserver: ").append(observer != null);
            if (info != null) {
                infoBuilder.append(" packageName: ").append(info.packageName);
                infoBuilder.append(" plugin_ver: ").append(info.pluginVersion);
                infoBuilder.append(" plugin_gray_ver: ").append(info.pluginGrayVersion);
            }

            return infoBuilder.toString();
        }

        @Override
        public boolean meetCondition() {
            boolean canMeetCondition = false;
            boolean serviceConnected = callbackHost.isConnected();
            if (serviceConnected && info != null) {
                canMeetCondition = callbackHost.canUninstallPackage(info);
            } else if (!serviceConnected) {
                // set canMeetCondition to true in case of
                // PluginPackageManagerService
                // is not connected, so that the action can be added in action list.
                canMeetCondition = true;
            }
            if (null != info) {
                PluginDebugLog.installFormatLog(TAG,
                        "%s 's PluginDeleteAction canMeetCondition %s", info.packageName, canMeetCondition);
            }
            return canMeetCondition;
        }

        @Override
        public void doAction() {
            if (callbackHost != null) {
                callbackHost.uninstallInternal(info);
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
                    NotifyCenter.notifyServiceConnected(mContext, PluginPackageManagerService.class);
                    try {
                        String processName = FileUtils.getCurrentProcessName(mContext);
                        mService.setActionFinishCallback(new ActionFinishCallback(processName));
                    } catch (RemoteException e) {
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
    private class ExecutionPackageAction {

        ActionType type;// 类型：
        long time;// 时间；
        IInstallCallBack callBack;// 安装回调
        PluginLiteInfo packageInfo;//包名
    }
}
