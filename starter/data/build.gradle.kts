import convention.utils.ModuleType

plugins {
    alias(libs.plugins.conventionLibraryPluginId)
    alias(libs.plugins.conventionJacocoPluginId)
}

android {
    namespace = "com.sarim.husk.starter.data"
}

libraryConfig {
    moduleType.set(ModuleType.DATA)
    internalDependencies.set(listOf(":starter:domain"))
}

dependencies {
    implementation(libs.kotlinxCoroutinesCoreLibrary)
}
