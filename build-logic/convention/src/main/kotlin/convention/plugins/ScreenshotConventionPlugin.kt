package convention.plugins

import com.android.build.api.dsl.LibraryExtension
import convention.common.applyQualityPlugins
import convention.common.configureAndroidCommon
import convention.common.configurePreCommitLinting
import convention.utils.Config
import convention.utils.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class ScreenshotConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(
                libs.plugins.androidLibraryPlugin
                    .get()
                    .pluginId,
            )
            pluginManager.apply(
                libs.plugins.kotlinComposePlugin
                    .get()
                    .pluginId,
            )
            pluginManager.apply(
                libs.plugins.conventionPaparazziPluginId
                    .get()
                    .pluginId,
            )

            applyQualityPlugins(false)

            extensions.configure<LibraryExtension> {
                configureAndroidCommon(this)
                compileSdk = Config.COMPILE_SDK
            }

            configurePreCommitLinting()

            dependencies {
                add("implementation", project(":ui"))
                add("implementation", project(":nav"))
                add("implementation", project(":utils"))
                add("implementation", platform(libs.androidxComposeBomLibrary))
                add("implementation", libs.androidxComposeUiToolingPreviewLibrary)
                add("implementation", libs.immutableCollectionsLibrary)
                add("testImplementation", libs.bundles.unitTestingBundle)
                add("testImplementation", libs.bundles.composeUiBundle)
            }
        }
    }
}
