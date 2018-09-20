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

import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.SparseArray;
import android.view.View;

import org.qiyi.pluginlibrary.component.base.PluginSupportFragment;

import java.util.ArrayList;

/**
 * Fragment 插件的 Helper，辅助一些 OnSaveInstanceState 的操作，防止进程被回收以后恢复过程中的 ClassNotFound.
 */
public class FragmentPluginHelper {
    private FragmentManager mFragmentManager;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private SparseArray<Fragment> mActive;
    private ArrayList<Fragment> mAdded;
    private ArrayList<Object/* BackStackRecord */> mBackStack;

    public FragmentPluginHelper(FragmentManager fragmentManager) {
        mFragmentManager = fragmentManager;
    }

    /**
     * 取消 View 以及其 children View 的 onSaveInstanceState 调用
     * <p>
     * 耗时大约 1ms
     *
     * @param view 顶层 view
     */
    public static void disableViewSaveInstanceRecursively(View view) {
        ViewPluginHelper.disableViewSaveInstanceRecursively(view);
    }

    /**
     * 过滤掉插件 Fragment，插件 Fragment 不会被保存到 savedInstanceState 中.
     */
    public void beforeOnSaveInstanceState() {
        mActive = ReflectionUtils.on(mFragmentManager).get("mActive");
        mAdded = ReflectionUtils.on(mFragmentManager).get("mAdded");
        mBackStack = ReflectionUtils.on(mFragmentManager).get("mBackStack");
        // active
        if (mActive != null) {
            int size = mActive.size();
            SparseArray<Fragment> newActive = new SparseArray<>();
            for (int i = 0; i < size; i++) {
                Fragment fragment = mActive.valueAt(i);
                if (!(fragment instanceof PluginSupportFragment)) {
                    newActive.put(mActive.keyAt(i), fragment);
                }
            }
            ReflectionUtils.on(mFragmentManager).set("mActive", newActive);
        }
        // added
        ArrayList<Fragment> newAdded = new ArrayList<>();
        for (Fragment fragment : mAdded) {
            if (!(fragment instanceof PluginSupportFragment)) {
                newAdded.add(fragment);
            }
        }
        ReflectionUtils.on(mFragmentManager).set("mAdded", newAdded);
        // backStack
        if (mBackStack != null) {
            ArrayList<Object> newBackStack = new ArrayList<>();
            for (Object backStack : mBackStack) {
                ArrayList<Object/* BackStackRecord.Op */> ops = ReflectionUtils.on(backStack).get("mOps");
                boolean containsPluginFragment = false;
                for (Object op : ops) {
                    Fragment fragment = ReflectionUtils.on(op).get("fragment");
                    if (fragment instanceof PluginSupportFragment) {
                        containsPluginFragment = true;
                        break;
                    }
                }
                if (!containsPluginFragment) {
                    newBackStack.add(backStack);
                }
            }
            ReflectionUtils.on(mFragmentManager).set("mBackStack", newBackStack);
        }
    }

    /**
     * 待 onSaveInstanceState 执行完毕后，会恢复插件 Fragment
     */
    public void afterOnSaveInstanceState() {
        // 不能直接恢复，post 是为了等到 Fragment#performSaveInstanceState 执行完毕
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                ReflectionUtils.on(mFragmentManager).set("mActive", mActive);
                ReflectionUtils.on(mFragmentManager).set("mAdded", mAdded);
                ReflectionUtils.on(mFragmentManager).set("mBackStack", mBackStack);
            }
        });
    }
}
