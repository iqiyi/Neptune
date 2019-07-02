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
package org.qiyi.pluginlibrary.install;

import android.text.TextUtils;
import android.util.Log;

import org.qiyi.pluginlibrary.utils.CpuAbiUtils;
import org.qiyi.pluginlibrary.utils.PluginDebugLog;
import org.qiyi.pluginlibrary.utils.VersionUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import dalvik.system.DexClassLoader;


public class DexOptimizer {
    public static final String ODEX_SUFFIX = ".odex";
    public static final String DEX_SUFFIX = ".dex";
    private static final String TAG = "DexOptimizer";

    public static boolean optimize(File dexFile, File optimizedDir,
                                   boolean useInterpretMode, ResultCallback cb) {
        String isa = CpuAbiUtils.getCurrentInstructionSet();
        OptimizeWorker worker = new OptimizeWorker(dexFile, optimizedDir, useInterpretMode, isa, cb);
        if (!worker.run()) {
            return false;
        }
        return true;


    }

    public interface ResultCallback {
        void onStart(File dexFile, File optimizedDir);

        void onSuccess(File dexFile, File optimizedDir, File optimizedFile);

        void onFailed(File dexFile, File optimizedDir, Throwable thr);
    }

    private static class OptimizeWorker {
        private final File dexFile;
        private final File optimizedDir;
        private final boolean useInterpretMode;
        private final ResultCallback callback;
        private final String targetISA;

        OptimizeWorker(File dexFile, File optimizedDir, boolean useInterpretMode, String targetISA, ResultCallback cb) {
            this.dexFile = dexFile;
            this.optimizedDir = optimizedDir;
            this.useInterpretMode = useInterpretMode;
            this.callback = cb;
            this.targetISA = targetISA;
        }

        public static String optimizedPathFor(File path, File optimizedDirectory, String isa) {
            if (VersionUtils.hasOreo()) {
                if (TextUtils.isEmpty(isa)) {
                    throw new RuntimeException("target isa is empty!,dex2oat fail!");
                }

                File parentFile = path.getParentFile();
                String fileName = path.getName();
                int index = fileName.lastIndexOf('.');
                if (index > 0) {
                    fileName = fileName.substring(0, index);
                }

                String result = parentFile.getAbsolutePath() + "/oat/"
                        + isa + "/" + fileName + ODEX_SUFFIX;
                return result;
            }


            String fileName = path.getName();
            if (!fileName.endsWith(DEX_SUFFIX)) {
                int lastDot = fileName.lastIndexOf(".");
                if (lastDot < 0) {
                    fileName += DEX_SUFFIX;
                } else {
                    StringBuilder sb = new StringBuilder(lastDot + 4);
                    sb.append(fileName, 0, lastDot);
                    sb.append(DEX_SUFFIX);
                    fileName = sb.toString();
                }
            }

            File result = new File(optimizedDirectory, fileName);
            return result.getPath();
        }

        private boolean isLegalFile(File file) {
            return file.exists() && file.canRead() && file.isFile() && file.length() > 0;
        }

        public boolean run() {
            try {
                if (!isLegalFile(dexFile)) {
                    if (callback != null) {
                        callback.onFailed(dexFile, optimizedDir,
                                new IOException("dex file " + dexFile.getAbsolutePath() + " is not exist!"));
                        return false;
                    }
                }
                if (callback != null) {
                    callback.onStart(dexFile, optimizedDir);
                }
                String optimizedPath = optimizedPathFor(this.dexFile, this.optimizedDir, targetISA);
                if (useInterpretMode) {
                    interpretDex2Oat(dexFile.getAbsolutePath(), optimizedPath);
                } else {
                    new DexClassLoader(dexFile.getAbsolutePath(), this.optimizedDir.getAbsolutePath(), null, this.getClass().getClassLoader());
                }
                if (callback != null) {
                    callback.onSuccess(dexFile, optimizedDir, new File(optimizedPath));
                }
            } catch (final Throwable e) {
                Log.e(TAG, "Failed to optimize dex: " + dexFile.getAbsolutePath(), e);
                if (callback != null) {
                    callback.onFailed(dexFile, optimizedDir, e);
                    return false;
                }
            }
            return true;
        }

        private void interpretDex2Oat(String dexFilePath, String oatFilePath) throws IOException {

            final File oatFile = new File(oatFilePath);
            if (!oatFile.exists()) {
                oatFile.getParentFile().mkdirs();
            }

            final List<String> commandAndParams = new ArrayList<>();
            commandAndParams.add("dex2oat");
            if (VersionUtils.hasNougat()) {
                commandAndParams.add("--runtime-arg");
                commandAndParams.add("-classpath");
                commandAndParams.add("--runtime-arg");
                commandAndParams.add("&");
            }
            commandAndParams.add("--dex-file=" + dexFilePath);
            commandAndParams.add("--oat-file=" + oatFilePath);
            commandAndParams.add("--instruction-set=" + targetISA);
            if (VersionUtils.hasOreo()) {
                commandAndParams.add("--compiler-filter=quicken");
            } else {
                commandAndParams.add("--compiler-filter=interpret-only");
            }
            PluginDebugLog.installFormatLog(TAG, "DexOptimizer params:%s", commandAndParams.toString());
            final ProcessBuilder pb = new ProcessBuilder(commandAndParams);
            pb.redirectErrorStream(true);
            final Process dex2oatProcess = pb.start();
            StreamConsumer.consumeInputStream(dex2oatProcess.getInputStream());
            StreamConsumer.consumeInputStream(dex2oatProcess.getErrorStream());
            try {
                final int ret = dex2oatProcess.waitFor();
                if (ret != 0) {
                    throw new IOException("dex2oat works unsuccessfully, exit code: " + ret);
                }
            } catch (InterruptedException e) {
                throw new IOException("dex2oat is interrupted, msg: " + e.getMessage(), e);
            }
        }
    }

    private static class StreamConsumer {
        static final Executor STREAM_CONSUMER = Executors.newSingleThreadExecutor();

        static void consumeInputStream(final InputStream is) {
            STREAM_CONSUMER.execute(new Runnable() {
                @Override
                public void run() {
                    if (is == null) {
                        return;
                    }
                    final byte[] buffer = new byte[256];
                    try {
                        while ((is.read(buffer)) > 0) {
                            // To satisfy checkstyle rules.
                        }
                    } catch (IOException ignored) {
                        // Ignored.
                    } finally {
                        try {
                            is.close();
                        } catch (Exception ignored) {
                            // Ignored.
                        }
                    }
                }
            });
        }
    }
}


