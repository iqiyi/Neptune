package com.iqiyi.host.sample;

import android.app.Application;

import org.qiyi.pluginlibrary.Neptune;
import org.qiyi.pluginlibrary.NeptuneConfig;


public class HostApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        initPluginFramework();
    }


    private void initPluginFramework() {
        NeptuneConfig config = new NeptuneConfig.Builder()
                .configSdkMode(NeptuneConfig.INSTRUMENTATION_MODE)
                .enableDebug(true)
                .build();
        Neptune.init(this, config);
    }
}
