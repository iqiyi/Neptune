// IUninstallCallBack.aidl
package org.qiyi.pluginlibrary.install;
import org.qiyi.pluginlibrary.pm.PluginLiteInfo;
interface IUninstallCallBack {
    /**
     * 卸载插件回调
     *
     * @param info
     *            插件基础信息
     */
    void onPackageUninstalled(in PluginLiteInfo info, int resultCode);
}
