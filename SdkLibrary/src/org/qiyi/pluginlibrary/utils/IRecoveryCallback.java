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

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.TextView;

import org.qiyi.pluginlibrary.R;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * 恢复插件 Activity 时的准备阶段 UI，由宿主提供
 */
public interface IRecoveryCallback {
    /**
     * 恢复前准备，比如启动需要的 service
     *
     * @param context   context
     * @param pkgName   插件包名
     * @param className 插件类名
     */
    void beforeRecovery(Context context, String pkgName, String className);

    /**
     * 准备恢复的 UI
     *
     * @param activity  Activity
     * @param pkgName   插件包名
     * @param className 插件类名
     */
    void onSetContentView(Activity activity, String pkgName, String className);

    /**
     * 恢复后工作
     *
     * @param context   context
     * @param pkgName   插件包名
     * @param className 插件类名
     */
    void afterRecovery(Context context, String pkgName, String className);

    /**
     * 检查外部环境是否满足恢复条件
     *
     * @param context   context
     * @param pkgName   插件包名
     * @param className 插件类名
     * @return true 可以启动 false 不可以启动
     */
    boolean beforeLaunch(Context context, String pkgName, String className);

    /**
     * 默认的 RecoveryUi
     */
    class DefaultRecoveryCallback implements IRecoveryCallback {

        @Override
        public void beforeRecovery(Context context, String pkgName, String className) {
        }

        @Override
        public void onSetContentView(Activity activity, String pkgName, String className) {
            TextView textView = new TextView(activity);
            textView.setText(R.string.under_recovery);
            FrameLayout frameLayout = new FrameLayout(activity);
            LayoutParams lp = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER);
            frameLayout.addView(textView, lp);
            activity.setContentView(frameLayout);
        }

        @Override
        public void afterRecovery(Context context, String pkgName, String className) {
        }

        @Override
        public boolean beforeLaunch(Context context, String pluginPackageName, String pluginClassName) {
            return true;
        }
    }
}
