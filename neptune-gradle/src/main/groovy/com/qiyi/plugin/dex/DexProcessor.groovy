package com.qiyi.plugin.dex

import com.qiyi.plugin.utils.Utils
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

/**
 * 实现插件Activity / Fragment 的基类的修改和替换
 */
class DexProcessor {
    def private static ReplaceRules = [
            'android.app.Activity'                    : 'org.qiyi.pluginlibrary.component.base.PluginActivity',
            'android.app.ListActivity'                : 'org.qiyi.pluginlibrary.component.base.PluginListActivity',
            'android.app.ExpandableListActivity'      : 'org.qiyi.pluginlibrary.component.base.PluginExpandableListActivity',
            'android.preference.PreferenceActivity'   : 'org.qiyi.pluginlibrary.component.base.PluginPreferenceActivity',
            'android.app.ActivityGroup'               : 'org.qiyi.pluginlibrary.component.base.PluginActivityGroup',
            'android.app.TabActivity'                 : 'org.qiyi.pluginlibrary.component.base.PluginTabActivity',
            'android.support.v4.app.FragmentActivity' : 'org.qiyi.pluginlibrary.component.base.PluginFragmentActivity',
            'android.support.v7.app.AppCompatActivity': 'org.qiyi.pluginlibrary.component.base.PluginAppCompatActivity',
            'android.support.v4.app.Fragment'         : 'org.qiyi.pluginlibrary.component.base.PluginSupportFragment'
    ]

    void processDir(File dir) {
        dir.eachFileRecurse { file ->
            if (file.name.endsWith(".class")) {
                processClass(file)
            } else if (file.name.endsWith(".jar")) {
                processJar(file)
            }
        }
    }

    void processClass(File classFile) {
        File tmpFile = new File(classFile.parentFile, classFile.name + "_bak")
        classFile.withInputStream { ins ->
            byte[] bytes = replaceClassIfNeed(ins)
            tmpFile.withOutputStream { out ->
                out.write(bytes, 0, bytes.length)
                out.flush()
            }
        }
        Utils.renameFile(tmpFile, classFile)
    }

    void processJar(File jarFile) {
        JarFile jf = new JarFile(jarFile)
        Enumeration<JarEntry> je = jf.entries()
        File tmpJar = new File(jarFile.parentFile, "temp.jar")
        JarOutputStream jos = new JarOutputStream(new FileOutputStream(tmpJar))

        while (je.hasMoreElements()) {
            JarEntry jarEntry = je.nextElement()
            ZipEntry zipEntry = new ZipEntry(jarEntry.getName())
            InputStream originIns = jf.getInputStream(jarEntry)
            byte[] bytes
            if (jarEntry.getName().endsWith(".class")) {
                bytes = replaceClassIfNeed(originIns)
            } else {
                bytes = Utils.toByteArray(originIns)
            }
            originIns.close()
            jos.putNextEntry(zipEntry)
            jos.write(bytes)
            jos.closeEntry()
        }

        jos.close()
        jf.close()
        Utils.renameFile(tmpJar, jarFile)
    }

    /**
     * 使用ASM修改Class的superName
     * @param is
     * @return
     */
    byte[] replaceClassIfNeed(InputStream is) {
        ClassReader cr = new ClassReader(is)
        ClassWriter cw = new ClassWriter(cr, 0)
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM4, cw) {
            @Override
            void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                //println "class name => ${name}, superName => ${superName}, interfaces => ${interfaces.toString()}"
                String clsName = name.replace("/", ".")
                String superClsName = superName.replace("/", ".")
                if (clsName in ReplaceRules.values()) {
                    /* No need to replace */
                } else if (clsName.startsWith("android.app.")
                        || clsName.startsWith("android.support")) {
                    /* Class in system or support library, No need to replace, such as SupportActivity */
                } else if (superClsName in ReplaceRules.values()) {
                    /* Already Replaced */
                } else if (superClsName in ReplaceRules.keySet()) {
                    /* Need Replace */
                    String target = ReplaceRules.get(superClsName).replace(".", "/")
                    println "replace class ${name}'s super class name from ${superName} to ${target}"
                    superName = target
                }

                super.visit(version, access, name, signature, superName, interfaces)
            }
        }
        cr.accept(cv, 0)
        return cw.toByteArray()
    }
}
