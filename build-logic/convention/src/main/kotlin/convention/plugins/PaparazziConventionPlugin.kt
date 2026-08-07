package convention.plugins

import com.android.build.api.dsl.LibraryExtension
import convention.utils.Config
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class PaparazziConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("app.cash.paparazzi")
            pluginManager.withPlugin("com.android.library") {
                extensions.configure<LibraryExtension> {
                    compileSdk = Config.COMPILE_SDK
                }
            }
            tasks.configureEach {
                // Android Lint is switched off here because Paparazzi modules hold only screenshot
                // tests. The lint model tasks are named generate*Lint*/write*Lint* rather than lint*,
                // so matching on the prefix alone left them enabled: they then demanded the AAR that
                // disabling bundle*LocalLintAar had already suppressed, and check failed with a
                // missing out.aar. Matching the capital-L "Lint" keeps ktlint's tasks untouched,
                // since those spell it "ktlint" with a lowercase l.
                val isAndroidLintTask =
                    name == "lint" ||
                        name.startsWith("lint") ||
                        name.startsWith("updateLint") ||
                        ((name.startsWith("generate") || name.startsWith("write")) && name.contains("Lint"))
                val exactNames =
                    setOf(
                        "checkDebugAarMetadata",
                        "checkReleaseAarMetadata",
                        "bundleDebugAar",
                        "bundleReleaseAar",
                        "bundleDebugLocalLintAar",
                        "bundleReleaseLocalLintAar",
                        "assembleDebug",
                        "assembleRelease",
                    )
                if (name in exactNames || isAndroidLintTask) {
                    enabled = false
                }
            }
        }
    }
}
