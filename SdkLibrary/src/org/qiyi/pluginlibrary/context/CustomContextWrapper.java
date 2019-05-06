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
package org.qiyi.pluginlibrary.context;

import android.annotation.NonNull;
import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ServiceInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.ArrayMap;

import org.qiyi.pluginlibrary.component.stackmgr.PServiceSupervisor;
import org.qiyi.pluginlibrary.component.stackmgr.PluginServiceWrapper;
import org.qiyi.pluginlibrary.plugin.InterfaceToGetHost;
import org.qiyi.pluginlibrary.pm.PluginPackageInfo;
import org.qiyi.pluginlibrary.pm.PluginPackageManager;
import org.qiyi.pluginlibrary.runtime.PluginLoadedApk;
import org.qiyi.pluginlibrary.utils.ComponentFinder;
import org.qiyi.pluginlibrary.utils.ErrorUtil;
import org.qiyi.pluginlibrary.utils.FileUtils;
import org.qiyi.pluginlibrary.utils.IntentUtils;
import org.qiyi.pluginlibrary.utils.PluginDebugLog;
import org.qiyi.pluginlibrary.utils.ReflectionUtils;
import org.qiyi.pluginlibrary.utils.ResourcesToolForPlugin;
import org.qiyi.pluginlibrary.utils.VersionUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public abstract class CustomContextWrapper extends ContextWrapper implements InterfaceToGetHost {
    private static final String TAG = "CustomContextWrapper";

    private static final String DATABASE_PATH = "/databases/";
    private static final String SHAREDPREF_PATH = "/shared_prefs/";

    private static final String S_SHARED_PREFS =
            VersionUtils.hasNougat() ? "sSharedPrefsCache" : "sSharedPrefs";
    private static final String M_SHARED_PREFS_PATHS = "mSharedPrefsPaths";

    protected static ConcurrentMap<String, Vector<Method>> sMethods = new ConcurrentHashMap<String, Vector<Method>>(2);

    private ApplicationInfo mApplicationInfo = null;

    public CustomContextWrapper(Context base) {
        super(base);
    }

    private static void setFilePermissionsForDb(String dbPath, int perms) {
        android.os.FileUtils.setPermissions(dbPath, perms, -1, -1);
    }

    private static File makeBackupFile(File prefsFile) {
        return new File(prefsFile.getPath() + ".bak");
    }

    @Override
    public ContentResolver getContentResolver() {
        ContentResolver cr = getPluginLoadedApk().getPluginContentResolver();
        if (cr == null) {
            cr = super.getContentResolver();
        }
        return cr;
    }

    @Override
    public ClassLoader getClassLoader() {
        return getPluginLoadedApk().getPluginClassLoader();
    }

    @Override
    public Context getApplicationContext() {
        return getPluginLoadedApk().getPluginApplication();
    }

    @Override
    public ApplicationInfo getApplicationInfo() {

        if (mApplicationInfo == null) {
            mApplicationInfo = new ApplicationInfo(super.getApplicationInfo());
            PluginPackageInfo targetMapping = getPluginPackageInfo();
            if (targetMapping != null && targetMapping.isUsePluginAppInfo()) {
                mApplicationInfo.dataDir = targetMapping.getDataDir();
                mApplicationInfo.nativeLibraryDir = targetMapping.getNativeLibraryDir();
            }
        }
        return mApplicationInfo;
    }

    @Override
    public Resources getResources() {
        return getPluginLoadedApk().getPluginResource();
    }

    @Override
    public AssetManager getAssets() {
        return getResources().getAssets();
    }

    @Override
    public ComponentName startService(Intent service) {
        PluginDebugLog.log(TAG, "startService: " + service);
        PluginLoadedApk mLoadedApk = getPluginLoadedApk();
        if (mLoadedApk != null) {
            ComponentFinder.switchToServiceProxy(mLoadedApk, service);
        }
        return super.startService(service);
    }

    @Override
    public boolean stopService(Intent name) {
        PluginDebugLog.log(TAG, "stopService: " + name);
        PluginLoadedApk mLoadedApk = getPluginLoadedApk();
        if (mLoadedApk != null) {
            String actServiceClsName = "";
            if (name.getComponent() != null) {
                actServiceClsName = name.getComponent().getClassName();
            } else {
                ServiceInfo mServiceInfo = getPluginPackageInfo().resolveService(name);
                if (mServiceInfo != null) {
                    actServiceClsName = mServiceInfo.name;
                }
            }

            PluginServiceWrapper plugin = PServiceSupervisor
                    .getServiceByIdentifer(PluginServiceWrapper.getIdentify(getPluginPackageName(), actServiceClsName));
            if (plugin != null) {
                plugin.updateServiceState(PluginServiceWrapper.PLUGIN_SERVICE_STOPED);
                plugin.tryToDestroyService();
                return true;
            }
        }
        return super.stopService(name);
    }

    @Override
    public boolean bindService(Intent service, ServiceConnection conn, int flags) {
        PluginDebugLog.log(TAG, "bindService: " + service);
        PluginLoadedApk mLoadedApk = getPluginLoadedApk();
        if (mLoadedApk != null) {
            ComponentFinder.switchToServiceProxy(mLoadedApk, service);
        }
        if (conn != null) {
            if (mLoadedApk != null && service != null) {
                String serviceClass = IntentUtils.getTargetClass(service);
                String packageName = mLoadedApk.getPluginPackageName();
                if (!TextUtils.isEmpty(serviceClass) && !TextUtils.isEmpty(packageName)) {
                    PServiceSupervisor.addServiceConnectionByIdentifer(packageName + "." + serviceClass, conn);
                }
            }
        }
        return super.bindService(service, conn, flags);
    }

    @Override
    public void unbindService(ServiceConnection conn) {
        super.unbindService(conn);
        PServiceSupervisor.removeServiceConnection(conn);
        PluginDebugLog.log(TAG, "unbindService: " + conn);
    }

    @Override
    public void startActivity(Intent intent) {
        super.startActivity(ComponentFinder.switchToActivityProxy(getPluginPackageName(), intent, -1, this));
    }

    @Override
    public void startActivity(Intent intent, Bundle options) {
        super.startActivity(ComponentFinder.switchToActivityProxy(getPluginPackageName(), intent, -1, this), options);
    }

    @Override
    public File getFilesDir() {
        File superFile = super.getFilesDir();
        PluginLoadedApk mLoadedApk = getPluginLoadedApk();
        if (mLoadedApk == null) {
            return superFile;
        }

        File fileDir = new File(getPluginPackageInfo().getDataDir() + "/files/");
        if (!fileDir.exists()) {
            fileDir.mkdirs();
        }
        return mLoadedApk.getPluginAssetManager() == null ? superFile : fileDir;
    }

    @Override
    public File getCacheDir() {
        PluginLoadedApk mLoadedApk = getPluginLoadedApk();
        if (mLoadedApk == null) {
            return super.getCacheDir();
        }
        File cacheDir = new File(getPluginPackageInfo().getDataDir() + "/cache/");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        return mLoadedApk.getPluginAssetManager() == null ? super.getCacheDir() : cacheDir;
    }

    @Override
    public File getExternalFilesDir(String type) {
        try {
            PluginLoadedApk mLoadedApk = getPluginLoadedApk();

            File pluginExternalFileRootDir = PluginPackageManager.getExternalFilesRootDir();

            if (null != mLoadedApk && null != pluginExternalFileRootDir && pluginExternalFileRootDir.exists()) {
                File subPluginFileRootDir = new File(pluginExternalFileRootDir, mLoadedApk.getPluginPackageName());
                if (subPluginFileRootDir.exists() || subPluginFileRootDir.mkdirs()) {
                    if (TextUtils.isEmpty(type)) {
                        PluginDebugLog.runtimeFormatLog(
                                TAG, "getExternalFilesDir subPluginFileRootDir %s : ",
                                subPluginFileRootDir.getAbsolutePath());

                        return subPluginFileRootDir;
                    } else {
                        File targetSubFileDir = new File(subPluginFileRootDir, type);
                        if (targetSubFileDir.exists() || targetSubFileDir.mkdirs()) {
                            PluginDebugLog.runtimeFormatLog(
                                    TAG, "getExternalFilesDir targetSubFileDir %s : ",
                                    targetSubFileDir.getAbsolutePath());

                            return targetSubFileDir;
                        }
                    }
                }
            }
        } catch (Exception e) {
            PluginDebugLog.runtimeFormatLog(TAG, "getExternalFilesDir throws exception %s : ", e.getMessage());
        }
        PluginDebugLog.runtimeFormatLog(TAG, "get hooked external files dir failed, return default");

        return super.getExternalFilesDir(type);
    }

    @Override
    public File getExternalCacheDir() {
        try {
            PluginLoadedApk mLoadedApk = getPluginLoadedApk();

            File pluginExternalCacheRootDir = PluginPackageManager.getExternalCacheRootDir();

            if (null != mLoadedApk && null != pluginExternalCacheRootDir && pluginExternalCacheRootDir.exists()) {
                File subPluginCacheRootDir = new File(pluginExternalCacheRootDir, mLoadedApk.getPluginPackageName());
                if (subPluginCacheRootDir.exists() || subPluginCacheRootDir.mkdirs()) {
                    PluginDebugLog.runtimeFormatLog(
                            TAG, "getExternalCacheDir subPluginCacheRootDir %s : ",
                            subPluginCacheRootDir.getAbsolutePath());

                    return subPluginCacheRootDir;
                }
            }
        } catch (Exception e) {
            PluginDebugLog.runtimeFormatLog(TAG, "getExternalCacheDir throws exception %s : ", e.getMessage());
        }
        PluginDebugLog.runtimeLog(TAG, "get hooked external cache dir failed, return default");

        return super.getExternalCacheDir();
    }

    @Override
    public File getFileStreamPath(String name) {
        PluginLoadedApk mLoadedApk = getPluginLoadedApk();
        if (mLoadedApk == null) {
            return super.getFilesDir();
        }
        File file = new File(getPluginPackageInfo().getDataDir() + "/files/" + name);
        return mLoadedApk.getPluginAssetManager() == null ? super.getFileStreamPath(name) : file;
    }

    @Override
    public File getDir(String name, int mode) {
        PluginLoadedApk mLoadedApk = getPluginLoadedApk();
        if (mLoadedApk == null) {
            return super.getFilesDir();
        }
        File fileDir = new File(getPluginPackageInfo().getDataDir() + "/app_" + name + "/");
        if (!fileDir.exists()) {
            fileDir.mkdirs();
        }
        return mLoadedApk.getPluginAssetManager() == null ? super.getDir(name, mode) : fileDir;

    }

    @Override
    public File getDatabasePath(String name) {
        PluginLoadedApk mLoadedApk = getPluginLoadedApk();
        File dir;
        File f;
        if (name.charAt(0) == File.separatorChar) {
            String dirPath = name.substring(0, name.lastIndexOf(File.separatorChar));
            dir = new File(dirPath);
            name = name.substring(name.lastIndexOf(File.separatorChar));
            f = new File(dir, name);
            return f;
        } else {
            if (mLoadedApk == null) {
                return super.getDatabasePath(name);
            }
            File tmpDir = new File(getPluginPackageInfo().getDataDir() + DATABASE_PATH);
            if (!tmpDir.exists()) {
                tmpDir.mkdirs();
            }
            if (tmpDir.exists() && VersionUtils.hasOreo_MR1()) {
                int perms = android.os.FileUtils.S_IRUSR | android.os.FileUtils.S_IWUSR | android.os.FileUtils.S_IXUSR
                        | android.os.FileUtils.S_IRGRP | android.os.FileUtils.S_IWGRP | android.os.FileUtils.S_IXGRP
                        | android.os.FileUtils.S_IXOTH;
                setFilePermissionsForDb(tmpDir.getAbsolutePath(), perms);

            }
            f = new File(tmpDir, name);
            if (VersionUtils.hasOreo_MR1()) {
                try {
                    if (!f.exists()) {
                        f.createNewFile();
                    }
                    int perms = android.os.FileUtils.S_IRUSR | android.os.FileUtils.S_IWUSR | android.os.FileUtils.S_IRGRP | android.os.FileUtils.S_IWGRP;
                    setFilePermissionsForDb(f.getAbsolutePath(), perms);
                } catch (IOException | SecurityException e) {
                    // ignore
                }
            }
        }

        return f;
    }

    @Override
    public FileInputStream openFileInput(String name) throws FileNotFoundException {
        if (getPluginLoadedApk() == null) {
            return super.openFileInput(name);
        }
        File f = makeFilename(getFilesDir(), name);
        return new FileInputStream(f);
    }

    @Override
    public FileOutputStream openFileOutput(String name, int mode) throws FileNotFoundException {
        if (getPluginLoadedApk() == null) {
            return super.openFileOutput(name, mode);
        }
        final boolean append = (mode & MODE_APPEND) != 0;
        File f = makeFilename(getFilesDir(), name);
        try {
            FileOutputStream fos = new FileOutputStream(f, append);
            return fos;
        } catch (FileNotFoundException e) {

        }
        File parent = f.getParentFile();
        parent.mkdirs();
        FileOutputStream fos = new FileOutputStream(f, append);
        return fos;
    }

    private File makeFilename(File base, String name) {
        if (name.indexOf(File.separatorChar) < 0) {
            return new File(base, name);
        }
        throw new IllegalArgumentException("File " + name + "contains a path separator");
    }

    @Override
    public boolean deleteFile(String name) {
        PluginLoadedApk mLoadedApk = getPluginLoadedApk();
        if (mLoadedApk == null) {
            return super.deleteFile(name);
        }
        File fileDir = new File(getPluginPackageInfo().getDataDir() + "/files/" + name);
        return mLoadedApk.getPluginAssetManager() == null ? super.deleteFile(name) : fileDir.delete();
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String name, int mode, CursorFactory factory) {
        File databaseDir = new File(getPluginPackageInfo().getDataDir() + DATABASE_PATH);
        if (!databaseDir.exists()) {
            databaseDir.mkdirs();
        }
        // backup database for old version start
        checkBackupDB(name);
        // backup database for old version end
        return super.openOrCreateDatabase(databaseDir.getAbsolutePath() + "/" + name, mode, factory);
    }

    /**
     * this is move DB from /data/data/packageName/database to
     * /data/data/package/app_pluginapp/pluginpackage/databases if the app is
     * upgrade,we need backup and recover the db for user,
     */
    private void checkBackupDB(String name) {

        if (name.lastIndexOf(".") == -1) {
            return; // if migrate file is not db file,ignore it.
        }

        String dbName = name.substring(0, name.lastIndexOf("."));

        String dbPath = "/data/data/" + this.getPackageName() + DATABASE_PATH;
        File file = new File(dbPath, name);
        if (file.exists()) {
            File targetFile = new File(getPluginPackageInfo().getDataDir() + DATABASE_PATH + name);
            if (!targetFile.exists()) {
                FileUtils.moveFile(file, targetFile);
            }
            File bakFile = new File(dbPath, dbName + ".db-journal");
            File targetBakFile = new File(
                    getPluginPackageInfo().getDataDir() + DATABASE_PATH + dbName + ".db-journal");
            if (bakFile.exists() && !targetBakFile.exists()) {
                FileUtils.moveFile(bakFile, targetBakFile);
            }
        }
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String name, int mode, CursorFactory factory, DatabaseErrorHandler errorHandler) {
        File databaseDir = new File(getPluginPackageInfo().getDataDir() + DATABASE_PATH);
        if (!databaseDir.exists()) {
            databaseDir.mkdirs();
        }
        // backup database for old version start
        checkBackupDB(name);
        // backup database for old version end

        return super.openOrCreateDatabase(databaseDir.getAbsolutePath() + "/" + name, mode, factory, errorHandler);
    }

    @Override
    public boolean deleteDatabase(String name) {
        File databaseDir = new File(getPluginPackageInfo().getDataDir() + DATABASE_PATH);
        if (!databaseDir.exists()) {
            databaseDir.mkdirs();
        }
        return super.deleteDatabase(databaseDir.getAbsolutePath() + "/" + name);
    }

    @Override
    public String[] databaseList() {
        File databaseDir = new File(getPluginPackageInfo().getDataDir() + DATABASE_PATH);
        if (!databaseDir.exists()) {
            databaseDir.mkdirs();
        }
        return databaseDir.list();
    }

    public File getSharedPrefsFile(String name) {
        File base = new File(getPluginPackageInfo().getDataDir() + SHAREDPREF_PATH);
        if (!base.exists()) {
            base.mkdirs();
        }
        return new File(base, name + ".xml");
    }

    /**
     * Override Oppo method in Context Resolve cann't start plugin on oppo
     * devices, true or false both OK, false as the temporary result
     * [warning] 不要删除该方法，在oppo机型的Context类中存在
     *
     * @Override
     */
    public boolean isOppoStyle() {
        return false;
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        if (getPluginLoadedApk() != null && getPluginPackageInfo() != null) {
            backupSharedPreference(name);
            SharedPreferences sp = getSharedPreferencesForPlugin(name, mode);
            if (sp != null) {
                return sp;
            }
        }
        return super.getSharedPreferences(name, mode);
    }

    private void backupSharedPreference(String name) {
        String sharePath = "/data/data/" + this.getPackageName() + SHAREDPREF_PATH;
        File sFile = new File(sharePath);
        String[] fileList = sFile.list();
        if (fileList == null) {
            return;
        }
        for (String file : fileList) {
            if (file != null && (file.equals(name + ".xml") || file.contains("_" + name + ".xml"))) {
                File oriFile = new File(sharePath + file);
                File tarFile = getSharedPrefsFile(name);
                if (oriFile.exists() && !tarFile.exists()) {
                    FileUtils.moveFile(oriFile, tarFile, false);
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void backupSharedPreferenceV28(String name) {
        File pluginSharePath = new File(getPluginPackageInfo().getDataDir() + SHAREDPREF_PATH);
        File sharePath = new File("/data/data/" + this.getPackageName() + SHAREDPREF_PATH);
        String[] fileList = pluginSharePath.list();
        if (fileList == null) {
            return;
        }
        final String packageName = getPluginPackageName();
        for (String file : fileList) {
            if (file != null) {
                File oriFile = new File(pluginSharePath, file);
                File tarFile = new File(sharePath, packageName + "_" + file);
                if (oriFile.exists() && !tarFile.exists()) {
                    FileUtils.moveFile(oriFile, tarFile, false);
                }
            }
        }
    }

    /**
     * 获取插件的SharedPreference对象
     */
    private SharedPreferences getSharedPreferencesForPlugin(String name, int mode) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                // Android 7.0+
                return getSharedPreferencesV24(name, mode);
            } else if (android.os.Build.VERSION.SDK_INT >= 19) {
                // Android 4.4~6.0
                return getSharedPreferencesV19(name, mode);
            } else if (android.os.Build.VERSION.SDK_INT >= 14) {
                // Android 4.0~4.3
                return getSharedPreferencesV14(name, mode);
            } else {
                // Android 2.3
                return getSharedPreferencesV4(name, mode);
            }
        } catch (Exception e) {
            ErrorUtil.throwErrorIfNeed(e);
        }
        return null;
    }

    /**
     * Android 7.0+系统，
     * <href>http://androidxref.com/7.0.0_r1/xref/frameworks/base/core/java/android/app/ContextImpl.java#141</href>
     * ContextImpl.java
     * private static ArrayMap<String, ArrayMap<File, SharedPreferencesImpl>> sSharedPrefsCache;
     * private ArrayMap<String, File> mSharedPrefsPaths;
     */
    @TargetApi(Build.VERSION_CODES.N)
    private SharedPreferences getSharedPreferencesV24(String name, int mode) throws Exception {
        Object sp = null;
        Class<?> clazz = Class.forName("android.app.ContextImpl");
        Class<?> SharedPreferencesImpl = Class.forName("android.app.SharedPreferencesImpl");
        Constructor<?> constructor = SharedPreferencesImpl.getDeclaredConstructor(File.class, int.class);
        constructor.setAccessible(true);
        ArrayMap<String, ArrayMap<File, Object>> oSharedPrefs = ReflectionUtils.on(this.getBaseContext())
                .<ArrayMap<String, ArrayMap<File, Object>>>get(S_SHARED_PREFS);
        ArrayMap<String, File> oSharedPrefsPaths = VersionUtils.hasPie() ? null :
                ReflectionUtils.on(this.getBaseContext()).<ArrayMap<String, File>>get(M_SHARED_PREFS_PATHS);
        synchronized (clazz) {
            if (oSharedPrefsPaths == null) {
                oSharedPrefsPaths = new ArrayMap<String, File>();
            }
            final String packageName = getPluginPackageName();
            final String fileKey = packageName + "_" + name;
            File prefsFile = oSharedPrefsPaths.get(fileKey);
            if (prefsFile == null) {
                prefsFile = getSharedPrefsFile(name);
                oSharedPrefsPaths.put(fileKey, prefsFile);
            }

            if (oSharedPrefs == null) {
                oSharedPrefs = new ArrayMap<String, ArrayMap<File, Object>>();
            }

            ArrayMap<File, Object> packagePrefs = oSharedPrefs.get(packageName);
            if (packagePrefs == null) {
                packagePrefs = new ArrayMap<File, Object>();
                oSharedPrefs.put(packageName, packagePrefs);
            }

            sp = packagePrefs.get(prefsFile);
            if (sp == null) {
                sp = constructor.newInstance(prefsFile, mode);
                packagePrefs.put(prefsFile, sp);
                return (SharedPreferences) sp;
            }
            if ((mode & Context.MODE_MULTI_PROCESS) != 0 || getPluginPackageInfo()
                    .getPackageInfo().applicationInfo.targetSdkVersion < android.os.Build.VERSION_CODES.HONEYCOMB) {
                ReflectionUtils.on(sp).call("startReloadIfChangedUnexpectedly", sMethods, null);
            }
        }
        return (SharedPreferences) sp;
    }

    /**
     * Android 4.4-6.0
     * <herf>http://androidxref.com/4.4_r1/xref/frameworks/base/core/java/android/app/ContextImpl.java#184</herf>
     * <href>http://androidxref.com/6.0.0_r1/xref/frameworks/base/core/java/android/app/ContextImpl.java#132</href>
     * ContextImpl.java
     * private static ArrayMap<String, ArrayMap<String, SharedPreferencesImpl>> sSharedPrefs;
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private SharedPreferences getSharedPreferencesV19(String name, int mode)
            throws Exception {
        Object sp = null;
        Class<?> clazz = Class.forName("android.app.ContextImpl");
        Class<?> SharedPreferencesImpl = Class.forName("android.app.SharedPreferencesImpl");
        Constructor<?> constructor = SharedPreferencesImpl.getDeclaredConstructor(File.class, int.class);
        constructor.setAccessible(true);
        ArrayMap<String, ArrayMap<String, Object>> oSharedPrefs = ReflectionUtils.on(this.getBaseContext())
                .<ArrayMap<String, ArrayMap<String, Object>>>get(S_SHARED_PREFS);
        synchronized (clazz) {
            if (oSharedPrefs == null) {
                oSharedPrefs = new ArrayMap<String, ArrayMap<String, Object>>();
            }

            final String packageName = getPluginPackageName();
            ArrayMap<String, Object> packagePrefs = oSharedPrefs.get(packageName);
            if (packagePrefs == null) {
                packagePrefs = new ArrayMap<String, Object>();
                oSharedPrefs.put(packageName, packagePrefs);
            }

            sp = packagePrefs.get(name);
            if (sp == null) {
                File prefsFile = getSharedPrefsFile(name);
                sp = constructor.newInstance(prefsFile, mode);
                packagePrefs.put(name, sp);
                return (SharedPreferences) sp;
            }
            if ((mode & Context.MODE_MULTI_PROCESS) != 0 || getPluginPackageInfo()
                    .getPackageInfo().applicationInfo.targetSdkVersion < android.os.Build.VERSION_CODES.HONEYCOMB) {
                ReflectionUtils.on(sp).call("startReloadIfChangedUnexpectedly", sMethods, null);
            }
        }
        return (SharedPreferences) sp;
    }

    /**
     * Android 4.0-4.3
     * <href>http://androidxref.com/4.0.3_r1/xref/frameworks/base/core/java/android/app/ContextImpl.java#142</href>
     * <href>http://androidxref.com/4.3_r2.1/xref/frameworks/base/core/java/android/app/ContextImpl.java#172</href>
     * ContextImpl.java
     * private static final HashMap<String, SharedPreferencesImpl> sSharedPrefs
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private SharedPreferences getSharedPreferencesV14(String name, int mode)
            throws Exception {
        Object sp = null;
        Class<?> SharedPreferencesImpl = Class.forName("android.app.SharedPreferencesImpl");
        Constructor<?> constructor = SharedPreferencesImpl.getDeclaredConstructor(File.class, int.class);
        constructor.setAccessible(true);
        HashMap<String, Object> oSharedPrefs = ReflectionUtils.on(this.getBaseContext())
                .<HashMap<String, Object>>get(S_SHARED_PREFS);

        final String packageName = getPluginPackageName();
        final String nameKey = packageName + "_" + name;
        synchronized (oSharedPrefs) {
            sp = oSharedPrefs.get(nameKey);
            if (sp == null) {
                File prefsFile = getSharedPrefsFile(name);
                sp = constructor.newInstance(prefsFile, mode);
                oSharedPrefs.put(nameKey, sp);
                return (SharedPreferences) sp;
            }
        }
        if ((mode & Context.MODE_MULTI_PROCESS) != 0 || getPluginPackageInfo()
                .getPackageInfo().applicationInfo.targetSdkVersion < android.os.Build.VERSION_CODES.HONEYCOMB) {
            ReflectionUtils.on(sp).call("startReloadIfChangedUnexpectedly", sMethods, null);
        }
        return (SharedPreferences) sp;
    }

    /**
     * Android 4.0以下系统
     */
    private SharedPreferences getSharedPreferencesV4(String name, int mode) throws Exception {
        // now the plugin don't support 2.3,but if it will support in
        // the furture,we can use this.
        Object sp = null;
        Class<?> SharedPreferencesImpl = Class.forName("android.app.ContextImpl$SharedPreferencesImpl");
        Constructor<?> constructor = SharedPreferencesImpl.getDeclaredConstructor(File.class, int.class);
        constructor.setAccessible(true);
        Class<?> clazz = Class.forName("android.app.ContextImpl");
        File prefsFile;
        boolean needInitialLoad = false;
        final HashMap<String, Object> oSharedPrefs = ReflectionUtils.on(this.getBaseContext()).get(S_SHARED_PREFS);
        synchronized (oSharedPrefs) {
            sp = oSharedPrefs.get(name);
            Method hasFileChangedUnexpectedly = SharedPreferencesImpl.getDeclaredMethod("hasFileChangedUnexpectedly");
            boolean mHasFileChangedUnexpectedly = (Boolean) hasFileChangedUnexpectedly.invoke(sp);
            if (sp != null && !mHasFileChangedUnexpectedly) {
                return (SharedPreferences) sp;
            }
            prefsFile = getSharedPrefsFile(name);
            if (sp == null) {
                sp = constructor.newInstance(prefsFile, mode);
                oSharedPrefs.put(name, sp);
                needInitialLoad = true;
            }
        }
        synchronized (sp) {
            Method isLoaded = SharedPreferencesImpl.getDeclaredMethod("isLoaded");
            boolean isLoadResult = (Boolean) isLoaded.invoke(sp);
            if (needInitialLoad && isLoadResult) {
                return (SharedPreferences) sp;
            }
            File backup = makeBackupFile(prefsFile);
            if (backup.exists()) {
                prefsFile.delete();
                backup.renameTo(prefsFile);
            }

            Map map = null;
            Class<?> fileUtilsClass = Class.forName("android.os.FileUtils");

            Class<?> fileStatusClass = Class.forName("android.os.FileUtils$FileStatus");
            Constructor<?> fileStatusConstructor = fileStatusClass.getConstructor();
            Object FileStatus = fileStatusConstructor.newInstance();

            Method getFileStatus = fileUtilsClass.getDeclaredMethod("getFileStatus", String.class, fileStatusClass);
            boolean getFileStatusResult = (Boolean) getFileStatus.invoke(FileStatus, prefsFile.getPath(), FileStatus);
            FileInputStream fis = null;
            if (getFileStatusResult && prefsFile.canRead()) {
                try {
                    fis = new FileInputStream(prefsFile);
                    Class<?> xmlUtilClass = Class.forName("com.android.internal.util.XmlUtils");
                    map = (Map) xmlUtilClass.getDeclaredMethod("readMapXml", FileInputStream.class)
                            .invoke(xmlUtilClass.newInstance(), fis);
                } catch (Exception e) {
                    // ignore
                } finally {
                    FileUtils.closeQuietly(fis);
                }
            }
            SharedPreferencesImpl.getMethod("replace", Map.class, fileStatusClass).invoke(sp, map, FileStatus);
        }
        return (SharedPreferences) sp;
    }

    /**
     * Get the context which start this plugin
     */
    @Override
    public Context getOriginalContext() {
        PluginLoadedApk mLoadedApk = getPluginLoadedApk();
        if (null != mLoadedApk) {
            return mLoadedApk.getHostContext();
        }
        return null;
    }

    @Override
    public String getPackageCodePath() {
        PluginPackageInfo targetMapping = getPluginPackageInfo();
        if (targetMapping != null && targetMapping.isUsePluginCodePath()) {
            PackageInfo packageInfo = targetMapping.getPackageInfo();
            if (packageInfo != null && packageInfo.applicationInfo != null) {
                String sourceDir = packageInfo.applicationInfo.sourceDir;
                if (!TextUtils.isEmpty(sourceDir)) {
                    return sourceDir;
                }
            }
        }
        return super.getPackageCodePath();
    }

    /**
     * 获取宿主的ResourcesTool
     */
    @Override
    public ResourcesToolForPlugin getHostResourceTool() {
        PluginLoadedApk mLoadedApk = getPluginLoadedApk();
        if (null != mLoadedApk) {
            return mLoadedApk.getHostResourceTool();
        }
        return null;
    }

    public PluginPackageInfo getPluginPackageInfo() {
        PluginLoadedApk mLoadedApk = getPluginLoadedApk();
        if (mLoadedApk != null) {
            return mLoadedApk.getPluginPackageInfo();
        }
        return null;
    }

    @Override
    public void exitApp() {
        PluginLoadedApk mLoadedApk = getPluginLoadedApk();
        if (null != mLoadedApk) {
            mLoadedApk.quitApp(true);
        }
    }

    /**
     * 获取插件的PluginLoadedApk
     */
    @NonNull protected abstract PluginLoadedApk getPluginLoadedApk();
}
