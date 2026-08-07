package convention.plugins

import convention.utils.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class DataConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
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

            dependencies {
                add("ksp", libs.androidxRoomCompilerLibrary)
                add("api", libs.bundles.roomBundle)
                add("implementation", libs.bundles.dataStoreBundle)
            }
        }
    }
}
