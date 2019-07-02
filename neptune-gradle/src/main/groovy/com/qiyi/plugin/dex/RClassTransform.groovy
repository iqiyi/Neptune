package com.qiyi.plugin.dex

import com.android.build.api.transform.Context
import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.Format
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Status
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInput
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.utils.FileUtils
import com.qiyi.plugin.QYPluginExtension
import com.qiyi.plugin.utils.Utils
import org.gradle.api.Project

/**
 * 修改class文件，替换插件Activity的基类为PluginActivity
 */
class RClassTransform extends Transform{

    Project project

    DexProcessor dexProcessor

    QYPluginExtension pluginExt

    public RClassTransform(Project project) {
        this.project = project
        this.dexProcessor = new DexProcessor()
    }

    @Override
    String getName() {
        return "RClass"
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    @Override
    Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @Override
    boolean isIncremental() {
        return true
    }

    @Override
    void transform(Context context,
                   Collection<TransformInput> inputs,
                   Collection<TransformInput> referencedInputs,
                   TransformOutputProvider outputProvider,
                   boolean isIncremental) throws IOException, TransformException, InterruptedException {

        println "RClassTransform transform start......"
        this.pluginExt = project.extensions.findByType(QYPluginExtension)

        if (!isIncremental) {
            outputProvider.deleteAll()
        }

        // Gather a full list of all inputs.
        List<JarInput> jarInputs = new ArrayList<>()
        List<DirectoryInput> directoryInputs = new ArrayList<>()
        inputs.each {
            jarInputs.addAll(it.jarInputs)
            directoryInputs.addAll(it.directoryInputs)
        }

        // directory input
        directoryInputs.each { directoryInput ->
            File file = directoryInput.file
            println "RClass transform directory input ${file}"

            String name = Utils.md5(file.absolutePath)
            File outDir = outputProvider.getContentLocation(name, outputTypes, scopes, Format.DIRECTORY)

            if (!isIncremental || !directoryInput.changedFiles.isEmpty()) {
                if (outDir.exists()) {
                    FileUtils.deleteDirectoryContents(outDir)
                }
                Utils.copy(file, outDir)
                process(outDir)
            }
        }
        // jar input
        jarInputs.each { jarInput ->

            File file = jarInput.file
            println "RClass transform jar input ${file}"

            String hash = Utils.md5(file.absolutePath)
            String jarName = "${file.name - '.jar'}_${hash}"
            File jarFile = outputProvider.getContentLocation(jarName, outputTypes, scopes, Format.JAR)

            switch (jarInput.getStatus()) {
                case Status.NOTCHANGED:
                    if (isIncremental) {
                        break
                    }
                // intended fall-through
                case Status.CHANGED:
                case Status.ADDED:
                    jarFile.delete()
                    Utils.copy(file, jarFile)
                    process(jarFile)
                    break
                case Status.REMOVED:
                    jarFile.delete()
                    break
            }
        }
    }



    private void process(File file) {
        if (!pluginExt.useBaseActivity) {
            println "RClassTransform no need to modify classes"
            return
        }

        if (file.isDirectory()) {
            dexProcessor.processDir(file)
        } else {
            dexProcessor.processJar(file)
        }
    }
}
