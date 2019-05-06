package com.qiyi.plugin.collector

import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.gradle.api.ApkVariant
import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.build.gradle.internal.dependency.ArtifactCollectionWithExtraArtifact
import com.android.build.gradle.internal.ide.ModelBuilder
import com.android.build.gradle.internal.ide.dependencies.BuildMappingUtils
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.builder.dependency.MavenCoordinatesImpl
import com.android.builder.dependency.level2.AndroidDependency
import com.android.builder.model.AndroidLibrary
import com.android.builder.model.Dependencies
import com.android.builder.model.MavenCoordinates
import com.android.builder.model.SyncIssue
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Maps
import com.google.common.collect.Sets
import com.qiyi.plugin.QYPluginExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.util.VersionNumber

import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.function.Consumer
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * 处理Android Library依赖的收集
 * 参考android插件{@link com.android.build.gradle.internal.ide.ArtifactDependencyGraph}的实现
 */
class DependencyCollector {

    Project project

    QYPluginExtension pluginExt

    ApkVariant apkVariant

    public DependencyCollector(Project project, ApkVariant apkVariant) {
        this.project = project
        this.pluginExt = project.extensions.findByType(QYPluginExtension)
        this.apkVariant = apkVariant
    }

    /**
     * Android Gradle Plugin 3.0.0+
     * @return
     */
    public Set<AndroidDependency> getAndroidDependencies() {
        println "DependencyCollector getAndroidDependencies() ........"

        Set<AndroidDependency> androidDependencies = [] as Set<AndroidDependency>
        def scope = apkVariant.getVariantData().getScope() as VariantScope
        Set<ResolvedArtifactResult> allArtifacts = getAllArtifacts(scope,
                AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH)
        allArtifacts.each {
            if (it instanceof AarResolvedArtifactResult) {
                AarResolvedArtifactResult aarArtifact = (AarResolvedArtifactResult)it
                AndroidDependency androidDependency = getAndroidDependency(aarArtifact)
                androidDependencies.add(androidDependency)
            }
        }

        return androidDependencies
    }

    /**
     * Android Gradle Plugin 3.0.0+
     * 该方法无法获取到Library模块implementation间接依赖的aar组件
     * @return
     */
    @Deprecated
    public Set<AndroidLibrary> getAndroidLibraries() {
        println "DependencyCollector getAndroidLibraries() ........"

        BaseVariantData variantData = ((ApplicationVariantImpl)apkVariant).variantData

        Dependencies dependencies
        Consumer<SyncIssue> consumer = new Consumer<SyncIssue>() {
            @Override
            void accept(SyncIssue syncIssue) {
                println "get Android Libraries issue: " + syncIssue
            }
        }

        if (pluginExt.agpVersion >= VersionNumber.parse("3.3")) {
            // AGP 3.3.0+
            Class<?> graphCls = Class.forName("com.android.build.gradle.internal.ide.dependencies.ArtifactDependencyGraph")
            Constructor<?> constructor = graphCls.getDeclaredConstructor()
            constructor.setAccessible(true)
            Object graph = constructor.newInstance()
            Method method = graphCls.getDeclaredMethod("createDependencies", VariantScope.class, boolean.class, ImmutableMap.class, Consumer.class)
            method.setAccessible(true)
            dependencies = (Dependencies)method.invoke(graph, variantData.scope, false, BuildMappingUtils.computeBuildMapping(project.gradle), consumer)
        } else if (pluginExt.agpVersion >= VersionNumber.parse("3.1")) {
            // AGP 3.1.0+
            Class<?> graphCls = Class.forName("com.android.build.gradle.internal.ide.ArtifactDependencyGraph")
            Object graph = graphCls.getConstructor().newInstance()
            Method method = graphCls.getDeclaredMethod("createDependencies", VariantScope.class, boolean.class, ImmutableMap.class, Consumer.class)
            method.setAccessible(true)
            dependencies = (Dependencies)method.invoke(graph, variantData.scope, false, ModelBuilder.computeBuildMapping(project.gradle), consumer)
        } else {
            // APG 3.0.0+
            Class<?> graphCls = Class.forName("com.android.build.gradle.internal.ide.ArtifactDependencyGraph")
            Object graph = graphCls.getConstructor().newInstance()
            Method method = graphCls.getDeclaredMethod("createDependencies", VariantScope.class, boolean.class, Consumer.class)
            method.setAccessible(true)
            dependencies = (Dependencies)method.invoke(graph, variantData.scope, false, consumer)
        }

//        ArtifactDependencyGraph graph = new ArtifactDependencyGraph()
//        try {
//            dependencies = graph.createDependencies(variantData.scope, false, consumer)
//        } catch (Throwable t) {
//            t.printStackTrace()
//            // NoSuchMethodError
//            dependencies = graph.createDependencies(variantData.scope, false, ModelBuilder.computeBuildMapping(project.gradle), consumer)
//        }
        return dependencies.libraries
    }

    /**
     * 处理依赖
     * @param artifact
     */
    protected AndroidDependency getAndroidDependency(AarResolvedArtifactResult artifact) {
        ComponentIdentifier id = artifact.getId().getComponentIdentifier()
        MavenCoordinates mavenCoordinates = computeMavenCoordinates(artifact)
        println "artifact class ${artifact.getClass().getName()}, file: ${artifact.getFile()}, ${mavenCoordinates.groupId}:${mavenCoordinates.artifactId}:${mavenCoordinates.version}"
        AndroidDependency androidDependency = null
        if (id instanceof ProjectComponentIdentifier) {
            String projectPath = ((ProjectComponentIdentifier)id).getProjectPath()
            Project subProject = project.findProject(projectPath)
            String pathLeaf = apkVariant.name != null ? apkVariant.name : "default"
            File stagingDir = FileUtils.join(subProject.buildDir,
                    "intermediates", "bundles", pathLeaf)
            androidDependency = new AndroidDependency(
                    artifact.getArtifact(),
                    mavenCoordinates,
                    id.getDisplayName(),
                    projectPath,
                    stagingDir,
                    stagingDir,
                    mavenCoordinates.classifier,
                    true)
        } else {
            androidDependency = AndroidDependency.createExplodedAarLibrary(
                    artifact.getArtifact(),
                    mavenCoordinates,
                    id.getDisplayName(),
                    null,
                    artifact.getExplodedFolder())
        }
        return androidDependency
    }



    protected MavenCoordinates computeMavenCoordinates(
            @NonNull ResolvedArtifactResult artifact) {

        ComponentIdentifier id = artifact.getId().getComponentIdentifier()

        final File artifactFile = artifact.getFile()
        final String fileName = artifactFile.getName()
        String extension = "jar"
        if (artifact instanceof AarResolvedArtifactResult) {
            extension = "aar"
        }
        if (id instanceof ModuleComponentIdentifier) {
            ModuleComponentIdentifier moduleComponentId = (ModuleComponentIdentifier) id
            final String module = moduleComponentId.getModule()
            final String version = moduleComponentId.getVersion()
            String classifier = null

            if (!artifact.getFile().isDirectory()) {
                // attempts to compute classifier based on the filename.
                String pattern = "^" + module + "-" + version + "-(.+)\\." + extension + "\$"

                Pattern p = Pattern.compile(pattern)
                Matcher m = p.matcher(fileName)
                if (m.matches()) {
                    classifier = m.group(1)
                }
            }

            return new MavenCoordinatesImpl(
                    moduleComponentId.getGroup(), module, version, extension, classifier)
        } else if (id instanceof ProjectComponentIdentifier) {
            String projectPath = ((ProjectComponentIdentifier) id).getProjectPath()
            return new MavenCoordinatesImpl(
                    project.rootProject.name, projectPath.replace(":", ""), "unspecified")
        } else {
            println "this is not project or module dependency"
        }
    }



    /**
     * 获取所有的Android Library相关依赖
     * @see com.android.build.gradle.internal.ide.ArtifactDependencyGraph
     * @param variantScope
     * @param consumedConfigType
     * @return
     */
    public Set<ResolvedArtifactResult> getAllArtifacts(VariantScope variantScope,
                        AndroidArtifacts.ConsumedConfigType consumedConfigType) {
        // we need to figure out the following:
        // - Is it an external dependency or a sub-project?
        // - Is it an android or a java dependency

        // Querying for JAR type gives us all the dependencies we care about, and we can use this
        // to differentiate external vs sub-projects (to a certain degree).
        ArtifactCollection allArtifactList =
                computeArtifactList(
                        variantScope,
                        consumedConfigType,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.JAR)

        // Then we can query for MANIFEST that will give us only the Android project so that we
        // can detect JAVA vs ANDROID.
        ArtifactCollection manifestList =
                computeArtifactList(
                        variantScope,
                        consumedConfigType,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.MANIFEST)

        ArtifactCollection nonNamespacedManifestList = null
        if (pluginExt.agpVersion >= VersionNumber.parse("3.2")) {
            // AGP 3.2.0+添加该部分实现
            nonNamespacedManifestList =
                    computeArtifactList(
                            variantScope,
                            consumedConfigType,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.NON_NAMESPACED_MANIFEST)
        }

        // We still need to understand wrapped jars and aars. The former is difficult (TBD), but
        // the latter can be done by querying for EXPLODED_AAR. If a sub-project is in this list,
        // then we need to override the type to be external, rather than sub-project.
        // This is why we query for Scope.ALL
        // But we also simply need the exploded AARs for external Android dependencies so that
        // Studio can access the content.
        ArtifactCollection explodedAarList =
                computeArtifactList(
                        variantScope,
                        consumedConfigType,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.EXPLODED_AAR)

        // We also need the actual AARs so that we can get the artifact location and find the source
        // location from it.
        ArtifactCollection aarList =
                computeArtifactList(
                        variantScope,
                        consumedConfigType,
                        AndroidArtifacts.ArtifactScope.EXTERNAL,
                        AndroidArtifacts.ArtifactType.AAR)

        final Set<ResolvedArtifactResult> explodedAarArtifacts = explodedAarList.getArtifacts()
        final Map<ComponentIdentifier, ResolvedArtifactResult> explodedAarResults =
                Maps.newHashMapWithExpectedSize(explodedAarArtifacts.size())
        for (ResolvedArtifactResult result : explodedAarArtifacts) {
            final ComponentIdentifier componentIdentifier = result.getId().getComponentIdentifier()
//            if (componentIdentifier instanceof ProjectComponentIdentifier) {
//                wrapperModules.add(componentIdentifier)
//            }
            explodedAarResults.put(componentIdentifier, result)
        }

        final Set<ResolvedArtifactResult> aarArtifacts = aarList.getArtifacts()
        final Map<ComponentIdentifier, ResolvedArtifactResult> aarResults =
                Maps.newHashMapWithExpectedSize(aarArtifacts.size())
        for (ResolvedArtifactResult result : aarArtifacts) {
            aarResults.put(result.getId().getComponentIdentifier(), result)
        }

        // build a list of android dependencies based on them publishing a MANIFEST element
        final Set<ResolvedArtifactResult> manifestArtifacts = new HashSet<>()
        manifestArtifacts.addAll(manifestList.getArtifacts())
        if (nonNamespacedManifestList != null) {
            manifestArtifacts.addAll(nonNamespacedManifestList.getArtifacts())
        }

        final Set<ComponentIdentifier> manifestIds =
                Sets.newHashSetWithExpectedSize(manifestArtifacts.size())
        for (ResolvedArtifactResult result : manifestArtifacts) {
            manifestIds.add(result.getId().getComponentIdentifier())
        }

        // build the final list, using the main list augmented with data from the previous lists.
        final Set<ResolvedArtifactResult> allArtifacts = allArtifactList.getArtifacts()

        // use a linked hash set to keep the artifact order.
        final Set<ResolvedArtifactResult> artifacts =
                Sets.newLinkedHashSetWithExpectedSize(allArtifacts.size())

        for (ResolvedArtifactResult artifact : allArtifacts) {
            final ComponentIdentifier componentIdentifier =
                    artifact.getId().getComponentIdentifier()
            if (manifestIds.contains(componentIdentifier)) {
                // Android Dependency
                println "this is an Android Library, isProject=${componentIdentifier instanceof ProjectComponentIdentifier}"
                ResolvedArtifactResult explodedAar = explodedAarResults.get(componentIdentifier)
                if (explodedAar != null) {
                    artifact = explodedAar
                }

                // and we need the AAR itself (if it exists)
                ResolvedArtifactResult aarResult = aarResults.get(componentIdentifier)

                artifacts.add(new AarResolvedArtifactResult(artifact, aarResult))
            } else {
                println "this is a Java Library"
                artifacts.add(new JarResolvedArtifactResult(artifact))
            }
        }

        return artifacts
    }



    @NonNull
    private static ArtifactCollection computeArtifactList(
            @NonNull VariantScope variantScope,
            @NonNull AndroidArtifacts.ConsumedConfigType consumedConfigType,
            @NonNull AndroidArtifacts.ArtifactScope scope,
            @NonNull AndroidArtifacts.ArtifactType type) {
        ArtifactCollection artifacts =
                variantScope.getArtifactCollection(consumedConfigType, scope, type);

        // because the ArtifactCollection could be a collection over a test variant which ends
        // up being a ArtifactCollectionWithExtraArtifact, we need to get the actual list
        // without the tested artifact.
        if (artifacts instanceof ArtifactCollectionWithExtraArtifact) {
            return ((ArtifactCollectionWithExtraArtifact) artifacts).getParentArtifacts()
        }

        return artifacts
    }

    public static class JarResolvedArtifactResult implements ResolvedArtifactResult {
        @NonNull @Delegate ResolvedArtifactResult delegate

        public JarResolvedArtifactResult(
                @NonNull ResolvedArtifactResult delegate) {
            this.delegate = delegate
        }
    }

    public static class AarResolvedArtifactResult implements ResolvedArtifactResult {
        @NonNull @Delegate ResolvedArtifactResult delegate

        /**
         * An optional sub-result that represents the bundle file, when the current result
         * represents an exploded aar
         */
        private ResolvedArtifactResult bundleResult

        public AarResolvedArtifactResult(
                @NonNull ResolvedArtifactResult delegate,
                @Nullable ResolvedArtifactResult bundleResult) {
            this.delegate = delegate
            this.bundleResult = bundleResult
        }

        public String getExtension() {
            return "aar"
        }

        public File getExplodedFolder() {
            return delegate.getFile()
        }

        public File getArtifact() {
            return bundleResult != null ? bundleResult.getFile() : delegate.getFile()
        }
    }
}
