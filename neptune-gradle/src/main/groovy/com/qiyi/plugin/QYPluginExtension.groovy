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
    /** Variant application id */
    String packageName
    /** Package path for java classes */
    String packagePath
    /** Variant version name */
    String versionName
    /** File of split R.java */
    File splitRJavaFile
    /** Host App package name */
    String hostPackageName
    /** Host App supported abi */
    String hostAbi = "armeabi"
    /** host Symbol file - R.txt */
    File hostSymbolFile
    /** host dependence - aar module*/
    String hostDependencies
    /** Modify class before dex */
    boolean dexModify = false
    /** deeplink to enter plugin home page supported in host app */
    String enterPluginLink
}
