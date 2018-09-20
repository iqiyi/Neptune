/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.qiyi.pluginlibrary.utils;

import android.content.Context;
import android.content.SharedPreferences;

import org.qiyi.pluginlibrary.Neptune;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;


class MultiDexExtractor {

    static final String EXTRACTED_SUFFIX = ".zip";
    private static final String TAG = "MultiDexExtractor";
    /* Keep value away from 0 because it is a too probable time stamp value */
    private static final long NO_VALUE = -1L;

    private static final String EXTRACTED_NAME_EXT = ".classes";
    private static final int MAX_EXTRACT_ATTEMPTS = 3;
    private static final String PREFS_FILE = "plugin.multidex.version";
    private static final String KEY_TIME_STAMP = "timestamp";
    private static final String KEY_CRC = "crc";
    private static final String KEY_DEX_NUMBER = "dex.number";
    private static final String KEY_DEX_CRC = "dex.crc.";
    private static final String KEY_DEX_TIME = "dex.time.";
    private final String pkgName;
    private final File sourceApk;
    private final long sourceCrc;
    private final File dexDir;

    /**
     * Zip file containing one secondary dex file.
     */
    private static class ExtractedDex extends File {
        public long crc = NO_VALUE;

        public ExtractedDex(File dexDir, String fileName) {
            super(dexDir, fileName);
        }
    }

    MultiDexExtractor(String pkgName, File sourceApk, File dexDir) {
        this.pkgName = pkgName;
        this.sourceApk = sourceApk;
        this.dexDir = dexDir;
        sourceCrc = getZipCrc(sourceApk);
    }

    /**
     * 校验Zip包是否被修改
     */
    private static boolean isModified(File archive, long currentCrc,
                                      String prefsKeyPrefix) {
        SharedPreferences prefs = getMultiDexPreferences();
        return (prefs.getLong(prefsKeyPrefix + KEY_TIME_STAMP, NO_VALUE) != getTimeStamp(archive))
                || (prefs.getLong(prefsKeyPrefix + KEY_CRC, NO_VALUE) != currentCrc);
    }

    private static SharedPreferences getMultiDexPreferences() {
        return Neptune.getHostContext().getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
    }

    private static long getTimeStamp(File archive) {
        long timeStamp = archive.lastModified();
        if (timeStamp == NO_VALUE) {
            // never return NO_VALUE
            timeStamp--;
        }
        return timeStamp;
    }

    private static long getZipCrc(File archive) {
        long crc = 0;
        try {
            crc = FileUtils.getZipCrc(archive);
        } catch (IOException e) {
            crc = NO_VALUE;
        }
        if (crc == NO_VALUE) {
            crc--;
        }
        return crc;
    }

    private static void clearDexDir(File dexDir) {
        if (dexDir.isDirectory()) {
            File[] files = dexDir.listFiles();
            if (files == null) {
                return;
            }
            for (File oldFile : files) {
                if (!oldFile.delete()) {
                    PluginDebugLog.warningLog(TAG, "Failed to delete old dex file " + oldFile.getPath());
                }
            }
            if (!dexDir.delete()) {
                PluginDebugLog.warningLog(TAG, "Failed to delete secondary dex dir " + dexDir.getPath());
            }
        }
    }

    /**
     * 解压apk里的多余的Dex文件
     */
    List<? extends File> load(boolean forceLoad) throws IOException {

        List<ExtractedDex> dexFiles;
        String prefsKeyPrefix = pkgName + ".";
        if (!forceLoad && !isModified(sourceApk, sourceCrc, prefsKeyPrefix)) {
            try {
                dexFiles = loadExistingExtractions(prefsKeyPrefix);
            } catch (IOException ioe) {
                PluginDebugLog.runtimeLog(TAG, "Failed to reload existing extracted secondary dex files,"
                        + " falling back to fresh extraction" + ioe.getMessage());
                dexFiles = performExtractions();
                storeApkInfo(prefsKeyPrefix, getTimeStamp(sourceApk), sourceCrc, dexFiles);
            }
        } else {
            dexFiles = performExtractions();
            storeApkInfo(prefsKeyPrefix, getTimeStamp(sourceApk), sourceCrc, dexFiles);
        }

        return dexFiles;
    }

    /**
     * 加载提前释放好的Dex文件
     */
    private List<ExtractedDex> loadExistingExtractions(String prefsKeyPrefix) throws IOException {
        PluginDebugLog.runtimeLog(TAG, "loading existing secondary dex files for plugin: " + pkgName);

        final String extractedDexPrefix = sourceApk.getName() + EXTRACTED_NAME_EXT;
        SharedPreferences prefs = getMultiDexPreferences();
        int totalDexNumber = prefs.getInt(prefsKeyPrefix + KEY_DEX_NUMBER, 1);
        final List<ExtractedDex> files = new ArrayList<>(totalDexNumber - 1);

        for (int secondaryNumber = 2; secondaryNumber <= totalDexNumber; secondaryNumber++) {
            // xxx.apk.classes.N.zip
            String fileName = extractedDexPrefix + secondaryNumber + EXTRACTED_SUFFIX;
            ExtractedDex dexFile = new ExtractedDex(dexDir, fileName);
            if (dexFile.isFile() && dexFile.exists()) {
                // find existing dexFile, verify
                dexFile.crc = getZipCrc(dexFile);
                long lastModified = dexFile.lastModified();
                long expectedCrc = prefs.getLong(prefsKeyPrefix + KEY_DEX_CRC + secondaryNumber, NO_VALUE);
                long expectedModTime = prefs.getLong(prefsKeyPrefix + KEY_DEX_TIME + secondaryNumber, NO_VALUE);
                if ((expectedModTime != lastModified) || (expectedCrc != dexFile.crc)) {
                    PluginDebugLog.runtimeLog(TAG, "Invalid extracted dex, file has changed");
                    throw new IOException("Invalid extracted dex: " + dexFile +
                            " (key \"" + prefsKeyPrefix + "\"), expected modification time: "
                            + expectedModTime + ", modification time: "
                            + lastModified + ", expected crc: "
                            + expectedCrc + ", file crc: " + dexFile.crc
                            + ", plugin: " + pkgName);
                }
                // add it
                files.add(dexFile);
            } else {
                throw new IOException("Missing extracted secondary dex file '" +
                        dexFile.getPath() + "'");
            }
        }
        return files;
    }

    /**
     * 解压apk中的dex文件到指定目录下
     */
    private List<ExtractedDex> performExtractions() throws IOException {
        final String extractedDexPrefix = sourceApk.getName() + EXTRACTED_NAME_EXT;
        // clear already exist dex files
        clearDexDir(dexDir);

        List<ExtractedDex> files = new ArrayList<>();
        final ZipFile apkFile = new ZipFile(sourceApk);
        try {
            int secondaryNumber = 2;
            String entryName = "classes" + secondaryNumber + ".dex";
            ZipEntry dexEntry = apkFile.getEntry(entryName);
            while (dexEntry != null) {
                // extractd dexFile:  xxx.apk.classes.N.zip
                String fileName = extractedDexPrefix + secondaryNumber + EXTRACTED_SUFFIX;
                ExtractedDex dexFile = new ExtractedDex(dexDir, fileName);
                files.add(dexFile);

                int numAttempts = 0;
                boolean extractSuccess = false;
                while (numAttempts < MAX_EXTRACT_ATTEMPTS && !extractSuccess) {
                    numAttempts++;
                    // Read zip crc of extracted dex
                    try {
                        // Create a zip file (extractedFile) containing only the secondary dex file
                        // (dexFile) from the apk.
                        extractDex2Zip(apkFile, dexEntry, dexFile);

                        dexFile.crc = getZipCrc(dexFile);
                        extractSuccess = true;
                    } catch (IOException e) {
                        extractSuccess = false;
                    }

                    if (!extractSuccess) {
                        dexFile.delete();
                    }
                }

                if (!extractSuccess) {
                    throw new IOException("Could not create zip file " +
                            dexFile.getAbsolutePath() + " for secondary dex (" +
                            secondaryNumber + ")");
                }

                secondaryNumber++;
                entryName = "classes" + secondaryNumber + ".dex";
                dexEntry = apkFile.getEntry(entryName);
            }
        } finally {
            FileUtils.closeQuietly(apkFile);
        }

        return files;
    }

    /**
     * 从Apk中解压出一个Dex文件，重新生成一个Zip，输入到DexClassLoader
     */
    private void extractDex2Zip(ZipFile apk, ZipEntry dexFile, File outDex) throws IOException {

        InputStream in = apk.getInputStream(dexFile);
        ZipOutputStream out = null;
        // create tmp file
        File tmp = new File(outDex.getParentFile(), outDex.getName() + ".tmp");
        try {
            out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)));
            try {
                ZipEntry classesDex = new ZipEntry("classes.dex");
                // keep zip entry time since it is the criteria used by Dalvik
                classesDex.setTime(dexFile.getTime());
                out.putNextEntry(classesDex);

                byte[] buffer = new byte[4096];
                int length = -1;
                while ((length = in.read(buffer)) != -1) {
                    out.write(buffer, 0, length);
                }
                out.closeEntry();
            } finally {
                out.close();
            }
            if (!tmp.setReadOnly()) {
                throw new IOException("Failed to mark readonly \"" + tmp.getAbsolutePath() +
                        "\" (tmp of \"" + outDex.getAbsolutePath() + "\")");
            }
            if (!tmp.renameTo(outDex)) {
                throw new IOException("Failed to rename \"" + tmp.getAbsolutePath() +
                        "\" to \"" + outDex.getAbsolutePath() + "\"");
            }
        } finally {
            FileUtils.closeQuietly(in);
            tmp.delete();
        }
    }

    /**
     * 保存apk和dex相关校验信息
     */
    private void storeApkInfo(String keyPrefix, long timeStamp, long crc, List<ExtractedDex> dexFiles) {
        SharedPreferences prefs = getMultiDexPreferences();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(keyPrefix + KEY_TIME_STAMP, timeStamp);
        editor.putLong(keyPrefix + KEY_CRC, crc);
        editor.putInt(keyPrefix + KEY_DEX_NUMBER, dexFiles.size() + 1);

        int dexNumber = 2;
        for (ExtractedDex dex : dexFiles) {
            editor.putLong(keyPrefix + KEY_DEX_CRC + dexNumber, dex.crc);
            editor.putLong(keyPrefix + KEY_DEX_TIME + dexNumber, dex.lastModified());
            dexNumber++;
        }
        editor.commit();
    }
}
