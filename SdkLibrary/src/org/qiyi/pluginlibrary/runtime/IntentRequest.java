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

import android.content.Intent;
import android.content.ServiceConnection;

import java.lang.ref.WeakReference;

/**
 * Intent调起插件的请求, 缓存Intent和ServiceConnection对象
 * bindService时需要传递ServiceConnection
 */
public class IntentRequest {
    private Intent mIntent;  // intent
    private WeakReference<ServiceConnection> mScRef; // ServiceConnection

    public IntentRequest(Intent intent, ServiceConnection sc) {
        this.mIntent = intent;
        this.mScRef = new WeakReference<>(sc);
    }

    public Intent getIntent() {
        return mIntent;
    }

    public ServiceConnection getServiceConnection() {
        return mScRef.get();
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + mIntent.hashCode();
        ServiceConnection sc = mScRef.get();
        if (sc != null) {
            result = 31 * result + sc.hashCode();
        }
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof IntentRequest) {
            IntentRequest other = (IntentRequest)obj;
            if (!this.mIntent.equals(other.mIntent)) {
                return false;
            }
            ServiceConnection sc = this.getServiceConnection();
            ServiceConnection osc = other.getServiceConnection();
            return sc != null ? sc.equals(osc) : osc != null;
        }
        return false;
    }

    @Override
    public String toString() {
        String result = mIntent.toString();
        ServiceConnection sc = getServiceConnection();
        if (sc != null) {
            result += ", sc=" + sc.toString();
        }
        return result;
    }
}
