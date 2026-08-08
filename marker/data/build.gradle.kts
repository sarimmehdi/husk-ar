import convention.utils.ModuleType

plugins {
    alias(libs.plugins.conventionLibraryPluginId)
    alias(libs.plugins.conventionJacocoPluginId)
    alias(libs.plugins.kspPlugin)
    alias(libs.plugins.roomPlugin)
}

android {
    namespace = "com.sarim.husk.marker.data"

    // The contract is inherited by both the in-memory store on the JVM and the Room one on a
    // device, so it lives where both can see it rather than being duplicated into each.
    sourceSets {
        getByName("test") { kotlin.srcDir("src/sharedTest/java") }
        getByName("androidTest") { kotlin.srcDir("src/sharedTest/java") }
    }
}

libraryConfig {
    moduleType.set(ModuleType.DATA)
    internalDependencies.set(
        listOf(
            ":marker:domain",
        ),
    )
}

dependencies {
    implementation(libs.kotlinxCoroutinesCoreLibrary)
    implementation(libs.bundles.roomBundle)
    ksp(libs.androidxRoomCompilerLibrary)

    androidTestImplementation(libs.androidxRoomTestingLibrary)

    androidTestImplementation(libs.androidxJunitLibrary)
    androidTestImplementation(libs.androidxTestCoreLibrary)
    androidTestImplementation(libs.androidxTestRunnerLibrary)
    androidTestImplementation(libs.kotlinxCoroutinesTestLibrary)
}
