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
package org.qiyi.pluginlibrary.pm;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import org.qiyi.pluginlibrary.Neptune;
import org.qiyi.pluginlibrary.error.ErrorType;
import org.qiyi.pluginlibrary.install.PluginInstaller;
import org.qiyi.pluginlibrary.runtime.PluginManager;
import org.qiyi.pluginlibrary.utils.ErrorUtil;
import org.qiyi.pluginlibrary.utils.PluginDebugLog;
import org.qiyi.pluginlibrary.utils.ResolveInfoUtil;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 存放插件apk的{@link PackageInfo}里面的信息
 */
public class PluginPackageInfo implements Parcelable {

    private static final String TAG = "PluginPackageInfo";

    private static final String META_KEY_PLUGIN_APPLICATION_SPECIAL = "pluginapp_application_special";
    private static final String PLUGIN_APPLICATION_INFO = "handle_plugin_appinfo";
    private static final String PLUGIN_APPLICATION_CODE_PATH = "handle_plugin_code_path";

    /**
     * 配置是否注入到宿主的ClassLoader, 已废弃
     */
    private static final String META_KEY_CLASS_INJECT = "pluginapp_class_inject";
    /**
     * 配置资源id分段之后，是否合并宿主的资源到插件的AssetManager
     */
    private static final String META_KEY_MERGE_RES = "pluginapp_res_merge";
    /**
     * 配置是否插入Webview的资源到插件资源池
     */
    private static final String META_KEY_ADD_WEBVIEW_RES = "pluginapp_add_webview_res";
    /**
     * 配置插件是否运行在独立空间，完全不依赖宿主的类和资源
     */
    private static final String META_KEY_INDIVIDUAL = "pluginapp_individual";
    /**
     * 配置插件是否支持ContentProvider
     */
    private static final String META_KEY_SUPPORT_PROVIDER = "pluginapp_support_provider";

    private String packageName;
    private String applicationClassName;
    private String defaultActivityName;
    private PermissionInfo[] permissions;
    private PackageInfo packageInfo;
    private ApplicationInfo applicationInfo;
    private Bundle metaData;
    private String dataDir;
    private String nativeLibraryDir;
    private String processName;
    // 是否需要把插件class注入进入父classloader，已废弃
    private boolean mIsClassInject = false;
    // 是否需要把宿主的Resource合并进插件的Resources
    private boolean mIsMergeResource = false;
    // 是否需要插入Webview的资源到插件的Resources
    private boolean mAddWebviewResource = false;
    // 是否支持ContentProvider
    private boolean mSupportProvider = false;
    // 是否运行在独立空间，插件完全不依赖基线的类和资源
    private boolean mIsIndividualMode = false;
    private boolean mUsePluginAppInfo = false;
    private boolean mUsePluginCodePath = false;

    /**
     * Save all activity's resolve info
     */
    private Map<String, ActivityIntentInfo> mActivityIntentInfos = new HashMap<String, ActivityIntentInfo>(0);
    /**
     * Save all service's resolve info
     */
    private Map<String, ServiceIntentInfo> mServiceIntentInfos = new HashMap<String, ServiceIntentInfo>(0);
    /**
     * Save all receiver's resolve info
     */
    private Map<String, ReceiverIntentInfo> mReceiverIntentInfos = new HashMap<String, ReceiverIntentInfo>(0);
    /**
     * Save all provider's resolve info
     */
    private Map<String, ProviderIntentInfo> mProviderIntentInfos = new HashMap<String, ProviderIntentInfo>(0);


    public static final Creator<PluginPackageInfo> CREATOR = new Creator<PluginPackageInfo>() {
        @Override
        public PluginPackageInfo createFromParcel(Parcel in) {
            return new PluginPackageInfo(in);
        }

        @Override
        public PluginPackageInfo[] newArray(int size) {
            return new PluginPackageInfo[size];
        }
    };

    public PluginPackageInfo(Context context, File apkFile) {
        try {
            final String apkPath = apkFile.getAbsolutePath();
            // PackageInfo
            packageInfo = context.getPackageManager().getPackageArchiveInfo(apkPath,
                    PackageManager.GET_ACTIVITIES
                            | PackageManager.GET_PERMISSIONS
                            | PackageManager.GET_META_DATA
                            | PackageManager.GET_SERVICES
                            | PackageManager.GET_CONFIGURATIONS
                            | PackageManager.GET_RECEIVERS
                            | PackageManager.GET_PROVIDERS);
            if (packageInfo == null || packageInfo.applicationInfo == null) {
                PluginDebugLog.runtimeLog(TAG, "getPackageArchiveInfo is null for plugin apk: " + apkPath);
                throw new RuntimeException("getPackageArchiveInfo is null for file: " + apkPath);
            }
            packageName = packageInfo.packageName;
            applicationClassName = packageInfo.applicationInfo.className;

            packageInfo.applicationInfo.sourceDir = apkPath;
            packageInfo.applicationInfo.publicSourceDir = apkPath;

            if (TextUtils.isEmpty(packageInfo.applicationInfo.processName)) {
                packageInfo.applicationInfo.processName = packageInfo.applicationInfo.packageName;
            }

            dataDir = new File(PluginInstaller.getPluginappRootPath(context), packageName).getAbsolutePath();
            nativeLibraryDir = new File(dataDir, PluginInstaller.NATIVE_LIB_PATH).getAbsolutePath();

            packageInfo.applicationInfo.dataDir = dataDir;
            packageInfo.applicationInfo.nativeLibraryDir = nativeLibraryDir;

            processName = packageInfo.applicationInfo.processName;
            permissions = packageInfo.permissions;
            applicationInfo = packageInfo.applicationInfo;

            metaData = packageInfo.applicationInfo.metaData;
            if (metaData != null) {
                mIsClassInject = metaData.getBoolean(META_KEY_CLASS_INJECT);
                mIsMergeResource = metaData.getBoolean(META_KEY_MERGE_RES);
                mAddWebviewResource = metaData.getBoolean(META_KEY_ADD_WEBVIEW_RES);
                mSupportProvider = metaData.getBoolean(META_KEY_SUPPORT_PROVIDER);
                mIsIndividualMode = metaData.getBoolean(META_KEY_INDIVIDUAL);
                String applicationMetaData = metaData.getString(META_KEY_PLUGIN_APPLICATION_SPECIAL);
                if (!TextUtils.isEmpty(applicationMetaData)) {
                    if (applicationMetaData.contains(PLUGIN_APPLICATION_INFO)) {
                        mUsePluginAppInfo = true;
                    }

                    if (applicationMetaData.contains(PLUGIN_APPLICATION_CODE_PATH)) {
                        mUsePluginCodePath = true;
                    }
                }

                if (mIsClassInject) {
                    PluginDebugLog.runtimeFormatLog(TAG, "plugin %s need class inject: true", packageName);
                }
            }

            if (Neptune.NEW_COMPONENT_PARSER) {
                PluginDebugLog.runtimeLog(TAG, "resolve component info with our ManifestParser");
                ResolveInfoUtil.parseNewResolveInfo(context, apkPath, this);
            } else {
                PluginDebugLog.runtimeLog(TAG, "resolve component info by @hide api PackageParser#parsePacakge");
                ResolveInfoUtil.parseResolveInfo(context, apkPath, this);
            }

            Intent launchIntent = new Intent(Intent.ACTION_MAIN);
            launchIntent.addCategory(Intent.CATEGORY_DEFAULT);
            ActivityInfo actInfo = resolveActivity(launchIntent);
            if (actInfo != null) {
                defaultActivityName = actInfo.name;
            }

        } catch (RuntimeException e) {
            ErrorUtil.throwErrorIfNeed(e);
            String errMsg = "create PluginPackageInfo failed: " + e.getMessage();
            PluginManager.deliver(context, false, packageName, ErrorType.ERROR_PLUGIN_PARSER_PACKAGE_INFO, errMsg);
        } catch (Throwable e) {
            // java.lang.VerifyError: android/content/pm/PackageParser
            // java.lang.NoSuchMethodError: android.content.pm.PackageParser
            // java.lang.NoSuchFieldError:
            // com.android.internal.R$styleable.AndroidManifest
            // java.lang.NoSuchMethodError: android.graphics.PixelXorXfermode
            ErrorUtil.throwErrorIfNeed(e);
            String errMsg = "create PluginPackageInfo failed: " + e.getMessage();
            PluginManager.deliver(context, false, packageName, ErrorType.ERROR_PLUGIN_PARSER_PACKAGE_INFO, errMsg);
        }
    }

    protected PluginPackageInfo(Parcel in) {
        packageName = in.readString();
        processName = in.readString();
        applicationClassName = in.readString();
        defaultActivityName = in.readString();
        permissions = in.createTypedArray(PermissionInfo.CREATOR);
        packageInfo = in.readParcelable(PackageInfo.class.getClassLoader());
        metaData = in.readBundle();
        dataDir = in.readString();
        nativeLibraryDir = in.readString();
        mIsClassInject = in.readByte() != 0;
        mIsMergeResource = in.readByte() != 0;
        mAddWebviewResource = in.readByte() != 0;
        mSupportProvider = in.readByte() != 0;
        mIsIndividualMode = in.readByte() != 0;
        mUsePluginAppInfo = in.readByte() != 0;
        mUsePluginCodePath = in.readByte() != 0;

        applicationInfo = packageInfo.applicationInfo;

        final Bundle activityStates = in.readBundle(ActivityIntentInfo.class.getClassLoader());

        for (String key : activityStates.keySet()) {
            final ActivityIntentInfo state = getBundleParcelable(activityStates, key);
            if (state != null) {
                mActivityIntentInfos.put(key, state);
            }
        }

        final Bundle serviceStates = in.readBundle(ServiceIntentInfo.class.getClassLoader());
        for (String key : serviceStates.keySet()) {
            final ServiceIntentInfo state = getBundleParcelable(serviceStates, key);
            if (state != null) {
                mServiceIntentInfos.put(key, state);
            }
        }

        final Bundle receiverStates = in.readBundle(ReceiverIntentInfo.class.getClassLoader());
        for (String key : receiverStates.keySet()) {
            final ReceiverIntentInfo state = getBundleParcelable(receiverStates, key);
            if (state != null) {
                mReceiverIntentInfos.put(key, state);
            }
        }

        final Bundle providerStates = in.readBundle(ProviderIntentInfo.class.getClassLoader());
        for (String key : providerStates.keySet()) {
            final ProviderIntentInfo state = getBundleParcelable(providerStates, key);
            if (state != null) {
                mProviderIntentInfos.put(key, state);
            }
        }
    }

    /**
     * vivo X5L, 4,4, java.lang.NoSuchMethodError: android.os.Bundle.getParcelable
     */
    private static <T extends Parcelable> T getBundleParcelable(Bundle bundle, String key) {
        try {
            return bundle.getParcelable(key);
        } catch (NoSuchMethodError e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getProcessName() {
        return processName;
    }

    public String getDataDir() {
        return dataDir;
    }

    public String getNativeLibraryDir() {
        return nativeLibraryDir;
    }

    public ApplicationInfo getApplicationInfo() {
        if (applicationInfo != null) {
            return applicationInfo;
        }
        return packageInfo.applicationInfo;
    }

    public Map<String, ReceiverIntentInfo> getReceiverIntentInfos() {
        return mReceiverIntentInfos;
    }

    public Map<String, ProviderIntentInfo> getProviderIntentInfos() {
        return mProviderIntentInfos;
    }

    public ActivityInfo findActivityByClassName(String activityClsName) {
        if (packageInfo == null || packageInfo.activities == null) {
            return null;
        }
        for (ActivityInfo act : packageInfo.activities) {
            if (act != null && act.name.equals(activityClsName)) {
                return act;
            }
        }
        return null;
    }

    public ServiceInfo findServiceByClassName(String className) {
        if (packageInfo == null || packageInfo.services == null) {
            return null;
        }
        for (ServiceInfo service : packageInfo.services) {
            if (service != null && service.name.equals(className)) {
                return service;
            }
        }
        return null;
    }

    public ActivityInfo findReceiverByClassName(String className) {
        if (packageInfo == null || packageInfo.receivers == null) {
            return null;
        }
        for (ActivityInfo receiver : packageInfo.receivers) {
            if (receiver.name.equals(className)) {
                return receiver;
            }
        }
        return null;
    }

    public ProviderInfo findProviderByClassName(String className) {
        if (packageInfo == null || packageInfo.providers == null) {
            return null;
        }
        for (ProviderInfo provider : packageInfo.providers) {
            if (provider.name.equals(className)) {
                return provider;
            }
        }
        return null;
    }

    /**
     * 查找能够响应这个Intent的Activity
     */
    public ActivityInfo resolveActivity(Intent intent) {
        if (intent == null) {
            return null;
        }
        if (mActivityIntentInfos != null) {
            ComponentName compName = intent.getComponent();
            String className = null;
            if (compName != null) {
                className = compName.getClassName();
            }
            if (!TextUtils.isEmpty(className)) {
                ActivityIntentInfo act = mActivityIntentInfos.get(className);
                if (act != null) {
                    return act.mInfo;
                }
            } else {
                for (ActivityIntentInfo info : mActivityIntentInfos.values()) {
                    if (info != null && info.mFilter != null) {
                        for (IntentFilter filter : info.mFilter) {
                            if (filter.match(intent.getAction(), intent.getType(), intent.getScheme(), intent.getData(),
                                    intent.getCategories(), TAG) > 0) {
                                return info.mInfo;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 查找能够响应这个Intent的Service
     */
    public ServiceInfo resolveService(Intent intent) {
        if (intent == null) {
            return null;
        }

        if (mServiceIntentInfos != null) {
            ComponentName compName = intent.getComponent();
            String className = null;
            if (compName != null) {
                className = compName.getClassName();
            }
            if (!TextUtils.isEmpty(className)) {
                ServiceIntentInfo service = mServiceIntentInfos.get(className);
                if (service != null) {
                    return service.mInfo;
                }
            } else {
                for (ServiceIntentInfo info : mServiceIntentInfos.values()) {
                    if (info != null && info.mFilter != null) {
                        for (IntentFilter filter : info.mFilter) {
                            if (filter.match(intent.getAction(), null, intent.getScheme(), intent.getData(),
                                    intent.getCategories(), TAG) > 0) {
                                return info.mInfo;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }


    /**
     * 查找能够响应这个Intent的Recevier
     */
    public ActivityInfo resolveReceiver(Intent mIntent) {
        if (mIntent == null) {
            return null;
        }
        if (mReceiverIntentInfos != null) {
            ComponentName compName = mIntent.getComponent();
            String className = null;
            if (compName != null) {
                className = compName.getClassName();
            }
            if (!TextUtils.isEmpty(className)) {
                ReceiverIntentInfo mReceiverInfo = mReceiverIntentInfos.get(className);
                if (mReceiverInfo != null) {
                    return mReceiverInfo.mInfo;
                }
            } else {
                for (ReceiverIntentInfo info : mReceiverIntentInfos.values()) {
                    if (info != null && info.mFilter != null) {
                        for (IntentFilter filter : info.mFilter) {
                            if (filter.match(mIntent.getAction(), null, null, null,
                                    null, TAG) > 0) {
                                return info.mInfo;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 查找能够处理这个authority的Provider
     */
    public ProviderInfo resolveProvider(String authority) {
        if (TextUtils.isEmpty(authority)) {
            return null;
        }
        if (mProviderIntentInfos != null) {
            for (ProviderIntentInfo info : mProviderIntentInfos.values()) {
                if (info != null && TextUtils.equals(authority, info.mInfo.authority)) {
                    return info.mInfo;
                }
            }
        }
        return null;
    }

    public boolean isClassNeedInject() {
        return mIsClassInject;
    }

    public boolean isResourceNeedMerge() {
        return mIsMergeResource;
    }

    public boolean isNeedAddWebviewResource() {
        return mAddWebviewResource;
    }

    public boolean isSupportProvider() {
        return mSupportProvider;
    }

    public boolean isIndividualMode() {
        return mIsIndividualMode;
    }

    public boolean isUsePluginAppInfo() {
        return mUsePluginAppInfo;
    }

    public boolean isUsePluginCodePath() {
        return mUsePluginCodePath;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getApplicationClassName() {
        return applicationClassName;
    }

    public String getDefaultActivityName() {
        return defaultActivityName;
    }

    public PermissionInfo[] getPermissions() {
        return permissions;
    }

    public String getVersionName() {
        return packageInfo.versionName;
    }

    public int getVersionCode() {
        return packageInfo.versionCode;
    }

    public PackageInfo getPackageInfo() {
        return packageInfo;
    }

    public int getThemeResource(String activity) {
        ActivityInfo info = getActivityInfo(activity);
        if (info == null || info.getThemeResource() == 0) {
            // 支持不同系统的默认Theme
            if (Build.VERSION.SDK_INT >= 24) {
                return android.R.style.Theme_DeviceDefault_Light_DarkActionBar;
            } else if (Build.VERSION.SDK_INT >= 14) {
                return android.R.style.Theme_DeviceDefault;
            } else if (Build.VERSION.SDK_INT >= 11) {
                return android.R.style.Theme_Holo;
            } else {
                return android.R.style.Theme;
            }
        }
        return info.getThemeResource();
    }

    public ActivityInfo getActivityInfo(String activity) {
        if (!TextUtils.isEmpty(activity) && mActivityIntentInfos != null) {
            ActivityIntentInfo info = mActivityIntentInfos.get(activity);
            if (info != null) {
                return info.mInfo;
            }
        }
        return null;
    }

    public ServiceInfo getServiceInfo(String service) {
        if (!TextUtils.isEmpty(service) && mServiceIntentInfos != null) {
            ServiceIntentInfo info = mServiceIntentInfos.get(service);
            if (info != null) {
                return info.mInfo;
            }
        }
        return null;
    }

    public void addActivity(ActivityIntentInfo activity) {
        if (mActivityIntentInfos == null) {
            mActivityIntentInfos = new HashMap<String, ActivityIntentInfo>(0);
        }
        mActivityIntentInfos.put(activity.mInfo.name, activity);
    }

    public void addReceiver(ReceiverIntentInfo receiver) {
        if (mReceiverIntentInfos == null) {
            mReceiverIntentInfos = new HashMap<String, ReceiverIntentInfo>(0);
        }
        // 此时的activityInfo 表示 receiverInfo
        mReceiverIntentInfos.put(receiver.mInfo.name, receiver);
    }

    public void addService(ServiceIntentInfo service) {
        if (mServiceIntentInfos == null) {
            mServiceIntentInfos = new HashMap<String, ServiceIntentInfo>(0);
        }
        mServiceIntentInfos.put(service.mInfo.name, service);
    }

    public void addProvider(ProviderIntentInfo provider) {
        if (mProviderIntentInfos == null) {
            mProviderIntentInfos = new HashMap<String, ProviderIntentInfo>(0);
        }
        mProviderIntentInfos.put(provider.mInfo.name, provider);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(packageName);
        parcel.writeString(processName);
        parcel.writeString(applicationClassName);
        parcel.writeString(defaultActivityName);
        parcel.writeTypedArray(permissions, i);
        parcel.writeParcelable(packageInfo, i);
        parcel.writeBundle(metaData);
        parcel.writeString(dataDir);
        parcel.writeString(nativeLibraryDir);
        parcel.writeByte((byte) (mIsClassInject ? 1 : 0));
        parcel.writeByte((byte) (mIsMergeResource ? 1 : 0));
        parcel.writeByte((byte) (mAddWebviewResource ? 1: 0));
        parcel.writeByte((byte) (mSupportProvider ? 1 : 0));
        parcel.writeByte((byte) (mIsIndividualMode ? 1 : 0));
        parcel.writeByte((byte) (mUsePluginAppInfo ? 1 : 0));
        parcel.writeByte((byte) (mUsePluginCodePath ? 1 : 0));

        final Bundle activityStates = new Bundle();
        for (String uri : mActivityIntentInfos.keySet()) {
            final ActivityIntentInfo aii = mActivityIntentInfos.get(uri);
            activityStates.putParcelable(uri, aii);
        }
        parcel.writeBundle(activityStates);

        final Bundle serviceStates = new Bundle();
        for (String uri : mServiceIntentInfos.keySet()) {
            final ServiceIntentInfo sii = mServiceIntentInfos.get(uri);
            serviceStates.putParcelable(uri, sii);
        }
        parcel.writeBundle(serviceStates);

        final Bundle receiverStates = new Bundle();
        for (String uri : mReceiverIntentInfos.keySet()) {
            final ReceiverIntentInfo rii = mReceiverIntentInfos.get(uri);
            receiverStates.putParcelable(uri, rii);
        }
        parcel.writeBundle(receiverStates);

        final Bundle providerStates = new Bundle();
        for (String uri : mProviderIntentInfos.keySet()) {
            final ProviderIntentInfo pii = mProviderIntentInfos.get(uri);
            providerStates.putParcelable(uri, pii);
        }
        parcel.writeBundle(providerStates);
    }

    public final static class ActivityIntentInfo extends IntentInfo implements Parcelable {
        public static final Creator<ActivityIntentInfo> CREATOR = new Creator<ActivityIntentInfo>() {
            @Override
            public ActivityIntentInfo createFromParcel(Parcel in) {
                return new ActivityIntentInfo(in);
            }

            @Override
            public ActivityIntentInfo[] newArray(int size) {
                return new ActivityIntentInfo[size];
            }
        };
        public final ActivityInfo mInfo;

        public ActivityIntentInfo(final ActivityInfo info) {
            mInfo = info;
        }

        protected ActivityIntentInfo(Parcel in) {
            super(in);
            mInfo = ActivityInfo.CREATOR.createFromParcel(in);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int i) {
            super.writeToParcel(parcel, i);
            if (mInfo != null) {
                mInfo.writeToParcel(parcel, i);
            }
        }
    }

    public final static class ServiceIntentInfo extends IntentInfo implements Parcelable {
        public static final Creator<ServiceIntentInfo> CREATOR = new Creator<ServiceIntentInfo>() {
            @Override
            public ServiceIntentInfo createFromParcel(Parcel in) {
                return new ServiceIntentInfo(in);
            }

            @Override
            public ServiceIntentInfo[] newArray(int size) {
                return new ServiceIntentInfo[size];
            }
        };
        public final ServiceInfo mInfo;

        public ServiceIntentInfo(final ServiceInfo info) {
            mInfo = info;
        }

        protected ServiceIntentInfo(Parcel in) {
            super(in);
            mInfo = ServiceInfo.CREATOR.createFromParcel(in);

        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int i) {
            super.writeToParcel(parcel, i);
            if (mInfo != null) {
                mInfo.writeToParcel(parcel, i);
            }
        }
    }

    public final static class ReceiverIntentInfo extends IntentInfo implements Parcelable {
        public static final Creator<ReceiverIntentInfo> CREATOR = new Creator<ReceiverIntentInfo>() {
            @Override
            public ReceiverIntentInfo createFromParcel(Parcel in) {
                return new ReceiverIntentInfo(in);
            }

            @Override
            public ReceiverIntentInfo[] newArray(int size) {
                return new ReceiverIntentInfo[size];
            }
        };
        public final ActivityInfo mInfo;

        public ReceiverIntentInfo(final ActivityInfo info) {
            mInfo = info;
        }

        protected ReceiverIntentInfo(Parcel in) {
            super(in);
            mInfo = ActivityInfo.CREATOR.createFromParcel(in);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int i) {
            super.writeToParcel(parcel, i);
            if (mInfo != null) {
                mInfo.writeToParcel(parcel, i);
            }
        }
    }

    public final static class ProviderIntentInfo extends IntentInfo implements Parcelable {
        public static final Creator<ProviderIntentInfo> CREATOR = new Creator<ProviderIntentInfo>() {
            @Override
            public ProviderIntentInfo createFromParcel(Parcel in) {
                return new ProviderIntentInfo(in);
            }

            @Override
            public ProviderIntentInfo[] newArray(int size) {
                return new ProviderIntentInfo[size];
            }
        };
        public final ProviderInfo mInfo;

        public ProviderIntentInfo(final ProviderInfo info) {
            mInfo = info;
        }

        protected ProviderIntentInfo(Parcel in) {
            super(in);
            mInfo = ProviderInfo.CREATOR.createFromParcel(in);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int i) {
            super.writeToParcel(parcel, i);
            if (mInfo != null) {
                mInfo.writeToParcel(parcel, i);
            }
        }
    }

    public static class IntentInfo implements Parcelable {
        public static final Creator<IntentInfo> CREATOR = new Creator<IntentInfo>() {
            @Override
            public IntentInfo createFromParcel(Parcel in) {
                return new IntentInfo(in);
            }

            @Override
            public IntentInfo[] newArray(int size) {
                return new IntentInfo[size];
            }
        };
        public List<IntentFilter> mFilter;

        protected IntentInfo() {
        }

        protected IntentInfo(Parcel in) {
            mFilter = in.createTypedArrayList(IntentFilter.CREATOR);
        }

        public void setFilter(List<IntentFilter> filters) {
            mFilter = filters;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            if (mFilter != null) {
                dest.writeTypedList(mFilter);
            }
        }
    }
}
