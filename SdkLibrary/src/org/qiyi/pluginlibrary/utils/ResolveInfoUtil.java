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

import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.util.DisplayMetrics;

import org.qiyi.pluginlibrary.pm.PluginPackageInfo;
import org.qiyi.pluginlibrary.pm.PluginPackageInfo.ActivityIntentInfo;
import org.qiyi.pluginlibrary.pm.PluginPackageInfo.ProviderIntentInfo;
import org.qiyi.pluginlibrary.pm.PluginPackageInfo.ReceiverIntentInfo;
import org.qiyi.pluginlibrary.pm.PluginPackageInfo.ServiceIntentInfo;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class ResolveInfoUtil {
    /**
     * 解析插件Apk里的四大组件信息
     *
     * @param context 宿主的Context
     * @param dexPath 插件apk路径
     * @param target  插件PackageInfo
     * @deprecated
     */
    @Deprecated
    public static void parseResolveInfo(Context context, String dexPath, PluginPackageInfo target) {

        try { // 先得到解析类PackageParser并实例化
            Class<?> packageParserClass = Class.forName("android.content.pm.PackageParser");
            Object packageParser = null;
            if (Build.VERSION.SDK_INT >= 21) {
                packageParser = packageParserClass.getConstructor().newInstance();
            } else {
                packageParser = packageParserClass.getConstructor(String.class).newInstance(dexPath);
            }
            //
            File sourceFile = new File(dexPath);
            Object pkg = null;  // PackageParser$Package
            // 调用PackageParser的parsePackage解析数据
            if (Build.VERSION.SDK_INT >= 21) {
                Method method = packageParserClass.getDeclaredMethod("parsePackage", File.class, int.class);
                method.setAccessible(true);
                pkg = method.invoke(packageParser, sourceFile, 0);
            } else {
                DisplayMetrics metrics;  //构造参数
                if (context != null) {
                    metrics = context.getResources().getDisplayMetrics();
                } else {
                    metrics = new DisplayMetrics();
                    metrics.setToDefaults();
                }
                Method method = packageParserClass.getDeclaredMethod("parsePackage", File.class, String.class, DisplayMetrics.class,
                        int.class);
                method.setAccessible(true);
                pkg = method.invoke(packageParser, sourceFile, dexPath, metrics, 0);
            }

            if (null != pkg) {
                // 获取Activity信息
                Field activities = pkg.getClass().getDeclaredField("activities");
                activities.setAccessible(true);
                ArrayList<?> activityFilters = (ArrayList<?>) activities.get(pkg);
                for (int i = 0; i < activityFilters.size(); i++) {
                    Object activity = activityFilters.get(i);
                    Field intentsClassName = activity.getClass().getField("className");
                    intentsClassName.setAccessible(true);
                    String className = (String) intentsClassName.get(activity);
                    ActivityInfo info = target.findActivityByClassName(className);
                    if (info == null) {
                        // 反射info字段
                        Field actInfoField = activity.getClass().getField("info");
                        actInfoField.setAccessible(true);
                        info = (ActivityInfo) actInfoField.get(activity);
                    }
                    if (null != info) {
                        ActivityIntentInfo actInfo = new ActivityIntentInfo(info);
                        Field intentsField = activity.getClass().getField("intents");
                        intentsField.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        ArrayList<IntentFilter> intents = (ArrayList<IntentFilter>) intentsField.get(activity);
                        actInfo.setFilter(intents);
                        target.addActivity(actInfo);
                    }
                }

                // 获取Receivers信息
                Field receivers = pkg.getClass().getDeclaredField("receivers");
                receivers.setAccessible(true);
                ArrayList<?> receiverFilters = (ArrayList<?>) receivers.get(pkg);
                for (int i = 0; i < receiverFilters.size(); i++) {
                    Object receiver = receiverFilters.get(i);

                    Field intentsClassName = receiver.getClass().getField("className");
                    intentsClassName.setAccessible(true);
                    String className = (String) intentsClassName.get(receiver);
                    ActivityInfo info = target.findReceiverByClassName(className);
                    if (info == null) {
                        // 反射info字段
                        Field actInfoField = receiver.getClass().getField("info");
                        actInfoField.setAccessible(true);
                        info = (ActivityInfo) actInfoField.get(receiver);
                    }

                    if (null != info) {
                        ReceiverIntentInfo receiverInfo = new ReceiverIntentInfo(info);
                        Field intentsField = receiver.getClass().getField("intents");
                        intentsField.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        ArrayList<IntentFilter> intents = (ArrayList<IntentFilter>) intentsField.get(receiver);
                        receiverInfo.setFilter(intents);
                        target.addReceiver(receiverInfo);
                    }
                }

                // 获取Service信息
                Field services = pkg.getClass().getDeclaredField("services");
                services.setAccessible(true);
                ArrayList<?> serviceFilters = (ArrayList<?>) services.get(pkg);
                for (int i = 0; i < serviceFilters.size(); i++) {
                    Object service = serviceFilters.get(i);

                    Field intentsClassName = service.getClass().getField("className");
                    intentsClassName.setAccessible(true);
                    String className = (String) intentsClassName.get(service);
                    ServiceInfo info = target.findServiceByClassName(className);
                    if (info == null) {
                        // 反射info字段
                        Field serInfoField = service.getClass().getField("info");
                        serInfoField.setAccessible(true);
                        info = (ServiceInfo) serInfoField.get(service);
                    }

                    if (null != info) {
                        ServiceIntentInfo serviceInfo = new ServiceIntentInfo(info);
                        Field intentsField = service.getClass().getField("intents");
                        intentsField.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        ArrayList<IntentFilter> intents = (ArrayList<IntentFilter>) intentsField.get(service);
                        serviceInfo.setFilter(intents);
                        target.addService(serviceInfo);
                    }
                }

                // 获取Provider信息
                Field providers = pkg.getClass().getDeclaredField("providers");
                providers.setAccessible(true);
                ArrayList<?> providerFilters = (ArrayList<?>) providers.get(pkg);
                for (int i = 0; i < providerFilters.size(); i++) {
                    Object provider = providerFilters.get(i);

                    Field intentsClassName = provider.getClass().getField("className");
                    intentsClassName.setAccessible(true);
                    String className = (String) intentsClassName.get(provider);
                    ProviderInfo info = target.findProviderByClassName(className);
                    if (info == null) {
                        // 反射info字段
                        Field serInfoField = provider.getClass().getField("info");
                        serInfoField.setAccessible(true);
                        info = (ProviderInfo) serInfoField.get(provider);
                    }

                    if (null != info) {
                        ProviderIntentInfo providerInfo = new ProviderIntentInfo(info);
                        Field intentsField = provider.getClass().getField("intents");
                        intentsField.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        ArrayList<IntentFilter> intents = (ArrayList<IntentFilter>) intentsField.get(provider);
                        providerInfo.setFilter(intents);
                        target.addProvider(providerInfo);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 使用自定义的ManifestParser解析组件信息
     *
     * @param context 宿主的Context
     * @param dexPath 插件apk路径
     * @param target  插件PackageInfo
     */
    public static void parseNewResolveInfo(Context context, String dexPath, PluginPackageInfo target) {

        // 解析组件信息
        ManifestParser parser = new ManifestParser(context, dexPath);
        // 获取Activity信息
        for (ManifestParser.ComponentBean activity : parser.activities) {
            ActivityInfo info = target.findActivityByClassName(activity.className);
            if (info != null) {
                ActivityIntentInfo actInfo = new ActivityIntentInfo(info);
                actInfo.setFilter(activity.intentFilters);
                target.addActivity(actInfo);
            }
        }
        // 获取Receiver信息
        for (ManifestParser.ComponentBean receiver : parser.receivers) {
            ActivityInfo info = target.findReceiverByClassName(receiver.className);
            if (info != null) {
                ReceiverIntentInfo recInfo = new ReceiverIntentInfo(info);
                recInfo.setFilter(receiver.intentFilters);
                target.addReceiver(recInfo);
            }
        }
        // 获取Service信息
        for (ManifestParser.ComponentBean service : parser.services) {
            ServiceInfo info = target.findServiceByClassName(service.className);
            if (info != null) {
                ServiceIntentInfo serInfo = new ServiceIntentInfo(info);
                serInfo.setFilter(service.intentFilters);
                target.addService(serInfo);
            }
        }
        // 获取Provider信息
        for (ManifestParser.ComponentBean provider : parser.providers) {
            ProviderInfo info = target.findProviderByClassName(provider.className);
            if (info != null) {
                ProviderIntentInfo proInfo = new ProviderIntentInfo(info);
                proInfo.setFilter(provider.intentFilters);
                target.addProvider(proInfo);
            }
        }
    }
}
