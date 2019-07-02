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
package org.qiyi.pluginlibrary.context;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.view.LayoutInflater;

import org.qiyi.pluginlibrary.runtime.PluginLoadedApk;
import org.qiyi.pluginlibrary.utils.LayoutInflaterCompat;


/**
 * 自定义ContextWrapper的实现类
 */
public class PluginContextWrapper extends CustomContextWrapper {
    /* 插件内存实例 */
    private PluginLoadedApk mLoadedApk;
    /* 是否是Application, Service的Base Context */
    private boolean forApp;
    // 插件自己保存一个theme，不用父类创建的，为了兼容OPPO手机上的bug
    private Resources.Theme mTargetTheme;
    // 插件Application的Context保存一个LayoutInflater
    private LayoutInflater mLayoutInflater;

    public PluginContextWrapper(Context base, PluginLoadedApk loadedApk) {
        this(base, loadedApk, false);
    }

    public PluginContextWrapper(Context paramContext, PluginLoadedApk loadedApk, boolean forApplication) {
        super(paramContext);
        this.mLoadedApk = loadedApk;
        this.forApp = forApplication;
    }

    @Override
    public Theme getTheme() {
        if (mTargetTheme == null) {
            mTargetTheme = mLoadedApk.getPluginResource().newTheme();
            mTargetTheme.setTo(mLoadedApk.getPluginTheme());
        }
        return mTargetTheme;
    }

    @Override
    public void setTheme(int resid) {
        getTheme().applyStyle(resid, true);
    }

    @Override
    public Object getSystemService(String name) {

        if (LAYOUT_INFLATER_SERVICE.equals(name)) {
            if (mLayoutInflater == null) {
                // 重写插件Application Context的获取LayoutInflater方法，解决插件使用Application Context
                // 无法访问插件资源的问题，原因是LayoutInflater的构造函数使用的是Base Context的outerContext，
                // 而这个OuterContext是宿主的Application
                LayoutInflater inflater = (LayoutInflater) super.getSystemService(name);
                mLayoutInflater = forApp ? inflater.cloneInContext(this) : inflater;
                // 设置mPrivateFactory，修复多个插件同时依赖使用同名View的问题，比如android design库
                LayoutInflaterCompat.setPrivateFactory(inflater);
            }
            return mLayoutInflater;
        }

        return super.getSystemService(name);
    }

    @Override
    public String getPluginPackageName() {
        return mLoadedApk.getPluginPackageName();
    }

    @Override
    protected PluginLoadedApk getPluginLoadedApk() {
        return mLoadedApk;
    }
}
