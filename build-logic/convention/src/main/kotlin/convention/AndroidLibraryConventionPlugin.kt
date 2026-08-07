package convention

import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.HasUnitTestBuilder
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import convention.common.addPresentationDebugPreviewDependencies
import convention.common.applyQualityPlugins
import convention.common.configureAndroidCommon
import convention.common.configurePreCommitLinting
import convention.common.configurePresentationDebugPreviewSourceSet
import convention.common.configureToolSettings
import convention.utils.AndroidLibraryExtension
import convention.utils.ModuleType
import convention.utils.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val extension = extensions.create<AndroidLibraryExtension>("libraryConfig")
            extension.internalDependencies.convention(emptyList())
            extension.moduleType.convention(ModuleType.DOMAIN)

            pluginManager.apply(
                libs.plugins.androidLibraryPlugin
                    .get()
                    .pluginId,
            )

            applyQualityPlugins(true)

            extensions.configure<LibraryExtension> {
                configureAndroidCommon(this)
                defaultConfig.consumerProguardFiles("consumer-rules.pro")
                buildFeatures.buildConfig = true
            }

            extensions.configure<LibraryAndroidComponentsExtension> {
                beforeVariants { variantBuilder ->
                    (variantBuilder as HasUnitTestBuilder).enableUnitTest = true
                }
            }

            configureToolSettings()
            configurePreCommitLinting()

            dependencies {
                add("implementation", libs.androidxCoreKtxLibrary)
                add("implementation", libs.timberLibrary)
                add("implementation", libs.immutableCollectionsLibrary)
                add("testImplementation", libs.bundles.unitTestingBundle)
                add("testImplementation", libs.archunitLibrary)
            }

            afterEvaluate {
                val type = extension.moduleType.get()

                dependencies {
                    when (type) {
                        ModuleType.DOMAIN -> {
                            add("implementation", project(":utils"))
                            add("implementation", libs.kotlinxCoroutinesCoreLibrary)
                        }
                        ModuleType.DATA,
                        ModuleType.DI,
                        -> add("implementation", project(":utils"))
                        ModuleType.PRESENTATION -> {
                            add("implementation", project(":utils"))
                            add("implementation", project(":ui"))
                            add("implementation", project(":nav"))
                            add("implementation", libs.nav3RuntimeLibrary)

                            configurePresentationDebugPreviewSourceSet()
                            addPresentationDebugPreviewDependencies()
                        }
                        ModuleType.NAV -> {
                            add("implementation", project(":utils"))
                            add("implementation", libs.bundles.koinBundle)
                            add("implementation", libs.bundles.navBundle)
                        }
                        ModuleType.UTILS ->
                            add("implementation", libs.androidxDatastoreLibrary)
                        ModuleType.UI -> {
                            add("implementation", project(":utils"))
                            add("implementation", project(":nav"))
                        }
                    }

                    val internalDependencyConfiguration =
                        if (type == ModuleType.DI) "api" else "implementation"
                    extension.internalDependencies.get().forEach { modulePath ->
                        add(internalDependencyConfiguration, project(modulePath))
                    }
                }
            }
        }
    }
}
