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
package org.qiyi.pluginlibrary.utils;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.util.SimpleArrayMap;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;

import android.support.coreui.R;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 通过给LayoutInflater设置privateFactory，用于解决多个插件使用了
 * 相同类名的View或者Fragment导致的类冲突的问题，比如同时依赖了android design库，
 * 原因是{@link android.view.LayoutInflater}缓存了View的构造函数map，Android N以下没有verifyClassLoader，
 * {@link android.support.v4.app.Fragment}同样缓存了Fragment的构造函数map
 */
public class LayoutInflaterCompat {
    private static final String TAG = "LayoutInflaterCompat";
    private static final ConcurrentMap<String, Vector<Method>> sMethods = new ConcurrentHashMap<String, Vector<Method>>(1);

    /**
     * 给LayoutInflater设置privateFactory
     * 解决同名View或者Fragment冲突的问题
     */
    public static void setPrivateFactory(LayoutInflater inflater) {
        LayoutInflater.Factory2 factory2 = null;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // 5.0以下重复设置privateFactory没有FactoryMerger，而Activity会把自己设置成privateFactory
            factory2 = ReflectionUtils.on(inflater).get("mPrivateFactory");
        }
        LayoutInflater.Factory2 privateFactory = new CompatPrivateFactory(factory2);
        Class<?>[] paramTypes = new Class[]{LayoutInflater.Factory2.class};
        ReflectionUtils.on(inflater).call("setPrivateFactory", sMethods, paramTypes, privateFactory);
    }

    static class FragmentTag {
        public static final int[] Fragment = {
                0x01010003, 0x010100d0, 0x010100d1
        };
        public static final int Fragment_id = 1;
        public static final int Fragment_name = 0;
        public static final int Fragment_tag = 2;
    }

    /**
     * 自定义PrivateFactory，用于移除ClassLoader发生变化时系统缓存的构造函数或类
     */
    private static class CompatPrivateFactory implements LayoutInflater.Factory2 {
        private static String WIDGET_PACKAGE_NAME;
        // android.view.LayoutInflater中的View构造函数缓存map
        private static Map<String, Constructor<? extends View>> sViewConstructorMap;
        // android.support.v4.app.Fragment中的Fragment类缓存map
        private static SimpleArrayMap<String, Class<?>> sSupportFragmentClassMap;
        // android.app.Fragment中的Fragment类缓存map
        private static ArrayMap<String, Class<?>> sFragmentClassMap;
        // android.support.design.widget.CoordinatorLayout中的behavior缓存
        private static ThreadLocal<Map<String, Constructor<CoordinatorLayout.Behavior>>> sBehaviorConstructors;

        private final LayoutInflater.Factory2 mOrigFactory;

        static {
            final Package pkg = CoordinatorLayout.class.getPackage();
            WIDGET_PACKAGE_NAME = pkg != null ? pkg.getName() : null;
        }

        private static Map<String, Constructor<? extends View>> getViewConstructorMap() {
            if (sViewConstructorMap == null) {
                sViewConstructorMap = ReflectionUtils.on(LayoutInflater.class).get("sConstructorMap");
            }
            return sViewConstructorMap;
        }

        private static SimpleArrayMap<String, Class<?>> getSupportFragmentClassMap() {
            if (sSupportFragmentClassMap == null) {
                sSupportFragmentClassMap = ReflectionUtils.on(android.support.v4.app.Fragment.class).get("sClassMap");
            }
            return sSupportFragmentClassMap;
        }

        private static ArrayMap<String, Class<?>> getFragmentClassMap() {
            if (sFragmentClassMap == null) {
                sFragmentClassMap = ReflectionUtils.on(android.app.Fragment.class).get("sClassMap");
            }
            return sFragmentClassMap;
        }

        private static Map<String, Constructor<CoordinatorLayout.Behavior>> getBehaviorConstructors() {
            Map<String, Constructor<CoordinatorLayout.Behavior>> constructorMap = null;
            if (sBehaviorConstructors == null) {
                sBehaviorConstructors = ReflectionUtils.on(CoordinatorLayout.class).get("sConstructors");
            }
            if (sBehaviorConstructors != null) {
                constructorMap = sBehaviorConstructors.get();
            }
            return constructorMap;
        }

        CompatPrivateFactory(LayoutInflater.Factory2 factory) {
            mOrigFactory = factory;
        }

        @Override
        public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
            if ("fragment".equals(name)) {
                String fname = attrs.getAttributeValue(null, "class");
                TypedArray a =  context.obtainStyledAttributes(attrs, FragmentTag.Fragment);
                if (fname == null) {
                    fname = a.getString(FragmentTag.Fragment_name);
                }
                a.recycle();
                // 处理Fragment
                resetFragmentClassMap(context, fname);
            } else {
                // 处理View
                resetViewConstructorMap(context, attrs, name);
            }
            // 处理CoordinatorLayout的Behavior
            if (parent instanceof CoordinatorLayout) {
                final TypedArray ta = context.obtainStyledAttributes(attrs,
                        R.styleable.CoordinatorLayout_Layout);
                if (ta.hasValue(R.styleable.CoordinatorLayout_Layout_layout_behavior)) {
                    String behavior = ta.getString(
                            R.styleable.CoordinatorLayout_Layout_layout_behavior);
                    resetBehaviorConstructorMap(context, behavior);
                }
                ta.recycle();
            }

            return mOrigFactory != null ? mOrigFactory.onCreateView(parent, name, context, attrs) : null;
        }

        @Override
        public View onCreateView(String name, Context context, AttributeSet attrs) {
            // 这个API用于3.0之前的系统，暂不实现
            return null;
        }

        /**
         * 处理Fragment的缓存
         */
        private void resetFragmentClassMap(Context context, String fname) {
            if (isSupportFragmentClass(context, fname)) {
                resetSupportFragmentClassMap(context, fname);
                return;
            }

            ArrayMap<String, Class<?>> classMap = getFragmentClassMap();
            if (classMap != null) {
                Class<?> clazz = classMap.get(fname);
                if (clazz != null && !verifyClassLoader(context, clazz)) {
                    PluginDebugLog.runtimeFormatLog(TAG, "find same app fragment class name in LayoutInflater cache and remove it %s", fname);
                    clazz = null;
                    classMap.remove(fname);
                }
            }
        }

        /**
         * 处理android support库Fragment的缓存
         */
        private void resetSupportFragmentClassMap(Context context, String fname) {
            SimpleArrayMap<String, Class<?>> classMap = getSupportFragmentClassMap();
            if (classMap != null) {
                Class<?> clazz = classMap.get(fname);
                if (clazz != null && !verifyClassLoader(context, clazz)) {
                    PluginDebugLog.runtimeFormatLog(TAG, "find same support fragment class name in LayoutInflater cache and remove it %s", fname);
                    clazz = null;
                    classMap.remove(fname);
                }
            }
        }

        /**
         * 是否是android support库里的Fragment
         */
        private boolean isSupportFragmentClass(Context context, String fname) {
            try {
                Class<?> clazz = context.getClassLoader().loadClass(fname);
                return android.support.v4.app.Fragment.class.isAssignableFrom(clazz);
            } catch (Exception e) {
                ErrorUtil.throwErrorIfNeed(e);
            }
            return false;
        }

        /**
         * 处理View的缓存
         */
        private void resetViewConstructorMap(Context context, AttributeSet attrs, String viewName) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                PluginDebugLog.runtimeLog(TAG, "No need to handle LayoutInflater above N");
                return;
            }

            if (viewName.indexOf(".") > 0) {
                // 处理自定义View，忽略系统View类
                Map<String, Constructor<? extends View>> constructorMap = getViewConstructorMap();
                if (constructorMap != null) {
                    Constructor<? extends View> constructor = constructorMap.get(viewName);
                    if (constructor != null && !verifyClassLoader(context, constructor)) {
                        PluginDebugLog.runtimeFormatLog(TAG, "find same view class name in LayoutInflater cache and remove it %s", viewName);
                        constructor = null;
                        constructorMap.remove(viewName);
                    }
                }
            }
        }

        /**
         * 处理behavior的缓存
         */
        private void resetBehaviorConstructorMap(Context context, String name) {
            if (TextUtils.isEmpty(name)) {
                return;
            }

            final String behaviorName;
            if (name.startsWith(".")) {
                // Relative to the app package. Prepend the app package name.
                behaviorName = context.getPackageName() + name;
            } else if (name.indexOf('.') >= 0) {
                // Fully qualified package name.
                behaviorName = name;
            } else {
                // Assume stock behavior in this package (if we have one)
                behaviorName = !TextUtils.isEmpty(WIDGET_PACKAGE_NAME)
                        ? (WIDGET_PACKAGE_NAME + '.' + name)
                        : name;
            }

            Map<String, Constructor<CoordinatorLayout.Behavior>> constructors = getBehaviorConstructors();
            if (constructors != null) {
                Constructor<?> constructor = constructors.get(behaviorName);
                if (constructor != null && !verifyClassLoader(context, constructor)) {
                    PluginDebugLog.runtimeFormatLog(TAG, "find same behavior class name in CoordinatorLayout cache and remove it %s", behaviorName);
                    constructor = null;
                    constructors.remove(behaviorName);
                }
            }
        }

        private static final ClassLoader BOOT_CLASS_LOADER = LayoutInflater.class.getClassLoader();

        private static boolean verifyClassLoader(Context context, Constructor<?> constructor) {
            return verifyClassLoader(context, constructor.getDeclaringClass());
        }

        private static boolean verifyClassLoader(Context context, Class<?> clazz) {
            final ClassLoader constructorLoader = clazz.getClassLoader();
            if (constructorLoader == BOOT_CLASS_LOADER) {
                // fast path for boot class loader (most common case?) - always ok
                return true;
            }
            // in all normal cases (no dynamic code loading), we will exit the following loop on the
            // first iteration (i.e. when the declaring classloader is the contexts class loader).
            ClassLoader cl = context.getClassLoader();
            do {
                if (constructorLoader == cl) {
                    return true;
                }
                cl = cl.getParent();
            } while (cl != null);
            return false;
        }
    }
}
