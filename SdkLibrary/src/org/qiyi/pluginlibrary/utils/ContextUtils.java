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
import android.app.Application;
import android.app.Service;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageInfo;
import android.text.TextUtils;

import org.qiyi.pluginlibrary.install.PluginInstaller;
import org.qiyi.pluginlibrary.plugin.InterfaceToGetHost;
import org.qiyi.pluginlibrary.pm.PluginPackageInfo;
import org.qiyi.pluginlibrary.pm.PluginPackageManagerNative;
import org.qiyi.pluginlibrary.runtime.PluginLoadedApk;
import org.qiyi.pluginlibrary.runtime.PluginManager;

import java.io.File;

public class ContextUtils {
    private static final String TAG = ContextUtils.class.getSimpleName();

    /**
     * 将插件的Context转换为宿主的Context，这样可以访问宿主的资源
     */
    public static Context getOriginalContext(Context context) {

        if (context instanceof InterfaceToGetHost) {
            PluginDebugLog.log(TAG, "Return host  context for getOriginalContext");
            return ((InterfaceToGetHost) context).getOriginalContext();
        } else {
            if (context instanceof Activity) {
                Context base = ((Activity) context).getBaseContext();
                if (base instanceof InterfaceToGetHost) {
                    PluginDebugLog.log(TAG, "Return host  context for getOriginalContext");
                    return ((InterfaceToGetHost) base).getOriginalContext();
                } else if (base instanceof ContextWrapper) {
                    return getOriginalContext(base);
                }
            } else if (context instanceof Application) {
                Context base = ((Application) context).getBaseContext();
                if (base instanceof InterfaceToGetHost) {
                    PluginDebugLog.log(TAG, "Return Application host  context for getOriginalContext");
                    return ((InterfaceToGetHost) base).getOriginalContext();
                } else if (base instanceof ContextWrapper) {
                    return getOriginalContext(base);
                }
            } else if (context instanceof Service) {
                Context base = ((Service) context).getBaseContext();
                if (base instanceof InterfaceToGetHost) {
                    PluginDebugLog.log(TAG, "Return Service host  context for getOriginalContext");
                    return ((InterfaceToGetHost) base).getOriginalContext();
                } else if (base instanceof ContextWrapper) {
                    return getOriginalContext(base);
                }
            } else if (context instanceof ContextWrapper) {
                Context base = ((ContextWrapper) context).getBaseContext();
                if (base instanceof InterfaceToGetHost) {
                    PluginDebugLog.log(TAG, "getPluginPackageName context is ContextWrapper " +
                            "and base is InterfaceToGetHost!");
                    return ((InterfaceToGetHost) base).getOriginalContext();
                } else if (base instanceof ContextWrapper) {
                    // 递归调用
                    // DecorView的Context是com.android.internal.policy.DecorContext
                    // https://android.googlesource.com/platform/frameworks/base.git/+/master/core/java/com/android/internal/policy/DecorContext.java
                    return getOriginalContext(base);
                }
            }
            return context;
        }
    }


    /**
     * 获取宿主的HostResourceTool工具
     */
    @Deprecated
    public static ResourcesToolForPlugin getHostResourceTool(Context context) {
        if (context instanceof InterfaceToGetHost) {
            PluginDebugLog.log(TAG, "Return host  resource tool for getHostResourceTool");
            return ((InterfaceToGetHost) context).getHostResourceTool();
        } else {
            if (context instanceof Activity) {
                Context base = ((Activity) context).getBaseContext();
                if (base instanceof InterfaceToGetHost) {
                    PluginDebugLog.log(TAG, "Return host  resource tool for getHostResourceTool");
                    return ((InterfaceToGetHost) base).getHostResourceTool();
                }
            } else if (context instanceof Application) {
                Context base = ((Application) context).getBaseContext();
                if (base instanceof InterfaceToGetHost) {
                    PluginDebugLog.log(TAG, "Return Application host  resource tool for getHostResourceTool");
                    return ((InterfaceToGetHost) base).getHostResourceTool();
                }
            } else if (context instanceof Service) {
                Context base = ((Service) context).getBaseContext();
                if (base instanceof InterfaceToGetHost) {
                    PluginDebugLog.log(TAG, "Return Service host  resource tool for getHostResourceTool");
                    return ((InterfaceToGetHost) base).getHostResourceTool();
                }
            }
            PluginDebugLog.log(TAG, "Return local resource tool for getHostResourceTool");
            return new ResourcesToolForPlugin(context);
        }
    }

    /**
     * 获取插件的包名，如果不是插件的Context，返回APP的包名
     */
    public static String getPluginPackageName(Context context) {
        if (null == context) {
            return null;
        }
        if (context instanceof InterfaceToGetHost) {
            PluginDebugLog.log(TAG, "getPluginPackageName context is InterfaceToGetHost!");
            return ((InterfaceToGetHost) context).getPluginPackageName();
        } else {
            if (context instanceof Activity) {
                Context base = ((Activity) context).getBaseContext();
                if (base instanceof InterfaceToGetHost) {
                    PluginDebugLog.log(TAG, "getPluginPackageName context is Activity!");
                    return ((InterfaceToGetHost) base).getPluginPackageName();
                } else if (base instanceof ContextWrapper) {
                    return getPluginPackageName(base);
                }
            } else if (context instanceof Application) {
                Context base = ((Application) context).getBaseContext();
                if (base instanceof InterfaceToGetHost) {
                    PluginDebugLog.log(TAG, "getPluginPackageName context is Application!");
                    return ((InterfaceToGetHost) base).getPluginPackageName();
                } else if (base instanceof ContextWrapper) {
                    return getPluginPackageName(base);
                }
            } else if (context instanceof Service) {
                Context base = ((Service) context).getBaseContext();
                if (base instanceof InterfaceToGetHost) {
                    PluginDebugLog.log(TAG, "getPluginPackageName context is Service!");
                    return ((InterfaceToGetHost) base).getPluginPackageName();
                } else if (base instanceof ContextWrapper) {
                    return getPluginPackageName(base);
                }
            } else if (context instanceof ContextWrapper) {
                Context base = ((ContextWrapper) context).getBaseContext();
                if (base instanceof InterfaceToGetHost) {
                    PluginDebugLog.log(TAG, "getPluginPackageName context is ContextWrapper " +
                            "and base is InterfaceToGetHost!");
                    return ((InterfaceToGetHost) base).getPluginPackageName();
                } else if (base instanceof ContextWrapper) {
                    //递归调用
                    return getPluginPackageName(base);
                }
            }
            return context.getPackageName();
        }
    }

    /**
     * Try to exit current app and exit process
     */
    public static void exitApp(Context context) {
        if (null != context) {
            if (context instanceof InterfaceToGetHost) {
                ((InterfaceToGetHost) context).exitApp();
            } else if (context instanceof Activity) {
                Context base = ((Activity) context).getBaseContext();
                if (base instanceof InterfaceToGetHost) {
                    PluginDebugLog.log(TAG, "Activity exit");
                    ((InterfaceToGetHost) base).exitApp();
                }
            } else if (context instanceof Application) {
                Context base = ((Application) context).getBaseContext();
                if (base instanceof InterfaceToGetHost) {
                    PluginDebugLog.log(TAG, "Application exit");
                    ((InterfaceToGetHost) base).exitApp();
                }
            } else if (context instanceof Service) {
                Context base = ((Service) context).getBaseContext();
                if (base instanceof InterfaceToGetHost) {
                    PluginDebugLog.log(TAG, "Service exit");
                    ((InterfaceToGetHost) base).exitApp();
                }
            }
        }
    }


    /**
     * 获取插件的PackageInfo信息
     */
    public static PackageInfo getPluginPackageInfo(Context context) {
        String pkgName = getPluginPackageName(context);
        if (context == null || TextUtils.isEmpty(pkgName)) {
            return null;
        }

        PluginLoadedApk mLoadedApk = PluginManager.getPluginLoadedApkByPkgName(pkgName);
        if (mLoadedApk != null) {
            return mLoadedApk.getPackageInfo();
        }

        PluginPackageInfo packageInfo = PluginPackageManagerNative.getInstance(context).getPluginPackageInfo(pkgName);
        if (packageInfo != null) {
            return packageInfo.getPackageInfo();
        }
        return null;
    }
}
