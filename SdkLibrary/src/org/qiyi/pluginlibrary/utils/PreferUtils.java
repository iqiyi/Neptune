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
import android.content.SharedPreferences;

public class PreferUtils {

    private static IDataSaver sDataHelper = new SharedPreferenceSaver();

    public static void setDataSaver(IDataSaver saver) {
        if (saver != null) {
            sDataHelper = saver;
        }
    }

    public static <T> void save(Context context, String name, String key, T value) {
        if (sDataHelper == null) {
            sDataHelper = new SharedPreferenceSaver();
        }
        sDataHelper.save(context, name, key, value);
    }

    public static <T> T get(Context context, String name, String key, T defValue) {
        if (sDataHelper == null) {
            sDataHelper = new SharedPreferenceSaver();
        }
        return sDataHelper.get(context, name, key, defValue);
    }

    public static class SharedPreferenceSaver implements IDataSaver {

        @Override
        public <T> void save(Context context, String name, String key, T value) {
            SharedPreferences sp = context.getSharedPreferences(name, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            if (value instanceof String) {
                editor.putString(key, (String) value);
            } else if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(key, (Long) value);
            } else if (value instanceof Float) {
                editor.putFloat(key, (Float) value);
            }
            editor.apply();
        }

        @Override
        public <T> T get(Context context, String name, String key, T defValue) {
            SharedPreferences sp = context.getSharedPreferences(name, Context.MODE_PRIVATE);
            Object result = null;
            if (defValue instanceof String) {
                result = sp.getString(key, (String) defValue);
            } else if (defValue instanceof Boolean) {
                result = sp.getBoolean(key, (Boolean) defValue);
            } else if (defValue instanceof Integer) {
                result = sp.getInt(key, (Integer) defValue);
            } else if (defValue instanceof Long) {
                result = sp.getLong(key, (Long) defValue);
            } else if (defValue instanceof Float) {
                result = sp.getFloat(key, (Float) defValue);
            }
            return (T) result;
        }
    }

    public static interface IDataSaver {

        public <T> void save(Context context, String name, String key, T value);

        public <T> T get(Context context, String name, String key, T defValue);
    }
}
