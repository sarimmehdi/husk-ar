package convention.plugins

import convention.utils.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import java.io.File

/**
 * Configures the architecture-test module.
 *
 * Beyond applying the library convention, this wires the architecture tests to the modules they
 * actually inspect. Those tests find their subjects by walking the project tree for compiled
 * classes, which Gradle cannot see: without the wiring below the test task declares no dependency on
 * any of it, so it is free to run before those modules are built and free to report itself
 * up-to-date when only they have changed.
 *
 * Both failure modes end the same way — `check` passes while an architecture violation sits
 * unexamined in a module the suite never loaded.
 */
class ArchtestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(
                libs.plugins.conventionLibraryPluginId
                    .get()
                    .pluginId,
            )
            wireInspectedModules()
        }
    }

    private fun Project.wireInspectedModules() {
        // Every project is created while settings is evaluated, so this list is complete here and
        // reading a name or a directory does not force any of them to configure.
        val inspected = rootProject.subprojects.filter { it.name in LAYER_MODULE_NAMES }

        val buildTasks = inspected.map { "${it.path}:$ASSEMBLE_TASK" }
        val classDirectories =
            files(
                inspected.flatMap { module ->
                    CLASS_DIR_SUFFIXES.map { suffix -> File(module.projectDir, suffix) }
                },
            )

        tasks.withType(Test::class.java).configureEach {
            dependsOn(buildTasks)
            // Declared as inputs as well as dependencies. Ordering alone still leaves the task
            // up-to-date when a rule-breaking change lands in a module rather than in the tests.
            inputs
                .files(classDirectories)
                .withPropertyName("inspectedModuleClasses")
                .withPathSensitivity(PathSensitivity.RELATIVE)
                .optional()
        }
    }

    private companion object {
        /**
         * Module names the architecture tests inspect.
         *
         * Matches how the tests discover them: by directory name, so any feature laid out the way
         * the generator produces is picked up without naming it here.
         */
        val LAYER_MODULE_NAMES = setOf("domain", "data", "di", "presentation")

        const val ASSEMBLE_TASK = "assembleDebug"

        val CLASS_DIR_SUFFIXES =
            listOf(
                "build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes",
                "build/intermediates/javac/debug/compileDebugJavaWithJavac/classes",
            )
    }
}
