package convention.plugins

import com.skydoves.compose.stability.gradle.StabilityAnalyzerExtension
import convention.utils.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class ComposeStabilityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(
                libs.plugins.composeStabilityAnalyzerPlugin
                    .get()
                    .pluginId,
            )

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
        }
    }
}
