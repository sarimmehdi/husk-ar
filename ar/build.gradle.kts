import convention.utils.ModuleType

plugins {
    alias(libs.plugins.conventionLibraryPluginId)
    alias(libs.plugins.conventionComposePluginId)
    alias(libs.plugins.conventionJacocoPluginId)
}

android {
    namespace = "com.sarim.husk.ar"
}

// PURE means the convention injects nothing, not that the module is free of platform types. This
// one is the opposite of pure -- it is where Filament and ARCore live -- but it earns every
// dependency below explicitly rather than inheriting a layer's defaults.
libraryConfig {
    moduleType.set(ModuleType.PURE)
}

dependencies {
    api(project(":geometry"))
    api(libs.arSceneViewLibrary)
    implementation(libs.arCoreLibrary)
}
