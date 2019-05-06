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
package org.qiyi.pluginlibrary.component;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.qiyi.pluginlibrary.constant.IntentConstant;
import org.qiyi.pluginlibrary.runtime.PluginLoadedApk;
import org.qiyi.pluginlibrary.runtime.PluginManager;
import org.qiyi.pluginlibrary.utils.ComponentFinder;
import org.qiyi.pluginlibrary.utils.FileUtils;
import org.qiyi.pluginlibrary.utils.PluginDebugLog;
import org.qiyi.pluginlibrary.utils.ReflectionUtils;

import java.util.ArrayList;

public class ContentProviderProxy1 extends ContentProvider {
    private static final String TAG = "ContentProviderProxy1";

    @Override
    public boolean onCreate() {
        return true;
    }

    private String resolvePkgName(Uri uri) {
        String pkgName = uri.getQueryParameter(IntentConstant.EXTRA_TARGET_PACKAGE_KEY);
        if (!TextUtils.isEmpty(pkgName)) {
            return pkgName;
        }
        Uri pluginUri = Uri.parse(uri.getQueryParameter(IntentConstant.EXTRA_TARGET_URI_KEY));
        return ComponentFinder.resolvePkgName(getContext(), pluginUri);
    }

    /**
     * 根据Uri获取插件对应的ContentProvider对象
     */
    private ContentProvider getContentProvider(Uri uri) {
        String pkgName = resolvePkgName(uri);
        if (TextUtils.isEmpty(pkgName)) {
            return null;
        }

        PluginLoadedApk loadedApk = PluginManager.getPluginLoadedApkByPkgName(pkgName);
        if (loadedApk == null) {
            // 插件未加载，等待插件初始化成功
            PluginDebugLog.runtimeLog(TAG, "plugin not ready and wait environment init");
            PluginManager.loadPluginSync(getContext(), pkgName, FileUtils.getCurrentProcessName(getContext()));
            loadedApk = PluginManager.getPluginLoadedApkByPkgName(pkgName);
        }

        if (loadedApk == null) {
            PluginDebugLog.runtimeLog(TAG, "plugin init failed");
            return null;
        }
        Uri pluginUri = Uri.parse(uri.getQueryParameter(IntentConstant.EXTRA_TARGET_URI_KEY));
        return loadedApk.getContentProvider(pluginUri);
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        ContentProvider provider = getContentProvider(uri);
        if (provider != null) {
            Uri pluginUri = Uri.parse(uri.getQueryParameter(IntentConstant.EXTRA_TARGET_URI_KEY));
            return provider.query(pluginUri, projection, selection, selectionArgs, sortOrder);
        }
        return null;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        ContentProvider provider = getContentProvider(uri);
        if (provider != null) {
            Uri pluginUri = Uri.parse(uri.getQueryParameter(IntentConstant.EXTRA_TARGET_URI_KEY));
            return provider.getType(pluginUri);
        }
        return null;
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        ContentProvider provider = getContentProvider(uri);
        if (provider != null) {
            Uri pluginUri = Uri.parse(uri.getQueryParameter(IntentConstant.EXTRA_TARGET_URI_KEY));
            return provider.insert(pluginUri, values);
        }
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        ContentProvider provider = getContentProvider(uri);
        if (provider != null) {
            Uri pluginUri = Uri.parse(uri.getQueryParameter(IntentConstant.EXTRA_TARGET_URI_KEY));
            return provider.delete(pluginUri, selection, selectionArgs);
        }
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        ContentProvider provider = getContentProvider(uri);
        if (provider != null) {
            Uri pluginUri = Uri.parse(uri.getQueryParameter(IntentConstant.EXTRA_TARGET_URI_KEY));
            return provider.update(pluginUri, values, selection, selectionArgs);
        }
        return 0;
    }

    @Override
    public int bulkInsert(@NonNull Uri uri, @NonNull ContentValues[] values) {
        ContentProvider provider = getContentProvider(uri);
        if (provider != null) {
            Uri pluginUri = Uri.parse(uri.getQueryParameter(IntentConstant.EXTRA_TARGET_URI_KEY));
            return provider.bulkInsert(pluginUri, values);
        }
        return 0;
    }

    @Override
    public ContentProviderResult[] applyBatch(@NonNull ArrayList<ContentProviderOperation> operations) throws OperationApplicationException {
        if (operations.size() > 0) {
            ContentProvider provider = getContentProvider(operations.get(0).getUri());
            if (provider != null) {
                try {
                    for (ContentProviderOperation operation : operations) {
                        Uri pluginUri = Uri.parse(operation.getUri().getQueryParameter(IntentConstant.EXTRA_TARGET_URI_KEY));
                        ReflectionUtils.on(operation).set("mUri", pluginUri);
                    }
                    return provider.applyBatch(operations);
                } catch (Exception e) {
                    return new ContentProviderResult[0];
                }
            }
        }
        return new ContentProviderResult[0];
    }

    @Override
    public Bundle call(@NonNull String method, String arg, Bundle extras) {
        if (extras == null || TextUtils.isEmpty(extras.getString(IntentConstant.EXTRA_WRAPPER_URI_KEY))) {
            return null;
        }
        Uri uri = Uri.parse(extras.getString(IntentConstant.EXTRA_WRAPPER_URI_KEY));
        ContentProvider provider = getContentProvider(uri);
        if (provider != null) {
            return provider.call(method, arg, extras);
        }
        return null;
    }


    public static String getAuthority(Context context) {
        return context.getPackageName() + ".neptune.provider1";
    }

    public static String getUri(Context context) {
        return "content://" + getAuthority(context);
    }
}
