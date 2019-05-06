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
package org.qiyi.pluginlibrary.provider;

import android.content.Context;
import android.content.IContentProvider;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import org.qiyi.pluginlibrary.component.ContentProviderProxy1;
import org.qiyi.pluginlibrary.constant.IntentConstant;
import org.qiyi.pluginlibrary.utils.ComponentFinder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * author: liuchun
 * date: 2019/1/28
 */
public class IContentProviderProxy implements InvocationHandler {

    private IContentProvider mBase;
    private Context mContext;

    private IContentProviderProxy(Context context, IContentProvider rawProvider) {
        mBase = rawProvider;
        mContext = context;
    }

    public static IContentProvider newInstance(Context context, IContentProvider rawProvider) {
        return (IContentProvider) Proxy.newProxyInstance(rawProvider.getClass().getClassLoader(),
                new Class[]{IContentProvider.class}, new IContentProviderProxy(context, rawProvider));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        wrapperUri(method, args);
        try {
            return method.invoke(mBase, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    private void wrapperUri(Method method, Object[] args) {
        Uri uri = null;
        int index = -1;
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Uri) {
                    uri = (Uri)args[i];
                    index = i;
                }
            }
        }
        // Call方法
        Bundle bundle = null;
        if (TextUtils.equals(method.getName(), "call")) {
            bundle = getBundleParameter(args);
            if (bundle != null) {
                String uriStr = bundle.getString(IntentConstant.EXTRA_TARGET_URI_KEY);
                if (!TextUtils.isEmpty(uriStr)) {
                    uri = Uri.parse(uriStr);
                }
            }
        }

        if (uri == null || TextUtils.isEmpty(uri.getAuthority())) {
            return;
        }

        ProviderInfo provider = ComponentFinder.resolveProviderInfo(mContext, uri.getAuthority());
        if (provider != null) {
            String pkgName = provider.packageName;
            Uri wrapperUri = buildWrapperUri(pkgName, uri);
            if (TextUtils.equals(method.getName(), "call")) {
                if (bundle != null) {
                    bundle.putString(IntentConstant.EXTRA_WRAPPER_URI_KEY, wrapperUri.toString());
                }
            } else if (index >= 0) {
                args[index] = wrapperUri;
            }
        }
    }

    private Uri buildWrapperUri(String pkgName, Uri pluginUri) {
        Uri.Builder builder = new Uri.Builder()
                .scheme("content")
                .authority(ContentProviderProxy1.getAuthority(mContext))
                .appendQueryParameter(IntentConstant.EXTRA_TARGET_PACKAGE_KEY, pkgName)
                .appendQueryParameter(IntentConstant.EXTRA_TARGET_URI_KEY, pluginUri.toString());
        return builder.build();
    }

    private Bundle getBundleParameter(Object[] args) {
        Bundle bundle = null;
        if (args != null) {
            for (int i = 0; i< args.length; i++) {
                if (args[i] instanceof Bundle) {
                    bundle = (Bundle)args[i];
                    break;
                }
            }
        }
        return bundle;
    }
}
