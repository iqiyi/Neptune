package com.iqiyi.host.sample;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.qiyi.pluginlibrary.Neptune;
import org.qiyi.pluginlibrary.install.IInstallCallBack;
import org.qiyi.pluginlibrary.pm.IPluginUninstallCallBack;
import org.qiyi.pluginlibrary.pm.PluginLiteInfo;
import org.qiyi.pluginlibrary.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private static final int REQUEST_STORAGE_CODE = 100;

    private static final String SAMPLE_PLUGIN_PKG = "com.iqiyi.plugin.sample";

    @BindView(R.id.plugin_state) TextView pluginState;

    @BindView(R.id.install_plugin) Button installPlugin;
    @BindView(R.id.launch_plugin) Button launchPlugin;
    @BindView(R.id.uninstall_plugin) Button uninstallPlugin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "request WRITE_EXTERNAL_STORAGE permission");
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE_CODE);
        }

        updatePluginState();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (REQUEST_STORAGE_CODE == requestCode) {
            if (grantResults.length > 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "WRITE_EXTERNAL_STORAGE permission granted");
            } else {
                Log.w(TAG, "WRITE_EXTERNAL_STORAGE permission rejected");
            }
        }
    }

    private void updatePluginState() {
        boolean installed = Neptune.isPackageInstalled(this, SAMPLE_PLUGIN_PKG);
        pluginState.setText(getString(R.string.plugin_state, installed ? "已安装" : "未安装"));
    }

    /**
     * 安装插件
     */
    @OnClick(R.id.install_plugin) void installPlugin() {

        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            Toast.makeText(this, "sdcard was NOT MOUNTED!", Toast.LENGTH_SHORT).show();
            return;
        }

        String apkName = "com.iqiyi.plugin.sample.apk";
        File sampleApk = new File(Environment.getExternalStorageDirectory(), apkName);
        if (sampleApk.exists()) {
            installPluginInternal(sampleApk.getAbsolutePath());
        } else {
            Log.w(TAG, "sample plugin not exist in sdcard, try install from asset");
            File targetFile = new File(getFilesDir(), apkName);
            try {
                InputStream is = getAssets().open("pluginapp/" + apkName);
                FileUtils.copyToFile(is, targetFile);

                installPluginInternal(targetFile.getAbsolutePath());
            } catch (IOException e) {
                Toast.makeText(this, "sample plugin not found in asset", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void installPluginInternal(String apkPath) {

        Neptune.install(this, apkPath, new IInstallCallBack.Stub() {
            @Override
            public void onPackageInstalled(PluginLiteInfo info) throws RemoteException {
                Toast.makeText(MainActivity.this, "sample plugin install success", Toast.LENGTH_SHORT).show();
                updatePluginState();
            }

            @Override
            public void onPackageInstallFail(PluginLiteInfo info, int failReason) throws RemoteException {
                Toast.makeText(MainActivity.this, "sample plugin install failed", Toast.LENGTH_SHORT).show();
                updatePluginState();
            }
        });
    }

    /**
     * 启动插件
     */
    @OnClick(R.id.launch_plugin) void launchPlugin() {

        Intent intent = new Intent();
        ComponentName cn = new ComponentName(SAMPLE_PLUGIN_PKG, "");  // 启动默认入口Activity
        intent.setComponent(cn);
        if (Neptune.isPackageInstalled(this, SAMPLE_PLUGIN_PKG)) {
            Neptune.launchPlugin(this, intent);
        } else {
            Toast.makeText(this, "sample plugin not installed", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 卸载插件
     */
    @OnClick(R.id.uninstall_plugin) void uninstallPlugin() {
        Neptune.uninstall(this, SAMPLE_PLUGIN_PKG, new IPluginUninstallCallBack.Stub() {
            @Override
            public void onPluginUninstall(String packageName, int resultCode) throws RemoteException {
                Toast.makeText(MainActivity.this, "sample plugin uninstall success", Toast.LENGTH_SHORT).show();
                updatePluginState();
            }
        });
    }
}
