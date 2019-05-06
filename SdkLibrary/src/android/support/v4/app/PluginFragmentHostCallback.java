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
package android.support.v4.app;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.view.View;

import org.qiyi.pluginlibrary.component.wraper.ActivityWrapper;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * 插件 Fragment 的 mHost 对象，委托了大部分操作给原来的 mHost
 */
public class PluginFragmentHostCallback extends FragmentHostCallback<FragmentActivity> {
    private FragmentHostCallback<FragmentActivity> mOrigin;

    public PluginFragmentHostCallback(FragmentHostCallback<FragmentActivity> origin, ActivityWrapper activityWrapper) {
        super(activityWrapper);
        mOrigin = origin;
    }

    @Nullable
    @Override
    public FragmentActivity onGetHost() {
        return (FragmentActivity) getActivity();
    }

    @Override
    public void onDump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        mOrigin.onDump(prefix, fd, writer, args);
    }

    @Override
    public boolean onShouldSaveFragmentState(Fragment fragment) {
        return mOrigin.onShouldSaveFragmentState(fragment);
    }

    @Override
    public void onSupportInvalidateOptionsMenu() {
        mOrigin.onSupportInvalidateOptionsMenu();
    }

    @Override
    public boolean onHasWindowAnimations() {
        return mOrigin.onHasWindowAnimations();
    }

    @Override
    public int onGetWindowAnimations() {
        return mOrigin.onGetWindowAnimations();
    }

    @Nullable
    @Override
    public View onFindViewById(int id) {
        return mOrigin.onFindViewById(id);
    }

    @Override
    public boolean onHasView() {
        return mOrigin.onHasView();
    }

    @Override
    Handler getHandler() {
        return mOrigin.getHandler();
    }

    @Override
    FragmentManagerImpl getFragmentManagerImpl() {
        return mOrigin.getFragmentManagerImpl();
    }

    @Override
    void onAttachFragment(Fragment fragment) {
        mOrigin.onAttachFragment(fragment);
    }

    @Override
    public Fragment instantiate(Context context, String className, Bundle arguments) {
        return mOrigin.instantiate(context, className, arguments);
    }
}
