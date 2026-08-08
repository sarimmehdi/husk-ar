import convention.utils.ModuleType

plugins {
    alias(libs.plugins.conventionLibraryPluginId)
    alias(libs.plugins.conventionComposePluginId)
}

android {
    namespace = "com.sarim.husk.session.di"
}

libraryConfig {
    moduleType.set(ModuleType.DI)
    internalDependencies.set(
        listOf(
            ":session:data",
            ":session:domain",
            ":session:presentation",
        ),
    )
}

dependencies {
    implementation(libs.koinCoreLibrary)
    implementation(libs.koinCoreCoroutinesLibrary)
    implementation(libs.koinAndroidLibrary)

    implementation(libs.koinAndroidxComposeLibrary)
    implementation(libs.koinAndroidxComposeNavigationLibrary)
    implementation(libs.bundles.navBundle)
    implementation(project(":nav"))
}
