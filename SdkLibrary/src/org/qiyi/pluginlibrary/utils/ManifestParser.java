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
package org.qiyi.pluginlibrary.utils;

import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.XmlResourceParser;
import android.os.PatternMatcher;
import android.text.TextUtils;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 从AndroidManifest.xml中解析出Activity、Service、Receiver相关的组件信息及其Intent-Filter信息
 * 方便实现插件Intent的隐式查找，注册静态广播
 * 替换系统@hide 的PackageParser#parsePackage()方法
 */
public class ManifestParser {
    private static final String TAG = "ManifestParser";

    private static final String ANDROID_RESOURCES = "http://schemas.android.com/apk/res/android";
    private static final String ANDROID_MANIFEST_FILENAME = "AndroidManifest.xml";
    public List<ComponentBean> activities = new ArrayList<>();
    public List<ComponentBean> services = new ArrayList<>();
    public List<ComponentBean> receivers = new ArrayList<>();
    public List<ComponentBean> providers = new ArrayList<>();
    private String pkg;
    private String applicationClass;

    /**
     * 通过AssetManager创建XmlResourceParser，解析AndroidManifest
     *
     * @param context
     * @param apkPath
     */
    public ManifestParser(Context context, String apkPath) {

        final PackageManager pm = context.getPackageManager();
        PackageInfo pi = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_ACTIVITIES);
        pi.applicationInfo.sourceDir = apkPath;
        pi.applicationInfo.publicSourceDir = apkPath;

        try {
            AssetManager am = AssetManager.class.newInstance();
            Method addAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
            addAssetPath.setAccessible(true);
            int cookie = (int) addAssetPath.invoke(am, apkPath);

            XmlResourceParser parser = am.openXmlResourceParser(cookie, ANDROID_MANIFEST_FILENAME);
            parseManifest(parser);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void skipCurrentTag(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG
                || parser.getDepth() > outerDepth)) {
            // go on
        }
    }

    /**
     * 解析Manifest节点
     */
    private void parseManifest(XmlResourceParser parser) throws IOException, XmlPullParserException {
        int type;
        while ((type = parser.next()) != XmlPullParser.START_TAG
                && type != XmlPullParser.END_DOCUMENT) {
            // go on to find START_TAG
        }

        if (type != XmlPullParser.START_TAG) {
            throw new XmlPullParserException("No start tag found");
        }
        if (!parser.getName().equals("manifest")) {
            throw new XmlPullParserException("No <manifest> tag");
        }

        pkg = parser.getAttributeValue(null, "package");
        Log.i(TAG, "parsed packageName=" + pkg);

        int outerDepth = parser.getDepth();
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            String tagName = parser.getName();
            if (tagName.equals("application")) {
                // found Application Tag
                parseApplication(parser);
            }
        }
    }

    /**
     * 解析Application节点
     */
    private void parseApplication(XmlResourceParser parser) throws IOException, XmlPullParserException {

        String name = parser.getAttributeValue(ANDROID_RESOURCES, "name");
        applicationClass = buildClassName(pkg, name);

        final int innerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > innerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            String tagName = parser.getName();
            if (tagName.equals("activity")) {
                // Activity Info
                ComponentBean activity = parseComponent(parser);
                activities.add(activity);
            } else if (tagName.equals("receiver")) {
                // Recevier Info
                ComponentBean receiver = parseComponent(parser);
                receivers.add(receiver);
            } else if (tagName.equals("service")) {
                // Service Info
                ComponentBean service = parseComponent(parser);
                services.add(service);
            } else if (tagName.equals("provider")) {
                // Providers
                ComponentBean provider = parseComponent(parser);
                providers.add(provider);
            }
        }
    }

    /**
     * 解析Activity、Service、Recevier等组件节点
     */
    private ComponentBean parseComponent(XmlResourceParser parser) throws IOException, XmlPullParserException {
        ComponentBean bean = new ComponentBean();

        String name = parser.getAttributeValue(ANDROID_RESOURCES, "name");
        bean.className = buildClassName(pkg, name);

        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG
                || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            String tagName = parser.getName();
            if (tagName.equals("intent-filter")) {
                IntentFilter intentFilter = parseIntent(parser);
                if (intentFilter != null) {
                    bean.intentFilters.add(intentFilter);
                }
            }
        }

        return bean;
    }

    /**
     * 解析IntentFilter节点
     */
    private IntentFilter parseIntent(XmlResourceParser parser) throws IOException, XmlPullParserException {
        IntentFilter intentFilter = new IntentFilter();
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            String nodeName = parser.getName();
            if (nodeName.equals("action")) {
                String value = parser.getAttributeValue(ANDROID_RESOURCES, "name");
                if (!TextUtils.isEmpty(value)) {
                    intentFilter.addAction(value);
                }

                skipCurrentTag(parser);
            } else if (nodeName.equals("category")) {
                String value = parser.getAttributeValue(ANDROID_RESOURCES, "name");
                if (!TextUtils.isEmpty(value)) {
                    intentFilter.addCategory(value);
                }

                skipCurrentTag(parser);
            } else if (nodeName.equals("data")) {
                // mimeType
                String str = parser.getAttributeValue(ANDROID_RESOURCES, "mimeType");
                if (!TextUtils.isEmpty(str)) {
                    try {
                        intentFilter.addDataType(str);
                    } catch (IntentFilter.MalformedMimeTypeException e) {
                        e.printStackTrace();
                    }
                }
                // scheme
                str = parser.getAttributeValue(ANDROID_RESOURCES, "scheme");
                if (!TextUtils.isEmpty(str)) {
                    intentFilter.addDataScheme(str);
                }
                // host, port
                String host = parser.getAttributeValue(ANDROID_RESOURCES, "host");
                String port = parser.getAttributeValue(ANDROID_RESOURCES, "port");
                if (!TextUtils.isEmpty(host)) {
                    intentFilter.addDataAuthority(host, port);
                }
                // path, pathPattern, pathPrefix
                str = parser.getAttributeValue(ANDROID_RESOURCES, "path");
                if (!TextUtils.isEmpty(str)) {
                    intentFilter.addDataPath(str, PatternMatcher.PATTERN_LITERAL);
                }

                str = parser.getAttributeValue(ANDROID_RESOURCES, "pathPrefix");
                if (!TextUtils.isEmpty(str)) {
                    intentFilter.addDataPath(str, PatternMatcher.PATTERN_PREFIX);
                }

                str = parser.getAttributeValue(ANDROID_RESOURCES, "pathPattern");
                if (!TextUtils.isEmpty(str)) {
                    intentFilter.addDataPath(str, PatternMatcher.PATTERN_SIMPLE_GLOB);
                }

                skipCurrentTag(parser);
            }
        }
        return intentFilter;
    }

    private String buildClassName(String pkg, CharSequence clsSeq) {
        if (clsSeq == null || clsSeq.length() <= 0) {
            return "";
        }
        String cls = clsSeq.toString();
        char c = cls.charAt(0);
        if (c == '.') {
            return pkg + cls;
        }
        if (cls.indexOf('.') < 0) {
            StringBuilder b = new StringBuilder(pkg);
            b.append('.');
            b.append(cls);
            return b.toString();
        }
        return cls;
    }

    public static class ComponentBean {
        public String className;
        public List<IntentFilter> intentFilters = new ArrayList<>();

        @Override
        public String toString() {
            return String.format("{name:%s, intent-filter.size():%s}", className, intentFilters.size());
        }
    }
}

