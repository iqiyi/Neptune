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
package org.qiyi.pluginlibrary.plugin;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;

import java.io.FileDescriptor;
import java.io.PrintWriter;

public interface PluginActivityCallback {

    void callOnCreate(Bundle saveInstance);

    void callOnPostCreate(Bundle savedInstanceState);

    void callOnStart();

    void callOnResume();

    void callOnDestroy();

    void callOnStop();

    void callOnRestart();

    void callOnSaveInstanceState(Bundle outState);

    void callOnRestoreInstanceState(Bundle savedInstanceState);

    void callOnPause();

    void callOnBackPressed();

    boolean callOnKeyDown(int keyCode, KeyEvent event);

    void callOnPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig);

    void callOnPictureInPictureModeChanged(boolean isInPictureInPictureMode);

    void callDump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args);

    void callOnPostResume();

    void callOnDetachedFromWindow();

    View callOnCreateView(String name, Context context, AttributeSet attrs);

    View callOnCreateView(View parent, String name, Context context, AttributeSet attrs);

    void callOnNewIntent(Intent intent);

    void callOnConfigurationChanged(Configuration newConfig);

    void callOnActivityResult(int requestCode, int resultCode, Intent data);
}
