import convention.utils.ModuleType

plugins {
    alias(libs.plugins.conventionLibraryPluginId)
    alias(libs.plugins.conventionJacocoPluginId)
}

android {
    namespace = "com.sarim.husk.session.domain"
}

libraryConfig {
    moduleType.set(ModuleType.DOMAIN)
}

dependencies {
    // api rather than implementation: Pose, Ellipsoid and DualConic appear on the models
    // themselves, so anything reading a model needs them on its compile classpath too.
    api(project(":geometry"))
}
