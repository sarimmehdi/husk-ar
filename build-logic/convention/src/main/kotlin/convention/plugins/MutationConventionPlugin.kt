package convention.plugins

import convention.utils.Config
import convention.utils.MutationExtension
import convention.utils.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import pl.droidsonroids.gradle.pitest.PitestPluginExtension

class MutationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val mutation = extensions.create<MutationExtension>("mutationConfig")

            if (!providers.gradleProperty("enableMutationTesting").map(String::toBoolean).getOrElse(false)) {
                return@with
            }

            pluginManager.apply(
                libs.plugins.androidPitestPlugin
                    .get()
                    .pluginId,
            )

            extensions.configure<PitestPluginExtension> {
                threads.set(
                    Runtime
                        .getRuntime()
                        .availableProcessors()
                        .coerceAtMost(Config.MUTATION_MAX_THREADS),
                )
                outputFormats.set(setOf("HTML", "XML"))
                timestampedReports.set(false)
                mutators.set(setOf("DEFAULTS"))
                excludedClasses.set(Config.MUTATION_EXCLUSIONS)
                jvmArgs.set(listOf(Config.MUTATION_MAX_HEAP))
                failWhenNoMutations.set(false)
                mutationThreshold.set(0)
                coverageThreshold.set(0)
                testStrengthThreshold.set(0)
                verbose.set(providers.gradleProperty("mutationVerbose").map(String::toBoolean).getOrElse(false))
            }

            afterEvaluate {
                extensions.configure<PitestPluginExtension> {
                    targetClasses.set(mutation.targetClasses)
                    targetTests.set(mutation.targetTests)
                }
            }
        }
    }
}
