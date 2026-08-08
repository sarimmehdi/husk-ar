import convention.utils.ModuleType

plugins {
    alias(libs.plugins.conventionLibraryPluginId)
    alias(libs.plugins.conventionComposePluginId)
    alias(libs.plugins.conventionJacocoPluginId)
}

android {
    namespace = "com.sarim.husk.session.presentation"
}

libraryConfig {
    moduleType.set(ModuleType.PRESENTATION)
    internalDependencies.set(
        listOf(
            ":session:domain",
        ),
    )
}

dependencies {
    // MeasuredObject carries an Ellipsoid, so the test fakes need geometry on the classpath.
    testImplementation(project(":geometry"))
}
