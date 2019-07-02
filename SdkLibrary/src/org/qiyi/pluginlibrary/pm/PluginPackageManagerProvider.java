package org.qiyi.pluginlibrary.pm;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.text.TextUtils;


import org.qiyi.pluginlibrary.install.IInstallCallBack;
import org.qiyi.pluginlibrary.install.IUninstallCallBack;
import org.qiyi.pluginlibrary.utils.PluginDebugLog;

import java.util.ArrayList;

/**
 * author: liuchun
 * date: 2019/5/15
 */
public class PluginPackageManagerProvider extends ContentProvider {
    private static final String TAG = "PluginPackageManagerProvider";
    /**
     * @see PluginPackageManagerService#initBinder() 相关ipc方法
     */
    public static final String GET_INSTALLED_APPS = "getInstalledApps";
    public static final String GET_PACKAGE_INFO = "getPackageInfo";
    public static final String IS_PACKAGE_INSTALLED = "isPackageInstalled";
    public static final String CAN_INSTALL_PACKAGE = "canInstallPackage";
    public static final String CAN_UNINSTALL_PACAKGE = "canUninstallPackage";
    public static final String INSTALL_PACKAGE = "install";
    public static final String DELETE_PACKAGE = "deletePackage";
    public static final String UNINSTALL_PACKAGE = "uninstall";
    public static final String GET_PLUGIN_PACKAGE_INFO = "getPluginPackageInfo";
    public static final String GET_PLUGIN_REFS = "getPluginRefs";

    public static final String PLUGIN_INFO_KEY = "pluginInfo";
    public static final String CALLBACK_BINDER_KEY = "callbackBinder";
    public static final String RESULT_KEY = "result";

    private PluginPackageManager mManager;

    @Override
    public boolean onCreate() {
        mManager = PluginPackageManager.getInstance(getContext());
        return true;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        throw new UnsupportedOperationException("PluginPackageManagerProvider not support query");
    }

    @Override
    public String getType(Uri uri) {
        throw new UnsupportedOperationException("PluginPackageManagerProvider not support getType");
    }

    @Override
    public Uri insert(Uri uri, ContentValues contentValues) {
        throw new UnsupportedOperationException("PluginPackageManagerProvider not support insert");
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("PluginPackageManagerProvider not support delete");
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("PluginPackageManagerProvider not support update");
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        PluginDebugLog.runtimeFormatLog(TAG, "call() method: %s, arg: %s", method, arg);
        Bundle result = new Bundle();
        if (GET_INSTALLED_APPS.equals(method)) {
            ArrayList<PluginLiteInfo> installedApps = new ArrayList<>(mManager.getInstalledApps());
            result.putParcelableArrayList(RESULT_KEY, installedApps);
        } else if (GET_PACKAGE_INFO.equals(method)) {
            PluginLiteInfo info = mManager.getPackageInfo(arg);
            result.putParcelable(RESULT_KEY, info);
        } else if (IS_PACKAGE_INSTALLED.equals(method)) {
            boolean installed = mManager.isPackageInstalled(arg);
            result.putBoolean(RESULT_KEY, installed);
        } else if (CAN_INSTALL_PACKAGE.equals(method)) {
            PluginLiteInfo info = extras.getParcelable(PLUGIN_INFO_KEY);
            boolean canInstall = true;
            if (info != null && !TextUtils.isEmpty(info.packageName)) {
                canInstall = mManager.canInstallPackage(info);
            }
            result.putBoolean(RESULT_KEY, canInstall);
        } else if (CAN_UNINSTALL_PACAKGE.equals(method)) {
            PluginLiteInfo info = extras.getParcelable(PLUGIN_INFO_KEY);
            boolean canUninstall = true;
            if (info != null && !TextUtils.isEmpty(info.packageName)) {
                canUninstall = mManager.canUninstallPackage(info);
            }
            result.putBoolean(RESULT_KEY, canUninstall);
        } else if (INSTALL_PACKAGE.equals(method)) {
            PluginLiteInfo info = extras.getParcelable(PLUGIN_INFO_KEY);
            if (info != null && !TextUtils.isEmpty(info.packageName)) {
                IBinder binder = getBinder(extras, CALLBACK_BINDER_KEY);
                IInstallCallBack callBack = binder != null ? IInstallCallBack.Stub.asInterface(binder) : null;
                mManager.install(info, callBack);
            }
        } else if (DELETE_PACKAGE.equals(method)) {
            PluginLiteInfo info = extras.getParcelable(PLUGIN_INFO_KEY);
            if (info != null && !TextUtils.isEmpty(info.packageName)) {
                IBinder binder = getBinder(extras, CALLBACK_BINDER_KEY);
                IUninstallCallBack callBack = binder != null ? IUninstallCallBack.Stub.asInterface(binder) : null;
                mManager.clearPackage(info, callBack);
            }
        } else if (UNINSTALL_PACKAGE.equals(method)) {
            PluginLiteInfo info = extras.getParcelable(PLUGIN_INFO_KEY);
            if (info != null && !TextUtils.isEmpty(info.packageName)) {
                IBinder binder = getBinder(extras, CALLBACK_BINDER_KEY);
                IUninstallCallBack callBack = binder != null ? IUninstallCallBack.Stub.asInterface(binder) : null;
                mManager.uninstall(info, callBack);
            }
        } else if (GET_PLUGIN_PACKAGE_INFO.equals(method)) {
            PluginPackageInfo packageInfo = mManager.getPluginPackageInfo(arg);
            result.putParcelable(RESULT_KEY, packageInfo);
        } else if (GET_PLUGIN_REFS.equals(method)) {
            ArrayList<String> refs = new ArrayList<>(mManager.getPluginRefs(arg));
            result.putStringArrayList(RESULT_KEY, refs);
        }
        return result;
    }

    private IBinder getBinder(Bundle extras, String key) {
        IBinder binder = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            binder = extras.getBinder(key);
        }
        return binder;
    }

    public static String getAuthority(Context context) {
        return context.getPackageName() + ".neptune.manager.provider";
    }

    public static Uri getUri(Context context) {
        return Uri.parse("content://" + getAuthority(context));
    }
}
