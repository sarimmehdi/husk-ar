package convention.common

import convention.utils.libs
import org.gradle.api.Project

fun Project.applyQualityPlugins(applyDetekt: Boolean) {
    with(pluginManager) {
        apply(
            libs.plugins.ktlintPlugin
                .get()
                .pluginId,
        )
        if (applyDetekt) {
            apply(
                libs.plugins.detektPlugin
                    .get()
                    .pluginId,
            )
        }
        apply(
            libs.plugins.kotlinSerializationPlugin
                .get()
                .pluginId,
        )
        apply("kotlin-parcelize")
    }
}
