plugins {
    alias(libs.plugins.conventionApplicationPluginId)
    alias(libs.plugins.androidApplicationPlugin)
}

dependencies {
    // The application convention wires :starter:di for the generated demo. Husk's own feature is
    // added here rather than there, so the convention stays the skeleton's rather than this app's.
    implementation(project(":session:di"))
}
