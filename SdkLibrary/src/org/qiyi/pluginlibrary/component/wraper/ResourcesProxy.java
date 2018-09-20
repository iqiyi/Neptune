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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.Movie;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import java.io.InputStream;

/**
 * 插件资源管理
 */
public class ResourcesProxy extends Resources {

    /* 宿主的Resources */
    private Resources mHostResources = null;
    /* 插件包名 */
    private String mPluginPackageName = null;

    /**
     * @param assets  插件的AssetManager
     * @param metrics 资源Metrics
     * @param config  资源配置
     * @param hostRes 宿主的资源
     */
    public ResourcesProxy(AssetManager assets, DisplayMetrics metrics, Configuration config, Resources hostRes, String pluginPackageName) {
        super(assets, metrics, config);
        mHostResources = hostRes;
        mPluginPackageName = pluginPackageName;
    }

    @Override
    public CharSequence getText(int id) throws NotFoundException {
        try {
            return super.getText(id);
        } catch (NotFoundException e) {
            return mHostResources.getText(id);
        }
    }

    @Override
    public CharSequence getQuantityText(int id, int quantity) throws NotFoundException {
        try {
            return super.getQuantityText(id, quantity);
        } catch (NotFoundException e) {
            return mHostResources.getQuantityText(id, quantity);
        }
    }

    @Override
    public String getString(int id) throws NotFoundException {
        try {
            return super.getString(id);
        } catch (NotFoundException e) {
            return mHostResources.getString(id);
        }
    }

    @Override
    public String getString(int id, Object... formatArgs) throws NotFoundException {
        try {
            return super.getString(id, formatArgs);
        } catch (NotFoundException e) {
            return mHostResources.getString(id, formatArgs);
        }
    }

    @Override
    public String getQuantityString(int id, int quantity, Object... formatArgs) throws NotFoundException {
        try {
            return super.getQuantityString(id, quantity, formatArgs);
        } catch (NotFoundException e) {
            return mHostResources.getQuantityString(id, quantity, formatArgs);
        }
    }

    @Override
    public String getQuantityString(int id, int quantity) throws NotFoundException {
        try {
            return super.getQuantityString(id, quantity);
        } catch (NotFoundException e) {
            return mHostResources.getQuantityString(id, quantity);
        }
    }

    @Override
    public CharSequence getText(int id, CharSequence def) {
        CharSequence relt = null;
        try {
            relt = super.getText(id);
        } catch (NotFoundException e) {

        }

        if (relt != null) {
            return relt;
        } else {
            return mHostResources.getText(id, def);
        }
    }

    @Override
    public CharSequence[] getTextArray(int id) throws NotFoundException {
        try {
            return super.getTextArray(id);
        } catch (NotFoundException e) {
            return mHostResources.getTextArray(id);
        }
    }

    @Override
    public String[] getStringArray(int id) throws NotFoundException {
        try {
            return super.getStringArray(id);
        } catch (NotFoundException e) {
            return mHostResources.getStringArray(id);
        }
    }

    @Override
    public int[] getIntArray(int id) throws NotFoundException {
        try {
            return super.getIntArray(id);
        } catch (NotFoundException e) {
            return mHostResources.getIntArray(id);
        }
    }

    @Override
    public TypedArray obtainTypedArray(int id) throws NotFoundException {
        try {
            return super.obtainTypedArray(id);
        } catch (NotFoundException e) {
            return mHostResources.obtainTypedArray(id);
        }
    }

    @Override
    public float getDimension(int id) throws NotFoundException {
        try {
            return super.getDimension(id);
        } catch (NotFoundException e) {
            return mHostResources.getDimension(id);
        }
    }

    @Override
    public int getDimensionPixelOffset(int id) throws NotFoundException {
        try {
            return super.getDimensionPixelOffset(id);
        } catch (NotFoundException e) {
            return mHostResources.getDimensionPixelOffset(id);
        }
    }

    @Override
    public int getDimensionPixelSize(int id) throws NotFoundException {
        try {
            return super.getDimensionPixelSize(id);
        } catch (NotFoundException e) {
            return mHostResources.getDimensionPixelSize(id);
        }
    }

    @Override
    public float getFraction(int id, int base, int pbase) {
        try {
            return super.getFraction(id, base, pbase);
        } catch (NotFoundException e) {
            return mHostResources.getFraction(id, base, pbase);
        }
    }

    @Override
    public Drawable getDrawable(int id) throws NotFoundException {
        try {
            return super.getDrawable(id);
        } catch (NotFoundException e) {
            return mHostResources.getDrawable(id);
        }
    }

    /**
     * @param id    资源ID
     * @param theme Theme
     * @throws NotFoundException
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public Drawable getDrawable(int id, Theme theme) throws NotFoundException {
        try {
            return super.getDrawable(id, theme);
        } catch (NotFoundException e) {
            return mHostResources.getDrawable(id, theme);
        }

    }

    @SuppressLint("NewApi")
    @Override
    public Drawable getDrawableForDensity(int id, int density) throws NotFoundException {
        try {
            return super.getDrawableForDensity(id, density);
        } catch (NotFoundException e) {
            return mHostResources.getDrawableForDensity(id, density);
        }
    }

    /**
     * @param id      资源ID
     * @param density 分辨率
     * @param theme   Theme
     * @throws NotFoundException
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public Drawable getDrawableForDensity(int id, int density, Theme theme) throws NotFoundException {
        try {
            return super.getDrawableForDensity(id, density, theme);
        } catch (NotFoundException e) {
            return mHostResources.getDrawableForDensity(id, density, theme);
        }
    }

    @Override
    public Movie getMovie(int id) throws NotFoundException {
        try {
            return super.getMovie(id);
        } catch (NotFoundException e) {
            return mHostResources.getMovie(id);
        }
    }

    @Override
    public int getColor(int id) throws NotFoundException {
        try {
            return super.getColor(id);
        } catch (NotFoundException e) {
            return mHostResources.getColor(id);
        }
    }

    /**
     * @param id    资源ID
     * @param theme Theme
     * @throws NotFoundException
     */
    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public int getColor(int id, Theme theme) throws NotFoundException {
        try {
            return super.getColor(id, theme);
        } catch (NotFoundException e) {
            return mHostResources.getColor(id, theme);
        }
    }

    @Override
    public ColorStateList getColorStateList(int id) throws NotFoundException {
        try {
            return super.getColorStateList(id);
        } catch (NotFoundException e) {
            return mHostResources.getColorStateList(id);
        }
    }

    /**
     * @param id    资源ID
     * @param theme Theme
     * @throws NotFoundException
     */
    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public ColorStateList getColorStateList(int id, Theme theme) throws NotFoundException {
        try {
            return super.getColorStateList(id, theme);
        } catch (NotFoundException e) {
            return mHostResources.getColorStateList(id, theme);
        }
    }

    @Override
    public boolean getBoolean(int id) throws NotFoundException {
        try {
            return super.getBoolean(id);
        } catch (NotFoundException e) {
            return mHostResources.getBoolean(id);
        }
    }

    @Override
    public int getInteger(int id) throws NotFoundException {
        try {
            return super.getInteger(id);
        } catch (NotFoundException e) {
            return mHostResources.getInteger(id);
        }
    }

    @Override
    public XmlResourceParser getLayout(int id) throws NotFoundException {
        try {
            return super.getLayout(id);
        } catch (NotFoundException e) {
            return mHostResources.getLayout(id);
        }
    }

    @Override
    public XmlResourceParser getAnimation(int id) throws NotFoundException {
        try {
            return super.getAnimation(id);
        } catch (NotFoundException e) {
            return mHostResources.getAnimation(id);
        }
    }

    @Override
    public XmlResourceParser getXml(int id) throws NotFoundException {
        try {
            return super.getXml(id);
        } catch (NotFoundException e) {
            return mHostResources.getXml(id);
        }
    }

    @Override
    public InputStream openRawResource(int id) throws NotFoundException {
        try {
            return super.openRawResource(id);
        } catch (NotFoundException e) {
            return mHostResources.openRawResource(id);
        }
    }

    @Override
    public InputStream openRawResource(int id, TypedValue value) throws NotFoundException {
        try {
            return super.openRawResource(id, value);
        } catch (NotFoundException e) {
            return mHostResources.openRawResource(id, value);
        }
    }

    @Override
    public AssetFileDescriptor openRawResourceFd(int id) throws NotFoundException {
        try {
            return super.openRawResourceFd(id);
        } catch (NotFoundException e) {
            return mHostResources.openRawResourceFd(id);
        }
    }

    @Override
    public void getValue(int id, TypedValue outValue, boolean resolveRefs) throws NotFoundException {
        try {
            super.getValue(id, outValue, resolveRefs);
        } catch (NotFoundException e) {
            mHostResources.getValue(id, outValue, resolveRefs);
        }

    }

    @SuppressLint("NewApi")
    @Override
    public void getValueForDensity(int id, int density, TypedValue outValue, boolean resolveRefs) throws NotFoundException {
        try {
            super.getValueForDensity(id, density, outValue, resolveRefs);
        } catch (NotFoundException e) {
            mHostResources.getValueForDensity(id, density, outValue, resolveRefs);
        }

    }

    @Override
    public void getValue(String name, TypedValue outValue, boolean resolveRefs) throws NotFoundException {
        try {
            super.getValue(name, outValue, resolveRefs);
        } catch (NotFoundException e) {
            mHostResources.getValue(name, outValue, resolveRefs);
        }
    }

    @Override
    public TypedArray obtainAttributes(AttributeSet set, int[] attrs) {
        try {
            return super.obtainAttributes(set, attrs);
        } catch (NotFoundException e) {
            return mHostResources.obtainAttributes(set, attrs);
        }

    }

    @Override
    public String getResourceName(int resid) throws NotFoundException {
        try {
            return super.getResourceName(resid);
        } catch (NotFoundException e) {
            return mHostResources.getResourceName(resid);
        }

    }

    @Override
    public String getResourcePackageName(int resid) throws NotFoundException {
        try {
            return super.getResourcePackageName(resid);
        } catch (NotFoundException e) {
            return mHostResources.getResourcePackageName(resid);
        }
    }

    @Override
    public String getResourceTypeName(int resid) throws NotFoundException {
        try {
            return super.getResourceTypeName(resid);
        } catch (NotFoundException e) {
            return mHostResources.getResourceTypeName(resid);
        }
    }

    @Override
    public String getResourceEntryName(int resid) throws NotFoundException {
        try {
            return super.getResourceEntryName(resid);
        } catch (NotFoundException e) {
            return mHostResources.getResourceEntryName(resid);
        }
    }

    @Override
    public int getIdentifier(String name, String defType, String defPackage) {
        int result = 0;
        if (!TextUtils.isEmpty(mPluginPackageName)) {
            result = super.getIdentifier(name, defType, mPluginPackageName);
        }

        if (result == 0) {
            result = super.getIdentifier(name, defType, defPackage);
            if (result == 0) {
                result = mHostResources.getIdentifier(name, defType, defPackage);
            }
        }
        return result;
    }
}
