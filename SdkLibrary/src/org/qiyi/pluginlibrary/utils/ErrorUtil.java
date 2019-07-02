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

import android.os.Build;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;

/**
 * 处理异常的工具类
 */
public class ErrorUtil {

    /**
     * Debug环境下直接抛出异常
     * @param tr 异常
     */
    public static void throwErrorIfNeed(Throwable tr) {
        throwErrorIfNeed(tr, false);
    }

    /**
     * Debug环境下直接抛出异常
     * @param tr  异常
     * @param forceThrow  是否强制rethrow
     */
    public static void throwErrorIfNeed(Throwable tr, boolean forceThrow) {

        if (PluginDebugLog.isDebug()) {
            tr.printStackTrace();  // 打印堆栈

            StringBuilder sb = new StringBuilder("throwable occured: ");
            sb.append(tr.getClass().getName())
                    .append(", msg: ")
                    .append(tr.getMessage())
                    .append(", detail: ")
                    .append(getStackTraceString(tr));
            Log.e("plugin_error", sb.toString());
            // debug模式强制抛出异常
            throwError(tr);
        } else if (isSeriousException(tr)) {
            // 比较严重的异常，直接打印StackTrace到logcat
            tr.printStackTrace();
        }

        if (forceThrow) {
            throwError(tr);
        }
    }

    private static void throwError(Throwable tr) {
        if (tr instanceof Error) {
            throw (Error) tr;
        } else if (tr instanceof RuntimeException) {
            throw (RuntimeException) tr;
        } else if (tr instanceof Exception) {
            // normal exception, wrap it with RuntimeException
            throw new RuntimeException(tr);
        }
    }

    private static boolean isSeriousException(Throwable tr) {
        if (tr instanceof RuntimeException
            || tr instanceof Error) {
            // Runtime或者Error级别
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
            && tr instanceof ReflectiveOperationException) {
            // 反射相关异常
            return true;
        } else if (tr instanceof NoSuchFieldException
            || tr instanceof NoSuchMethodException
            || tr instanceof ClassNotFoundException
            || tr instanceof IllegalAccessException
            || tr instanceof InstantiationException
            || tr instanceof InvocationTargetException) {
            // 反射相关异常
            return true;
        }
        return false;
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
