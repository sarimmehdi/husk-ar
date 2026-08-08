import convention.utils.ModuleType

plugins {
    alias(libs.plugins.conventionLibraryPluginId)
    alias(libs.plugins.conventionJacocoPluginId)
}

android {
    namespace = "com.sarim.husk.session.data"
}

libraryConfig {
    moduleType.set(ModuleType.DATA)
    internalDependencies.set(
        listOf(
            ":session:domain",
        ),
    )
}

dependencies {
    implementation(libs.kotlinxCoroutinesCoreLibrary)

    androidTestImplementation(libs.androidxJunitLibrary)
    androidTestImplementation(libs.androidxTestCoreLibrary)
    androidTestImplementation(libs.androidxTestRunnerLibrary)
    androidTestImplementation(libs.kotlinxCoroutinesTestLibrary)
}
