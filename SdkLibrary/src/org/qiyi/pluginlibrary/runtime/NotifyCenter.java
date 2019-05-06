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
package org.qiyi.pluginlibrary.runtime;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import org.qiyi.pluginlibrary.constant.IntentConstant;

/**
 * 发送广播通知业务层做一些特殊处理
 * 比如插件加载成功
 */
public class NotifyCenter {


    /**
     * 通知插件加载完毕（解决快捷方式添加闪屏时，插件还没启动，闪屏就关闭了）
     */
    public static void notifyPluginActivityLoaded(Context context) {
        Intent intent = new Intent();
        intent.setAction(IntentConstant.ACTION_PLUGIN_LOADED);
        context.sendBroadcast(intent);
    }


    /**
     * 通知调用方可以取消Loading对话框
     */
    public static void notifyPluginStarted(Context context, Intent intent) {
        if (null != context && null != intent && !TextUtils.isEmpty(intent.getStringExtra(
                IntentConstant.EXTRA_SHOW_LOADING))) {
            context.sendBroadcast(new Intent(IntentConstant.EXTRA_SHOW_LOADING));
        }
    }

    /**
     * 通知插件启动错误
     */
    public static void notifyStartPluginError(Context context) {
        Intent intent = new Intent();
        intent.setAction(IntentConstant.ACTION_START_PLUGIN_ERROR);
        context.sendBroadcast(intent);
    }

    /**
     * 通知 Service 绑定成功
     */
    public static void notifyServiceConnected(Context context, String serviceName) {
        Intent intent = new Intent(IntentConstant.ACTION_SERVICE_CONNECTED);
        intent.putExtra(IntentConstant.EXTRA_SERVICE_CLASS, serviceName);
        // 在插件 activity 进程被回收以后恢复过程中，需要保证有序，具体参见恢复逻辑
        context.sendOrderedBroadcast(intent, null);
    }
}
