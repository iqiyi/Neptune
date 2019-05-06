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

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.util.Pair;
import android.util.Log;

import java.util.concurrent.CountDownLatch;

public class RunUtil {
    private static final int MSG_RUN_ON_UITHREAD = 0x01;
    private static Handler sHandler;

    public static void runOnUiThread(Runnable runnable) {
        runOnUiThread(runnable, false);
    }

    public static void runOnUiThread(Runnable runnable, boolean waitUtilDown) {
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            runnable.run();
            return;
        }
        CountDownLatch countDownLatch = null;
        if (waitUtilDown) {
            countDownLatch = new CountDownLatch(1);
        }
        Pair<Runnable, CountDownLatch> pair = new Pair<>(runnable, countDownLatch);
        getHandler().obtainMessage(MSG_RUN_ON_UITHREAD, pair).sendToTarget();
        if (waitUtilDown) {
            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static Handler getHandler() {
        synchronized (RunUtil.class) {
            if (sHandler == null) {
                sHandler = new InternalHandler();
            }
            return sHandler;
        }
    }

    private static class InternalHandler extends Handler {
        public InternalHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_RUN_ON_UITHREAD) {
                Pair<Runnable, CountDownLatch> pair = (Pair<Runnable, CountDownLatch>)msg.obj;
                try {
                    Runnable runnable = pair.first;
                    runnable.run();
                } finally {
                    if (pair.second != null) {
                        pair.second.countDown();
                    }
                }
            }
        }
    }
}
