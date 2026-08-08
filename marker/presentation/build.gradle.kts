import convention.utils.ModuleType

plugins {
    alias(libs.plugins.conventionLibraryPluginId)
    alias(libs.plugins.conventionComposePluginId)
    alias(libs.plugins.conventionJacocoPluginId)
}

android {
    namespace = "com.sarim.husk.marker.presentation"
}

libraryConfig {
    moduleType.set(ModuleType.PRESENTATION)
    internalDependencies.set(
        listOf(
            ":marker:domain",
        ),
    )
}

dependencies {
    // The print layout — paper, padding, the reference bar and the width correction — lives beside
    // the marker types it describes.
    implementation(project(":ar"))

    // The in-memory store, so the printing instructions can be tested against a real repository
    // rather than a fake that might disagree with it.
    testImplementation(project(":marker:data"))
}
