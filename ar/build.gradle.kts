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
    api(libs.arSceneViewLibrary) {
        // SceneView still depends on kotlin-android-extensions-runtime, the 2020-era predecessor of
        // kotlin-parcelize-runtime. Both publish kotlinx.android.parcel.*, so any module using
        // @Parcelize alongside SceneView fails dex merging on duplicate classes.
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-android-extensions-runtime")
    }
    implementation(libs.arCoreLibrary)
}
