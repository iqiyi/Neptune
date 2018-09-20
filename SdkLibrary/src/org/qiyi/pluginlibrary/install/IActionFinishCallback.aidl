package org.qiyi.pluginlibrary.install;

interface IActionFinishCallback {

    void onActionComplete(String packageName, int errorCode);

    String getProcessName();
}
