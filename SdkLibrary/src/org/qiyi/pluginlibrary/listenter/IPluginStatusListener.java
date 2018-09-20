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


public interface IPluginStatusListener {

    /**
     * 初始化成功的回调，主线程
     *
     * @param packageName 加载成功的插件包名
     */
    void onInitFinished(String packageName);

    /**
     * PluginLoadedApk加载成功回调
     *
     * @param packageName 加载的插件包名
     */
    void onPluginReady(String packageName);
}
