package convention.plugins

import convention.utils.Config
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

class JacocoAggregationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("jacoco")

            extensions.configure<JacocoPluginExtension> {
                toolVersion = Config.JACOCO_TOOL_VERSION
            }

            afterEvaluate {
                gradle.projectsEvaluated {
                    val key = "jacocoAggregationModules"

                    @Suppress("UNCHECKED_CAST")
                    val registeredModules: List<String> =
                        if (extensions.extraProperties.has(key)) {
                            extensions.extraProperties.get(key) as List<String>
                        } else {
                            emptyList()
                        }

                    val moduleProjects =
                        registeredModules.mapNotNull { path ->
                            project.findProject(path)
                        }

                    if (moduleProjects.isEmpty()) {
                        logger.warn("JaCoCo Aggregation: No modules registered for coverage aggregation.")
                        return@projectsEvaluated
                    }

                    val buildVariant = "debug"
                    val variantCapitalized = buildVariant.replaceFirstChar { it.uppercase() }

                    val allClassDirectories =
                        moduleProjects.map { moduleProject ->
                            fileTree(moduleProject.layout.buildDirectory.dir("tmp/kotlin-classes/$buildVariant")) {
                                exclude(Config.JACOCO_EXCLUSIONS)
                            } +
                                fileTree(
                                    moduleProject.layout.buildDirectory.dir(
                                        "intermediates/built_in_kotlinc/$buildVariant/compile${variantCapitalized}Kotlin/classes",
                                    ),
                                ) {
                                    exclude(Config.JACOCO_EXCLUSIONS)
                                } +
                                fileTree(
                                    moduleProject.layout.buildDirectory.dir(
                                        "intermediates/javac/$buildVariant/classes",
                                    ),
                                ) {
                                    exclude(Config.JACOCO_EXCLUSIONS)
                                }
                        }

                    val allSourceDirectories =
                        moduleProjects.flatMap { moduleProject ->
                            listOf(
                                moduleProject.file("src/main/java"),
                                moduleProject.file("src/main/kotlin"),
                            )
                        }

                    val allExecutionData =
                        moduleProjects.map { moduleProject ->
                            fileTree(moduleProject.layout.buildDirectory) {
                                include(
                                    "jacoco/test${variantCapitalized}UnitTest.exec",
                                    "outputs/unit_test_code_coverage/${buildVariant}UnitTest/test${variantCapitalized}UnitTest.exec",
                                )
                            }
                        }

                    tasks.register<JacocoReport>("jacocoAggregated${variantCapitalized}Report") {
                        group = "verification"
                        description = "Generates an aggregated JaCoCo code coverage report for all modules."

                        dependsOn(
                            moduleProjects.map { moduleProject ->
                                "${moduleProject.path}:test${variantCapitalized}UnitTest"
                            },
                        )

                        classDirectories.setFrom(allClassDirectories)
                        sourceDirectories.setFrom(files(allSourceDirectories))
                        executionData.setFrom(allExecutionData)

                        reports {
                            xml.required.set(true)
                            html.required.set(true)
                            csv.required.set(false)
                            xml.outputLocation.set(
                                layout.buildDirectory.file(
                                    "reports/jacoco/aggregated/jacocoAggregated${variantCapitalized}Report.xml",
                                ),
                            )
                            html.outputLocation.set(
                                layout.buildDirectory.dir("reports/jacoco/aggregated/html"),
                            )
                        }
                    }
                }
            }
        }
    }
}
