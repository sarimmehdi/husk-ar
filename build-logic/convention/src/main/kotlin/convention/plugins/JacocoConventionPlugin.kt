package convention.plugins

import convention.utils.Config
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

class JacocoConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("jacoco")

            extensions.configure<JacocoPluginExtension> {
                toolVersion = Config.JACOCO_TOOL_VERSION
            }

            tasks.withType<Test> {
                configure<JacocoTaskExtension> {
                    isIncludeNoLocationClasses = true
                    excludes = listOf("jdk.internal.*")
                }
            }

            afterEvaluate {
                val buildVariant = "debug"
                val variantCapitalized = buildVariant.replaceFirstChar { it.uppercase() }

                val classDirectories =
                    fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/$buildVariant")) {
                        exclude(Config.JACOCO_EXCLUSIONS)
                    } +
                        fileTree(
                            layout.buildDirectory.dir(
                                "intermediates/built_in_kotlinc/$buildVariant/compile${variantCapitalized}Kotlin/classes",
                            ),
                        ) {
                            exclude(Config.JACOCO_EXCLUSIONS)
                        } +
                        fileTree(layout.buildDirectory.dir("intermediates/javac/$buildVariant/classes")) {
                            exclude(Config.JACOCO_EXCLUSIONS)
                        }

                val sourceDirectories =
                    files(
                        "src/main/java",
                        "src/main/kotlin",
                    )

                val executionDataFiles =
                    fileTree(layout.buildDirectory) {
                        include(
                            "jacoco/test${variantCapitalized}UnitTest.exec",
                            "outputs/unit_test_code_coverage/${buildVariant}UnitTest/test${variantCapitalized}UnitTest.exec",
                        )
                    }

                tasks.register<JacocoReport>("jacoco${variantCapitalized}Report") {
                    group = "verification"
                    description = "Generates JaCoCo code coverage report for the $buildVariant variant."

                    dependsOn("test${variantCapitalized}UnitTest")

                    this.classDirectories.setFrom(classDirectories)
                    this.sourceDirectories.setFrom(sourceDirectories)
                    this.executionData.setFrom(executionDataFiles)

                    reports {
                        xml.required.set(true)
                        html.required.set(true)
                        csv.required.set(false)
                        xml.outputLocation.set(
                            layout.buildDirectory.file(
                                "reports/jacoco/jacoco${variantCapitalized}Report/jacoco${variantCapitalized}Report.xml",
                            ),
                        )
                        html.outputLocation.set(
                            layout.buildDirectory.dir("reports/jacoco/jacoco${variantCapitalized}Report/html"),
                        )
                    }
                }

                tasks.register<JacocoCoverageVerification>("jacoco${variantCapitalized}CoverageVerification") {
                    group = "verification"
                    description = "Verifies JaCoCo code coverage metrics for the $buildVariant variant."

                    dependsOn("test${variantCapitalized}UnitTest")

                    this.classDirectories.setFrom(classDirectories)
                    this.sourceDirectories.setFrom(sourceDirectories)
                    this.executionData.setFrom(executionDataFiles)

                    violationRules {
                        rule {
                            limit {
                                minimum = "0.0".toBigDecimal()
                            }
                        }
                    }
                }
            }

            rootProject.registerForAggregation(this)
        }
    }

    private fun Project.registerForAggregation(moduleProject: Project) {
        val registeredModules = rootProject.extensions.extraProperties
        val key = "jacocoAggregationModules"

        @Suppress("UNCHECKED_CAST")
        val modules =
            if (registeredModules.has(key)) {
                registeredModules.get(key) as MutableList<String>
            } else {
                mutableListOf<String>().also { registeredModules.set(key, it) }
            }

        modules.add(moduleProject.path)
    }
}
