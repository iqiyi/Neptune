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
package org.qiyi.pluginlibrary.component.wraper;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;

import org.qiyi.pluginlibrary.context.PluginContextWrapper;
import org.qiyi.pluginlibrary.runtime.PluginLoadedApk;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

/**
 * 包装 Activity，重写必要的方法，其他方法交给 origin 处理
 * <p>
 * 考虑到会用在 Fragment 插件化中，所以继承自 FragmentActivity
 */
public class ActivityWrapper extends FragmentActivity {
    private PluginContextWrapper mPluginContext; // 插件Context
    private Activity mOriginActivity; //原生的宿主Activity
    /**
     * 如果 mOriginActivity 是 FragmentActivity 则该值不为空且 FragmentActivity 调用可以用该对象代理
     */
    private FragmentActivity mOriginFragmentActivity;

    public ActivityWrapper(Activity origin, PluginLoadedApk loadedApk) {
        mPluginContext = new PluginContextWrapper(origin.getBaseContext(), loadedApk);
        mOriginActivity = origin;
        if (mOriginActivity instanceof FragmentActivity) {
            mOriginFragmentActivity = (FragmentActivity) mOriginActivity;
        }
        // 将ActivityWrapper的mBase设置为插件的Context
        attachBaseContext(mPluginContext);
    }

    @Override
    public ClassLoader getClassLoader() {
        return mPluginContext.getClassLoader();
    }

    @Override
    public Context getApplicationContext() {
        return mPluginContext.getApplicationContext();
    }

    @Override
    public ApplicationInfo getApplicationInfo() {
        return mPluginContext.getApplicationInfo();
    }

    @Override
    public Resources getResources() {
        return mPluginContext.getResources();
    }

    @Override
    public AssetManager getAssets() {
        return mPluginContext.getAssets();
    }

    @Override
    public ComponentName startService(Intent service) {
        return mPluginContext.startService(service);
    }

    @Override
    public boolean stopService(Intent name) {
        return mPluginContext.stopService(name);
    }

    @Override
    public boolean bindService(Intent service, ServiceConnection conn, int flags) {
        return mPluginContext.bindService(service, conn, flags);
    }

    @Override
    public void unbindService(ServiceConnection conn) {
        mPluginContext.unbindService(conn);
    }

    @Override
    public void startActivity(Intent intent, Bundle options) {
        mPluginContext.startActivity(intent, options);
    }

    @Override
    public void startActivity(Intent intent) {
        mPluginContext.startActivity(intent);
    }

    @Override
    public File getFilesDir() {
        return mPluginContext.getFilesDir();
    }

    @Override
    public File getCacheDir() {
        return mPluginContext.getCacheDir();
    }

    @Override
    public File getExternalFilesDir(String type) {
        return mPluginContext.getExternalFilesDir(type);
    }

    @Override
    public File getExternalCacheDir() {
        return mPluginContext.getExternalCacheDir();
    }

    @Override
    public File getFileStreamPath(String name) {
        return mPluginContext.getFileStreamPath(name);
    }

    @Override
    public File getDir(String name, int mode) {
        return mPluginContext.getDir(name, mode);
    }

    @Override
    public File getDatabasePath(String name) {
        return mPluginContext.getDatabasePath(name);
    }

    @Override
    public FileInputStream openFileInput(String name) throws FileNotFoundException {
        return mPluginContext.openFileInput(name);
    }

    @Override
    public FileOutputStream openFileOutput(String name, int mode) throws FileNotFoundException {
        return mPluginContext.openFileOutput(name, mode);
    }

    @Override
    public boolean deleteFile(String name) {
        return mPluginContext.deleteFile(name);
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
        return mPluginContext.openOrCreateDatabase(name, mode, factory);
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler errorHandler) {
        return mPluginContext.openOrCreateDatabase(name, mode, factory, errorHandler);
    }

    @Override
    public boolean deleteDatabase(String name) {
        return mPluginContext.deleteDatabase(name);
    }

    @Override
    public String[] databaseList() {
        return mPluginContext.databaseList();
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return mPluginContext.getSharedPreferences(name, mode);
    }

    @Override
    public Resources.Theme getTheme() {
        return mPluginContext.getTheme();
    }

    @Override
    public void setTheme(int resid) {
        mPluginContext.setTheme(resid);
    }

    @Override
    public LayoutInflater getLayoutInflater() {
        return (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public Object getSystemService(@NonNull String name) {
        if (LAYOUT_INFLATER_SERVICE.equals(name)) {
            LayoutInflater layoutInflater = (LayoutInflater) mPluginContext.getSystemService(name);
            return layoutInflater.cloneInContext(this);
        }
        return mOriginActivity.getSystemService(name);
    }


    // 下面是重写的非 ContextWrapper 的 Activity 方法
    // 负责将 activity 的相关逻辑委托给 mOriginActivity 处理
    // 部分 final 方法无法重写，可能引发问题，待观察
    // 来自代码自动生成

    @Override
    public android.app.ActionBar getActionBar() {
        return mOriginActivity.getActionBar();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void setActionBar(android.widget.Toolbar toolbar0) {
        mOriginActivity.setActionBar(toolbar0);
    }

    @Override
    public android.app.FragmentManager getFragmentManager() {
        return mOriginActivity.getFragmentManager();
    }

    @Override
    public android.app.LoaderManager getLoaderManager() {
        return mOriginActivity.getLoaderManager();
    }

    @Override
    public android.app.PendingIntent createPendingResult(int int0, android.content.Intent intent1, int int2) {
        return mOriginActivity.createPendingResult(int0, intent1, int2);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public android.app.VoiceInteractor getVoiceInteractor() {
        return mOriginActivity.getVoiceInteractor();
    }

    @Override
    public android.arch.lifecycle.Lifecycle getLifecycle() {
        return mOriginFragmentActivity.getLifecycle();
    }

    @Override
    public android.content.ComponentName getCallingActivity() {
        return mOriginActivity.getCallingActivity();
    }

    @Override
    public android.content.ComponentName getComponentName() {
        return mOriginActivity.getComponentName();
    }

    @Override
    public android.content.Intent getIntent() {
        return mOriginActivity.getIntent();
    }

    @Override
    public void setIntent(android.content.Intent intent0) {
        mOriginActivity.setIntent(intent0);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public android.content.Intent getParentActivityIntent() {
        return mOriginActivity.getParentActivityIntent();
    }

    @Override
    public android.content.SharedPreferences getPreferences(int int0) {
        return mOriginActivity.getPreferences(int0);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    @Override
    public android.net.Uri getReferrer() {
        return mOriginActivity.getReferrer();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public android.net.Uri onProvideReferrer() {
        return mOriginActivity.onProvideReferrer();
    }

    @Override
    public android.support.v4.app.FragmentManager getSupportFragmentManager() {
        return mOriginFragmentActivity.getSupportFragmentManager();
    }

    @Override
    public android.support.v4.app.LoaderManager getSupportLoaderManager() {
        return mOriginFragmentActivity.getSupportLoaderManager();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public android.transition.Scene getContentScene() {
        return mOriginActivity.getContentScene();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public android.transition.TransitionManager getContentTransitionManager() {
        return mOriginActivity.getContentTransitionManager();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void setContentTransitionManager(android.transition.TransitionManager transitionmanager0) {
        mOriginActivity.setContentTransitionManager(transitionmanager0);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public android.view.DragAndDropPermissions requestDragAndDropPermissions(android.view.DragEvent dragevent0) {
        return mOriginActivity.requestDragAndDropPermissions(dragevent0);
    }

    @Override
    public android.view.MenuInflater getMenuInflater() {
        return mOriginActivity.getMenuInflater();
    }

    @Override
    public <T extends View> T findViewById(int int0) {
        return mOriginActivity.findViewById(int0);
    }

    @Override
    public android.view.View getCurrentFocus() {
        return mOriginActivity.getCurrentFocus();
    }

    @Override
    public android.view.View onCreatePanelView(int int0) {
        return mOriginActivity.onCreatePanelView(int0);
    }

    @Override
    public android.view.View onCreateView(android.view.View view0, java.lang.String string1, android.content.Context context2, android.util.AttributeSet attributeset3) {
        return mOriginActivity.onCreateView(view0, string1, context2, attributeset3);
    }

    @Override
    public android.view.View onCreateView(java.lang.String string0, android.content.Context context1, android.util.AttributeSet attributeset2) {
        return mOriginActivity.onCreateView(string0, context1, attributeset2);
    }

    @Override
    public android.view.Window getWindow() {
        return mOriginActivity.getWindow();
    }

    @Override
    public android.view.WindowManager getWindowManager() {
        return mOriginActivity.getWindowManager();
    }

    @Override
    public boolean dispatchGenericMotionEvent(android.view.MotionEvent motionevent0) {
        return mOriginActivity.dispatchGenericMotionEvent(motionevent0);
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent keyevent0) {
        return mOriginActivity.dispatchKeyEvent(keyevent0);
    }

    @Override
    public boolean dispatchKeyShortcutEvent(android.view.KeyEvent keyevent0) {
        return mOriginActivity.dispatchKeyShortcutEvent(keyevent0);
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(android.view.accessibility.AccessibilityEvent accessibilityevent0) {
        return mOriginActivity.dispatchPopulateAccessibilityEvent(accessibilityevent0);
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent motionevent0) {
        return mOriginActivity.dispatchTouchEvent(motionevent0);
    }

    @Override
    public boolean dispatchTrackballEvent(android.view.MotionEvent motionevent0) {
        return mOriginActivity.dispatchTrackballEvent(motionevent0);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public boolean enterPictureInPictureMode(android.app.PictureInPictureParams pictureinpictureparams0) {
        return mOriginActivity.enterPictureInPictureMode(pictureinpictureparams0);
    }

    @Override
    public boolean hasWindowFocus() {
        return mOriginActivity.hasWindowFocus();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public boolean isActivityTransitionRunning() {
        return mOriginActivity.isActivityTransitionRunning();
    }

    @Override
    public boolean isChangingConfigurations() {
        return mOriginActivity.isChangingConfigurations();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    public boolean isDestroyed() {
        return mOriginActivity.isDestroyed();
    }

    @Override
    public boolean isFinishing() {
        return mOriginActivity.isFinishing();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public boolean isImmersive() {
        return mOriginActivity.isImmersive();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void setImmersive(boolean boolean0) {
        mOriginActivity.setImmersive(boolean0);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public boolean isInMultiWindowMode() {
        return mOriginActivity.isInMultiWindowMode();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public boolean isInPictureInPictureMode() {
        return mOriginActivity.isInPictureInPictureMode();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public boolean isLocalVoiceInteractionSupported() {
        return mOriginActivity.isLocalVoiceInteractionSupported();
    }

    @Override
    public boolean isTaskRoot() {
        return mOriginActivity.isTaskRoot();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public boolean isVoiceInteraction() {
        return mOriginActivity.isVoiceInteraction();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public boolean isVoiceInteractionRoot() {
        return mOriginActivity.isVoiceInteractionRoot();
    }

    @Override
    public boolean moveTaskToBack(boolean boolean0) {
        return mOriginActivity.moveTaskToBack(boolean0);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public boolean navigateUpTo(android.content.Intent intent0) {
        return mOriginActivity.navigateUpTo(intent0);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public boolean navigateUpToFromChild(android.app.Activity activity0, android.content.Intent intent1) {
        return mOriginActivity.navigateUpToFromChild(activity0, intent1);
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem menuitem0) {
        return mOriginActivity.onContextItemSelected(menuitem0);
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu0) {
        return mOriginActivity.onCreateOptionsMenu(menu0);
    }

    @Override
    public boolean onCreatePanelMenu(int int0, android.view.Menu menu1) {
        return mOriginActivity.onCreatePanelMenu(int0, menu1);
    }

    @Override
    public boolean onCreateThumbnail(android.graphics.Bitmap bitmap0, android.graphics.Canvas canvas1) {
        return mOriginActivity.onCreateThumbnail(bitmap0, canvas1);
    }

    @Override
    public boolean onGenericMotionEvent(android.view.MotionEvent motionevent0) {
        return mOriginActivity.onGenericMotionEvent(motionevent0);
    }

    @Override
    public boolean onKeyDown(int int0, android.view.KeyEvent keyevent1) {
        return mOriginActivity.onKeyDown(int0, keyevent1);
    }

    @Override
    public boolean onKeyLongPress(int int0, android.view.KeyEvent keyevent1) {
        return mOriginActivity.onKeyLongPress(int0, keyevent1);
    }

    @Override
    public boolean onKeyMultiple(int int0, int int1, android.view.KeyEvent keyevent2) {
        return mOriginActivity.onKeyMultiple(int0, int1, keyevent2);
    }

    @Override
    public boolean onKeyShortcut(int int0, android.view.KeyEvent keyevent1) {
        return mOriginActivity.onKeyShortcut(int0, keyevent1);
    }

    @Override
    public boolean onKeyUp(int int0, android.view.KeyEvent keyevent1) {
        return mOriginActivity.onKeyUp(int0, keyevent1);
    }

    @Override
    public boolean onMenuItemSelected(int int0, android.view.MenuItem menuitem1) {
        return mOriginActivity.onMenuItemSelected(int0, menuitem1);
    }

    @Override
    public boolean onMenuOpened(int int0, android.view.Menu menu1) {
        return mOriginActivity.onMenuOpened(int0, menu1);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public boolean onNavigateUp() {
        return mOriginActivity.onNavigateUp();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public boolean onNavigateUpFromChild(android.app.Activity activity0) {
        return mOriginActivity.onNavigateUpFromChild(activity0);
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem menuitem0) {
        return mOriginActivity.onOptionsItemSelected(menuitem0);
    }

    @Override
    public boolean onPrepareOptionsMenu(android.view.Menu menu0) {
        return mOriginActivity.onPrepareOptionsMenu(menu0);
    }

    @Override
    public boolean onPreparePanel(int int0, android.view.View view1, android.view.Menu menu2) {
        return mOriginActivity.onPreparePanel(int0, view1, menu2);
    }

    @Override
    public boolean onSearchRequested() {
        return mOriginActivity.onSearchRequested();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public boolean onSearchRequested(android.view.SearchEvent searchevent0) {
        return mOriginActivity.onSearchRequested(searchevent0);
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent motionevent0) {
        return mOriginActivity.onTouchEvent(motionevent0);
    }

    @Override
    public boolean onTrackballEvent(android.view.MotionEvent motionevent0) {
        return mOriginActivity.onTrackballEvent(motionevent0);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean releaseInstance() {
        return mOriginActivity.releaseInstance();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean requestVisibleBehind(boolean boolean0) {
        return mOriginActivity.requestVisibleBehind(boolean0);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public boolean shouldShowRequestPermissionRationale(java.lang.String string0) {
        return mOriginActivity.shouldShowRequestPermissionRationale(string0);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public boolean shouldUpRecreateTask(android.content.Intent intent0) {
        return mOriginActivity.shouldUpRecreateTask(intent0);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public boolean showAssist(android.os.Bundle bundle0) {
        return mOriginActivity.showAssist(bundle0);
    }

    @Override
    public boolean startActivityIfNeeded(android.content.Intent intent0, int int1) {
        return mOriginActivity.startActivityIfNeeded(intent0, int1);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public boolean startActivityIfNeeded(android.content.Intent intent0, int int1, android.os.Bundle bundle2) {
        return mOriginActivity.startActivityIfNeeded(intent0, int1, bundle2);
    }

    @Override
    public boolean startNextMatchingActivity(android.content.Intent intent0) {
        return mOriginActivity.startNextMatchingActivity(intent0);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public boolean startNextMatchingActivity(android.content.Intent intent0, android.os.Bundle bundle1) {
        return mOriginActivity.startNextMatchingActivity(intent0, bundle1);
    }

    @Override
    public int getChangingConfigurations() {
        return mOriginActivity.getChangingConfigurations();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int getMaxNumPictureInPictureActions() {
        return mOriginActivity.getMaxNumPictureInPictureActions();
    }

    @Override
    public int getRequestedOrientation() {
        return mOriginActivity.getRequestedOrientation();
    }

    @Override
    public void setRequestedOrientation(int int0) {
        mOriginActivity.setRequestedOrientation(int0);
    }

    @Override
    public int getTaskId() {
        return mOriginActivity.getTaskId();
    }

    @Override
    public java.lang.CharSequence onCreateDescription() {
        return mOriginActivity.onCreateDescription();
    }

    @Override
    public java.lang.Object getLastCustomNonConfigurationInstance() {
        return mOriginFragmentActivity.getLastCustomNonConfigurationInstance();
    }

    @Override
    public java.lang.Object getLastNonConfigurationInstance() {
        return mOriginActivity.getLastNonConfigurationInstance();
    }

    @Override
    public java.lang.Object onRetainCustomNonConfigurationInstance() {
        return mOriginFragmentActivity.onRetainCustomNonConfigurationInstance();
    }

    @Override
    public java.lang.String getCallingPackage() {
        return mOriginActivity.getCallingPackage();
    }

    @Override
    public java.lang.String getLocalClassName() {
        return mOriginActivity.getLocalClassName();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    public void applyOverrideConfiguration(android.content.res.Configuration configuration0) {
        mOriginActivity.applyOverrideConfiguration(configuration0);
    }

    @Override
    public void closeContextMenu() {
        mOriginActivity.closeContextMenu();
    }

    @Override
    public void closeOptionsMenu() {
        mOriginActivity.closeOptionsMenu();
    }

    @Override
    public void dump(java.lang.String string0, java.io.FileDescriptor filedescriptor1, java.io.PrintWriter printwriter2, java.lang.String[] string3) {
        mOriginActivity.dump(string0, filedescriptor1, printwriter2, string3);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void enterPictureInPictureMode() {
        mOriginActivity.enterPictureInPictureMode();
    }

    @Override
    public void finish() {
        mOriginActivity.finish();
    }

    @Override
    public void finishActivity(int int0) {
        mOriginActivity.finishActivity(int0);
    }

    @Override
    public void finishActivityFromChild(android.app.Activity activity0, int int1) {
        mOriginActivity.finishActivityFromChild(activity0, int1);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void finishAffinity() {
        mOriginActivity.finishAffinity();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void finishAfterTransition() {
        mOriginActivity.finishAfterTransition();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void finishAndRemoveTask() {
        mOriginActivity.finishAndRemoveTask();
    }

    @Override
    public void finishFromChild(android.app.Activity activity0) {
        mOriginActivity.finishFromChild(activity0);
    }

    @Override
    public void invalidateOptionsMenu() {
        mOriginActivity.invalidateOptionsMenu();
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onActionModeFinished(android.view.ActionMode actionmode0) {
        mOriginActivity.onActionModeFinished(actionmode0);
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onActionModeStarted(android.view.ActionMode actionmode0) {
        mOriginActivity.onActionModeStarted(actionmode0);
    }

    @SuppressLint("NewApi")
    @Override
    public void onActivityReenter(int int0, android.content.Intent intent1) {
        mOriginActivity.onActivityReenter(int0, intent1);
    }

    @Override
    public void onAttachFragment(android.app.Fragment fragment0) {
        mOriginActivity.onAttachFragment(fragment0);
    }

    @Override
    public void onAttachFragment(android.support.v4.app.Fragment fragment0) {
        mOriginFragmentActivity.onAttachFragment(fragment0);
    }

    @Override
    public void onAttachedToWindow() {
        mOriginActivity.onAttachedToWindow();
    }

    @Override
    public void onBackPressed() {
        mOriginActivity.onBackPressed();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration configuration0) {
        mOriginActivity.onConfigurationChanged(configuration0);
    }

    @Override
    public void onContentChanged() {
        mOriginActivity.onContentChanged();
    }

    @Override
    public void onContextMenuClosed(android.view.Menu menu0) {
        mOriginActivity.onContextMenuClosed(menu0);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void onCreateNavigateUpTaskStack(android.app.TaskStackBuilder taskstackbuilder0) {
        mOriginActivity.onCreateNavigateUpTaskStack(taskstackbuilder0);
    }

    @Override
    public void onDetachedFromWindow() {
        mOriginActivity.onDetachedFromWindow();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onEnterAnimationComplete() {
        mOriginActivity.onEnterAnimationComplete();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onLocalVoiceInteractionStarted() {
        mOriginActivity.onLocalVoiceInteractionStarted();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onLocalVoiceInteractionStopped() {
        mOriginActivity.onLocalVoiceInteractionStopped();
    }

    @Override
    public void onLowMemory() {
        mOriginActivity.onLowMemory();
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onMultiWindowModeChanged(boolean boolean0) {
        mOriginFragmentActivity.onMultiWindowModeChanged(boolean0);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onMultiWindowModeChanged(boolean boolean0, android.content.res.Configuration configuration1) {
        mOriginActivity.onMultiWindowModeChanged(boolean0, configuration1);
    }

    @Override
    public void onOptionsMenuClosed(android.view.Menu menu0) {
        mOriginActivity.onOptionsMenuClosed(menu0);
    }

    @Override
    public void onPanelClosed(int int0, android.view.Menu menu1) {
        mOriginActivity.onPanelClosed(int0, menu1);
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onPictureInPictureModeChanged(boolean boolean0) {
        mOriginFragmentActivity.onPictureInPictureModeChanged(boolean0);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onPictureInPictureModeChanged(boolean boolean0, android.content.res.Configuration configuration1) {
        mOriginActivity.onPictureInPictureModeChanged(boolean0, configuration1);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onPointerCaptureChanged(boolean boolean0) {
        mOriginActivity.onPointerCaptureChanged(boolean0);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onPostCreate(android.os.Bundle bundle0, android.os.PersistableBundle persistablebundle1) {
        mOriginActivity.onPostCreate(bundle0, persistablebundle1);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void onPrepareNavigateUpTaskStack(android.app.TaskStackBuilder taskstackbuilder0) {
        mOriginActivity.onPrepareNavigateUpTaskStack(taskstackbuilder0);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onProvideAssistContent(android.app.assist.AssistContent assistcontent0) {
        mOriginActivity.onProvideAssistContent(assistcontent0);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void onProvideAssistData(android.os.Bundle bundle0) {
        mOriginActivity.onProvideAssistData(bundle0);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onProvideKeyboardShortcuts(java.util.List list0, android.view.Menu menu1, int int2) {
        mOriginActivity.onProvideKeyboardShortcuts(list0, menu1, int2);
    }

    @Override
    public void onRequestPermissionsResult(int int0, java.lang.String[] string1, int[] int2) {
        mOriginFragmentActivity.onRequestPermissionsResult(int0, string1, int2);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onRestoreInstanceState(android.os.Bundle bundle0, android.os.PersistableBundle persistablebundle1) {
        mOriginActivity.onRestoreInstanceState(bundle0, persistablebundle1);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onSaveInstanceState(android.os.Bundle bundle0, android.os.PersistableBundle persistablebundle1) {
        mOriginActivity.onSaveInstanceState(bundle0, persistablebundle1);
    }

    @Override
    public void onStateNotSaved() {
        mOriginFragmentActivity.onStateNotSaved();
    }

    @Override
    public void onTrimMemory(int int0) {
        mOriginActivity.onTrimMemory(int0);
    }

    @Override
    public void onUserInteraction() {
        mOriginActivity.onUserInteraction();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @SuppressLint("MissingSuperCall")
    @Override
    public void onVisibleBehindCanceled() {
        mOriginActivity.onVisibleBehindCanceled();
    }

    @Override
    public void onWindowFocusChanged(boolean boolean0) {
        mOriginActivity.onWindowFocusChanged(boolean0);
    }

    @Override
    public void openContextMenu(android.view.View view0) {
        mOriginActivity.openContextMenu(view0);
    }

    @Override
    public void openOptionsMenu() {
        mOriginActivity.openOptionsMenu();
    }

    @Override
    public void overridePendingTransition(int int0, int int1) {
        mOriginActivity.overridePendingTransition(int0, int1);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void postponeEnterTransition() {
        mOriginActivity.postponeEnterTransition();
    }

    @Override
    public void recreate() {
        mOriginActivity.recreate();
    }

    @Override
    public void registerForContextMenu(android.view.View view0) {
        mOriginActivity.registerForContextMenu(view0);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public void reportFullyDrawn() {
        mOriginActivity.reportFullyDrawn();
    }

    @Override
    public void setContentView(android.view.View view0) {
        mOriginActivity.setContentView(view0);
    }

    @Override
    public void setContentView(int int0) {
        mOriginActivity.setContentView(int0);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void setEnterSharedElementCallback(android.app.SharedElementCallback sharedelementcallback0) {
        mOriginActivity.setEnterSharedElementCallback(sharedelementcallback0);
    }

    @Override
    public void setEnterSharedElementCallback(android.support.v4.app.SharedElementCallback sharedelementcallback0) {
        mOriginFragmentActivity.setEnterSharedElementCallback(sharedelementcallback0);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void setExitSharedElementCallback(android.app.SharedElementCallback sharedelementcallback0) {
        mOriginActivity.setExitSharedElementCallback(sharedelementcallback0);
    }

    @Override
    public void setExitSharedElementCallback(android.support.v4.app.SharedElementCallback sharedelementcallback0) {
        mOriginFragmentActivity.setExitSharedElementCallback(sharedelementcallback0);
    }

    @Override
    public void setFinishOnTouchOutside(boolean boolean0) {
        mOriginActivity.setFinishOnTouchOutside(boolean0);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void setPictureInPictureParams(android.app.PictureInPictureParams pictureinpictureparams0) {
        mOriginActivity.setPictureInPictureParams(pictureinpictureparams0);
    }

    @Override
    public void setTitle(int int0) {
        mOriginActivity.setTitle(int0);
    }

    @Override
    public void setTitle(java.lang.CharSequence charsequence0) {
        mOriginActivity.setTitle(charsequence0);
    }

    @Override
    public void setTitleColor(int int0) {
        mOriginActivity.setTitleColor(int0);
    }

    @Override
    public void setVisible(boolean boolean0) {
        mOriginActivity.setVisible(boolean0);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void setVrModeEnabled(boolean boolean0, android.content.ComponentName componentname1) throws PackageManager.NameNotFoundException {
        mOriginActivity.setVrModeEnabled(boolean0, componentname1);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void showLockTaskEscapeMessage() {
        mOriginActivity.showLockTaskEscapeMessage();
    }

    @Override
    public void startActivityForResult(android.content.Intent intent0, int int1) {
        mOriginActivity.startActivityForResult(intent0, int1);
    }

    @SuppressLint("RestrictedApi")
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void startActivityForResult(android.content.Intent intent0, int int1, android.os.Bundle bundle2) {
        mOriginActivity.startActivityForResult(intent0, int1, bundle2);
    }

    @Override
    public void startActivityFromChild(android.app.Activity activity0, android.content.Intent intent1, int int2) {
        mOriginActivity.startActivityFromChild(activity0, intent1, int2);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void startActivityFromChild(android.app.Activity activity0, android.content.Intent intent1, int int2, android.os.Bundle bundle3) {
        mOriginActivity.startActivityFromChild(activity0, intent1, int2, bundle3);
    }

    @Override
    public void startActivityFromFragment(android.app.Fragment fragment0, android.content.Intent intent1, int int2) {
        mOriginActivity.startActivityFromFragment(fragment0, intent1, int2);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void startActivityFromFragment(android.app.Fragment fragment0, android.content.Intent intent1, int int2, android.os.Bundle bundle3) {
        mOriginActivity.startActivityFromFragment(fragment0, intent1, int2, bundle3);
    }

    @Override
    public void startActivityFromFragment(android.support.v4.app.Fragment fragment0, android.content.Intent intent1, int int2) {
        mOriginFragmentActivity.startActivityFromFragment(fragment0, intent1, int2);
    }

    @Override
    public void startActivityFromFragment(android.support.v4.app.Fragment fragment0, android.content.Intent intent1, int int2, android.os.Bundle bundle3) {
        mOriginFragmentActivity.startActivityFromFragment(fragment0, intent1, int2, bundle3);
    }

    @Override
    public void startIntentSenderForResult(android.content.IntentSender intentsender0, int int1, android.content.Intent intent2, int int3, int int4, int int5) throws IntentSender.SendIntentException {
        mOriginActivity.startIntentSenderForResult(intentsender0, int1, intent2, int3, int4, int5);
    }

    @SuppressLint("RestrictedApi")
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void startIntentSenderForResult(android.content.IntentSender intentsender0, int int1, android.content.Intent intent2, int int3, int int4, int int5, android.os.Bundle bundle6) throws IntentSender.SendIntentException {
        mOriginActivity.startIntentSenderForResult(intentsender0, int1, intent2, int3, int4, int5, bundle6);
    }

    @Override
    public void startIntentSenderFromChild(android.app.Activity activity0, android.content.IntentSender intentsender1, int int2, android.content.Intent intent3, int int4, int int5, int int6) throws IntentSender.SendIntentException {
        mOriginActivity.startIntentSenderFromChild(activity0, intentsender1, int2, intent3, int4, int5, int6);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void startIntentSenderFromChild(android.app.Activity activity0, android.content.IntentSender intentsender1, int int2, android.content.Intent intent3, int int4, int int5, int int6, android.os.Bundle bundle7) throws IntentSender.SendIntentException {
        mOriginActivity.startIntentSenderFromChild(activity0, intentsender1, int2, intent3, int4, int5, int6, bundle7);
    }

    @Override
    public void startIntentSenderFromFragment(android.support.v4.app.Fragment fragment0, android.content.IntentSender intentsender1, int int2, android.content.Intent intent3, int int4, int int5, int int6, android.os.Bundle bundle7) throws IntentSender.SendIntentException {
        mOriginFragmentActivity.startIntentSenderFromFragment(fragment0, intentsender1, int2, intent3, int4, int5, int6, bundle7);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void startLocalVoiceInteraction(android.os.Bundle bundle0) {
        mOriginActivity.startLocalVoiceInteraction(bundle0);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void startLockTask() {
        mOriginActivity.startLockTask();
    }

    @Override
    public void startManagingCursor(android.database.Cursor cursor0) {
        mOriginActivity.startManagingCursor(cursor0);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void startPostponedEnterTransition() {
        mOriginActivity.startPostponedEnterTransition();
    }

    @Override
    public void startSearch(java.lang.String string0, boolean boolean1, android.os.Bundle bundle2, boolean boolean3) {
        mOriginActivity.startSearch(string0, boolean1, bundle2, boolean3);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void stopLocalVoiceInteraction() {
        mOriginActivity.stopLocalVoiceInteraction();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void stopLockTask() {
        mOriginActivity.stopLockTask();
    }

    @Override
    public void stopManagingCursor(android.database.Cursor cursor0) {
        mOriginActivity.stopManagingCursor(cursor0);
    }

    @Override
    public void supportFinishAfterTransition() {
        mOriginFragmentActivity.supportFinishAfterTransition();
    }

    @Override
    public void supportInvalidateOptionsMenu() {
        mOriginFragmentActivity.supportInvalidateOptionsMenu();
    }

    @Override
    public void supportPostponeEnterTransition() {
        mOriginFragmentActivity.supportPostponeEnterTransition();
    }

    @Override
    public void supportStartPostponedEnterTransition() {
        mOriginFragmentActivity.supportStartPostponedEnterTransition();
    }

    @Override
    public void takeKeyEvents(boolean boolean0) {
        mOriginActivity.takeKeyEvents(boolean0);
    }

    @Override
    public void triggerSearch(java.lang.String string0, android.os.Bundle bundle1) {
        mOriginActivity.triggerSearch(string0, bundle1);
    }

    @Override
    public void unregisterForContextMenu(android.view.View view0) {
        mOriginActivity.unregisterForContextMenu(view0);
    }

}
