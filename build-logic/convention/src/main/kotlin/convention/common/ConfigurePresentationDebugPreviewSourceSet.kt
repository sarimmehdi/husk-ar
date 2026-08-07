package convention.common

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

fun Project.configurePresentationDebugPreviewSourceSet() {
    extensions.configure<LibraryExtension> {
        sourceSets.getByName("debug") {
            java.directories.add("src/debug/java")
            res.directories.add("src/debug/res")
        }
    }
}
