import convention.utils.ModuleType

plugins {
    alias(libs.plugins.conventionLibraryPluginId)
    alias(libs.plugins.conventionJacocoPluginId)
}

android {
    namespace = "com.sarim.husk.marker.domain"
}

libraryConfig {
    moduleType.set(ModuleType.DOMAIN)
}

dependencies {
    implementation(libs.kotlinxCoroutinesCoreLibrary)
}
