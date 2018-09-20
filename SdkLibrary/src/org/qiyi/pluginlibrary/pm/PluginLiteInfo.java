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
package org.qiyi.pluginlibrary.pm;

import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 存放插件最基本的信息：
 * 安装前的路径（必须有)
 * 包名(必须有)
 * 版本号(可以为空)
 * 灰度版本号(可以为空)
 * 安装位置(安装完成后，将安装路径放在此字段返回给调用者)
 * apk包AndroidManifest配置的包名
 * apk包AndroidManifest配置的版本号
 */
public class PluginLiteInfo implements Parcelable {
    public static final String PLUGIN_INSTALLED = "installed";
    public static final String PLUGIN_UNINSTALLED = "uninstall";
    public static final String PLUGIN_UPGRADING = "upgrading";

    /* 插件apk路径（安装前) */
    public String mPath;
    /* 插件包名(后端吐的) */
    public String packageName;
    /* 插件的安装后路径 */
    public String srcApkPath;
    /* 插件的安装状态 */
    public String installStatus;
    /* 插件的正式版本 */
    public String pluginVersion = "";
    /* 插件的灰度版本 */
    public String pluginGrayVersion = "";
    /* 插件唯一标识符 */
    public String id = "";
    /* 控制插件启动是否投递 */
    public int mDeliverStartUp;
    /* 插件安装apk里的包名 */
    public String srcApkPkgName;
    /* 插件安装apk里的版本号 */
    public String srcApkVersion = "";
    /* 是否启动插件的进程恢复功能，(由于插件可能未适配，需要这个开关) */
    public boolean enableRecovery;
    /* 该插件的依赖插件包名列表, 用逗号分隔*/
    public String plugin_refs;

    public static final Creator<PluginLiteInfo> CREATOR = new Creator<PluginLiteInfo>() {
        @Override
        public PluginLiteInfo createFromParcel(Parcel in) {
            return new PluginLiteInfo(in);
        }

        @Override
        public PluginLiteInfo[] newArray(int size) {
            return new PluginLiteInfo[size];
        }
    };

    public PluginLiteInfo() {

    }

    public PluginLiteInfo(String json) {
        try {
            JSONObject jObj = new JSONObject(json);
            mPath = jObj.optString("mPath");
            packageName = jObj.optString("pkgName");
            installStatus = jObj.optString("installStatus");
            pluginVersion = jObj.optString("plugin_ver");
            pluginGrayVersion = jObj.optString("plugin_gray_ver");
            id = jObj.optString("plugin_id");
            mDeliverStartUp = jObj.optInt("deliver_startup");
            srcApkPath = jObj.optString("srcApkPath");
            srcApkPkgName = jObj.optString("srcPkgName");
            srcApkVersion = jObj.optString("srcApkVer");
            enableRecovery = jObj.optBoolean("enableRecovery");
            plugin_refs = jObj.optString("plugin_refs");
        } catch (JSONException e) {
            // ignore
        }
    }

    protected PluginLiteInfo(Parcel in) {
        mPath = in.readString();
        packageName = in.readString();
        srcApkPath = in.readString();
        installStatus = in.readString();
        pluginVersion = in.readString();
        pluginGrayVersion = in.readString();
        id = in.readString();
        mDeliverStartUp = in.readInt();
        srcApkPkgName = in.readString();
        srcApkVersion = in.readString();
        enableRecovery = in.readInt() == 1;
        plugin_refs = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(mPath);
        parcel.writeString(packageName);
        parcel.writeString(srcApkPath);
        parcel.writeString(installStatus);
        parcel.writeString(pluginVersion);
        parcel.writeString(pluginGrayVersion);
        parcel.writeString(id);
        parcel.writeInt(mDeliverStartUp);
        parcel.writeString(srcApkPkgName);
        parcel.writeString(srcApkVersion);
        parcel.writeInt(enableRecovery ? 1 : 0);
        parcel.writeString(plugin_refs);
    }


    public String toJson() {
        JSONObject jObj = new JSONObject();
        try {
            jObj.put("mPath", mPath);
            jObj.put("pkgName", packageName);
            jObj.put("installStatus", installStatus);
            jObj.put("plugin_ver", pluginVersion);
            jObj.put("plugin_gray_ver", pluginGrayVersion);
            jObj.put("plugin_id", id);
            jObj.put("deliver_startup", mDeliverStartUp);
            jObj.put("srcApkPath", srcApkPath);
            jObj.put("srcPkgName", srcApkPkgName);
            jObj.put("srcApkVer", srcApkVersion);
            jObj.put("enableRecovery", enableRecovery);
            jObj.put("plugin_refs", plugin_refs);
        } catch (JSONException e) {
            // ignore
        }
        return jObj.toString();
    }

    @Override
    public String toString() {
        return "mPath=" + mPath + ", packageName=" + packageName
                + ", srcApkPath=" + srcApkPath + ", installStatus=" + installStatus
                + ", version=" + pluginVersion + ", grayVersion=" + pluginGrayVersion
                + ", srcApkPkgName=" + srcApkPkgName + ", srcApkVersion=" + srcApkVersion
                + ", enableRecovery=" + enableRecovery + ", plugin_refs=[" + plugin_refs + "]";
    }
}
