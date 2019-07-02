package com.qiyi.plugin

import org.gradle.util.VersionNumber


class QYPluginExtension {
    /** Host or Plugin project compile */
    boolean pluginMode = true
    /** Android Gradle Plugin version */
    VersionNumber agpVersion
    /** strip resource */
    boolean stripResource = false
    /** Custom defined resource package Id */
    int packageId = 0x7f
    /** Host App package name */
    String hostPackageName
    /** Host App supported abi */
    String hostAbi = "armeabi"
    /** host Symbol file - R.txt */
    File hostSymbolFile
    /** host dependence - aar module*/
    String hostDependencies
    /** Modify class before dex */
    boolean useBaseActivity = false
    /** deeplink to enter plugin home page supported in host app */
    String enterPluginLink = ""
}
