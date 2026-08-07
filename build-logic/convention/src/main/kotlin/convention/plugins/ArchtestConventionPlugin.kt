package convention.plugins

import convention.utils.libs
import org.gradle.api.Plugin
import org.gradle.api.Project

class ArchtestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(
                libs.plugins.conventionLibraryPluginId
                    .get()
                    .pluginId,
            )
        }
    }
}
