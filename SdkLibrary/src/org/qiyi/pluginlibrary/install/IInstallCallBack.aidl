// IInstallCallBack.aidl
package org.qiyi.pluginlibrary.install;
import org.qiyi.pluginlibrary.pm.PluginLiteInfo;
interface IInstallCallBack {

    /**
     * 安装成功回调
     *
     * @param info
     *            插件基础信息
     */
    void onPackageInstalled(in PluginLiteInfo info);

    /**
     * 安装失败回调
     *
     * @param packageName
     *            插件基础信息
     * @param failReason
     *            失败原因
     */
    void onPackageInstallFail(in PluginLiteInfo info, int failReason);
}
