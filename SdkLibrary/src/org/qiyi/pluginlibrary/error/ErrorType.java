/*
 *
 * Copyright 2018 iQIYI.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.qiyi.pluginlibrary.error;

/**
 * 一些异常情况, 错误码定义
 */
public class ErrorType {

    /* 错误的原因 */
    public static final String ERROR_REASON = "error_reason";
    /* 成功 */
    public static final int SUCCESS = 0;
    public static final int SUCCESS_DOWNLOADED = 1;  //下载成功
    public static final int SUCCESS_INSTALLED = 2;   //安装成功
    public static final int SUCCESS_LOADED = 3;      //插件加载成功
    /* 插件下载，校验等错误，应用层定义，区间字段：0~3000 */

    /* 插件安装等错误，区间字段4000~5000*/
    /* 安装错误，asset下文件不存在 */
    public static final int INSTALL_ERROR_ASSET_APK_NOT_FOUND = 4000;
    /* 安装错误，插件apk不存在 */
    public static final int INSTALL_ERROR_APK_NOT_EXIST = 4001;
    /* 安装错误，插件apk读取IOException */
    public static final int INSTALL_ERROR_FILE_IOEXCEPTION = 4002;
    /* 安装错误，插件apk从Asset目录拷贝到内置存储区失败 */
    public static final int INSTALL_ERROR_ASSET_APK_COPY_FAILED = 4003;
    /* 安装错误，插件apk拷贝操作失败，比如安装到sdcard */
    public static final int INSTALL_ERROR_APK_COPY_FAILED = 4004;
    /* 安装错误，插件apk的路径无效 */
    public static final int INSTALL_ERROR_FILE_PATH_ILLEGAL = 4005;
    /* 安装错误，插件apk解析失败 */
    public static final int INSTALL_ERROR_APK_PARSE_FAILED = 4006;
    /* 安装错误，插件包名与apk里包名不一致 */
    public static final int INSTALL_ERROR_PKG_NAME_NOT_MATCH = 4007;
    /* 安装错误，插件重命名失败 */
    public static final int INSTALL_ERROR_RENAME_FAILED = 4008;
    /* 安装错误，插件安装目录创建失败 */
    public static final int INSTALL_ERROR_MKDIR_FAILED = 4009;
    /* 安装错误，插件apk未签名 */
    public static final int INSTALL_ERROR_APK_NO_SIGNATURE = 4010;
    /* 安装错误，插件apk签名不一致 */
    public static final int INSTALL_ERROR_APK_SIGNATURE_NOT_MATCH = 4011;
    /* 安装错误，安装so库不存在 */
    public static final int INSTALL_ERROR_SO_NOT_EXIST = 4100;
    /* 安装错误，安装so库拷贝失败 */
    public static final int INSTALL_ERROR_SO_COPY_FAILED = 4101;
    /* 安装错误，安装so库时解压失败 */
    public static final int INSTALL_ERROR_SO_UNZIP_FAILED = 4102;
    /* 安装错误，安装dex文件不存在 */
    public static final int INSTALL_ERROR_DEX_NOT_EXIST = 4200;
    /* 安装错误，安装dex文件拷贝失败 */
    public static final int INSTALL_ERROR_DEX_COPY_FAILED = 4201;
    /* 安装错误，远程Service超时 */
    public static final int INSTALL_ERROR_CLIENT_TIME_OUT = 4300;
    /* 安装错误，启动PluginInstallerService异常 */
    public static final int INSTALL_ERROR_BEFORE_START_SERVICE = 4301;



    /* 插件加载等错误, 区间字段5000~6000 */
    /* 插件未加载，未找到插件运行的环境，PluginLoadedApk未创建 */
    public static final int ERROR_PLUGIN_NOT_LOADED = 5000;
    /* 初始化插件Application，load类失败*/
    public static final int ERROR_PLUGIN_LOAD_APPLICATION = 5001;
    /* 初始化插件Application，调用attach方法失败 */
    public static final int ERROR_PLUGIN_APPLICATION_ATTACH_BASE = 5002;
    /* 初始化插件Application，调用onCreate时Crash */
    public static final int ERROR_PLUGIN_CREATE_APPLICATION = 5003;
    /* 启动插件组件时，加载类失败 */
    public static final int ERROR_PLUGIN_LOAD_COMP_CLASS = 5004;
    /* 初始化插件PluginLoadedApk时，解析插件apk信息出错 */
    public static final int ERROR_PLUGIN_PARSER_PACKAGE_INFO = 5005;
    /* 初始化插件PluginLoadedApk时，初始化资源出错 */
    public static final int ERROR_PLUGIN_INIT_RESOURCES = 5006;
    /* 初始化插件PluginLoadedApk时，创建ClassLoader出错 */
    public static final int ERROR_PLUGIN_CREATE_CLASSLOADER = 5007;
    /* 初始化插件PluginLoadedApk失败 */
    public static final int ERROR_PLUGIN_CREATE_LOADEDAPK = 5008;
    /* 加载插件时，没有解析到可以响应该Intent的插件包名 */
    public static final int ERROR_PLUGIN_LOAD_NO_PKGNAME_INTENT = 5009;
    /* 加载插件Service时类加载出错 */
    public static final int ERROR_PLUGIN_LOAD_TARGET_SERVICE = 5010;
    /* 加载插件Service时，调用onCreate出错 */
    public static final int ERROR_PLUGIN_CREATE_TARGET_SERVICE = 5011;
    /* hook获取Instrumentation失败*/
    public static final int ERROR_PLUGIN_HOOK_INSTRUMENTATION = 5012;
    /* InstrActivityProxy1 onCreate时从intent中解析包名失败，通常是由自定义序列化类导致 */
    public static final int ERROR_PLUGIN_GET_PKG_AND_CLS_FAIL = 5013;
    /* InstrActivityProxy1 加载插件Activity类失败 */
    public static final int ERROR_PLUGIN_LOAD_TARGET_ACTIVITY = 5014;
    /* InstrActivityProxy1 调用插件Activity的attach方法失败 */
    public static final int ERROR_PLUGIN_ACTIVITY_ATTACH_BASE = 5015;
    /* InstrActivityProxy1 调用插件Activity的onCreate方法失败 */
    public static final int ERROR_PLUGIN_CALL_ACTIVITY_ONCREATE = 5016;

    /* 加载插件时，插件没有安装 */
    public static final int ERROR_PLUGIN_LOAD_NOT_INSTALLED = 5017;
    /* 加载插件中的类失败，如ClassNotFound等 */
    public static final int ERROR_PLUGIN_LOAD_TARGET_CLASS = 5018;
    /* 加载插件中的类，创建类实例失败 */
    public static final int ERROR_PLUGIN_CREATE_CLASS_INSTANCE = 5019;
    /* 加载插件时，PluginLiteInfo对象没有找到 */
    public static final int ERROR_PLUGIN_LITEINFO_NOT_FOUND = 5020;
}
