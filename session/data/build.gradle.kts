import convention.utils.ModuleType

plugins {
    alias(libs.plugins.conventionLibraryPluginId)
    alias(libs.plugins.conventionJacocoPluginId)
    alias(libs.plugins.kspPlugin)
    alias(libs.plugins.roomPlugin)
}

android {
    namespace = "com.sarim.husk.session.data"

    // SessionRepositoryContract describes what any store must do, and is inherited by both the
    // in-memory implementation on the JVM and the Room one on a device. Shared through its own
    // source set rather than duplicated, because two copies would drift and the point of the
    // contract is that the two stores cannot.
    sourceSets {
        getByName("test") { kotlin.srcDir("src/sharedTest/java") }
        getByName("androidTest") { kotlin.srcDir("src/sharedTest/java") }
    }
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
    implementation(libs.bundles.roomBundle)
    ksp(libs.androidxRoomCompilerLibrary)

    androidTestImplementation(libs.androidxJunitLibrary)
    androidTestImplementation(libs.androidxTestCoreLibrary)
    androidTestImplementation(libs.androidxTestRunnerLibrary)
    androidTestImplementation(libs.androidxRoomTestingLibrary)
    androidTestImplementation(libs.kotlinxCoroutinesTestLibrary)
}
