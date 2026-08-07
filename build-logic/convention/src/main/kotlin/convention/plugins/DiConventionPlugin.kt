package convention.plugins

import convention.utils.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class DiConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(ComposeConventionPlugin::class.java)
            pluginManager.apply(
                libs.plugins.roomPlugin
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

            dependencies {
                add("ksp", libs.androidxRoomCompilerLibrary)
                add("implementation", libs.bundles.koinBundle)
                add("implementation", libs.androidxLifecycleRuntimeComposeLibrary)
                add("implementation", libs.bundles.roomBundle)
                add("implementation", libs.bundles.dataStoreBundle)
                add("implementation", libs.bundles.navBundle)

                add("implementation", project(":nav"))
            }
        }
    }
}
