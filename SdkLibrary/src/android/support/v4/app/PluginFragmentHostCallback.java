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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.SimpleArrayMap;
import android.view.LayoutInflater;
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

    @Override
    Activity getActivity() {
        return super.getActivity();
    }

    @Override
    Context getContext() {
        return super.getContext();
    }

    @Override
    public LayoutInflater onGetLayoutInflater() {
        return super.onGetLayoutInflater();
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
    public void onStartActivityFromFragment(Fragment fragment, Intent intent, int requestCode) {
        // 调用 supper 最终会走到 ActivityWrapper 的 startActivity
        super.onStartActivityFromFragment(fragment, intent, requestCode);
    }

    @Override
    public void onStartActivityFromFragment(Fragment fragment, Intent intent, int requestCode, @Nullable Bundle options) {
        super.onStartActivityFromFragment(fragment, intent, requestCode, options);
    }

    @Override
    public void onStartIntentSenderFromFragment(Fragment fragment, IntentSender intent, int requestCode, @Nullable Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags, Bundle options) throws IntentSender.SendIntentException {
        super.onStartIntentSenderFromFragment(fragment, intent, requestCode, fillInIntent, flagsMask, flagsValues, extraFlags, options);
    }

    @Override
    public void onRequestPermissionsFromFragment(@NonNull Fragment fragment, @NonNull String[] permissions, int requestCode) {
        super.onRequestPermissionsFromFragment(fragment, permissions, requestCode);
    }

    @Override
    public boolean onShouldShowRequestPermissionRationale(@NonNull String permission) {
        return super.onShouldShowRequestPermissionRationale(permission);
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
    LoaderManagerImpl getLoaderManagerImpl() {
        return mOrigin.getLoaderManagerImpl();
    }

    @Override
    void inactivateFragment(String who) {
        mOrigin.inactivateFragment(who);
    }

    @Override
    void onAttachFragment(Fragment fragment) {
        mOrigin.onAttachFragment(fragment);
    }

    @Override
    boolean getRetainLoaders() {
        return mOrigin.getRetainLoaders();
    }

    @Override
    void doLoaderStart() {
        mOrigin.doLoaderStart();
    }

    @Override
    void doLoaderStop(boolean retain) {
        mOrigin.doLoaderStop(retain);
    }

    @Override
    void doLoaderRetain() {
        mOrigin.doLoaderRetain();
    }

    @Override
    void doLoaderDestroy() {
        mOrigin.doLoaderDestroy();
    }

    @Override
    void reportLoaderStart() {
        mOrigin.reportLoaderStart();
    }

    @Override
    LoaderManagerImpl getLoaderManager(String who, boolean started, boolean create) {
        return mOrigin.getLoaderManager(who, started, create);
    }

    @Override
    SimpleArrayMap<String, LoaderManager> retainLoaderNonConfig() {
        return mOrigin.retainLoaderNonConfig();
    }

    @Override
    void restoreLoaderNonConfig(SimpleArrayMap<String, LoaderManager> loaderManagers) {
        mOrigin.restoreLoaderNonConfig(loaderManagers);
    }

    @Override
    void dumpLoaders(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        mOrigin.dumpLoaders(prefix, fd, writer, args);
    }

    @Override
    public Fragment instantiate(Context context, String className, Bundle arguments) {
        return mOrigin.instantiate(context, className, arguments);
    }
}
