// IPluginUninstallCallBack.aidl
package org.qiyi.pluginlibrary.pm;

interface IPluginUninstallCallBack {
    oneway void onPluginUninstall(String packageName, int resultCode);
}
