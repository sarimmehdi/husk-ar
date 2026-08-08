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
    // The data layer owns the adapter between the domain's ShellFitter port and the native solver,
    // which is why the solver is a dependency here and not of the domain.
    implementation(project(":solver"))
    implementation(libs.kotlinxCoroutinesCoreLibrary)

    androidTestImplementation(libs.androidxJunitLibrary)
    androidTestImplementation(libs.androidxTestCoreLibrary)
    androidTestImplementation(libs.androidxTestRunnerLibrary)
    androidTestImplementation(libs.kotlinxCoroutinesTestLibrary)
}
