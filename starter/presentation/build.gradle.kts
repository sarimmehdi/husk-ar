import convention.utils.ModuleType

plugins {
    alias(libs.plugins.conventionLibraryPluginId)
    alias(libs.plugins.conventionComposePluginId)
    alias(libs.plugins.conventionJacocoPluginId)
}

android {
    namespace = "com.sarim.husk.starter.presentation"
}

libraryConfig {
    moduleType.set(ModuleType.PRESENTATION)
    internalDependencies.set(listOf(":starter:domain"))
}
