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
package org.qiyi.pluginlibrary.component.stackmgr;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.text.TextUtils;

import org.qiyi.pluginlibrary.component.InstrActivityProxy1;
import org.qiyi.pluginlibrary.constant.IntentConstant;
import org.qiyi.pluginlibrary.runtime.IntentRequest;
import org.qiyi.pluginlibrary.runtime.PluginLoadedApk;
import org.qiyi.pluginlibrary.runtime.PluginManager;
import org.qiyi.pluginlibrary.utils.ComponentFinder;
import org.qiyi.pluginlibrary.utils.ContextUtils;
import org.qiyi.pluginlibrary.utils.IntentUtils;
import org.qiyi.pluginlibrary.utils.PluginDebugLog;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 插件的管理栈，模拟系统的{@link com.android.server.am.ActivityStackSupervisor}
 */
public class PActivityStackSupervisor {
    private static final String TAG = "PActivityStackSupervisor";

    // 等在加载的intent请求
    private static ConcurrentMap<String, LinkedBlockingQueue<IntentRequest>> sIntentCacheMap = new ConcurrentHashMap<>();
    // 正在加载中的intent请求
    private static ConcurrentMap<String, List<IntentRequest>> sIntentLoadingMap = new ConcurrentHashMap<>();

    // 当前进程所有插件的全局栈
    private static ConcurrentHashMap<String, PActivityStack> sAllActivityStacks = new ConcurrentHashMap<>();

    // 插件的Activity栈集合，支持taskAffinity多任务栈，目前只额外配置了一个坑位
    private final ConcurrentHashMap<String, PActivityStack> mActivityStacks;
    // 前台栈
    private PActivityStack mFocusedStack;
    // 后台栈
    private PActivityStack mLastFocusedStack = null;
    // 插件实例
    private PluginLoadedApk mLoadedApk;

    public PActivityStackSupervisor(PluginLoadedApk mLoadedApk) {
        this.mLoadedApk = mLoadedApk;
        this.mActivityStacks = new ConcurrentHashMap<>(1);

        String pkgName = mLoadedApk.getPluginPackageName();
        mFocusedStack = new PActivityStack(pkgName);
        mActivityStacks.put(pkgName, mFocusedStack);
    }

    public static void addCachedIntent(String pkgName, LinkedBlockingQueue<IntentRequest> cachedIntents) {
        if (TextUtils.isEmpty(pkgName) || null == cachedIntents) {
            return;
        }
        sIntentCacheMap.put(pkgName, cachedIntents);
    }

    /**
     * 获取对应插件缓存还未执行加载的Intent
     */
    public static LinkedBlockingQueue<IntentRequest> getCachedIntent(String pkgName) {
        if (TextUtils.isEmpty(pkgName)) {
            return null;
        }
        return sIntentCacheMap.get(pkgName);
    }

    /**
     * 清除等待队列，防止异常情况，导致所有Intent都阻塞在等待队列，导致插件无法启动
     *
     * @param packageName 包名
     */
    public static void clearLoadingIntent(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return;
        }
        sIntentCacheMap.remove(packageName);
    }

    /**
     * 插件是否正在loading中
     *
     * @param packageName 插件包名
     * @return 正在loading返回true，否则返回false
     */
    public static boolean isLoading(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return false;
        }

        return sIntentCacheMap.containsKey(packageName);
    }

    public static void addLoadingIntent(String pkgName, IntentRequest intent) {
        if (null == intent || TextUtils.isEmpty(pkgName)) {
            return;
        }
        List<IntentRequest> intents = sIntentLoadingMap.get(pkgName);
        if (null == intents) {
            intents = Collections.synchronizedList(new ArrayList<IntentRequest>());
            sIntentLoadingMap.put(pkgName, intents);
        }
        PluginDebugLog.runtimeLog(TAG, "addLoadingIntent pkgName: " + pkgName + " intent: " + intent);
        intents.add(intent);
    }

    public static void removeLoadingIntent(String pkgName) {
        if (TextUtils.isEmpty(pkgName)) {
            return;
        }
        sIntentLoadingMap.remove(pkgName);
    }

    public static void removeLoadingIntent(String pkgName, Intent intent) {
        if (null == intent || TextUtils.isEmpty(pkgName)) {
            return;
        }
        List<IntentRequest> intents = sIntentLoadingMap.get(pkgName);
        Intent toBeRemoved = null;
        if (null != intents) {
            for (IntentRequest request : intents) {
                Intent temp = request.getIntent();
                if (TextUtils.equals(temp.getStringExtra(IntentConstant.EXTRA_TARGET_CLASS_KEY),
                        intent.getStringExtra(IntentConstant.EXTRA_TARGET_CLASS_KEY))) {
                    toBeRemoved = temp;
                    break;
                }
            }
        }
        boolean result = false;
        if (null != toBeRemoved) {
            result = intents.remove(toBeRemoved);
        }
        PluginDebugLog.runtimeLog(TAG, "removeLoadingIntent pkgName: " + pkgName + " toBeRemoved: "
                + toBeRemoved + " result: " + result);
    }

    /**
     * 获取当前Activity对应的插件Activity的名称
     * 如果是代理Activity，则搜索对应的插件Activity实例
     * 如果本身就是插件Activity，直接返回即可
     */
    private static String getActivityStackKey(Activity activity) {
        String key = "";
        if (activity instanceof InstrActivityProxy1) {
            InstrActivityProxy1 proxy = (InstrActivityProxy1) activity;
            PluginActivityControl ctl = proxy.getController();
            if (ctl != null && ctl.getPlugin() != null) {
                key = ctl.getPlugin().getClass().getName();
            }
        } else {
            key = activity.getClass().getName();
        }
        return key;
    }

    /**
     * 把插件Activity压入堆栈
     * 旧的插件方案：压入的是代理的Activity
     * hook Instr方案：压入的是插件真实的Activity
     */
    public void pushActivityToStack(Activity activity) {
        if (PluginDebugLog.isDebug()) {
            PluginDebugLog.runtimeLog(TAG, "pushActivityToStack activity: " + activity + " "
                    + IntentUtils.dump(activity));
        }

        PActivityStack sysStack = findAssociatedStack(mFocusedStack);
        sysStack.push(activity);  // 插件Activity推入全局栈
        removeLoadingIntent(mLoadedApk.getPluginPackageName(), activity.getIntent());
        mFocusedStack.push(activity);  // 插件Activity推入插件自身管理的堆栈
    }

    /**
     * 把插件Activity移出堆栈
     * 旧的插件方案：弹出的是代理Activity
     * hook Instr方案：弹出的是插件真实的Activity
     */
    public boolean popActivityFromStack(Activity activity) {

        // 退出的时候，前后台栈都需要搜索一遍
        PActivityStack sysStack = findAssociatedStack(mFocusedStack);
        sysStack.pop(activity);
        if (mLastFocusedStack != null) {
            sysStack = findAssociatedStack(mLastFocusedStack);
            sysStack.pop(activity);
        }

        boolean result = mFocusedStack.pop(activity);
        if (mLastFocusedStack != null) {
            result = mLastFocusedStack.pop(activity) || result;
        }

        if (PluginDebugLog.isDebug()) {
            PluginDebugLog.runtimeLog(TAG, "popActivityFromStack activity: " + activity + " "
                    + IntentUtils.dump(activity) + ", success: " + result);
        }

        return result;
    }

    /**
     * 清空任务栈，销毁Activity
     */
    public void clearActivityStack() {

        for (Map.Entry<String, PActivityStack> entry : mActivityStacks.entrySet()) {
            PActivityStack stack = entry.getValue();
            stack.clear(true);
        }
    }

    /**
     * 获取对应插件可能位于栈顶的Activity
     */
    public Activity getTopActivity() {
        if (!mFocusedStack.isEmpty()) {
            return mFocusedStack.getTop();
        }

        return null;
    }

    /**
     * 获取对应插件可用的Activity，用于启动其他插件的Context
     */
    public Activity getAvailableActivity() {
        if (!mFocusedStack.isEmpty()) {
            return mFocusedStack.getTop();
        }

        if (mLastFocusedStack != null && !mLastFocusedStack.isEmpty()) {
            return mLastFocusedStack.getTop();
        }

        return null;
    }

    /**
     * 当前插件的栈里是否有Activity在执行
     */
    public boolean hasActivityRunning() {
        return !mFocusedStack.isEmpty() ||
                (mLastFocusedStack != null && !mLastFocusedStack.isEmpty());
    }

    /**
     * 当前插件的栈是否为空
     */
    public boolean isStackEmpty() {
        return mFocusedStack.isEmpty() &&
                (mLastFocusedStack != null && mLastFocusedStack.isEmpty());
    }

    /**
     * dump当前插件堆栈的信息
     */
    public void dump(PrintWriter pw) {
        pw.print("foreground stack: ");
        pw.print(mFocusedStack.size() + " ");
        for (Activity activity : mFocusedStack.getActivities()) {
            String info = IntentUtils.dump(activity);
            pw.print(info);
            pw.print("\n");
        }

        if (mLastFocusedStack != null) {
            pw.print("background stack: ");
            pw.print(mLastFocusedStack.size() + " ");
            for (Activity activity : mLastFocusedStack.getActivities()) {
                String info = IntentUtils.dump(activity);
                pw.print(info);
                pw.print("\n");
            }
        }
    }

    /**
     * 根据插件栈搜索全局栈
     *
     * @param stack 插件的堆栈
     * @return  返回插件所在的全局堆栈
     */
    private PActivityStack findAssociatedStack(PActivityStack stack) {
        String stackName = stack.getTaskName();
        String pkgName = mLoadedApk.getPluginPackageName();
        if (TextUtils.equals(stackName, pkgName)) {
            // 插件包名
            stackName = mLoadedApk.getHostPackageName();
        } else if (stackName.startsWith(pkgName)) {
            stackName = stackName.substring(pkgName.length());
        }

        PActivityStack sysStack = sAllActivityStacks.get(stackName);
        if (sysStack == null) {
            sysStack = new PActivityStack(stackName);
            sAllActivityStacks.put(stackName, sysStack);
        }
        return sysStack;
    }

    /**
     * 处理Activity的launchMode，给Intent添加相关的Flags
     */
    public void dealLaunchMode(Intent intent) {
        if (null == intent) {
            return;
        }

        String targetActivity = IntentUtils.getTargetClass(intent);
        if (TextUtils.isEmpty(targetActivity)) {
            return;
        }

        PluginDebugLog.runtimeLog(TAG, "dealLaunchMode target activity: " + intent + " source: "
                + targetActivity);
        // 不支持LAUNCH_SINGLE_INSTANCE
        ActivityInfo info = mLoadedApk.getPluginPackageInfo().getActivityInfo(targetActivity);
        if (info == null || info.launchMode == ActivityInfo.LAUNCH_SINGLE_INSTANCE) {
            return;
        }
        boolean isSingleTop = info.launchMode == ActivityInfo.LAUNCH_SINGLE_TOP
                || (intent.getFlags() & Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0;
        boolean isSingleTask = info.launchMode == ActivityInfo.LAUNCH_SINGLE_TASK;
        boolean isClearTop = (intent.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0;
        PluginDebugLog.runtimeLog(TAG, "dealLaunchMode isSingleTop " + isSingleTop + " isSingleTask "
                + isSingleTask + " isClearTop " + isClearTop);
        int flag = intent.getFlags();
        PluginDebugLog.runtimeLog(TAG, "before flag: " + Integer.toHexString(intent.getFlags()));
        if ((isSingleTop || isSingleTask) && (flag & Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0) {
            flag = flag ^ Intent.FLAG_ACTIVITY_SINGLE_TOP;
        }
        if ((isSingleTask || isClearTop) && (flag & Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0) {
            flag = flag ^ Intent.FLAG_ACTIVITY_CLEAR_TOP;
        }
        intent.setFlags(flag);
        PluginDebugLog.runtimeLog(TAG, "after flag: " + Integer.toHexString(intent.getFlags()));

        if (isSingleTop && !isClearTop) {
            // 判断栈顶是否为需要启动的Activity, 只需要处理前台栈
            Activity activity = null;
            if (!mFocusedStack.isEmpty()) {
                activity = mFocusedStack.getTop();
            }
            boolean hasSameActivity = false;
            if (activity != null && !ContextUtils.isFinished(activity)) {
                String proxyClsName = ComponentFinder.findActivityProxy(mLoadedApk, info);
                // 栈内有实例, 可能是ProxyActivity，也可能是插件真实的Activity
                if (TextUtils.equals(proxyClsName, activity.getClass().getName())
                        || TextUtils.equals(targetActivity, activity.getClass().getName())) {
                    String key = getActivityStackKey(activity);

                    if (!TextUtils.isEmpty(key) && TextUtils.equals(targetActivity, key)) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        hasSameActivity = true;
                    }
                }
            }
            if (hasSameActivity) {
                handleOtherPluginActivityStack(activity, mFocusedStack);
            }
        } else if (isSingleTask || isClearTop) {

            PActivityStack targetStack; // 需要搜索的任务栈
            boolean fromBackStack = false;
            if (isClearTop) {
                targetStack = mFocusedStack;
            } else {
                // singleTask
                if (mLastFocusedStack != null
                        && TextUtils.equals(mLastFocusedStack.getTaskName(), matchTaskName(info.taskAffinity))) {
                    // 后台栈和Activity的taskAffinity匹配
                    targetStack = mLastFocusedStack;
                    fromBackStack = true;
                    PluginDebugLog.runtimeLog(TAG, "dealLaunchMode search in background stack: " + info.taskAffinity);
                } else {
                    // 前台栈中搜索
                    targetStack = mFocusedStack;
                }
            }
            // 查找栈中是否存在已有实例
            Activity found = null;
            // 遍历已经起过的activity
            for (Activity activity : targetStack.getActivities()) {
                if (activity != null && !ContextUtils.isFinished(activity)) {
                    // 堆栈里的Activity还没有销毁
                    String proxyClsName = ComponentFinder.findActivityProxy(mLoadedApk, info);
                    if (TextUtils.equals(proxyClsName, activity.getClass().getName())
                            || TextUtils.equals(targetActivity, activity.getClass().getName())) {
                        String key = getActivityStackKey(activity);
                        if (!TextUtils.isEmpty(key) && TextUtils.equals(targetActivity, key)) {
                            PluginDebugLog.runtimeLog(TAG, "dealLaunchMode found:" + IntentUtils.dump(activity));
                            found = activity;
                            break;
                        }
                    }
                }
            }

            // 栈中已经有当前activity
            if (found != null) {
                // 处理其他插件的逻辑
                // 在以这两种SingleTask， ClearTop flag启动情况下，在同一个栈的情况下
                handleOtherPluginActivityStack(found, targetStack);

                // 处理当前插件的Activity
                List<Activity> popActivities = new ArrayList<Activity>(5);
                for (Activity activity : targetStack.getActivities()) {
                    if (activity == found) {
                        if (isSingleTask || isSingleTop) {
                            PluginDebugLog.runtimeLog(TAG, "dealLaunchMode add single top flag!");
                            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        }
                        PluginDebugLog.runtimeLog(TAG, "dealLaunchMode add clear top flag!");
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        break;
                    }
                    popActivities.add(activity);
                }

                for (Activity act : popActivities) {
                    PluginDebugLog.runtimeLog(TAG, "dealLaunchMode popActivities finish " + IntentUtils.dump(act));
                    popActivityFromStack(act);
                    if (!ContextUtils.isFinished(act)) {
                        act.finish();
                    }
                }

                // 如果Activity是在后台堆栈中找到的，需要合并前后台栈
                if (fromBackStack) {
                    // https://developer.android.com/guide/components/activities/tasks-and-back-stack
                    // 把返回栈中的Activity全部推到前台
                    PActivityStack sysForeStack = findAssociatedStack(mFocusedStack);
                    PActivityStack sysBackStack = findAssociatedStack(mLastFocusedStack);
                    mergeActivityStack(sysBackStack, sysForeStack);
                    // 处理插件自身的栈
                    mergeActivityStack(mLastFocusedStack, mFocusedStack);
                    // 切换前后台堆栈
                    switchToBackStack(mFocusedStack, mLastFocusedStack);
                }

                mLoadedApk.quitApp(false);
            } else {
                // 堆栈里没有找到，遍历还未启动cache中的activity记录
                LinkedBlockingQueue<IntentRequest> records = sIntentCacheMap
                        .get(mLoadedApk.getPluginPackageName());
                if (null != records) {
                    Iterator<IntentRequest> recordIterator = records.iterator();
                    String notLaunchTargetClassName = null;
                    while (recordIterator.hasNext()) {
                        Intent record = recordIterator.next().getIntent();
                        if (null != record) {
                            if (null != record.getComponent()) {
                                notLaunchTargetClassName = record.getComponent().getClassName();
                            }
                            if (TextUtils.equals(notLaunchTargetClassName, targetActivity)) {
                                PluginDebugLog.runtimeLog(TAG, "sIntentCacheMap found: " + targetActivity);
                                if (isSingleTask || isSingleTop) {
                                    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                }
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                break;
                            }
                        }
                    }
                }
                // 遍历启动过程中的activity记录
                List<IntentRequest> loadingIntents = sIntentLoadingMap.get(mLoadedApk.getPluginPackageName());
                if (null != loadingIntents) {
                    Iterator<IntentRequest> loadingRecordIterator = loadingIntents.iterator();
                    String notLaunchTargetClassName = null;
                    while (loadingRecordIterator.hasNext()) {
                        Intent record = loadingRecordIterator.next().getIntent();
                        if (null != record) {
                            notLaunchTargetClassName = IntentUtils.getTargetClass(record);
                            if (TextUtils.equals(notLaunchTargetClassName, targetActivity)) {
                                PluginDebugLog.runtimeLog(TAG,
                                        "sIntentLoadingMap found: " + targetActivity);
                                if (isSingleTask || isSingleTop) {
                                    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                }
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                break;
                            }
                        }
                    }
                }

                if (isSingleTask) {
                    // 是否需要放到单独的任务栈
                    String taskName = matchTaskName(info.taskAffinity);
                    if (!TextUtils.equals(mFocusedStack.getTaskName(), taskName)) {
                        PluginDebugLog.runtimeLog(TAG, "dealLaunchMode push activity into separated stack: " + taskName);
                        PActivityStack stack = mActivityStacks.get(taskName);
                        if (stack == null) {
                            // 创建一个新的任务栈
                            stack = new PActivityStack(taskName);
                            mActivityStacks.put(taskName, stack);
                        }
                        // 切换前后台栈
                        switchToBackStack(mFocusedStack, stack);
                    } else {
                        PluginDebugLog.runtimeLog(TAG, "dealLaunchMode push activity into current stack: " + taskName);
                    }
                }
            }
        }
        PluginDebugLog.runtimeLog(TAG, "dealLaunchMode end: " + intent + " "
                + targetActivity);
    }

    /**
     * 处理当前Activity堆栈里的其他Activity
     */
    private void handleOtherPluginActivityStack(Activity act, PActivityStack stack) {
        // 假如栈中存在之前的Activity，并且在该Activity之上存在其他插件的activity，则finish掉其之上的activity
        // 例如场景桌面有多个插件的图标，点击一个业务的进入，然后home键，然后再点击另外一个循环。
        if (act != null) {
            List<Activity> needRemove = new ArrayList<Activity>();
            PActivityStack sysStack = findAssociatedStack(stack);
            for (Activity temp : sysStack.getActivities()) {
                if (null != temp && act == temp) {
                    break;
                }
                String pkgName = IntentUtils.parsePkgNameFromActivity(temp);
                if (temp != null && !TextUtils.equals(mLoadedApk.getPluginPackageName(),
                        pkgName)) {
                    // 其他插件Activity
                    needRemove.add(temp);
                }
            }

            PluginLoadedApk mPlugin = null;
            for (Activity removeItem : needRemove) {
                if (null != removeItem) {
                    String pkgName = IntentUtils.parsePkgNameFromActivity(removeItem);
                    mPlugin = PluginManager.getPluginLoadedApkByPkgName(pkgName);
                    if (mPlugin != null) {
                        popActivityFromStack(removeItem);
                        if (!ContextUtils.isFinished(removeItem)) {
                            removeItem.finish();
                        }
                    }
                }
            }
        }
    }

    /**
     * 把后台栈中的Activity推到前台Activity栈的最前面
     */
    private void mergeActivityStack(PActivityStack backStack, PActivityStack foreStack) {

        for (Activity activity : foreStack.getActivities()) {
            // 把前台栈的Activity压入后台栈的末位
            backStack.insertFirst(activity);
        }
        // 清空前台栈
        foreStack.clear(false);
    }

    /**
     * 把前台栈切换成后台栈
     */
    private void switchToBackStack(PActivityStack foreStack, PActivityStack nextStack) {
        mLastFocusedStack = foreStack;
        mFocusedStack = nextStack;
    }

    /**
     * 映射插件ActivityInfo的affinity到限定的任务栈上
     */
    private String matchTaskName(String affinity) {

        if (TextUtils.equals(affinity, mLoadedApk.getPluginPackageName() + IntentConstant.TASK_AFFINITY_CONTAINER1)) {
            // container1 坑位
            return affinity;
        } else if (TextUtils.equals(affinity, mLoadedApk.getPluginPackageName() + IntentConstant.TASK_AFFINITY_CONTAINER2)) {
            // container2 坑位
            return affinity;
        } else {
            return mLoadedApk.getPluginPackageName();
        }
    }
}
