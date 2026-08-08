import convention.utils.ModuleType

plugins {
    alias(libs.plugins.conventionLibraryPluginId)
}

android {
    namespace = "com.sarim.husk.marker.di"
}

libraryConfig {
    moduleType.set(ModuleType.DI)
    internalDependencies.set(
        listOf(
            ":marker:data",
            ":marker:domain",
            ":marker:presentation",
        ),
    )
}

dependencies {
    implementation(libs.koinCoreLibrary)
    implementation(libs.koinCoreCoroutinesLibrary)
    implementation(libs.koinAndroidLibrary)
    implementation(libs.bundles.roomBundle)
}
