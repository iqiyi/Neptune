// IPluginPackageManager.aidl
package org.qiyi.pluginlibrary.pm;
import org.qiyi.pluginlibrary.pm.PluginLiteInfo;
import org.qiyi.pluginlibrary.install.IInstallCallBack;
import org.qiyi.pluginlibrary.install.IActionFinishCallback;
import org.qiyi.pluginlibrary.pm.IPluginUninstallCallBack;
import org.qiyi.pluginlibrary.pm.PluginPackageInfo;

interface IPluginPackageManager {

    List<PluginLiteInfo> getInstalledApps();

    PluginLiteInfo getPackageInfo(String pkg);

    boolean isPackageInstalled(String pkg);

    boolean canInstallPackage(in PluginLiteInfo info);

    boolean canUninstallPackage(in PluginLiteInfo info);

    oneway void install(in PluginLiteInfo info, IInstallCallBack listener);

    boolean uninstall(in PluginLiteInfo info);

    oneway void packageAction(in PluginLiteInfo info, IInstallCallBack callBack);

    oneway void setActionFinishCallback(IActionFinishCallback actionFinishCallback);

    PluginPackageInfo getPluginPackageInfo(in String pkgName);

    List<String> getPluginRefs(in String pkgName);
}
