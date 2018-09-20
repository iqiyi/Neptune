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
package org.qiyi.pluginlibrary.component.base;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;

import org.qiyi.pluginlibrary.utils.ComponentFinder;
import org.qiyi.pluginlibrary.utils.ContextUtils;


public class PluginFragmentActivity extends FragmentActivity implements IPluginBase {

    private PluginActivityDelegate mDelegate;

    @Override
    protected void attachBaseContext(Context newBase) {
        mDelegate = new PluginActivityDelegate();
        newBase = mDelegate.createActivityContext(this, newBase);

        super.attachBaseContext(newBase);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (mDelegate != null) {
            mDelegate.handleActivityOnCreateBefore(this, savedInstanceState);
        }
        super.onCreate(savedInstanceState);
        if (mDelegate != null) {
            mDelegate.handleActivityOnCreateAfter(this, savedInstanceState);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mDelegate != null) {
            mDelegate.handleActivityOnDestroy(this);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        // 替换成坑位Activity
        String pkgName = getPluginPackageName();
        intent = ComponentFinder.switchToActivityProxy(pkgName, intent, requestCode, this);

        super.startActivityForResult(intent, requestCode);
    }

    // Api 16新增
    @Override
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
    public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
        // 替换成坑位Activity
        String pkgName = getPluginPackageName();
        intent = ComponentFinder.switchToActivityProxy(pkgName, intent, requestCode, this);

        super.startActivityForResult(intent, requestCode, options);
    }


    ////////////////////////////////////////////////////////////////////
    // 以下是IPluginBase接口实现
    ///////////////////////////////////////////////////////////////////

    @Override
    public String getPluginPackageName() {
        String pkgName = mDelegate != null ? mDelegate.getPluginPackageName() : ContextUtils.getPluginPackageName(this);
        if (TextUtils.isEmpty(pkgName)) {
            pkgName = getPackageName();
        }
        return pkgName;
    }
}
