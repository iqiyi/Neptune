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

import android.view.View;
import android.view.ViewGroup;

/**
 * View 插件化辅助类，主要用来关闭 view 的 onSaveInstanceState 防止进程恢复时的 ClassNotFound
 */
public class ViewPluginHelper {
    /**
     * 取消 View 以及其 children View 的 onSaveInstanceState 调用
     * <p>
     * 耗时大约 1ms
     *
     * @param view 顶层 view
     */
    public static void disableViewSaveInstanceRecursively(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0, n = viewGroup.getChildCount(); i < n; i++) {
                disableViewSaveInstanceRecursively(viewGroup.getChildAt(i));
            }
        } else if (view != null) {
            view.setSaveEnabled(false);
        }
    }
}
