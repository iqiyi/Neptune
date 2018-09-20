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
package org.qiyi.pluginlibrary.component.stackmgr;

import android.content.ServiceConnection;
import android.text.TextUtils;

import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 保存所有正在运行的Service
 */
public class PServiceSupervisor {
    /**
     * 记录正在运行的service
     */
    private final static ConcurrentMap<String, PluginServiceWrapper> sAliveServices = new ConcurrentHashMap<String, PluginServiceWrapper>(
            1);

    private final static ConcurrentMap<String, ServiceConnection> sAliveServiceConnection = new ConcurrentHashMap<String, ServiceConnection>();

    public static ConcurrentMap<String, PluginServiceWrapper> getAliveServices() {
        return sAliveServices;
    }

    public static PluginServiceWrapper getServiceByIdentifer(String identity) {
        if (TextUtils.isEmpty(identity)) {
            return null;
        }
        return sAliveServices.get(identity);
    }

    public static void removeServiceByIdentity(String identity) {
        if (TextUtils.isEmpty(identity)) {
            return;
        }
        sAliveServices.remove(identity);
    }

    public static void addServiceByIdentity(String identity, PluginServiceWrapper serviceWrapper) {
        if (TextUtils.isEmpty(identity) || null == serviceWrapper) {
            return;
        }
        sAliveServices.put(identity, serviceWrapper);
    }

    public static void clearServices() {
        sAliveServices.clear();
    }

    public static ConcurrentMap<String, ServiceConnection> getAllServiceConnection() {
        return sAliveServiceConnection;
    }

    public static ServiceConnection getConnection(String identity) {
        if (TextUtils.isEmpty(identity)) {
            return null;
        }
        return sAliveServiceConnection.get(identity);
    }

    public static void clearConnections() {
        sAliveServiceConnection.clear();
    }

    public static void addServiceConnectionByIdentifer(String identity, ServiceConnection conn) {
        if (TextUtils.isEmpty(identity) || null == conn) {
            return;
        }
        sAliveServiceConnection.put(identity, conn);
    }

    public static void removeServiceConnection(ServiceConnection conn) {
        if (null == conn) {
            return;
        }
        if (sAliveServiceConnection.containsValue(conn)) {
            String key = null;
            for (Entry<String, ServiceConnection> entry : sAliveServiceConnection.entrySet()) {
                if (conn == entry.getValue()) {
                    key = entry.getKey();
                    break;
                }
            }
            if (!TextUtils.isEmpty(key)) {
                sAliveServiceConnection.remove(key);
            }
        }
    }
}
