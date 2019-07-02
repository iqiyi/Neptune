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


import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Process;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Locale;

public class ProcessUtils {
    /* 是否主进程 */
    private static Boolean sIsMainProcess;
    /* 当前进程名 */
    private static String currentProcessName = null;

    public static boolean isMainProcess(Context context) {
        if (sIsMainProcess == null) {
            String mainProcess = null;
            ApplicationInfo appInfo = context.getApplicationInfo();
            if (appInfo != null) {
                mainProcess = appInfo.processName;
            }
            if (TextUtils.isEmpty(mainProcess)) {
                mainProcess = context.getPackageName();
            }

            String curProcessName = getCurrentProcessName(context);
            sIsMainProcess = TextUtils.equals(curProcessName, mainProcess);
        }
        return sIsMainProcess;
    }

    public static String getCurrentProcessName(Context context) {
        if (!TextUtils.isEmpty(currentProcessName)) {
            return currentProcessName;
        }

        int pid = android.os.Process.myPid();
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        try {
            for (ActivityManager.RunningAppProcessInfo process : manager.getRunningAppProcesses()) {
                if (process.pid == pid) {
                    return process.processName;
                }
            }
        } catch (Exception e) {
            // ActivityManager.getRunningAppProcesses() may throw NPE in some custom-made devices (oem BIRD)
        }

        // try to read process name in /proc/pid/cmdline if no result from activity manager
        String cmdline = null;
        BufferedReader processFileReader = null;
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(String.format(Locale.getDefault(), "/proc/%d/cmdline", Process.myPid()));
            processFileReader = new BufferedReader(fileReader);
            cmdline = processFileReader.readLine().trim();
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            FileUtils.closeQuietly(processFileReader);
            FileUtils.closeQuietly(fileReader);
        }

        return cmdline;
    }
}
