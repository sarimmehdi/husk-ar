package convention.plugins

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.TestOptions
import com.google.devtools.ksp.gradle.KspExtension
import com.skydoves.compose.stability.gradle.StabilityAnalyzerExtension
import convention.common.configureAndroidCommon
import convention.utils.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class ComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(
                libs.plugins.kotlinComposePlugin
                    .get()
                    .pluginId,
            )
            pluginManager.apply(
                libs.plugins.kspPlugin
                    .get()
                    .pluginId,
            )
            pluginManager.apply(
                libs.plugins.navGraphPlugin
                    .get()
                    .pluginId,
            )
            pluginManager.apply(
                libs.plugins.composeStabilityAnalyzerPlugin
                    .get()
                    .pluginId,
            )

            extensions.configure<KspExtension> {
                arg("navgraph.annotatedOnly", "true")
            }

            extensions.configure<StabilityAnalyzerExtension> {
                enabled.set(true)
                stabilityValidation {
                    enabled.set(true)
                    outputDir.set(layout.projectDirectory.dir("stability"))
                    includeTests.set(false)
                    failOnStabilityChange.set(true)
                    ignoreNonRegressiveChanges.set(false)
                    allowMissingBaseline.set(false)
                    allowIncrementalDisabling.set(true)
                }
            }

            pluginManager.withPlugin("com.android.application") {
                extensions.configure<ApplicationExtension> {
                    configureAndroidCommon(this)
                    testOptions {
                        configureUnitTestOptions()
                    }
                }
            }

            pluginManager.withPlugin("com.android.library") {
                extensions.configure<LibraryExtension> {
                    configureAndroidCommon(this)
                    testOptions {
                        configureUnitTestOptions()
                    }
                }
            }

            dependencies {
                add("implementation", platform(libs.androidxComposeBomLibrary))
                add("implementation", libs.androidxLifecycleRuntimeKtxLibrary)
                add("implementation", libs.androidxLifecycleRuntimeComposeLibrary)
                add("implementation", libs.androidxActivityComposeLibrary)
                add("implementation", libs.bundles.composeUiBundle)

                add("testImplementation", libs.roboelectricLibrary)
                add("testImplementation", libs.androidxComposeUiTestJunit4Library)

                add("debugImplementation", libs.androidxComposeUiToolingLibrary)
                add("debugImplementation", libs.androidxComposeUiTestManifestLibrary)

                add("androidTestImplementation", platform(libs.androidxComposeBomLibrary))
                add("androidTestImplementation", libs.androidxComposeUiTestJunit4Library)
                add("androidTestImplementation", libs.androidxJunitLibrary)
                add("androidTestImplementation", libs.androidxEspressoCoreLibrary)
                add("androidTestImplementation", libs.koinTestLibrary)
                add("androidTestImplementation", libs.koinAndroidLibrary)
                add("androidTestImplementation", libs.koinAndroidxComposeLibrary)
                add("androidTestImplementation", libs.androidxComposeUiToolingLibrary)
            }
        }
    }
}

private fun TestOptions.configureUnitTestOptions() {
    unitTests {
        isIncludeAndroidResources = true

        all {
            it.jvmArgs("-noverify")
        }
    }
}
