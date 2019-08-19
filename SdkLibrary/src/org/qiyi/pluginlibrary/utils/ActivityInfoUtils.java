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


import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.TypedValue;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;

public class ActivityInfoUtils {
    /* Required API  18 @see ActivityInfo */
    private static final int SCREEN_ORIENTATION_USER_LANDSCAPE = 11;
    private static final int SCREEN_ORIENTATION_USER_PORTRAIT = 12;
    private static final int SCREEN_ORIENTATION_LOCKED = 14;

    public static boolean isFixedOrientation(ActivityInfo actInfo) {
        return isFixedOrientationLandscape(actInfo) || isFixedOrientationPortrait(actInfo)
                || (actInfo.screenOrientation == SCREEN_ORIENTATION_LOCKED);
    }

    public static boolean isFixedOrientationLandscape(ActivityInfo actInfo) {
        int orientation = actInfo.screenOrientation;
        return orientation == SCREEN_ORIENTATION_LANDSCAPE
                || orientation == SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                || orientation == SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                || orientation == SCREEN_ORIENTATION_USER_LANDSCAPE;
    }

    public static boolean isFixedOrientationPortrait(ActivityInfo actInfo) {
        int orientation = actInfo.screenOrientation;
        return orientation == SCREEN_ORIENTATION_PORTRAIT
                || orientation == SCREEN_ORIENTATION_SENSOR_PORTRAIT
                || orientation == SCREEN_ORIENTATION_REVERSE_PORTRAIT
                || orientation == SCREEN_ORIENTATION_USER_PORTRAIT;
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    public static boolean isTranslucent(Activity activity, Resources.Theme theme, ActivityInfo actInfo) {
        return isTranslucentOrFloating(activity) || isTranslucentTheme(theme, actInfo);
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    public static boolean isTranslucentOrFloating(Activity activity) {
        final int[] windowAttr = new int[]{
                android.R.attr.windowIsTranslucent,
                android.R.attr.windowSwipeToDismiss,
                android.R.attr.windowIsFloating
        }; /*android.R.styleable.Window*/
        final TypedArray ta = activity.obtainStyledAttributes(windowAttr);
        final boolean isTranslucent =
                ta.getBoolean(0 /*android.R.styleable.Window_windowIsTranslucent*/,
                        false);
        final boolean isSwipeToDismiss = !ta.hasValue(
                0 /*android.R.styleable.Window_windowIsTranslucent*/)
                && ta.getBoolean(1/*android.R.styleable.Window_windowSwipeToDismiss*/, false);
        final boolean isFloating =
                ta.getBoolean(2/*android.R.styleable.Window_windowIsFloating*/,
                        false);
        final boolean isTranslucentOrFloating = isFloating || isTranslucent || isSwipeToDismiss;
        ta.recycle();
        return isTranslucentOrFloating;
    }

    public static boolean isTranslucentTheme(Resources.Theme mTheme, ActivityInfo actInfo) {
        boolean isTranslucent = false;
        mTheme.applyStyle(actInfo.getThemeResource(), true);
        TypedArray ta = mTheme.obtainStyledAttributes(new int[]{
                android.R.attr.windowIsTranslucent,
                android.R.attr.windowBackground
        });
        boolean attr_0 = ta.getBoolean(0, false);
        try {
            TypedValue tv = new TypedValue();
            mTheme.resolveAttribute(android.R.attr.windowBackground, tv, true);
            if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                isTranslucent = attr_0 && (tv.data == Color.TRANSPARENT);
            } else {
                // windowBackground is drawable
                Drawable drawable = ta.getDrawable(1);
                if (drawable instanceof ColorDrawable) {
                    int color = ((ColorDrawable) drawable).getColor();
                    isTranslucent = attr_0 && (color == Color.TRANSPARENT);
                } else {
                    isTranslucent = false;
                }
            }
        } catch (Exception e) {
            isTranslucent = attr_0;
        }
        ta.recycle();
        return isTranslucent;
    }
}
