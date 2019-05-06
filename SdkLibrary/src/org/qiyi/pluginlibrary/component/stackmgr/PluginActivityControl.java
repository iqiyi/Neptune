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
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import org.qiyi.pluginlibrary.error.ErrorType;
import org.qiyi.pluginlibrary.exception.ReflectException;
import org.qiyi.pluginlibrary.plugin.PluginActivityCallback;
import org.qiyi.pluginlibrary.runtime.PluginLoadedApk;
import org.qiyi.pluginlibrary.runtime.PluginManager;
import org.qiyi.pluginlibrary.utils.ErrorUtil;
import org.qiyi.pluginlibrary.utils.PluginDebugLog;
import org.qiyi.pluginlibrary.utils.ReflectionUtils;
import org.qiyi.pluginlibrary.utils.VersionUtils;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 插件的控制器，派发插件事件和控制插件生命周期
 */
public class PluginActivityControl implements PluginActivityCallback {
    private static final String TAG = "PluginActivityControl";
    public static final ConcurrentMap<String, Vector<Method>> sMethods = new ConcurrentHashMap<String, Vector<Method>>(10);

    private Activity mProxy;// 代理Activity
    private Activity mPlugin;// 插件Activity
    private ReflectionUtils mProxyRef;// 指向代理Activity的反射工具类
    private ReflectionUtils mPluginRef;// 指向插件Activity的反射工具类
    private Application mApplication;// 分派给插件的Application
    private Instrumentation mHostInstr;

    /**
     * @param proxy  代理Activity
     * @param plugin 插件Activity
     * @param app    分派给插件的Application
     */
    public PluginActivityControl(Activity proxy, Activity plugin,
                                 Application app, Instrumentation pluginInstr) {
        mProxy = proxy;
        mPlugin = plugin;
        mApplication = app;

        mHostInstr = pluginInstr;

        // 使反射工具类指向相应的对象
        mProxyRef = ReflectionUtils.on(proxy);
        mPluginRef = ReflectionUtils.on(plugin);
    }

    /**
     * 修改插件Activity的ActivityInfo
     * 执行Activity#attach()和ActivityThread启动Activity过程中的逻辑
     *
     * @param activity  插件Activity
     * @param className 插件Activity类名
     * @param loadedApk 插件内存实例
     */
    public static void changeActivityInfo(Activity activity, String className,
                                          PluginLoadedApk loadedApk) {
        if (loadedApk == null || TextUtils.isEmpty(className)) {
            return;
        }

        PluginDebugLog.runtimeFormatLog(TAG, "changeActivityInfo activity name:%s, pkgName:%s", className, loadedApk.getPluginPackageName());
        ActivityInfo origActInfo = ReflectionUtils.on(activity).get("mActivityInfo");
        ActivityInfo actInfo = loadedApk.getActivityInfoByClassName(className);
        if (actInfo != null) {
            if (loadedApk.getPackageInfo() != null) {
                actInfo.applicationInfo = loadedApk.getPackageInfo().applicationInfo;
            }
            if (origActInfo != null) {
                origActInfo.applicationInfo = actInfo.applicationInfo;
                origActInfo.configChanges = actInfo.configChanges;
                origActInfo.descriptionRes = actInfo.descriptionRes;
                origActInfo.enabled = actInfo.enabled;
                origActInfo.exported = actInfo.exported;
                origActInfo.flags = actInfo.flags;
                origActInfo.icon = actInfo.icon;
                origActInfo.labelRes = actInfo.labelRes;
                origActInfo.logo = actInfo.logo;
                origActInfo.metaData = actInfo.metaData;
                origActInfo.name = actInfo.name;
                origActInfo.nonLocalizedLabel = actInfo.nonLocalizedLabel;
                origActInfo.packageName = actInfo.packageName;
                origActInfo.permission = actInfo.permission;
                origActInfo.screenOrientation = actInfo.screenOrientation;
                origActInfo.softInputMode = actInfo.softInputMode;
                origActInfo.targetActivity = actInfo.targetActivity;
                origActInfo.taskAffinity = actInfo.taskAffinity;
                origActInfo.theme = actInfo.theme;
            }

            // 修改Window的属性
            Window window = activity.getWindow();
            if (actInfo.softInputMode != WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED) {
                window.setSoftInputMode(actInfo.softInputMode);
            }
            if (actInfo.uiOptions != 0) {
                window.setUiOptions(actInfo.uiOptions);
            }
            if (Build.VERSION.SDK_INT >= 26) {
                window.setColorMode(actInfo.colorMode);
            }
        }

        // onCreate()中调用时需要修改插件Activity的主题
        int resTheme = loadedApk.getActivityThemeResourceByClassName(className);
        if (resTheme != 0) {
            activity.setTheme(resTheme);
        }

        if (origActInfo != null) {
            // handle ActionBar title
            if (origActInfo.nonLocalizedLabel != null) {
                activity.setTitle(origActInfo.nonLocalizedLabel);
            } else if (origActInfo.labelRes != 0) {
                activity.setTitle(origActInfo.labelRes);
            } else if (origActInfo.applicationInfo != null) {
                if (origActInfo.applicationInfo.nonLocalizedLabel != null) {
                    activity.setTitle(origActInfo.applicationInfo.nonLocalizedLabel);
                } else if (origActInfo.applicationInfo.labelRes != 0) {
                    activity.setTitle(origActInfo.applicationInfo.labelRes);
                } else {
                    activity.setTitle(origActInfo.applicationInfo.name);
                }
            } else {
                activity.setTitle(origActInfo.name);
            }
        }

        if (actInfo != null) {
            // copy from VirtualApk, is it really need?
            if (actInfo.screenOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                activity.setRequestedOrientation(actInfo.screenOrientation);
            }
            PluginDebugLog.log(TAG, "changeActivityInfo->changeTheme: " + " theme = " +
                    actInfo.getThemeResource() + ", icon = " + actInfo.getIconResource()
                    + ", logo = " + actInfo.logo + ", labelRes=" + actInfo.labelRes);
        }
    }

    /**
     * 调用插件Activity的attach方法，注入基础信息
     *
     * @param pluginInstr    插件Instrumentation
     * @param contextWrapper 插件的BaseContext
     * @param packageName    插件的包名
     * @return 注入成功返回true; 失败返回false
     */
    public boolean dispatchProxyToPlugin(Instrumentation pluginInstr, Context contextWrapper, String packageName) {
        if (mPlugin == null || mPlugin.getBaseContext() != null || pluginInstr == null) {
            return false;
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                // Android O的attach方法在7.0的参数基础上增加了android.view.ViewRootImpl$ActivityConfigCallback这样一个参数，这个参数是PhoneWindow的成员变量
                // 8.0, android.os.Build.VERSION_CODES.O
                callAttachV26(pluginInstr);
            } else if (android.os.Build.VERSION.SDK_INT >= 24) {
                // Android N的attach方法在6.0的参数基础上增加了一个Window的参数
                // 7.0, android.os.Build.VERSION_CODES.N
                callAttachV24(pluginInstr);
            } else if (android.os.Build.VERSION.SDK_INT >= 22) {
                // Android 5.1的attach方法在5.0基础上增加了一个String mReferrer参数
                // 5.1 and 6.0, android.os.Build.VERSION_CODES.LOLLIPOP_MR1
                // android.os.Build.VERSION_CODES.M
                callAttachV22(pluginInstr);
            } else if (android.os.Build.VERSION.SDK_INT >= 21) {
                // Android 5.0的attach方法在4.x基础上增加了一个IVoiceInteractor mVoiceInteractor参数
                // 5.0, android.os.Build.VERSION_CODES.LOLLIPOP;
                callAttachV21(pluginInstr);
            } else if (android.os.Build.VERSION.SDK_INT >= 14) {
                // 4.x, android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH~KITKAT
                callAttachV14(pluginInstr);
            } else {
                // 2.x, android.os.Build.VERSION_CODES.GINGERBREAD
                callAttachV4(pluginInstr);
            }

            mPluginRef.set("mWindow", mProxy.getWindow());
            mPluginRef.set("mWindowManager", mProxy.getWindow().getWindowManager());
            mPlugin.getWindow().setCallback(mPlugin);
            // 替换ContextImpl的OuterContext，插件Activity的LayoutInflater才能正常使用
            Class<?>[] paramTypes = new Class[]{Context.class};
            ReflectionUtils.on(mProxy.getBaseContext()).call("setOuterContext", sMethods, paramTypes, mPlugin);

            return true;
        } catch (ReflectException e) {
            PluginManager.deliver(mProxy, false, packageName, ErrorType.ERROR_PLUGIN_ACTIVITY_ATTACH_BASE);
            ErrorUtil.throwErrorIfNeed(e);
        }
        return false;
    }

    /**
     * 反射调用Android O上的Activity#attach()方法
     * Android O的attach方法在7.0的参数基础上增加了android.view.ViewRootImpl$ActivityConfigCallback参数
     * <p>
     * <href>http://androidxref.com/8.0.0_r4/xref/frameworks/base/core/java/android/app/Activity.java#6902</href>
     */
    private void callAttachV26(Instrumentation pluginInstr) {
        try {
            mPluginRef.call(
                    // 方法名
                    "attach",
                    sMethods,
                    null,
                    // Context context
                    // contextWrapper,
                    mProxy,
                    // ActivityThread aThread
                    mProxyRef.get("mMainThread"),
                    // Instrumentation instr
                    pluginInstr,
                    // IBinder token
                    mProxyRef.get("mToken"),
                    // int ident
                    mProxyRef.get("mIdent"),
                    // Application application
                    mApplication == null ? mProxy.getApplication() : mApplication,
                    // Intent intent
                    mProxy.getIntent(),
                    // ActivityInfo info
                    mProxyRef.get("mActivityInfo"),
                    // CharSequence title
                    mProxy.getTitle(),
                    // Activity parent
                    mProxy.getParent(),
                    // String id
                    mProxyRef.get("mEmbeddedID"),
                    // NonConfigurationInstances
                    // lastNonConfigurationInstances
                    mProxy.getLastNonConfigurationInstance(),
                    // Configuration config
                    mProxyRef.get("mCurrentConfig"),
                    // String mReferrer
                    mProxyRef.get("mReferrer"),
                    // IVoiceInteractor mVoiceInteractor
                    mProxyRef.get("mVoiceInteractor"),
                    // Window window
                    mProxy.getWindow(),
                    // android.view.ViewRootImpl$ActivityConfigCallback activityConfigCallback, 这个参数在PhoneWindow中
                    // 由于后面会替换插件Activity的Window对象，此处可以传null
                    null);
        } catch (ReflectException re) {
            re.printStackTrace();
            callAttachV24(pluginInstr);
        }
    }

    /**
     * 反射调用Android N上的Activity#attach()方法
     * Android N的attach方法在6.0的参数基础上增加了一个Window的参数
     * <p>
     * <href>http://androidxref.com/7.0.0_r1/xref/frameworks/base/core/java/android/app/Activity.java#6593</href>
     */
    private void callAttachV24(Instrumentation pluginInstr) {
        try {
            mPluginRef.call(
                    // 方法名
                    "attach",
                    sMethods,
                    null,
                    // Context context
                    // contextWrapper,
                    mProxy,
                    // ActivityThread aThread
                    mProxyRef.get("mMainThread"),
                    // Instrumentation instr
                    pluginInstr,
                    // IBinder token
                    mProxyRef.get("mToken"),
                    // int ident
                    mProxyRef.get("mIdent"),
                    // Application application
                    mApplication == null ? mProxy.getApplication() : mApplication,
                    // Intent intent
                    mProxy.getIntent(),
                    // ActivityInfo info
                    mProxyRef.get("mActivityInfo"),
                    // CharSequence title
                    mProxy.getTitle(),
                    // Activity parent
                    mProxy.getParent(),
                    // String id
                    mProxyRef.get("mEmbeddedID"),
                    // NonConfigurationInstances
                    // lastNonConfigurationInstances
                    mProxy.getLastNonConfigurationInstance(),
                    // Configuration config
                    mProxyRef.get("mCurrentConfig"),
                    // String mReferrer
                    mProxyRef.get("mReferrer"),
                    // IVoiceInteractor mVoiceInteractor
                    mProxyRef.get("mVoiceInteractor"),
                    // Window window
                    mProxy.getWindow());
        } catch (ReflectException re) {
            re.printStackTrace();
            callAttachV22(pluginInstr);
        }
    }

    /**
     * 反射调用Android 5.1 and 6.0上的Activity#attach()方法
     * Android 5.1的attach方法在5.0基础上增加了一个String mReferrer参数
     * <p>
     * <href>http://androidxref.com/5.1.0_r1/xref/frameworks/base/core/java/android/app/Activity.java#5922</href>
     */
    private void callAttachV22(Instrumentation pluginInstr) {
        try {
            mPluginRef.call(
                    // 方法名
                    "attach",
                    sMethods,
                    null,
                    // Context context
                    // contextWrapper,
                    mProxy,
                    // ActivityThread aThread
                    mProxyRef.get("mMainThread"),
                    // Instrumentation instr
                    pluginInstr,
                    // IBinder token
                    mProxyRef.get("mToken"),
                    // int ident
                    mProxyRef.get("mIdent"),
                    // Application application
                    mApplication == null ? mProxy.getApplication() : mApplication,
                    // Intent intent
                    mProxy.getIntent(),
                    // ActivityInfo info
                    mProxyRef.get("mActivityInfo"),
                    // CharSequence title
                    mProxy.getTitle(),
                    // Activity parent
                    mProxy.getParent(),
                    // String id
                    mProxyRef.get("mEmbeddedID"),
                    // NonConfigurationInstances
                    // lastNonConfigurationInstances
                    mProxy.getLastNonConfigurationInstance(),
                    // Configuration config
                    mProxyRef.get("mCurrentConfig"),
                    // String mReferrer
                    mProxyRef.get("mReferrer"),
                    // IVoiceInteractor mVoiceInteractor
                    mProxyRef.get("mVoiceInteractor"));
        } catch (ReflectException re) {
            re.printStackTrace();
            callAttachV21(pluginInstr);
        }
    }

    /**
     * 反射调用Android 5.0上的Activity#attach()方法
     * Android 5.0的attach方法在4.x基础上增加了一个IVoiceInteractor mVoiceInteractor参数
     * <p>
     * <href>http://androidxref.com/5.0.0_r2/xref/frameworks/base/core/java/android/app/Activity.java#5866</href>
     */
    private void callAttachV21(Instrumentation pluginInstr) {
        try {
            mPluginRef.call(
                    // 方法名
                    "attach",
                    sMethods,
                    null,
                    // Context context
                    // contextWrapper,
                    mProxy,
                    // ActivityThread aThread
                    mProxyRef.get("mMainThread"),
                    // Instrumentation instr
                    pluginInstr,
                    // IBinder token
                    mProxyRef.get("mToken"),
                    // int ident
                    mProxyRef.get("mIdent"),
                    // Application application
                    mApplication == null ? mProxy.getApplication() : mApplication,
                    // Intent intent
                    mProxy.getIntent(),
                    // ActivityInfo info
                    mProxyRef.get("mActivityInfo"),
                    // CharSequence title
                    mProxy.getTitle(),
                    // Activity parent
                    mProxy.getParent(),
                    // String id
                    mProxyRef.get("mEmbeddedID"),
                    // NonConfigurationInstances
                    // lastNonConfigurationInstances
                    mProxy.getLastNonConfigurationInstance(),
                    // Configuration config
                    mProxyRef.get("mCurrentConfig"),
                    // IVoiceInteractor mVoiceInteractor
                    mProxyRef.get("mVoiceInteractor"));
        } catch (ReflectException re) {
            re.printStackTrace();
            callAttachV14(pluginInstr);
        }
    }

    /**
     * 反射调用4.x上的Activity#attach()方法
     * <p>
     * <href>http://androidxref.com/4.0.3_r1/xref/frameworks/base/core/java/android/app/Activity.java#4409</href>
     */
    private void callAttachV14(Instrumentation pluginInstr) {
        try {
            mPluginRef.call(
                    // 方法名
                    "attach",
                    sMethods,
                    null,
                    // Context context
                    // contextWrapper,
                    mProxy,
                    // ActivityThread aThread
                    mProxyRef.get("mMainThread"),
                    // Instrumentation instr
                    pluginInstr,
                    // IBinder token
                    mProxyRef.get("mToken"),
                    // int ident
                    mProxyRef.get("mIdent"),
                    // Application application
                    mApplication == null ? mProxy.getApplication() : mApplication,
                    // Intent intent
                    mProxy.getIntent(),
                    // ActivityInfo info
                    mProxyRef.get("mActivityInfo"),
                    // CharSequence title
                    mProxy.getTitle(),
                    // Activity parent
                    mProxy.getParent(),
                    // String id
                    mProxyRef.get("mEmbeddedID"),
                    // NonConfigurationInstances
                    // lastNonConfigurationInstances
                    mProxy.getLastNonConfigurationInstance(),
                    // Configuration config
                    mProxyRef.get("mCurrentConfig"));
        } catch (ReflectException re) {
            re.printStackTrace();
            callAttachV4(pluginInstr);
        }
    }

    /**
     * 反射调用2.x上的Activity#attach()方法
     * <href>http://androidxref.com/2.3.6/xref/frameworks/base/core/java/android/app/Activity.java#3739</href>
     */
    private void callAttachV4(Instrumentation pluginInstr) {
        mPluginRef.call(
                // 方法名
                "attach",
                sMethods,
                null,
                // Context context
                // contextWrapper,
                mProxy,
                // ActivityThread aThread
                mProxyRef.get("mMainThread"),
                // Instrumentation instr
                pluginInstr,
                // IBinder token
                mProxyRef.get("mToken"),
                // int ident
                mProxyRef.get("mIdent"),
                // Application application
                mApplication == null ? mProxy.getApplication() : mApplication,
                // Intent intent
                mProxy.getIntent(),
                // ActivityInfo info
                mProxyRef.get("mActivityInfo"),
                // CharSequence title
                mProxy.getTitle(),
                // Activity parent
                mProxy.getParent(),
                // String id
                mProxyRef.get("mEmbeddedID"),
                // Object lastNonConfigurationInstances
                mProxy.getLastNonConfigurationInstance(),
                // HashMap<String, Object> lastNonConfigurationChildInstances
                null,
                // Configuration config
                mProxyRef.get("mCurrentConfig"));
    }

    /**
     * @return 插件的Activity
     */
    public Activity getPlugin() {
        return mPlugin;
    }

    /**
     * @return 代理Activity的反射工具类
     */
    public ReflectionUtils getProxyRef() {
        return mProxyRef;
    }

    /**
     * @return 插件Activity的反射工具类
     */
    public ReflectionUtils getPluginRef() {
        return mPluginRef;
    }

    /**
     * 执行插件的onCreate方法
     *
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    public void callOnCreate(Bundle saveInstance) {
        if (null != mHostInstr) {
            mHostInstr.callActivityOnCreate(mPlugin, saveInstance);
        }
    }

    @Override
    public void callOnPostCreate(Bundle savedInstanceState) {
        if (null != mHostInstr) {
            mHostInstr.callActivityOnPostCreate(mPlugin, savedInstanceState);
        }
    }

    /**
     * 执行插件的onStart方法
     *
     * @see android.app.Activity#onStart()
     */
    @Override
    public void callOnStart() {
        if (null != getPluginRef()) {
            getPluginRef().call("performStart", sMethods, null);
        }
    }

    /**
     * 执行插件的onResume方法
     *
     * @see android.app.Activity#onResume()
     */
    @Override
    public void callOnResume() {
        if (null != getPluginRef()) {
            getPluginRef().call("performResume", sMethods, null);
        }
    }

    /**
     * 执行插件的onDestroy方法
     *
     * @see android.app.Activity#onDestroy()
     */
    @Override
    public void callOnDestroy() {
        if (null != mHostInstr) {
            mHostInstr.callActivityOnDestroy(mPlugin);
        }
    }

    /**
     * 执行插件的onStop方法
     *
     * @see android.app.Activity#onStop()
     */
    @Override
    public void callOnStop() {
        if (null != getPluginRef()) {
            if (VersionUtils.hasNougat()) {
                // 此处强制写false可能带来一些风险，暂时没有其他的方法处理
                getPluginRef().call("performStop", sMethods, null, false);
            } else {
                getPluginRef().call("performStop", sMethods, null);
            }
        }
    }

    /**
     * 执行插件的onRestart方法
     *
     * @see android.app.Activity#onRestart()
     */
    @Override
    public void callOnRestart() {
        if (null != getPluginRef()) {
            getPluginRef().call("performRestart", sMethods, null);
        }
    }

    /**
     * 执行插件的onSaveInstanceState方法
     *
     * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
     */
    @Override
    public void callOnSaveInstanceState(Bundle outState) {
        if (null != mHostInstr) {
            mHostInstr.callActivityOnSaveInstanceState(mPlugin, outState);
        }
    }

    /**
     * 执行插件的onRestoreInstanceState方法
     *
     * @see android.app.Activity#onRestoreInstanceState(android.os.Bundle)
     */
    @Override
    public void callOnRestoreInstanceState(Bundle savedInstanceState) {
        if (null != mHostInstr) {
            mHostInstr.callActivityOnRestoreInstanceState(mPlugin, savedInstanceState);
        }
    }

    /**
     * 执行插件的onStop方法
     *
     * @see android.app.Activity#onStop()
     */
    @Override
    public void callOnPause() {
        if (null != getPluginRef()) {
            getPluginRef().call("performPause", sMethods, null);
        }
    }

    /**
     * 执行插件的onBackPressed方法
     *
     * @see android.app.Activity#onBackPressed()
     */
    @Override
    public void callOnBackPressed() {
        if (null != mPlugin) {
            mPlugin.onBackPressed();
        }
    }

    /**
     * 执行插件的onKeyDown方法
     *
     * @see android.app.Activity#onKeyDown(int, android.view.KeyEvent)
     */
    @Override
    public boolean callOnKeyDown(int keyCode, KeyEvent event) {
        if (null != mPlugin) {
            return mPlugin.onKeyDown(keyCode, event);
        }
        return false;
    }

    /**
     * 执行插件的onPictureInPictureModeChanged方法
     * @param isInPictureInPictureMode
     * @param newConfig
     */
    @Override
    public void callOnPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && null != mPlugin) {
            mPlugin.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        }
    }

    @Override
    public void callOnPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && null != mPlugin) {
            mPlugin.onPictureInPictureModeChanged(isInPictureInPictureMode);
        }
    }

    @Override
    public void callDump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        if (null != mPlugin) {
            mPlugin.dump(prefix, fd, writer, args);
        }
    }

    @Override
    public void callOnConfigurationChanged(Configuration newConfig) {
        if (null != mPlugin) {
            mPlugin.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public void callOnPostResume() {
        getPluginRef().call("onPostResume", sMethods, null);
    }

    @Override
    public void callOnDetachedFromWindow() {
        if (null != mPlugin) {
            mPlugin.onDetachedFromWindow();
        }
    }

    @Override
    public View callOnCreateView(String name, Context context, AttributeSet attrs) {
        if (null != mPlugin) {
            return mPlugin.onCreateView(name, context, attrs);
        }
        return null;
    }

    @Override
    public View callOnCreateView(View parent, String name, Context context, AttributeSet attrs) {
        if (null != mPlugin) {
            return mPlugin.onCreateView(parent, name, context, attrs);
        }
        return null;
    }

    @Override
    public void callOnNewIntent(Intent intent) {
        if (null != mHostInstr) {
            mHostInstr.callActivityOnNewIntent(mPlugin, intent);
        }
    }

    @Override
    public void callOnActivityResult(int requestCode, int resultCode, Intent data) {
        Class<?>[] paramTypes = new Class[]{int.class, int.class, Intent.class};
        getPluginRef().call("onActivityResult", sMethods, paramTypes, requestCode, resultCode, data);
    }
}
