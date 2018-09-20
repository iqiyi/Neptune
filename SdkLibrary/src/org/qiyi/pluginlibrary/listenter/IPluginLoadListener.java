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
package org.qiyi.pluginlibrary.listenter;

/**
 * 加载插件实例到内存，PluginLoadedApk监听回调
 */
public interface IPluginLoadListener {

    /**
     * 加载成功的回调，主线程回调
     *
     * @param packageName 加载成功的插件包名
     */
    void onLoadSuccess(String packageName);

    /**
     * 加载失败的回调，主线程回调
     *
     * @param packageName 加载失败的插件包名
     */
    void onLoadFailed(String packageName);
}
