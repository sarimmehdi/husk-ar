import convention.utils.ModuleType

plugins {
    alias(libs.plugins.conventionLibraryPluginId)
    alias(libs.plugins.conventionJacocoPluginId)
}

android {
    namespace = "com.sarim.husk.starter.domain"
}

libraryConfig {
    moduleType.set(ModuleType.DOMAIN)
}
