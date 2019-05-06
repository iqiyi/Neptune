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
package org.qiyi.pluginlibrary;

import org.qiyi.pluginlibrary.pm.IPluginInfoProvider;
import org.qiyi.pluginlibrary.utils.IRecoveryCallback;

/**
 * 插件框架运行配置信息
 *
 * author: liuchun
 * date: 2018/6/4
 */
public final class NeptuneConfig {
    /* 传统的插件框架模式，使用InstrActivityProxy进行代理转发 */
    @Deprecated
    public static final int LEGACY_MODE = 0;
    /* Hook Instrumentation方案 */
    public static final int INSTRUMENTATION_MODE = 1;
    /* Hook Instrumentation方案 + Base PluginActivity方案 */
    public static final int INSTRUMENTATION_BASEACT_MODE = 2;
    /* 插件框架运行模式，已经全面切换到Hook Instrumentation方案，适配Android P */
    private int mSdkMode;

    private IPluginInfoProvider mPluginInfoProvider;
    private IRecoveryCallback mRecoveryCallback;
    /* 是否支持插件ContentProvider */
    private boolean mSupportProvider;
    /* 是否使用独立进程安装插件 */
    private boolean mInstallerProcess;
    /* Debug调试日志是否打开 */
    private boolean mIsDebug;

    NeptuneConfig(NeptuneConfigBuilder builder) {
        this.mSdkMode = builder.sdkMode;
        this.mPluginInfoProvider = builder.pluginInfoProvider;
        this.mRecoveryCallback = builder.recoveryCallback;
        this.mIsDebug = builder.isDebug;
        this.mInstallerProcess = builder.installerProcess;
        this.mSupportProvider = builder.supportProvider;
    }


    public int getSdkMode() {
        return mSdkMode;
    }

    public IPluginInfoProvider getPluginInfoProvider() {
        return mPluginInfoProvider;
    }

    public IRecoveryCallback getRecoveryCallback() {
        return mRecoveryCallback;
    }

    public boolean withInstallerProcess() {
        return mInstallerProcess;
    }

    public boolean isSupportProvider() {
        return mSupportProvider;
    }

    public boolean isDebug() {
        return mIsDebug;
    }

    public static class NeptuneConfigBuilder {
        int sdkMode = 0;
        IPluginInfoProvider pluginInfoProvider;
        IRecoveryCallback recoveryCallback;
        boolean supportProvider;
        boolean installerProcess;
        boolean isDebug;

        public NeptuneConfigBuilder configSdkMode(int sdkMode) {
            this.sdkMode = sdkMode;
            return this;
        }

        public NeptuneConfigBuilder pluginInfoProvider(IPluginInfoProvider pluginInfoProvider) {
            this.pluginInfoProvider = pluginInfoProvider;
            return this;
        }

        public NeptuneConfigBuilder recoveryCallback(IRecoveryCallback callback) {
            this.recoveryCallback = callback;
            return this;
        }

        public NeptuneConfigBuilder enableDebug(boolean isDebuggable) {
            this.isDebug = isDebuggable;
            return this;
        }

        public NeptuneConfigBuilder supportProvider(boolean supportProvider) {
            this.supportProvider = supportProvider;
            return this;
        }

        public NeptuneConfigBuilder installerProcess(boolean process) {
            this.installerProcess = process;
            return this;
        }

        public NeptuneConfig build() {
            return new NeptuneConfig(this);
        }
    }
}
