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
import android.app.Fragment;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

import org.qiyi.pluginlibrary.utils.ComponentFinder;
import org.qiyi.pluginlibrary.utils.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 负责转移插件的跳转目标<br>
 * 用于Hook插件Activity中Instrumentation
 *
 * @see android.app.Activity#startActivity(android.content.Intent)
 */
public class PluginInstrument extends Instrumentation {
    private static final String TAG = "PluginInstrument";

    private static ConcurrentMap<String, Vector<Method>> sMethods = new ConcurrentHashMap<String, Vector<Method>>(5);
    Instrumentation mHostInstr;
    private String mPkgName;
    private ReflectionUtils mInstrumentRef;

    /**
     * 插件的Instrumentation
     */
    public PluginInstrument(Instrumentation hostInstr) {
        this(hostInstr, "");
    }

    public PluginInstrument(Instrumentation hostInstr, String pkgName) {
        mHostInstr = hostInstr;
        mInstrumentRef = ReflectionUtils.on(hostInstr);
        mPkgName = pkgName;
    }

    /**
     * 如果是PluginInstrumentation，拆装出原始的HostInstr
     *
     * @param instrumentation
     * @return
     */
    public static Instrumentation unwrap(Instrumentation instrumentation) {
        if (instrumentation instanceof PluginInstrument) {
            return ((PluginInstrument) instrumentation).mHostInstr;
        }
        return instrumentation;
    }

    /**
     * @Override
     */
    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode, Bundle options) {

        ComponentFinder.switchToActivityProxy(mPkgName, intent, requestCode, who);
        try {
            Class<?>[] paramTypes = new Class[]{Context.class, IBinder.class, IBinder.class, Activity.class,
                    Intent.class, int.class, Bundle.class};
            return mInstrumentRef.call("execStartActivity", sMethods, paramTypes, who, contextThread, token, target, intent, requestCode, options).get();
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * @Override
     */
    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode) {
        ComponentFinder.switchToActivityProxy(mPkgName, intent, requestCode, who);
        try {
            Class<?>[] paramTypes = new Class[]{Context.class, IBinder.class, IBinder.class, Activity.class,
                    Intent.class, int.class};
            return mInstrumentRef.call("execStartActivity", sMethods, paramTypes, who, contextThread, token, target, intent, requestCode).get();
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * @Override For below android 6.0
     */
    public ActivityResult execStartActivityAsCaller(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode, Bundle options, int userId) {
        ComponentFinder.switchToActivityProxy(mPkgName, intent, requestCode, who);
        try {
            Class<?>[] paramTypes = new Class[]{Context.class, IBinder.class, IBinder.class, Activity.class,
                    Intent.class, int.class, Bundle.class, int.class};
            return mInstrumentRef.call("execStartActivityAsCaller", sMethods, paramTypes, who, contextThread, token, target, intent, requestCode, options, userId)
                    .get();
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * @Override For android 6.0
     */
    public ActivityResult execStartActivityAsCaller(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode, Bundle options,
            boolean ignoreTargetSecurity, int userId) {
        ComponentFinder.switchToActivityProxy(mPkgName, intent, requestCode, who);
        try {
            Class<?>[] paramTypes = new Class[]{Context.class, IBinder.class, IBinder.class, Activity.class,
                    Intent.class, int.class, Bundle.class, boolean.class, int.class};
            return mInstrumentRef.call("execStartActivityAsCaller", sMethods, paramTypes, who, contextThread, token, target, intent, requestCode, options,
                    ignoreTargetSecurity, userId).get();
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * @Override
     */
    public void execStartActivitiesAsUser(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent[] intents, Bundle options, int userId) {
        for (Intent intent : intents) {
            ComponentFinder.switchToActivityProxy(mPkgName, intent, 0, who);
        }
        try {
            Class<?>[] paramTypes = new Class[]{Context.class, IBinder.class, IBinder.class, Activity.class,
                    Intent[].class, Bundle.class, int.class};
            mInstrumentRef.call("execStartActivitiesAsUser", sMethods, paramTypes, who, contextThread, token, target, intents, options, userId);
        } catch (Exception e) {
            // ignore
        }
    }

    /**
     * @Override For below android 6.0, start activity from Fragment
     */
    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, Fragment target,
            Intent intent, int requestCode, Bundle options) {
        ComponentFinder.switchToActivityProxy(mPkgName, intent, requestCode, who);
        try {
            Class<?>[] paramTypes = new Class[]{Context.class, IBinder.class, IBinder.class, Fragment.class,
                    Intent.class, int.class, Bundle.class};
            return mInstrumentRef.call("execStartActivity", sMethods, paramTypes, who, contextThread, token, target, intent, requestCode, options).get();
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * @Override For android 6.0, start activity from Fragment
     */
    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, String target,
            Intent intent, int requestCode, Bundle options) {
        ComponentFinder.switchToActivityProxy(mPkgName, intent, requestCode, who);
        try {
            Class<?>[] paramTypes = new Class[]{Context.class, IBinder.class, IBinder.class, String.class,
                    Intent.class, int.class, Bundle.class};
            return mInstrumentRef.call("execStartActivity", sMethods, paramTypes, who, contextThread, token, target, intent, requestCode, options).get();
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
}
