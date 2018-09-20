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

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;

import org.qiyi.pluginlibrary.install.PluginInstaller;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;

/**
 * 把插件classloader注入到parent classloader中，这样可以使parent classloader能够找到插件中的类。
 * <p>
 * 这种方案也有不好的地方，安全性等。
 * <p>
 * 但是 插件中通过Intent .put serialize extra 无法找到对应的类。只能通过此方法。
 */
public class ClassLoaderInjectHelper {

    /**
     * 注入jar
     *
     * @param context application object
     * @param dexPath lib path
     * @return inject result
     */
    public static InjectResult inject(Context context, String dexPath, String someClass, String soPath) {
        try {
            Class.forName("dalvik.system.LexClassLoader");
            return injectInAliyunOs(context, dexPath, soPath);
        } catch (ClassNotFoundException e) {
            // ignore
        }
        boolean hasBaseDexClassLoader = true;
        try {
            Class.forName("dalvik.system.BaseDexClassLoader");
        } catch (ClassNotFoundException e) {
            hasBaseDexClassLoader = false;
        }
        if (!hasBaseDexClassLoader) {
            return injectBelowApiLevel14(context, dexPath, someClass, soPath);
        } else {
            return injectAboveEqualApiLevel14(context, dexPath, soPath);
        }
    }

    public static InjectResult inject(ClassLoader parentClassLoader, ClassLoader childClassLoader, String someClass) {

        boolean hasBaseDexClassLoader = true;
        try {
            Class.forName("dalvik.system.BaseDexClassLoader");
        } catch (ClassNotFoundException e) {
            hasBaseDexClassLoader = false;
        }
        // If version > 22 LOLLIPOP_MR1
        if (Build.VERSION.SDK_INT > 22) {
            return injectAboveApiLevel22(parentClassLoader, childClassLoader);
        } else {
            if (!hasBaseDexClassLoader) {
                return injectBelowApiLevel14(parentClassLoader, childClassLoader, someClass);
            } else {
                return injectAboveEqualApiLevel14(parentClassLoader, childClassLoader);
            }
        }
    }

    /**
     * 阿里云系统注入jar
     *
     * @param context application object
     * @param dexPath lib path
     * @return inject result
     */
    private static InjectResult injectInAliyunOs(Context context, String dexPath, String soPath) {
        InjectResult result = null;
        PathClassLoader localClassLoader = (PathClassLoader) context.getClassLoader();
        File optDir = PluginInstaller.getPluginInjectRootPath(context);
        FileUtils.checkOtaFileValid(optDir, new File(dexPath));
        new DexClassLoader(dexPath, optDir.getAbsolutePath(), soPath, localClassLoader);
        String lexFileName = new File(dexPath).getName();
        lexFileName = lexFileName.replaceAll("\\.[a-zA-Z0-9]+", ".lex");
        try {
            Class<?> classLexClassLoader = Class.forName("dalvik.system.LexClassLoader");
            Constructor<?> constructorLexClassLoader = classLexClassLoader.getConstructor(String.class, String.class, String.class,
                    ClassLoader.class);
            Object localLexClassLoader = constructorLexClassLoader.newInstance(
                    optDir.getAbsolutePath() + File.separator + lexFileName, optDir.getAbsolutePath(),
                    soPath, localClassLoader);
            setField(localClassLoader, PathClassLoader.class, "mPaths",
                    appendArray(getField(localClassLoader, PathClassLoader.class, "mPaths"),
                            getField(localLexClassLoader, classLexClassLoader, "mRawDexPath")));
            setField(localClassLoader, PathClassLoader.class, "mFiles",
                    combineArray(getField(localClassLoader, PathClassLoader.class, "mFiles"),
                            getField(localLexClassLoader, classLexClassLoader, "mFiles")));
            setField(localClassLoader, PathClassLoader.class, "mZips",
                    combineArray(getField(localClassLoader, PathClassLoader.class, "mZips"),
                            getField(localLexClassLoader, classLexClassLoader, "mZips")));
            setField(localClassLoader, PathClassLoader.class, "mLexs",
                    combineArray(getField(localClassLoader, PathClassLoader.class, "mLexs"),
                            getField(localLexClassLoader, classLexClassLoader, "mDexs")));

            result = makeInjectResult(true, null);
        } catch (Exception e) {
            result = makeInjectResult(false, e);
            e.printStackTrace();
        }

        return result;
    }

    /**
     * api < 14时，注入jar
     *
     * @param context application
     * @param dexPath lib path
     * @return inject result
     */
    private static InjectResult injectBelowApiLevel14(Context context, String dexPath, String someClass, String soPath) {
        InjectResult result = null;
        PathClassLoader pathClassLoader = (PathClassLoader) context.getClassLoader();
        File optDir = PluginInstaller.getPluginInjectRootPath(context);
        FileUtils.checkOtaFileValid(optDir, new File(dexPath));
        DexClassLoader dexClassLoader = new DexClassLoader(dexPath, optDir.getAbsolutePath(),
                soPath, pathClassLoader);

        result = injectBelowApiLevel14(pathClassLoader, dexClassLoader, someClass);

        return result;
    }

    @SuppressLint("NewApi")
    private static InjectResult injectBelowApiLevel14(ClassLoader parentClassLoader, ClassLoader childClassLoader, String someClass) {
        InjectResult result = null;
        PathClassLoader pathClassLoader = null;
        if (parentClassLoader instanceof PathClassLoader) {
            pathClassLoader = (PathClassLoader) parentClassLoader;
        } else {
            return result;
        }
        DexClassLoader dexClassLoader = (DexClassLoader) childClassLoader;
        try {
            dexClassLoader.loadClass(someClass);
            setField(pathClassLoader, PathClassLoader.class, "mPaths",
                    appendArray(getField(pathClassLoader, PathClassLoader.class, "mPaths"),
                            getField(dexClassLoader, DexClassLoader.class, "mRawDexPath")));
            setField(pathClassLoader, PathClassLoader.class, "mFiles", combineArray(
                    getField(pathClassLoader, PathClassLoader.class, "mFiles"), getField(dexClassLoader, DexClassLoader.class, "mFiles")));
            setField(pathClassLoader, PathClassLoader.class, "mZips", combineArray(
                    getField(pathClassLoader, PathClassLoader.class, "mZips"), getField(dexClassLoader, DexClassLoader.class, "mZips")));
            setField(pathClassLoader, PathClassLoader.class, "mDexs", combineArray(
                    getField(pathClassLoader, PathClassLoader.class, "mDexs"), getField(dexClassLoader, DexClassLoader.class, "mDexs")));

            try {
                @SuppressWarnings("unchecked")
                ArrayList<String> libPaths = (ArrayList<String>) getField(pathClassLoader, PathClassLoader.class, "libraryPathElements");
                String[] libArray = (String[]) getField(dexClassLoader, DexClassLoader.class, "mLibPaths");
                for (String path : libArray) {
                    libPaths.add(path);
                }
            } catch (Exception e) {
                setField(pathClassLoader, PathClassLoader.class, "mLibPaths",
                        combineArray(getField(pathClassLoader, PathClassLoader.class, "mLibPaths"),
                                getField(dexClassLoader, DexClassLoader.class, "mLibPaths")));
            }

            result = makeInjectResult(true, null);
        } catch (Exception e) {
            result = makeInjectResult(false, e);
            e.printStackTrace();
        }

        return result;
    }

    /**
     * api >= 14时，注入jar
     *
     * @param context application object
     * @param dexPath lib path
     * @return inject object
     */
    private static InjectResult injectAboveEqualApiLevel14(Context context, String dexPath, String soPath) {
        PathClassLoader pathClassLoader = (PathClassLoader) context.getClassLoader();
        File optDir = PluginInstaller.getPluginInjectRootPath(context);
        FileUtils.checkOtaFileValid(optDir, new File(dexPath));
        DexClassLoader dexClassLoader = new DexClassLoader(dexPath, optDir.getAbsolutePath(), soPath,
                pathClassLoader);
        InjectResult result = null;

        // If version > 22 LOLLIPOP_MR1
        if (Build.VERSION.SDK_INT > 22) {
            result = injectAboveApiLevel22(pathClassLoader, dexClassLoader);
        } else {
            result = injectAboveEqualApiLevel14(pathClassLoader, dexClassLoader);
        }

        return result;
    }

    private static InjectResult injectAboveApiLevel22(ClassLoader parentClassLoader, ClassLoader childClassLoader) {
        InjectResult result = null;

        PathClassLoader pathClassLoader = null;
        if (parentClassLoader instanceof PathClassLoader) {
            pathClassLoader = (PathClassLoader) parentClassLoader;
        } else {
            return result;
        }

        DexClassLoader dexClassLoader = (DexClassLoader) childClassLoader;
        try {
            // 注入 dex
            Object dexElements = combineArray(getDexElements(getPathList(pathClassLoader)), getDexElements(getPathList(dexClassLoader)));

            Object pathList = getPathList(pathClassLoader);

            setField(pathList, pathList.getClass(), "dexElements", dexElements);

            // 注入 native lib so 目录，需要parent class loader
            // 遍历此目录能够找到。因为注入了以后，不处理这个目录找不到。
            // Android M版本修改了native lib 目录
            // http://androidxref.com/6.0.0_r1/diff/libcore/dalvik/src/main/java/dalvik/system/DexPathList.java?r2=%2Flibcore%2Fdalvik%2Fsrc%2Fmain%2Fjava%2Fdalvik%2Fsystem%2FDexPathList.java%40f38cae4f22e46c49f5fba94e6e0579dedd2d8fd1&r1=%2Flibcore%2Fdalvik%2Fsrc%2Fmain%2Fjava%2Fdalvik%2Fsystem%2FDexPathList.java%407694b783f48e2cc57928b61c84fd90311cb0c35a
            // nativeLibraryPathElements
            Object dexNativeLibraryDirs = combineArray(getNativeLibraryPathElements(getPathList(pathClassLoader)),
                    getNativeLibraryPathElements(getPathList(dexClassLoader)));
            setField(pathList, pathList.getClass(), "nativeLibraryPathElements", dexNativeLibraryDirs);

            Object parentDirs = getPathList(pathClassLoader);
            Class<?> parentDirsLocalClass = parentDirs.getClass();
            @SuppressWarnings("unchecked")
            List<File> nativeLibraryDirectories = (List<File>) getField(parentDirs, parentDirsLocalClass, "nativeLibraryDirectories");

            Object childDirs = getPathList(childClassLoader);
            Class<?> childDirsLocalClass = childDirs.getClass();
            @SuppressWarnings("unchecked")
            List<File> childNativeLibraryDirectories = (List<File>) getField(childDirs, childDirsLocalClass, "nativeLibraryDirectories");

            if (nativeLibraryDirectories == null) {
                nativeLibraryDirectories = new ArrayList<File>();
            }
            if (childNativeLibraryDirectories != null) {
                nativeLibraryDirectories.addAll(childNativeLibraryDirectories);
            }

            result = makeInjectResult(true, null);
        } catch (Exception e) {
            result = makeInjectResult(false, e);
            e.printStackTrace();
        }

        return result;
    }

    private static InjectResult injectAboveEqualApiLevel14(ClassLoader parentClassLoader, ClassLoader childClassLoader) {
        InjectResult result = null;

        PathClassLoader pathClassLoader = null;
        if (parentClassLoader instanceof PathClassLoader) {
            pathClassLoader = (PathClassLoader) parentClassLoader;
        } else {
            return result;
        }

        DexClassLoader dexClassLoader = (DexClassLoader) childClassLoader;
        try {
            // 注入 dex
            Object dexElements = combineArray(getDexElements(getPathList(pathClassLoader)), getDexElements(getPathList(dexClassLoader)));

            Object pathList = getPathList(pathClassLoader);

            setField(pathList, pathList.getClass(), "dexElements", dexElements);

            // 注入 native lib so 目录，需要parent class loader
            // 遍历此目录能够找到。因为注入了以后，不处理这个目录找不到。
            Object dexNativeLibraryDirs = combineArray(getNativeLibraryDirectories(getPathList(pathClassLoader)),
                    getNativeLibraryDirectories(getPathList(dexClassLoader)));

            setField(pathList, pathList.getClass(), "nativeLibraryDirectories", dexNativeLibraryDirs);

            result = makeInjectResult(true, null);
        } catch (Exception e) {
            result = makeInjectResult(false, e);
            e.printStackTrace();
        }

        return result;
    }

    /**
     * set field
     *
     * @param oObj   object
     * @param aCl    class
     * @param aField field
     * @param value  value
     * @throws NoSuchFieldException     NoSuchFieldException
     * @throws IllegalArgumentException IllegalArgumentException
     * @throws IllegalAccessException   IllegalAccessException
     */
    private static void setField(Object oObj, Class<?> aCl, String aField, Object value)
            throws NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        Field localField = aCl.getDeclaredField(aField);
        localField.setAccessible(true);
        localField.set(oObj, value);
    }

    /**
     * @param oObj   object
     * @param aCl    class
     * @param aField field
     * @return field
     * @throws NoSuchFieldException     NoSuchFieldException
     * @throws IllegalArgumentException IllegalArgumentException
     * @throws IllegalAccessException   IllegalAccessException
     */
    private static Object getField(Object oObj, Class<?> aCl, String aField)
            throws NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        Field localField = aCl.getDeclaredField(aField);
        localField.setAccessible(true);
        return localField.get(oObj);
    }

    /**
     * combine array
     *
     * @param aArrayLhs array
     * @param aArrayRhs array
     * @return array
     */
    private static Object combineArray(Object aArrayLhs, Object aArrayRhs) {
        Class<?> localClass = aArrayLhs.getClass().getComponentType();
        int i = Array.getLength(aArrayLhs);
        int j = i + Array.getLength(aArrayRhs);
        Object result = Array.newInstance(localClass, j);
        for (int k = 0; k < j; ++k) {
            if (k < i) {
                Array.set(result, k, Array.get(aArrayLhs, k));
            } else {
                Array.set(result, k, Array.get(aArrayRhs, k - i));
            }
        }
        return result;
    }

    /**
     * delete elements from specific array object. 只删除一次，因为 add的时候会添加重复的，比如
     * /system/lib 这个删除的时候不能全部删除
     *
     * @param srcArray    src array
     * @param targetArray array
     * @return array
     */
    private static Object removeArrayElements(Object srcArray, Object targetArray) {
        Class<?> localClass = srcArray.getClass().getComponentType();
        int srcLen = Array.getLength(srcArray);
        int targetLen = Array.getLength(targetArray);

        // 备份一下target，用于删除处理
        ArrayList<Object> targetCopy = new ArrayList<Object>();
        for (int j = 0; j < targetLen; j++) {
            Object target = Array.get(targetArray, j);
            targetCopy.add(target);
        }

        ArrayList<Object> resultArray = new ArrayList<Object>();

        for (int i = 0; i < srcLen; i++) {
            Object src = Array.get(srcArray, i);
            boolean finded = false;
            for (int j = 0; j < targetLen; j++) {
                Object target = targetCopy.get(j);
                if (target != null && src.equals(target)) {
                    finded = true;
                    targetCopy.set(j, null); // 找到后设置为空，表示已经从src删除过一次了，
                    break;
                }
            }

            if (!finded) {
                resultArray.add(src);
            }

        }

        int length = resultArray.size();
        Object result = Array.newInstance(localClass, length);

        for (int i = 0; i < length; i++) {
            Array.set(result, i, resultArray.get(i));
        }

        return result;
    }

    /**
     * delete elements from specific array object.
     *
     * @param srcArray    src array
     * @param targetArray array
     * @return array
     */
    private static Object removeArrayElement(Object srcArray, Object targetArray) {
        Class<?> localClass = srcArray.getClass().getComponentType();
        int srcLen = Array.getLength(srcArray);

        ArrayList<Object> resultArray = new ArrayList<Object>();

        for (int i = 0; i < srcLen; i++) {
            Object src = Array.get(srcArray, i);
            boolean finded = false;
            if (src.equals(targetArray)) {
                finded = true;
            }

            if (!finded) {
                resultArray.add(src);
            }

        }

        int length = resultArray.size();
        Object result = Array.newInstance(localClass, length);

        for (int i = 0; i < length; i++) {
            Array.set(result, i, resultArray.get(i));
        }

        return result;
    }

    /**
     * append for array
     *
     * @param aArray array
     * @param aValue value
     * @return new array
     */
    private static Object appendArray(Object aArray, Object aValue) {
        Class<?> localClass = aArray.getClass().getComponentType();
        int i = Array.getLength(aArray);
        int j = i + 1;
        Object localObject = Array.newInstance(localClass, j);
        for (int k = 0; k < j; ++k) {
            if (k < i) {
                Array.set(localObject, k, Array.get(aArray, k));
            } else {
                Array.set(localObject, k, aValue);
            }
        }
        return localObject;
    }

    /**
     * make a inject result
     *
     * @param aResult result
     * @param aT      throwable
     * @return inject result
     */
    public static InjectResult makeInjectResult(boolean aResult, Throwable aT) {
        InjectResult ir = new InjectResult();
        ir.mIsSuccessful = aResult;
        ir.mErrMsg = (aT != null ? aT.getLocalizedMessage() : null);
        return ir;
    }

    /**
     * @param aBaseDexClassLoader BaseDexClassLoader
     * @return path list
     * @throws IllegalArgumentException IllegalArgumentException
     * @throws NoSuchFieldException     NoSuchFieldException
     * @throws IllegalAccessException   IllegalAccessException
     * @throws ClassNotFoundException   ClassNotFoundException
     */
    private static Object getPathList(Object aBaseDexClassLoader)
            throws IllegalArgumentException, NoSuchFieldException, IllegalAccessException, ClassNotFoundException {
        return getField(aBaseDexClassLoader, Class.forName("dalvik.system.BaseDexClassLoader"), "pathList");
    }

    /**
     * @param aParamObject param
     * @return dexElements
     * @throws IllegalArgumentException IllegalArgumentException
     * @throws NoSuchFieldException     NoSuchFieldException
     * @throws IllegalAccessException   IllegalAccessException
     */
    private static Object getDexElements(Object aParamObject)
            throws IllegalArgumentException, NoSuchFieldException, IllegalAccessException {
        return getField(aParamObject, aParamObject.getClass(), "dexElements");
    }

    private static Object getNativeLibraryPathElements(Object aParamObject)
            throws IllegalArgumentException, NoSuchFieldException, IllegalAccessException {
        return getField(aParamObject, aParamObject.getClass(), "nativeLibraryPathElements");
    }

    private static Object getNativeLibraryDirectories(Object aParamObject)
            throws IllegalArgumentException, NoSuchFieldException, IllegalAccessException {
        return getField(aParamObject, aParamObject.getClass(), "nativeLibraryDirectories");
    }

    public static InjectResult eject(ClassLoader parentClassLoader, ClassLoader childClassLoader) {

        boolean hasBaseDexClassLoader = true;
        try {
            Class.forName("dalvik.system.BaseDexClassLoader");
        } catch (ClassNotFoundException e) {
            hasBaseDexClassLoader = false;
        }
        if (!hasBaseDexClassLoader) {
            return ejectBelowApiLevel14(parentClassLoader, childClassLoader);
        } else {
            return ejectAboveEqualApiLevel14(parentClassLoader, childClassLoader);
        }
    }

    private static InjectResult ejectBelowApiLevel14(ClassLoader parentClassLoader, ClassLoader childClassLoader) {
        InjectResult result = null;
        PathClassLoader pathClassLoader = (PathClassLoader) parentClassLoader;
        DexClassLoader dexClassLoader = (DexClassLoader) childClassLoader;
        try {
            setField(pathClassLoader, PathClassLoader.class, "mPaths",
                    removeArrayElement(getField(pathClassLoader, PathClassLoader.class, "mPaths"),
                            getField(dexClassLoader, DexClassLoader.class, "mRawDexPath")));
            setField(pathClassLoader, PathClassLoader.class, "mFiles", removeArrayElements(
                    getField(pathClassLoader, PathClassLoader.class, "mFiles"), getField(dexClassLoader, DexClassLoader.class, "mFiles")));
            setField(pathClassLoader, PathClassLoader.class, "mZips", removeArrayElements(
                    getField(pathClassLoader, PathClassLoader.class, "mZips"), getField(dexClassLoader, DexClassLoader.class, "mZips")));
            setField(pathClassLoader, PathClassLoader.class, "mDexs", removeArrayElements(
                    getField(pathClassLoader, PathClassLoader.class, "mDexs"), getField(dexClassLoader, DexClassLoader.class, "mDexs")));

            try {
                @SuppressWarnings("unchecked")
                ArrayList<String> libPaths = (ArrayList<String>) getField(pathClassLoader, PathClassLoader.class, "libraryPathElements");
                String[] libArray = (String[]) getField(dexClassLoader, DexClassLoader.class, "mLibPaths");
                for (String path : libArray) {
                    libPaths.remove(path);
                }
            } catch (Exception e) {
                setField(pathClassLoader, PathClassLoader.class, "mLibPaths",
                        removeArrayElements(getField(pathClassLoader, PathClassLoader.class, "mLibPaths"),
                                getField(dexClassLoader, DexClassLoader.class, "mLibPaths")));
            }

            result = makeInjectResult(true, null);
        } catch (Exception e) {
            result = makeInjectResult(false, e);
            e.printStackTrace();
        }

        return result;
    }

    private static InjectResult ejectAboveEqualApiLevel14(ClassLoader parentClassLoader, ClassLoader childClassLoader) {
        PathClassLoader pathClassLoader = (PathClassLoader) parentClassLoader;
        DexClassLoader dexClassLoader = (DexClassLoader) childClassLoader;
        InjectResult result = null;
        try {
            // 注入 dex
            Object dexElements = removeArrayElements(getDexElements(getPathList(pathClassLoader)),
                    getDexElements(getPathList(dexClassLoader)));

            Object pathList = getPathList(pathClassLoader);

            setField(pathList, pathList.getClass(), "dexElements", dexElements);

            // 注入 native lib so 目录，需要parent class loader
            // 遍历此目录能够找到。因为注入了以后，不处理这个目录找不到。
            Object dexNativeLibraryDirs = removeArrayElements(getNativeLibraryDirectories(getPathList(pathClassLoader)),
                    getNativeLibraryDirectories(getPathList(dexClassLoader)));

            setField(pathList, pathList.getClass(), "nativeLibraryDirectories", dexNativeLibraryDirs);

            result = makeInjectResult(true, null);
        } catch (Exception e) {
            result = makeInjectResult(false, e);
            e.printStackTrace();
        }

        return result;
    }

    /**
     * inject result
     */
    public static class InjectResult {
        /**
         * is successful
         */
        public boolean mIsSuccessful;
        /**
         * error msg
         */
        public String mErrMsg;
    }
}
