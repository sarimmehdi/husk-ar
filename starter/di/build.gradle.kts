import convention.utils.ModuleType

plugins {
    alias(libs.plugins.conventionLibraryPluginId)
}

dependencies {
    implementation(libs.koinAndroidLibrary)
}

android {
    namespace = "com.sarim.husk.starter.di"
}

libraryConfig {
    moduleType.set(ModuleType.DI)
    internalDependencies.set(
        listOf(
            ":starter:data",
            ":starter:domain",
            ":starter:presentation",
        ),
    )
}
