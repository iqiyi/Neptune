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

import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 处理异常的工具类
 */
public class ErrorUtil {

    /**
     * Debug环境下直接抛出异常
     */
    public static void throwErrorIfNeed(Throwable tr) {

        if (PluginDebugLog.isDebug()) {
            tr.printStackTrace();  // 打印堆栈

            StringBuilder sb = new StringBuilder("throwable occured: ");
            sb.append(tr.getClass().getName())
                    .append(", msg: ")
                    .append(tr.getMessage())
                    .append(", detail: ")
                    .append(getStackTraceString(tr));
            Log.e("plugin_error", sb.toString());

            if (tr instanceof Error) {
                throw (Error) tr;
            } else if (tr instanceof RuntimeException) {
                throw (RuntimeException) tr;
            } else if (tr instanceof Exception) {
                // normal exception, wrap it with RuntimeExcepiton
                throw new RuntimeException(tr);
            }
        } else if (tr instanceof RuntimeException
                || tr instanceof Error) {
            // 比较严重的异常，直接打印StackTrace
            tr.printStackTrace();
        }
    }

    /**
     * Handy function to get a loggable stack trace from a Throwable
     * copy from {@link android.util.Log#getStackTraceString(Throwable)} and make some modify
     *
     * @param tr An exception to log
     */
    private static String getStackTraceString(Throwable tr) {
        if (tr == null) {
            return "";
        }

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        try {
            tr.printStackTrace(pw);
            pw.flush();
            return sw.toString();
        } finally {
            pw.close();
        }
    }
}
