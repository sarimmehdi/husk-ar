import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.jvm.tasks.Jar
import org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask
import org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask

plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlintPlugin)
    alias(libs.plugins.detektPlugin)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
}

val stagedFilesProvider =
    providers
        .exec {
            commandLine(
                "git",
                "--no-pager",
                "diff",
                "--name-only",
                "--cached",
                "--diff-filter=ACMR",
                "--",
                "*.kt",
            )
        }.standardOutput.asText
        .map { outputText ->
            outputText
                .trim()
                .lineSequence()
                .filter { it.isNotBlank() }
                .map { layout.projectDirectory.file("../$it").asFile }
                .filter { it.exists() }
                .toList()
        }

if (hasProperty("precommit")) {
    val filesToLint = stagedFilesProvider.get()

    if (filesToLint.isNotEmpty()) {
        tasks.withType<Detekt>().configureEach {
            dependsOn(buildLogicJar)
            setSource(files(filesToLint))
            exclude("**/*.gradle.kts")
        }

        tasks.withType<BaseKtLintCheckTask>().configureEach {
            setSource(files(filesToLint))
        }

        tasks.withType<KtLintFormatTask>().configureEach {
            setSource(files(filesToLint))
        }
    } else {
        tasks.withType<Detekt> { enabled = false }
        tasks.withType<BaseKtLintCheckTask> { enabled = false }
    }
}

group = "com.sarim.buildlogic"

val buildLogicJar = tasks.named<Jar>("jar")

dependencies {
    implementation(libs.androidGradlePluginLibrary)
    implementation(libs.kotlinGradlePluginLibrary)
    implementation(libs.kspGradlePluginLibrary)
    implementation(libs.roomGradlePluginLibrary)
    implementation(libs.detektGradlePluginLibrary)
    implementation(libs.ktlintGradlePluginLibrary)
    implementation(libs.baselineProfileGradlePluginLibrary)
    implementation(libs.androidPitestGradlePluginLibrary)
    implementation(libs.kotlinSerializationGradlePluginLibrary)
    implementation(libs.navGraphGradlePluginLibrary)
    implementation(libs.composeStabilityAnalyzerGradlePluginLibrary)
    implementation(libs.googleServicesGradlePluginLibrary)
    implementation(libs.sentryAndroidGradlePluginLibrary)
    compileOnly(libs.detektApiLibrary)
    compileOnly(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    testImplementation(libs.detektTestLibrary)
    testImplementation(platform(libs.junitBomLibrary))
    testImplementation(libs.junitJupiterLibrary)
    testRuntimeOnly(libs.junitPlatformLauncherLibrary)

    add(
        "detektPlugins",
        files(buildLogicJar),
    )
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        create(libs.versions.conventionLibraryPluginName.get()) {
            id =
                libs.plugins.conventionLibraryPluginId
                    .get()
                    .pluginId
            implementationClass = libs.versions.conventionLibraryPluginClass.get()
        }
        create(libs.versions.conventionApplicationPluginName.get()) {
            id =
                libs.plugins.conventionApplicationPluginId
                    .get()
                    .pluginId
            implementationClass = libs.versions.conventionApplicationPluginClass.get()
        }
        create(libs.versions.conventionPaparazziPluginName.get()) {
            id =
                libs.plugins.conventionPaparazziPluginId
                    .get()
                    .pluginId
            implementationClass = libs.versions.conventionPaparazziPluginClass.get()
        }
        create(libs.versions.conventionDataPluginName.get()) {
            id =
                libs.plugins.conventionDataPluginId
                    .get()
                    .pluginId
            implementationClass = libs.versions.conventionDataPluginClass.get()
        }
        create(libs.versions.conventionComposePluginName.get()) {
            id =
                libs.plugins.conventionComposePluginId
                    .get()
                    .pluginId
            implementationClass = libs.versions.conventionComposePluginClass.get()
        }
        create(libs.versions.conventionDiPluginName.get()) {
            id =
                libs.plugins.conventionDiPluginId
                    .get()
                    .pluginId
            implementationClass = libs.versions.conventionDiPluginClass.get()
        }
        create(libs.versions.conventionScreenshotPluginName.get()) {
            id =
                libs.plugins.conventionScreenshotPluginId
                    .get()
                    .pluginId
            implementationClass = libs.versions.conventionScreenshotPluginClass.get()
        }
        create(libs.versions.conventionJacocoPluginName.get()) {
            id =
                libs.plugins.conventionJacocoPluginId
                    .get()
                    .pluginId
            implementationClass = libs.versions.conventionJacocoPluginClass.get()
        }
        create(libs.versions.conventionJacocoAggregationPluginName.get()) {
            id =
                libs.plugins.conventionJacocoAggregationPluginId
                    .get()
                    .pluginId
            implementationClass = libs.versions.conventionJacocoAggregationPluginClass.get()
        }
        create(libs.versions.conventionBaselineProfilePluginName.get()) {
            id =
                libs.plugins.conventionBaselineProfilePluginId
                    .get()
                    .pluginId
            implementationClass = libs.versions.conventionBaselineProfilePluginClass.get()
        }
        create(libs.versions.conventionArchtestPluginName.get()) {
            id =
                libs.plugins.conventionArchtestPluginId
                    .get()
                    .pluginId
            implementationClass = libs.versions.conventionArchtestPluginClass.get()
        }
        create(libs.versions.conventionMutationPluginName.get()) {
            id =
                libs.plugins.conventionMutationPluginId
                    .get()
                    .pluginId
            implementationClass = libs.versions.conventionMutationPluginClass.get()
        }
    }
}
