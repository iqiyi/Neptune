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

import android.text.TextUtils;

import org.qiyi.pluginlibrary.exception.ReflectException;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;

public class ReflectionUtils {
    /* 被包装的对象，访问一个实例的方法和字段 */
    private final Object object;
    /* 被访问的实例的方法或者字段所在的类 */
    private final Class<?> clazz;
    /* 反射的是一个Class还是一个Object实例? */
    private final boolean isClass;

    private ReflectionUtils(Class<?> type) {
        this.object = type;
        this.clazz = type;
        this.isClass = true;
    }

    private ReflectionUtils(Object object) {
        this.object = object;
        this.clazz = object != null ? object.getClass() : null;
        this.isClass = false;
    }

    private ReflectionUtils(Object object, Class<?> type) {
        this.object = object;
        this.clazz = type;
        this.isClass = false;
    }

    public static <T> T getFieldValue(Object obj, String fieldName)
            throws IllegalAccessException, IllegalArgumentException, NoSuchFieldException {
        return getFieldValue(obj, fieldName, true);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getFieldValue(Object obj, String fieldName, boolean resolveParent)
            throws IllegalAccessException, IllegalArgumentException, NoSuchFieldException {
        Object[] rs = getField(obj, fieldName, resolveParent);
        if (rs == null) {
            throw new NoSuchFieldException("field:" + fieldName);
        }
        Field field = (Field) rs[0];
        Object targetObj = rs[1];
        return (T) field.get(targetObj);
    }

    public static void setFieldValue(Object obj, String fieldName, Object val)
            throws IllegalAccessException, IllegalArgumentException, NoSuchFieldException {
        setFieldValue(obj, fieldName, val, true);
    }

    public static void setFieldValue(Object obj, String fieldName, Object val, boolean resolveParent)
            throws IllegalAccessException, IllegalArgumentException, NoSuchFieldException {
        Object[] rs = getField(obj, fieldName, resolveParent);
        if (rs == null) {
            throw new NoSuchFieldException("field:" + fieldName);
        }
        Field field = (Field) rs[0];
        Object targetObj = rs[1];
        field.set(targetObj, val);
    }

    private static Object[] getField(Object obj, String elFieldName, boolean resolveParent)
            throws IllegalAccessException, IllegalArgumentException, NoSuchFieldException {
        if (obj == null) {
            return null;
        }
        String[] fieldNames = elFieldName.split("[.]");
        Object targetObj = obj;
        Class<?> targetClass = targetObj.getClass();
        Object val = null;
        int i = 0;
        Field field = null;
        Object[] rs = new Object[2];
        for (String fName : fieldNames) {
            i++;
            field = getField_(targetClass, fName, resolveParent);
            if (null != field) {
                field.setAccessible(true);
                rs[0] = field;
                rs[1] = targetObj;
                val = field.get(targetObj);
                if (val == null) {
                    if (i < fieldNames.length) {
                        throw new IllegalAccessException(
                                "can not getFieldValue as field '" + fName + "' value is null in '" + targetClass.getName() + "'");
                    }
                    break;
                }
                targetObj = val;
                targetClass = targetObj.getClass();
            }
        }
        return rs;
    }

    // ---------------------------------------------------------------------
    // 成员
    // ---------------------------------------------------------------------

    public static Field getField_(Class<?> targetClass, String fieldName, boolean resolveParent)
            throws IllegalAccessException, IllegalArgumentException, NoSuchFieldException {
        NoSuchFieldException noSuchFieldExceptionOccor = null;
        Field rsField = null;
        try {
            Field field = targetClass.getDeclaredField(fieldName);
            rsField = field;
            if (!resolveParent) {
                field.setAccessible(true);
                return field;
            }
        } catch (NoSuchFieldException e) {
            noSuchFieldExceptionOccor = e;
        }
        if (noSuchFieldExceptionOccor != null) {
            if (resolveParent) {
                while (true) {
                    targetClass = targetClass.getSuperclass();
                    if (targetClass == null) {
                        break;
                    }
                    try {
                        Field field = targetClass.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        return rsField = field;
                    } catch (NoSuchFieldException e) {
                        if (targetClass.getSuperclass() == null) {
                            throw e;
                        }
                    }
                }
            } else {
                throw noSuchFieldExceptionOccor;
            }
        }
        return rsField;
    }

    /**
     * 封装Class.forName(name) <p/> 可以这样调用: <code>on(Class.forName(name))</code>
     *
     * @param name 完整类名
     * @return 工具类自身
     * @throws ReflectException 反射时发生的异常
     * @see #on(Class)
     */
    public static ReflectionUtils on(String name) throws ReflectException {
        return on(forName(name));
    }

    /**
     * 封装Class.forName(name) <p/> 可以这样调用: <code>on(Xxx.class)</code>
     *
     * @param clazz 类
     * @return 工具类自身
     * @throws ReflectException 反射时发生的异常
     * @see #on(Class)
     */
    public static ReflectionUtils on(Class<?> clazz) {
        return new ReflectionUtils(clazz);
    }

    // ---------------------------------------------------------------------
    // 构造器
    // ---------------------------------------------------------------------

    /**
     * 包装起一个对象 <p/> 当你需要访问实例的字段和方法时可以使用此方法 {@link Object}
     *
     * @param object 需要被包装的对象
     * @return 工具类自身
     */
    public static ReflectionUtils on(Object object) {
        return new ReflectionUtils(object);
    }

    /**
     * 包装起一个对象 <p/> 当你需要访问实例或其父类的字段和方法时可以使用此方法 {@link Object}
     *
     * @param object 需要被包装的对象
     * @param clazz  被包装类自身或其父类
     */
    public static ReflectionUtils on(Object object, Class<?> clazz) {
        return new ReflectionUtils(object, clazz);
    }

    /**
     * 使受访问权限限制的对象转为不受限制。 一般情况下， 一个类的私有字段和方法是无法获取和调用的， 原因在于调用前Java会检查是否具有可访问权限，
     * 当调用此方法后， 访问权限检查机制将被关闭。
     *
     * @param accessible 受访问限制的对象
     * @return 不受访问限制的对象
     */
    private static <T extends AccessibleObject> T accessible(T accessible) {

        // 默认为false,即反射时检查访问权限，
        // 设为true时不检查访问权限,可以访问private字段和方法
        if (!accessible.isAccessible()) {
            accessible.setAccessible(true);
        }

        if (accessible instanceof Member) {
            Member member = (Member) accessible;

            if (Modifier.isPublic(member.getModifiers())
                    && Modifier.isPublic(member.getDeclaringClass().getModifiers())) {
                return accessible;
            }
        }

        return accessible;
    }

    private static String property(String string) {
        int length = string.length();

        if (length == 0) {
            return "";
        } else if (length == 1) {
            return string.toLowerCase();
        } else {
            return string.substring(0, 1).toLowerCase() + string.substring(1);
        }
    }

    private static ReflectionUtils on(Constructor<?> constructor, Object... args) throws ReflectException {
        try {
            return on(accessible(constructor).newInstance(args));
        } catch (Exception e) {
            throw new ReflectException(e);
        }
    }

    private static ReflectionUtils on(Method method, Object object, Object... args) throws ReflectException {
        try {
            accessible(method);

            if (method.getReturnType() == void.class) {
                method.invoke(object, args);
                return on(object);
            } else {
                return on(method.invoke(object, args));
            }
        } catch (Exception e) {
            throw new ReflectException(e);
        }
    }

    /**
     * 内部类，使一个对象脱离包装
     */
    private static Object unwrap(Object object) {
        if (object instanceof ReflectionUtils) {
            return ((ReflectionUtils) object).get();
        }

        return object;
    }

    /**
     * 内部类， 给定一系列参数，返回它们的类型
     *
     * @see Object#getClass()
     */
    private static Class<?>[] types(Object... values) {
        if (values == null) {
            // 空
            return new Class[0];
        }

        Class<?>[] result = new Class[values.length];

        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            result[i] = value == null ? NULL.class : value.getClass();
        }

        return result;
    }

    /**
     * 加载一个类
     *
     * @see Class#forName(String)
     */
    private static Class<?> forName(String name) throws ReflectException {
        try {
            return Class.forName(name);
        } catch (Exception e) {
            throw new ReflectException(e);
        }
    }

    /**
     * 得到包装的对象的类型， 如果是基本类型,像int,float,boolean这种, 那么将被转换成相应的对象类型。
     */
    private static Class<?> wrapper(Class<?> type) {
        if (type.isPrimitive()) {
            if (boolean.class == type) {
                return Boolean.class;
            } else if (int.class == type) {
                return Integer.class;
            } else if (long.class == type) {
                return Long.class;
            } else if (short.class == type) {
                return Short.class;
            } else if (byte.class == type) {
                return Byte.class;
            } else if (double.class == type) {
                return Double.class;
            } else if (float.class == type) {
                return Float.class;
            } else if (char.class == type) {
                return Character.class;
            } else if (void.class == type) {
                return Void.class;
            }
        }

        return type;
    }

    /**
     * 得到当前包装的对象
     */
    public <T> T get() {
        return (T) object;
    }

    /**
     * 修改一个字段的值 <p/> 等价于
     * 如果包装的对象是一个{@link Class}, 那么修改的将是一个静态字段， 如果包装的对象是一个{@link Object}, 那么修改的就是一个实例字段。
     *
     * @param name  字段名
     * @param value 字段的值
     * @return 完事后的工具类
     * @throws ReflectException
     */
    public ReflectionUtils set(String name, Object value) throws ReflectException {
        try {
            Field field = field0(name);
            if (null != field) {
                field.set(object, unwrap(value));
            }
            return this;
        } catch (Exception e) {
            throw new ReflectException(e);
        }
    }

    /**
     * 修改一个字段的值，不抛出异常
     *
     * @param name
     * @param value
     * @return
     */
    public ReflectionUtils setNoException(String name, Object value) {
        try {
            set(name, value);
        } catch (ReflectException re) {
            re.printStackTrace();
        }
        return this;
    }

    /**
     * 反射获取字段的值
     *
     * @param name 字段名
     * @return The field value
     * @throws ReflectException
     * @see #field(String)
     */
    public <T> T get(String name) throws ReflectException {
        return field(name).<T>get();
    }

    /**
     * 反射获取字段的值，不抛出异常
     *
     * @param name
     * @param <T>
     * @return
     */
    public <T> T getNoException(String name) {
        try {
            return get(name);
        } catch (ReflectException re) {
            re.printStackTrace();
        }
        return (T) null;
    }

    /**
     * 取得字段
     *
     * @param name 字段名
     * @return 字段
     * @throws ReflectException
     */
    private ReflectionUtils field(String name) throws ReflectException {
        try {
            Field field = field0(name);
            return on(field.get(object));
        } catch (Exception e) {
            throw new ReflectException(e);
        }
    }

    /**
     * 反射得到Field
     *
     * @param name 字段名
     * @return
     * @throws ReflectException
     */
    private Field field0(String name) throws ReflectException {
        Class<?> type = type();

        // 尝试作为公有字段处理
        try {
            return accessible(type.getField(name));
        }
        // 尝试以私有方式处理
        catch (NoSuchFieldException e) {
            do {
                try {
                    return accessible(type.getDeclaredField(name));
                } catch (NoSuchFieldException ignore) {
                    /* ignore */
                }
                type = type.getSuperclass();
            } while (type != null);

            throw new ReflectException(e);
        }
    }

    /**
     * 将一个对象的所有对象映射到一个Map中,key为字段名。
     *
     * @return 包含所有字段的map
     */
    public Map<String, ReflectionUtils> fields() {
        Map<String, ReflectionUtils> result = new LinkedHashMap<String, ReflectionUtils>();
        Class<?> type = type();

        do {
            for (Field field : type.getDeclaredFields()) {
                if (!isClass ^ Modifier.isStatic(field.getModifiers())) {
                    String name = field.getName();

                    if (!result.containsKey(name))
                        result.put(name, field(name));
                }
            }

            type = type.getSuperclass();
        } while (type != null);

        return result;
    }

    /**
     * 给定方法名称，调用无参方法 <p/> 等价于 <code>call(name, new Object[0])</code>
     *
     * @param name 方法名
     * @return 工具类自身
     * @throws ReflectException
     * @see #call(String, Object...)
     */
    public ReflectionUtils call(String name) throws ReflectException {
        return call(name, new Object[0]);
    }

    /**
     * 给定方法名和参数，调用一个方法。
     *
     * @param name 方法名
     * @param args 方法参数
     * @return 工具类自身
     * @throws ReflectException
     */
    public ReflectionUtils call(String name, Object... args) throws ReflectException {
        return call(name, null, null, args);
    }

    /**
     * 给定方法名参数，MethodCache及可选的参数类型列表，调用一个方法
     *
     * @param name
     * @param methodCache
     * @param paramTypes
     * @param args
     * @return
     * @throws ReflectException
     */
    public ReflectionUtils call(String name, Map<String, Vector<Method>> methodCache, Class<?>[] paramTypes, Object... args) throws ReflectException {
        Class<?>[] types = types(args);

        // 尝试调用方法
        try {
            if (null != methodCache) {
                ReflectionUtils res = callInner(name, methodCache, types, args);
                if (null != res) {
                    return res;
                }
            }
            Method method;
            if (paramTypes != null) {
                try {
                    // 先尝试使用外部直接传入的类型参数获取Method，然后再使用推导的类型
                    method = exactMethod(name, paramTypes);
                } catch (NoSuchMethodException e) {
                    method = exactMethod(name, types);
                }
            } else {
                method = exactMethod(name, types);
            }

            if (null != methodCache && null != method) {
                Vector<Method> methods = methodCache.get(name);
                if (methods == null) {
                    methods = new Vector<Method>(4);
                    methodCache.put(name, methods);
                }
                methods.add(method);
            }
            return on(method, object, args);
        }
        // 如果没有符合参数的方法，
        // 则匹配一个与方法名最接近的方法。
        catch (NoSuchMethodException e) {
            try {
                Method method = similarMethod(name, types);
                if (null != methodCache && null != method) {
                    Vector<Method> methods = methodCache.get(name);
                    if (methods == null) {
                        methods = new Vector<Method>(4);
                        methodCache.put(name, methods);
                    }
                    methods.add(method);
                }
                return on(method, object, args);
            } catch (NoSuchMethodException e1) {
                throw new ReflectException(e1);
            }
        }
    }

    // 提高反射复用率
    private ReflectionUtils callInner(String name, Map<String, Vector<Method>> methodCache, Class<?>[] types,
                                      Object... args) throws ReflectException {
        Vector<Method> temp = methodCache.get(name);
        if (null != temp) {
            for (Method method : temp) {
                if (null != method && method.getDeclaringClass().isAssignableFrom(type())
                        && isSimilarSignature(method, name, types)) {
                    return on(method, object, args);
                }
            }
        }

        return null;
    }

    // ---------------------------------------------------------------------
    // 对象API
    // ---------------------------------------------------------------------

    /**
     * 根据方法名和方法参数列表查找最接近的方法
     */
    public Method exactMethod(String name, Object... args) throws NoSuchMethodException {
        Class<?>[] types = types(args);
        Method method = null;
        try {
            // 使用精确匹配查找
            method = exactMethod(name, types);
        } catch (NoSuchMethodException e) {
            // 使用模糊匹配查找
            method = similarMethod(name, types);
        }
        return method;
    }

    /**
     * 根据方法名和方法参数得到该方法。
     */
    public Method exactMethod(String name, Class<?>[] types) throws NoSuchMethodException {
        Class<?> type = type();

        // 先尝试直接调用
        try {
            return accessible(type.getMethod(name, types));
        }
        // 也许这是一个私有方法
        catch (NoSuchMethodException e) {
            do {
                try {
                    return accessible(type.getDeclaredMethod(name, types));
                } catch (NoSuchMethodException ignore) {
                    /* ignore */
                }

                type = type.getSuperclass();
            } while (type != null);

            throw new NoSuchMethodException(e.getMessage());
        }
    }

    /**
     * 给定方法名和参数，匹配一个最接近的方法
     */
    private Method similarMethod(String name, Class<?>[] types) throws NoSuchMethodException {
        Class<?> type = type();

        // 对于公有方法:
        try {
            for (Method method : type.getMethods()) {
                if (isSimilarSignature(method, name, types)) {
                    return accessible(method);
                }
            }
        } catch (NoClassDefFoundError e) {
            // 可能因为平台差异 getMethods 时发生 NoClassDefFoundError，
            // 比如 360 修改了 Instrumentation 并且方法签名引用了 4.x 设备没有的类
            // 由于后续 getDeclaredMethods 会继续去父类查询，这里只是简单的 try catch
            ErrorUtil.throwErrorIfNeed(e);
        }

        // 对于私有方法：
        do {
            try {
                for (Method method : type.getDeclaredMethods()) {
                    if (isSimilarSignature(method, name, types)) {
                        return accessible(method);
                    }
                }
            } catch (NoClassDefFoundError e) {
                // 可能因为平台差异 getDeclaredMethods 时发生 NoClassDefFoundError，
                // 比如 360 修改了 Instrumentation 并且方法签名引用了 4.x 设备没有的类
                ErrorUtil.throwErrorIfNeed(e);
            }

            type = type.getSuperclass();
        } while (type != null);

        throw new NoSuchMethodException(
                "No similar method " + name + " with params " + Arrays.toString(types) + " could be found on type " + type() + ".");
    }

    /**
     * 再次确认方法签名与实际是否匹配， 将基本类型转换成对应的对象类型， 如int转换成Int
     */
    private boolean isSimilarSignature(Method possiblyMatchingMethod, String desiredMethodName, Class<?>[] desiredParamTypes) {
        return possiblyMatchingMethod.getName().equals(desiredMethodName)
                && match(possiblyMatchingMethod.getParameterTypes(), desiredParamTypes);
    }

    /**
     * 调用一个无参构造器 <p/> 等价于 <code>create(new Object[0])</code>
     *
     * @return 工具类自身
     * @throws ReflectException
     * @see #create(Object...)
     */
    public ReflectionUtils create() throws ReflectException {
        return create(new Object[0]);
    }

    /**
     * 调用一个有参构造器
     *
     * @param args 构造器参数
     * @return 工具类自身
     * @throws ReflectException
     */
    public ReflectionUtils create(Object... args) throws ReflectException {
        Class<?>[] types = types(args);

        try {
            Constructor<?> constructor = type().getDeclaredConstructor(types);
            return on(constructor, args);
        }

        // 这种情况下，构造器往往是私有的，多用于工厂方法，刻意的隐藏了构造器。
        catch (NoSuchMethodException e) {
            // private阻止不了反射的脚步:)
            for (Constructor<?> constructor : type().getDeclaredConstructors()) {
                if (match(constructor.getParameterTypes(), types)) {
                    return on(constructor, args);
                }
            }

            throw new ReflectException(e);
        }
    }

    // ---------------------------------------------------------------------
    // 内部工具方法
    // ---------------------------------------------------------------------

    /**
     * 为包装的对象创建一个代理。
     *
     * @param proxyType 代理类型
     * @return 包装对象的代理者。
     */
    @SuppressWarnings("unchecked")
    public <P> P as(Class<P> proxyType) {
        final boolean isMap = (object instanceof Map);
        final InvocationHandler handler = new InvocationHandler() {
            @SuppressWarnings("null")
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String name = method.getName();

                try {
                    return on(object).call(name, args).get();
                } catch (ReflectException e) {
                    if (isMap) {
                        Map<String, Object> map = (Map<String, Object>) object;
                        int length = (args == null ? 0 : args.length);

                        if (length == 0 && name.startsWith("get")) {
                            return map.get(property(name.substring(3)));
                        } else if (length == 0 && name.startsWith("is")) {
                            return map.get(property(name.substring(2)));
                        } else if (length == 1 && name.startsWith("set")) {
                            map.put(property(name.substring(3)), args[0]);
                            return null;
                        }
                    }

                    throw e;
                }
            }
        };

        return (P) Proxy.newProxyInstance(proxyType.getClassLoader(), new Class[]{proxyType}, handler);
    }

    private boolean match(Class<?>[] declaredTypes, Class<?>[] actualTypes) {
        if (declaredTypes.length == actualTypes.length) {
            for (int i = 0; i < actualTypes.length; i++) {
                if (actualTypes[i] == NULL.class)
                    continue;

                if (wrapper(declaredTypes[i]).isAssignableFrom(wrapper(actualTypes[i])))
                    continue;

                return false;
            }

            return true;
        } else {
            return false;
        }
    }

    private boolean same(Class<?>[] declaredTypes, Class<?>[] actualTypes) {
        if (declaredTypes.length == actualTypes.length) {
            for (int i = 0; i < actualTypes.length; i++) {
                if (actualTypes[i] == NULL.class) {
                    continue;
                }

                if (TextUtils.equals(wrapper(declaredTypes[i]).getName(), wrapper(actualTypes[i]).getName())) {
                    continue;
                }

                return false;
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return object.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ReflectionUtils) {
            return object.equals(((ReflectionUtils) obj).get());
        }

        return false;
    }

    @Override
    public String toString() {
        return object.toString();
    }

    /**
     * 获取包装的对象的类型
     *
     * @see Object#getClass()
     */
    private Class<?> type() {
        if (clazz != null) {
            return clazz;
        }

        if (isClass) {
            return (Class<?>) object;
        } else {
            return object.getClass();
        }
    }

    /**
     * 定义了一个NULL类型
     */
    private static class NULL {
    }
}
