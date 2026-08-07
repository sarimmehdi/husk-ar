package convention

import androidx.baselineprofile.gradle.consumer.BaselineProfileConsumerExtension
import com.android.build.api.dsl.ApplicationExtension
import convention.common.applyQualityPlugins
import convention.common.configureAndroidCommon
import convention.common.configurePreCommitLinting
import convention.common.configureToolSettings
import convention.utils.Config
import convention.utils.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.plugins.androidApplicationPlugin.get().pluginId)
            pluginManager.apply(libs.plugins.kotlinComposePlugin.get().pluginId)

            applyQualityPlugins(applyDetekt = true)

            extensions.configure<ApplicationExtension> {
                configureAndroidCommon(this)
                namespace = Config.NAMESPACE
                defaultConfig {
                    applicationId = Config.APPLICATION_ID
                    versionCode = Config.VERSION_CODE
                    versionName = Config.VERSION_NAME
                    testInstrumentationRunner = Config.TEST_INSTRUMENTATION_RUNNER
                }
                buildFeatures.compose = true
                packaging.resources.excludes += setOf("/nav-graph.json", "/preview-gallery.json")
                buildTypes.getByName("release") {
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro",
                    )
                }
            }

            configureToolSettings()
            configurePreCommitLinting()

            dependencies {
                add("implementation", project(":nav"))
                add("implementation", project(":ui"))
                add("implementation", project(":utils"))
                add("implementation", project(":starter:di"))
                add("implementation", libs.androidxCoreKtxLibrary)
                add("implementation", libs.androidxActivityComposeLibrary)
                add("implementation", platform(libs.androidxComposeBomLibrary))
                add("implementation", libs.bundles.composeUiBundle)
                add("implementation", libs.androidxLifecycleRuntimeComposeLibrary)
                add("implementation", libs.bundles.koinBundle)
                add("implementation", libs.bundles.navBundle)
                add("debugImplementation", libs.androidxComposeUiToolingLibrary)
                add("debugImplementation", libs.androidxComposeUiTestManifestLibrary)
                add("testImplementation", libs.bundles.unitTestingBundle)
                add("androidTestImplementation", platform(libs.androidxComposeBomLibrary))
                add("androidTestImplementation", libs.androidxComposeUiTestJunit4Library)
                add("androidTestImplementation", libs.androidxJunitLibrary)
                add("androidTestImplementation", libs.androidxEspressoCoreLibrary)
            }
        }
    }
}
