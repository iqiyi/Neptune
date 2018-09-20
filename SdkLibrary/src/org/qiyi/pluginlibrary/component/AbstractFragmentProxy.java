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

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.qiyi.pluginlibrary.constant.IntentConstant;
import org.qiyi.pluginlibrary.error.ErrorType;
import org.qiyi.pluginlibrary.listenter.IPluginElementLoadListener;
import org.qiyi.pluginlibrary.pm.PluginPackageManagerNative;
import org.qiyi.pluginlibrary.runtime.PluginManager;
import org.qiyi.pluginlibrary.utils.FragmentPluginHelper;

/**
 * 代理加载插件 Fragment 过程，这样宿主可以不需要关心 Fragment 的异步加载逻辑
 */
public abstract class AbstractFragmentProxy extends Fragment {
    private FragmentPluginHelper mPluginHelper;
    @Nullable
    private Fragment mPluginFragment;

    protected abstract View onCreateUi(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState);

    protected abstract void onLoadPluginFragmentSuccess(FragmentManager fragmentManager, Fragment fragment, String packageName);

    protected abstract void onLoadPluginFragmentFail(int errorType, String packageName);

    @Nullable
    @Override
    public final View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mPluginHelper = new FragmentPluginHelper(getChildFragmentManager());
        View view = onCreateUi(inflater, container, savedInstanceState);
        loadPluginFragment();
        return view;
    }

    protected void loadPluginFragment() {
        Bundle arguments = getArguments();
        if (arguments != null) {
            String packageName = arguments.getString(IntentConstant.EXTRA_TARGET_PACKAGE_KEY);
            String className = arguments.getString(IntentConstant.EXTRA_TARGET_CLASS_KEY);
            final Context hostContext = getContext().getApplicationContext();
            if (PluginPackageManagerNative.getInstance(hostContext).isPackageInstalled(packageName)) {
                PluginManager.createFragment(hostContext, packageName, className, getArguments(), new IPluginElementLoadListener<Fragment>() {
                    @Override
                    public void onSuccess(Fragment fragment, String packageName) {
                        mPluginFragment = fragment;
                        if (isAdded()) {
                            onLoadPluginFragmentSuccess(getChildFragmentManager(), fragment, packageName);
                        }
                    }

                    @Override
                    public void onFail(int errorType, String packageName) {
                        if (isAdded()) {
                            onLoadPluginFragmentFail(errorType, packageName);
                        }
                    }
                });
            } else {
                if (isAdded()) {
                    onLoadPluginFragmentFail(ErrorType.ERROR_PLUGIN_LOAD_NOT_INSTALLED, packageName);
                }
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        mPluginHelper.beforeOnSaveInstanceState();
        super.onSaveInstanceState(outState);
        mPluginHelper.afterOnSaveInstanceState();
    }

    @Override
    public boolean getUserVisibleHint() {
        if (mPluginFragment != null) {
            return mPluginFragment.getUserVisibleHint();
        }
        return super.getUserVisibleHint();
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (mPluginFragment != null) {
            mPluginFragment.setUserVisibleHint(isVisibleToUser);
        }
    }
}
