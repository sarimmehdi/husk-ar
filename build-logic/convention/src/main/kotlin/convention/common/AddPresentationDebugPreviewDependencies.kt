package convention.common

import convention.utils.libs
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

fun Project.addPresentationDebugPreviewDependencies() {
    dependencies {
        add("debugImplementation", platform(libs.androidxComposeBomLibrary))
        add("debugImplementation", libs.bundles.composePreviewBundle)
    }
}
