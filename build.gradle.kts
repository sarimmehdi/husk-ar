plugins {
    id("io.github.sarimmehdi.clean-android-skeleton") version "0.1.2"
    alias(libs.plugins.androidApplicationPlugin) apply false
    alias(libs.plugins.kotlinComposePlugin) apply false
    alias(libs.plugins.ktlintPlugin) apply false
    alias(libs.plugins.detektPlugin) apply false
    alias(libs.plugins.kspPlugin) apply false
    alias(libs.plugins.kotlinSerializationPlugin) apply false
    alias(libs.plugins.androidLibraryPlugin) apply false
    alias(libs.plugins.roomPlugin) apply false
    alias(libs.plugins.paparazziPlugin) apply false
    alias(libs.plugins.conventionJacocoPluginId) apply false
    alias(libs.plugins.conventionJacocoAggregationPluginId)
    alias(libs.plugins.androidTestPlugin) apply false
    alias(libs.plugins.baselineprofilePlugin) apply false
    alias(libs.plugins.androidPitestPlugin) apply false
    alias(libs.plugins.composeStabilityAnalyzerPlugin) apply false
    alias(libs.plugins.navGraphPlugin) apply false
    alias(libs.plugins.googleServicesPlugin) apply false
    alias(libs.plugins.sentryAndroidGradlePlugin) apply false
}
