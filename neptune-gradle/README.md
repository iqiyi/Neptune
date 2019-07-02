# Neptune Gradle Plugin

A Gradle Plugin for Plugin Compile to Strip Resources in Host App and Reassign Plugin Package Id to Resolve Resource Ids Conflict Issue.

# Usage

**Step1**: Add the following content to your root project's `build.gradle` file

```gradle
buildscript {
    repositories {
         google()
         jcenter()
    }
    
    dependencies {
            classpath 'com.android.tools.build:gradle:3.4.0'
            classpath 'com.iqiyi.tools.build:neptune-gradle:1.3.0'  // Add the plugin classpath
        }
}
```

**Step2**: Apply the plugin and config the plugin parameters in your app module's `build.gradle` file

```gradle
apply plugin: 'com.qiyi.neptune.plugin'

neptune {
   pluginMode = true   // default is true, for individual app compile, you can set it false
   packageId = 0x30    // plugin arsc package id
   hostDependencies = "com.qiyi.video.allclasses:commonres_lib"  // host app common resourece dependencies
}
```

**Step3**: Sync the project and execute `assembleDebug` or `assembleRelease` to build a plugin apk

# Principle

Android Resource Id is format as `PPTTNNNN`, PP represent the package id, TT represent the resource type, NNNN is increment by order.
By default, PP is 0x01 for system app and 0x7F for normal app, 0x02-0x7e is reserved, so we can modify the plugin apk PP and make it different from host app.

In Android Build System, task `process{variant.name}Resources` will call aapt to compile the resource, generate resources.arsc and R.java files.
So the gradle plugin will hook this task, and rewrite the arsc file to strip the resources in host app, and generate related new R.java files.
 