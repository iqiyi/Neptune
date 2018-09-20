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
package org.qiyi.pluginlibrary.component.processmgr;


import android.content.Context;

/**
 * 管理插件运行在哪个进程, 可以由外部配置
 */
public class ProcessManager {

    private static final String PROXY_PROCESS1 = ":plugin1";
    private static final String PROXY_PROCESS2 = ":plugin2";
    private static IProcessSelector sOuterSelector;

    /**
     * 设置进程选择器
     */
    public static void setOuterSelector(IProcessSelector selector) {
        sOuterSelector = selector;
    }

    /**
     * 为插件pkg选择进程名
     *
     * @param hostContext  宿主的Context
     * @param pkgName  插件的包名
     * @return 返回插件运行的进程名
     */
    public static String chooseDefaultProcess(Context hostContext, String pkgName) {
        // 默认放到插件进程1
        return hostContext.getPackageName() + PROXY_PROCESS1;
    }

    /**
     * 获取进程名称对应的index
     *
     * @param processName 插件运行的进程名称
     * @return 插件的index
     */
    public static int getProcessIndex(String processName) {

        if (sOuterSelector != null) {
            return sOuterSelector.getProcessIndex(processName);
        }
        // 默认选择策略
        if (processName.endsWith(PROXY_PROCESS1)) {
            return 1;
        } else if (processName.endsWith(PROXY_PROCESS2)) {
            return 2;
        }
        // 运行在主进程
        return 0;
    }

    public interface IProcessSelector {

        int getProcessIndex(String processName);
    }
}
