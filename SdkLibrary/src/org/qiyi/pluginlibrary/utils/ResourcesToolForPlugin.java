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
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import java.lang.reflect.Field;

/**
 * Wrapper class for invoker to get resource id Wrapper of gen/R.java
 */
public class ResourcesToolForPlugin {
    private static final String TAG = "ResourcesToolForPlugin";

    private static final String ANIM = "anim";
    private static final String ANIMATOR = "animator";
    private static final String ARRAY = "array";
    private static final String ATTR = "attr";
    private static final String BOOL = "bool";
    private static final String COLOR = "color";
    private static final String DIMEN = "dimen";
    private static final String DRAWABLE = "drawable";
    private static final String ID = "id";
    private static final String INTEGER = "integer";
    private static final String INTERPOLATOR = "interpolator";
    private static final String LAYOUT = "layout";
    private static final String MENU = "menu";
    private static final String RAW = "raw";
    private static final String STRING = "string";
    private static final String STYLE = "style";
    private static final String STYLEABLE = "styleable";
    private static final String TRANSITION = "transition";
    private static final String XML = "xml";

    private String mPackageName;
    private Resources mResources;
    private ClassLoader mClassLoader;

    /**
     * Whether resolve resource by reflect to package.R.java first
     */
    private boolean mResolveByReflect = false;

    /**
     * Create resource tool
     *
     * @param context 上下文
     */
    public ResourcesToolForPlugin(@NonNull Context context) {
        this(context, false);
    }

    /**
     * Create resource tool
     *
     * @param context          上下文
     * @param resolveByReflect 是否反射R文件
     */
    public ResourcesToolForPlugin(@NonNull Context context, boolean resolveByReflect) {
        this(context.getResources(), context.getPackageName(), context.getClassLoader(), resolveByReflect);
    }

    /**
     * Create resource tool
     *
     * @param resource    resources对象
     * @param packageName 资源对应的包名
     * @param classLoader 反射R使用的ClassLoader
     */
    public ResourcesToolForPlugin(Resources resource, String packageName, @Nullable ClassLoader classLoader) {
        this(resource, packageName, classLoader, false);
    }

    /**
     * Create resource tool
     *
     * @param resource         resources对象
     * @param packageName      资源对应的包名
     * @param classLoader      反射R使用的ClassLoader
     * @param resolveByReflect 是否反射R文件
     */
    public ResourcesToolForPlugin(Resources resource, String packageName, @Nullable ClassLoader classLoader,
                                  boolean resolveByReflect) {
        mPackageName = packageName;
        mResources = resource;
        mClassLoader = classLoader;
        mResolveByReflect = resolveByReflect;
    }

    /**
     * Set resolve type for resolve resource id, set true will resolve resource
     * id by reflection to packagename.R.java first, otherwise will use
     * Resource.getIdentifier(resName).
     */
    public void setResolveType(boolean resolveByReflect) {
        mResolveByReflect = resolveByReflect;
    }

    /**
     * 获取主包资源id， 使用getIdentifier方式
     *
     * @param sourceName 资源名称
     * @param sourceType 资源类型
     * @return 返回资源id
     */
    private int getResourceId(String sourceName, String sourceType) {
        if (mResources == null || TextUtils.isEmpty(mPackageName) ||
                TextUtils.isEmpty(sourceName)) {
            return -1;
        }
        return mResources.getIdentifier(sourceName, sourceType, mPackageName);
    }

    /**
     * 获取主包资源id，优先使用反射R方式，然后使用getIdentify方式
     *
     * @param sourceName 资源名称
     * @param sourceType 资源类型
     * @return 返回资源id
     */
    public int getResourceIdType(String sourceName, String sourceType) {
        int id = -1;
        if (mResolveByReflect) {
            id = optValue(sourceName, sourceType);
        }
        if (id <= 0) {
            id = getResourceId(sourceName, sourceType);
        }
        return id;
    }

    /**
     * 获取string类型资源ID
     */
    public int getResourceIdForString(String sourceName) {
        return getResourceIdType(sourceName, STRING);
    }

    /**
     * 获取id类型的资源ID
     */
    public int getResourceIdForID(String sourceName) {
        return getResourceIdType(sourceName, ID);
    }

    /**
     * 获取layout类型的资源ID
     */
    public int getResourceIdForLayout(String sourceName) {
        return getResourceIdType(sourceName, LAYOUT);
    }

    /**
     * 获取drawable类型的资源ID
     */
    public int getResourceIdForDrawable(String sourceName) {
        return getResourceIdType(sourceName, DRAWABLE);
    }

    /**
     * 获取style类型的资源ID
     */
    public int getResourceIdForStyle(String sourceName) {
        return getResourceIdType(sourceName, STYLE);
    }

    /**
     * 获取color类型的资源ID
     */
    public int getResourceIdForColor(String sourceName) {
        return getResourceIdType(sourceName, COLOR);
    }

    /**
     * 获取raw类型的资源ID
     */
    public int getResourceIdForRaw(String sourceName) {
        return getResourceIdType(sourceName, RAW);
    }

    /**
     * 获取anim类型的资源ID
     */
    public int getResourceForAnim(String sourceName) {
        return getResourceIdType(sourceName, ANIM);
    }

    /**
     * 获取animator类型的资源ID
     */
    public int getResourceForAnimator(String sourceName) {
        return getResourceIdType(sourceName, ANIMATOR);
    }

    /**
     * 获取attr类型的资源ID
     */
    public int getResourceForAttr(String sourceName) {
        return getResourceIdType(sourceName, ATTR);
    }

    /**
     * 获取array类型的资源ID
     */
    public int getResourceForArray(String sourceName) {
        return getResourceIdType(sourceName, ARRAY);
    }

    /**
     * 获取bool类型的资源ID
     */
    public int getResourceForBool(String sourceName) {
        return getResourceIdType(sourceName, BOOL);
    }

    /**
     * 获取dimen类型的资源ID
     */
    public int getResourceForDimen(String sourceName) {
        return getResourceIdType(sourceName, DIMEN);
    }

    /**
     * 获取integer类型的资源ID
     */
    public int getResourceForInteger(String sourceName) {
        return getResourceIdType(sourceName, INTEGER);
    }

    /**
     * 获取interpolator类型的资源ID
     */
    public int getResourceForInterpolator(String sourceName) {
        return getResourceIdType(sourceName, INTERPOLATOR);
    }

    /**
     * 获取menu类型的资源ID
     */
    public int getResourceForMenu(String sourceName) {
        return getResourceIdType(sourceName, MENU);
    }

    /**
     * 获取transition类型的资源ID
     */
    public int getResourceForTransition(String sourceName) {
        return getResourceIdType(sourceName, TRANSITION);

    }

    /**
     * 获取xml类型的资源ID
     */
    public int getResourceForXml(String sourceName) {
        return getResourceIdType(sourceName, XML);
    }

    /**
     * 获取R.styleable的index索引
     */
    public int getResourceForStyleable(String sourceName) {
        return optValue(sourceName, STYLEABLE);
    }

    /**
     * 获取R.styleable类型的索引数组
     */
    public int[] getResourceForStyleables(String sourceName) {
        return optValueArray(sourceName, STYLEABLE);
    }


    /**
     * Get int id from packagename.R.java result will by int
     *
     * @param resourceName resource name
     * @param resourceType resource type
     * @return resource id
     */
    private int optValue(String resourceName, String resourceType) {
        if (TextUtils.isEmpty(resourceName) || TextUtils.isEmpty(resourceType) || TextUtils.isEmpty(mPackageName)) {
            PluginDebugLog.formatLog(TAG, "optValue resourceName: %s, resourceType : %s, mPackageName : %s , just return -1",
                    resourceName, resourceType, mPackageName);
            return -1;
        }
        int result = -1;
        try {
            Class<?> cls;
            if (null != mClassLoader) {
                cls = Class.forName(mPackageName + ".R$" + resourceType, true, mClassLoader);
            } else {
                cls = Class.forName(mPackageName + ".R$" + resourceType);
            }
            Field field = cls.getDeclaredField(resourceName);
            if (null != field) {
                field.setAccessible(true);
                result = field.getInt(cls);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * Get int ids from packagename.R.java result will by int[]
     *
     * @param resourceName resource name
     * @param resourceType resource type
     * @return resource array
     */
    private int[] optValueArray(String resourceName, String resourceType) {
        int[] result = null;
        if (TextUtils.isEmpty(resourceName) || TextUtils.isEmpty(resourceType) || TextUtils.isEmpty(mPackageName)) {
            PluginDebugLog.formatLog(TAG, "optValueArray resourceName: %s, resourceType : %s, mPackageName : %s , just return 0!",
                    resourceName, resourceType, mPackageName);
            return result;
        }
        try {
            Class<?> cls;
            if (null != mClassLoader) {
                cls = Class.forName(mPackageName + ".R$" + resourceType, true, mClassLoader);
            } else {
                cls = Class.forName(mPackageName + ".R$" + resourceType);
            }
            Field field = cls.getDeclaredField(resourceName);
            if (null != field) {
                field.setAccessible(true);
                Object res = field.get(cls);
                if (res != null && res.getClass().isArray()) {
                    result = (int[]) res;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }
}
