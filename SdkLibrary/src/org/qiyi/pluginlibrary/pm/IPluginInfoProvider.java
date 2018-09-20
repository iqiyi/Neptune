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
package org.qiyi.pluginlibrary.pm;

import java.io.File;
import java.util.List;

public interface IPluginInfoProvider {

    /* ------------------通过IPC获取插件信息接口定义 START------------------ */

    /*
    以下接口只能在PluginPackageManagerService这个service被bind成功之后才能得到正确的数据，
    PluginPackageManagerService运行在主进程中。
     */

    /**
     * 获取已安装的插件
     *
     * @return 已安装插件信息列表
     */
    List<PluginLiteInfo> getInstalledPackages();

    /**
     * 通过包名获得特定的插件报信息
     *
     * @param packageName 插件包名
     * @return {@link PluginLiteInfo}插件包信息
     */
    PluginLiteInfo getPackageInfo(String packageName);

    /**
     * 判断插件是否安装
     *
     * @param packageName 插件包名
     * @return true：安装  false：未安装
     */
    boolean isPackageInstalled(String packageName);

    /**
     * 判断插件包是否可以安装
     *
     * @param info {@link PluginLiteInfo}插件包信息
     * @return true：可以安装  false：不可以安装
     */
    boolean canInstallPackage(PluginLiteInfo info);

    /**
     * 判断插件是否可以卸载
     *
     * @param info {@link PluginLiteInfo}插件包信息
     * @return true：可以卸载  false：不可以卸载
     */
    boolean canUninstallPackage(PluginLiteInfo info);

    /**
     * 处理插件异常信息
     *
     * @param pkgName      插件包名
     * @param exceptionStr 异常信息
     */
    void handlePluginException(String pkgName, String exceptionStr);

    /**
     * 获取当前插件依赖的插件列表
     *
     * @param packageName 插件包名
     * @return 依赖的插件列表，null表示没有依赖
     */
    List<String> getPluginRefs(String packageName);

    /* ------------------通过IPC获取插件信息接口定义 END------------------ */

    /** ------------------分割线------------------*/


    /* ------------------直接获取插件信息接口定义 START------------------ */

    /*
    下面接口仅仅在ipc还没有建立的时候调用，也就是PluginPackageManagerService没有bind成功的时候调用，
    下面的部分接口调用都存在比较久的耗时，因为要读取本地文件中的数据。
     */

    /**
     * 获取已安装的插件（不通过IPC）
     *
     * @return 已安装插件信息列表
     */
    List<PluginLiteInfo> getInstalledPackagesDirectly();

    /**
     * 判断插件是否安装（不通过IPC）
     *
     * @param packageName 插件包名
     * @return true：安装  false：未安装
     */
    boolean isPackageInstalledDirectly(String packageName);

    /**
     * 获取当前插件依赖的插件列表（不通过IPC）
     *
     * @param packageName 插件包名
     * @return 依赖的插件列表，null表示没有依赖
     */
    List<String> getPluginRefsDirectly(String packageName);

    /**
     * 通过包名获得特定的插件报信息（不通过IPC）
     *
     * @param packageName 插件包名
     * @return {@link PluginLiteInfo}插件包信息
     */
    PluginLiteInfo getPackageInfoDirectly(String packageName);

    /**
     * 获取内置存储的files根目录
     */
    File getExternalFilesRootDirDirectly();

    /**
     * 获取内置存储的cache根目录
     */
    File getExternalCacheRootDirDirectly();

    /* ------------------直接获取插件信息接口定义 END------------------ */
}
