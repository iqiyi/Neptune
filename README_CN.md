# Neptune

![license](http://img.shields.io/badge/license-Apache2.0-brightgreen.svg?style=flat)
![Release Version](https://img.shields.io/badge/release-2.5.0-red.svg)
![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)

**Neptune是一套灵活，稳定，轻量级的插件化方案。**

它现在每天在数亿的设备上动态加载和运行插件，支撑着爱奇艺许多独立业务模块的需求和发展，如爱奇艺文学，电影票等。

Neptune现在完全兼容Android P系统，可以在Android P设备上稳定且无缝地运行。框架只使用了少数几个浅灰名单中的API。

# 支持的特性

| 特性 | 描述  |
| :------ | :-----: |
| 组件 | Activity/Service/Receiver |
| 主程序Manifest注册 | 不需要 |
| 共享宿主代码 | 支持 |
| 共享宿主资源 | 支持 |
| 资源隔离 | 支持 |
| 独立运行插件 | 支持 |
| Android特性 | 支持几乎所有 |
| 兼容性  | 几乎市面上所有ROM |
| 进程隔离 | 支持 |
| 插件之间相互依赖  | 支持 |
| 插件开发  | 接近原生APP开发 |
| 支持的Android版本 | API Level 14+ |

# Architecture

![plugin_arch](plugin_arch.png)

# Getting Started

## Host Project

在App模块的`build.gradle`中compile移入Neptune库

```Gradle
    implementation 'org.qiyi.video:neptune:2.5.0'
```

在`Application#onCreate()`阶段初始化Neptune

```Java
public class XXXApplication extends Application {
    
    @Override
    public void onCreate() {
        NeptuneConfig config = new NeptuneConfig.NeptuneConfigBuilder()
                    .configSdkMode(NeptuneConfig.INSTRUMENTATION_MODE)
                    .enableDebug(BuildConfig.DEBUG)
                    .build();
        Neptune.init(this, config);
    }
}
```

更多细节和开发指南请参考wiki。

## Plugin Project

如果插件APP需要共享宿主APP的一些资源，你需要在插件工程根目录下的`build.gradle`中的`buildscript`块中添加如下依赖

```Gradle
dependencies {
    classpath  'com.iqiyi.tools.build:neptune-gradle:1.1.6'
}
```

在App模块的`build.gradle`中应用gradle插件并添加相应配置

```Gradle
apply plugin: 'com.qiyi.neptune.plugin'

neptune {
    pluginMode = true      // In plugin apk build mode
    packageId = 0x30       // The package id of Resources
    hostDependencies = "{group1}:{artifact1};{group2}:{artifact2}" // host app resources dependencies
}
```

# Developer Guide

* [API文档见wiki](https://github.com/iqiyi/Neptune/wiki)
* [宿主APP的示例工程](samples/HostApp)
* [插件APP的示例工程](samples/PluginApp)
* [阅读SDKLibrary的源码](SdkLibrary)

# Contribution

我们真诚地欢迎任何有价值的PR提交，包括代码，建议和文档。

# License

Neptune is [Apache v2.0 Licensed](LICENSE.md).

