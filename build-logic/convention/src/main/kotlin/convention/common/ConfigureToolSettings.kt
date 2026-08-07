package convention.common

import androidx.room.gradle.RoomExtension
import com.google.devtools.ksp.gradle.KspExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

fun Project.configureToolSettings() {
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    }

    pluginManager.withPlugin("androidx.room") {
        extensions.configure<RoomExtension> {
            schemaDirectory("$projectDir/schemas")
        }
    }

    pluginManager.withPlugin("com.google.devtools.ksp") {
        extensions.configure<KspExtension> {
            arg("KOIN_CONFIG_CHECK", "false")
        }
    }
}
